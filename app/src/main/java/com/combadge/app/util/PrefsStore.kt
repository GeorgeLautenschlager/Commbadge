package com.combadge.app.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "combadge_prefs")

class PrefsStore(private val context: Context) {

    companion object {
        private val KEY_CREW_NAME = stringPreferencesKey("crew_name")
        private val KEY_ALIASES = stringPreferencesKey("aliases")
        private val KEY_CHIRP_VOLUME = floatPreferencesKey("chirp_volume")
        private val KEY_HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        private val KEY_AUTO_ACCEPT = booleanPreferencesKey("auto_accept")
    }

    val crewName: Flow<String?> = context.dataStore.data
        .map { it[KEY_CREW_NAME] }

    val aliases: Flow<List<String>> = context.dataStore.data
        .map { prefs ->
            prefs[KEY_ALIASES]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: emptyList()
        }

    val chirpVolume: Flow<Float> = context.dataStore.data
        .map { it[KEY_CHIRP_VOLUME] ?: 1.0f }

    val hapticEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_HAPTIC_ENABLED] ?: true }

    val autoAccept: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_AUTO_ACCEPT] ?: true }

    suspend fun setCrewName(name: String) {
        context.dataStore.edit { it[KEY_CREW_NAME] = name }
    }

    suspend fun setAliases(aliases: List<String>) {
        context.dataStore.edit { it[KEY_ALIASES] = aliases.joinToString(",") }
    }

    suspend fun setChirpVolume(volume: Float) {
        context.dataStore.edit { it[KEY_CHIRP_VOLUME] = volume }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTIC_ENABLED] = enabled }
    }

    suspend fun setAutoAccept(autoAccept: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_ACCEPT] = autoAccept }
    }
}
