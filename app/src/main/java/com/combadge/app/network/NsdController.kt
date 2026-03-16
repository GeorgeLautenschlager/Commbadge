package com.combadge.app.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.*

/**
 * Handles mDNS/DNS-SD service registration and peer discovery via Android NSD.
 *
 * Service type: _combadge._tcp.
 * Service name: the device's crew name.
 * TXT-equivalent: embedded in service name / resolved via NsdServiceInfo attributes.
 *
 * NSD on Android can be flaky, so we also perform periodic re-discovery every 30 seconds.
 */
class NsdController(
    private val context: Context,
    private val registry: PeerRegistry
) {

    companion object {
        private const val TAG = "NsdController"
        private const val SERVICE_TYPE = "_combadge._tcp."
        private const val REDISCOVERY_INTERVAL_MS = 30_000L
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    @Volatile private var crewName: String = ""
    @Volatile private var tcpPort: Int = 0
    @Volatile private var aliases: List<String> = emptyList()

    fun start(crewName: String, tcpPort: Int, aliases: List<String>) {
        this.crewName = crewName
        this.tcpPort = tcpPort
        this.aliases = aliases

        registerService()
        startDiscovery()
        startPeriodicRediscovery()
    }

    fun stop() {
        scope.cancel()

        try {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping discovery: ${e.message}")
        }

        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering service: ${e.message}")
        }

        discoveryListener = null
        registrationListener = null
    }

    private fun registerService() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = crewName
            serviceType = SERVICE_TYPE
            port = tcpPort
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.e(TAG, "Registration failed: $code")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.w(TAG, "Unregistration failed: $code")
            }

            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d(TAG, "Registered as '${info.serviceName}' on port $tcpPort")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register NSD service", e)
        }
    }

    private fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Discovery started")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceName.equals(crewName, ignoreCase = true)) return  // skip self
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                resolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                registry.remove(serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Stop discovery failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start NSD discovery", e)
        }
    }

    private fun resolve(serviceInfo: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
                Log.w(TAG, "Resolve failed for ${info.serviceName}: $code")
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress ?: return
                val peerAliases = try {
                    @Suppress("UNCHECKED_CAST")
                    (info.attributes["aliases"] as? ByteArray)
                        ?.toString(Charsets.UTF_8)
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }

                val peer = com.combadge.app.model.Peer(
                    name = info.serviceName,
                    aliases = peerAliases,
                    ipAddress = host,
                    port = info.port,
                    lastSeen = System.currentTimeMillis()
                )
                registry.addOrUpdate(peer)
                Log.d(TAG, "Resolved: ${peer.name} @ $host:${info.port}")
            }
        }

        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve service", e)
        }
    }

    private fun startPeriodicRediscovery() {
        scope.launch {
            while (isActive) {
                delay(REDISCOVERY_INTERVAL_MS)
                Log.d(TAG, "Periodic re-discovery triggered")
                registry.removeStale()

                // Restart discovery to refresh the roster
                try {
                    discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
                } catch (e: Exception) { /* ignore */ }
                delay(500)
                startDiscovery()
            }
        }
    }
}
