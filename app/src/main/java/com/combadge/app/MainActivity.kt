package com.combadge.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.combadge.app.model.CombadgeState
import com.combadge.app.speech.SpeechManager
import com.combadge.app.ui.screens.CombadgeScreen
import com.combadge.app.ui.screens.RegistrationScreen
import com.combadge.app.ui.screens.SettingsScreen
import com.combadge.app.ui.theme.LcarsTheme
import com.combadge.app.viewmodel.CombadgeViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: CombadgeViewModel by viewModels()

    private var multicastLock: WifiManager.MulticastLock? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filter { !it.value }.keys
        if (denied.isNotEmpty()) {
            Log.w(TAG, "Permissions denied: $denied")
        }
        // Attach speech manager after permissions granted
        attachSpeechManager()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        acquireMulticastLock()
        requestRequiredPermissions()

        setContent {
            LcarsTheme {
                CombadgeApp(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMulticastLock()
    }

    private fun requestRequiredPermissions() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val toRequest = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isEmpty()) {
            attachSpeechManager()
        } else {
            permissionLauncher.launch(toRequest.toTypedArray())
        }
    }

    private fun attachSpeechManager() {
        val speechManager = SpeechManager(this)
        viewModel.attachSpeechManager(speechManager)
    }

    private fun acquireMulticastLock() {
        try {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            multicastLock = wm?.createMulticastLock("combadge_mdns")?.apply {
                setReferenceCounted(true)
                acquire()
            }
            Log.d(TAG, "Multicast lock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire multicast lock", e)
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.release()
            multicastLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Could not release multicast lock", e)
        }
    }
}

// ------------------------------------------------------------------
// Compose Navigation Root
// ------------------------------------------------------------------

private object Routes {
    const val REGISTER = "register"
    const val MAIN = "main"
    const val SETTINGS = "settings"
}

@Composable
private fun CombadgeApp(viewModel: CombadgeViewModel) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val myName by viewModel.prefs.crewName.collectAsStateWithLifecycle(initialValue = null)
    val myAliases by viewModel.prefs.aliases.collectAsStateWithLifecycle(initialValue = emptyList())
    val volume by viewModel.prefs.chirpVolume.collectAsStateWithLifecycle(initialValue = 1.0f)
    val haptic by viewModel.prefs.hapticEnabled.collectAsStateWithLifecycle(initialValue = true)
    val autoAccept by viewModel.prefs.autoAccept.collectAsStateWithLifecycle(initialValue = true)

    // Navigate based on state
    LaunchedEffect(state) {
        when (state) {
            is CombadgeState.NotRegistered -> {
                if (navController.currentDestination?.route != Routes.REGISTER) {
                    navController.navigate(Routes.REGISTER) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
            else -> {
                if (navController.currentDestination?.route == Routes.REGISTER) {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.MAIN) {

        composable(Routes.REGISTER) {
            RegistrationScreen(
                onRegister = { name ->
                    scope.launch {
                        viewModel.saveCrewName(name)
                        navController.navigate(Routes.MAIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.MAIN) {
            CombadgeScreen(
                state = state,
                peers = peers,
                myName = myName ?: "",
                onTap = { viewModel.onCombadgeTap() },
                onLongPress = { navController.navigate(Routes.SETTINGS) },
                onDisambiguationSelect = { peer -> viewModel.onDisambiguationSelect(peer) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                initialName = myName ?: "",
                initialAliases = myAliases.joinToString(", "),
                initialVolume = volume,
                initialHaptic = haptic,
                initialAutoAccept = autoAccept,
                onSave = { name, aliases, vol, hap, auto ->
                    scope.launch {
                        viewModel.saveSettings(name, aliases, vol, hap, auto)
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
