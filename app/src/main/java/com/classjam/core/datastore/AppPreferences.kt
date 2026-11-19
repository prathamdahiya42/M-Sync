package com.classjam.core.datastore

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.classjam.core.model.NetworkMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "class_jam_prefs")

class AppPreferences(private val context: Context) {

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val NETWORK_MODE = stringPreferencesKey("network_mode")
        val MANUAL_SSID = stringPreferencesKey("manual_ssid")
        val MANUAL_PASSWORD = stringPreferencesKey("manual_password")
        val SERVER_PORT = intPreferencesKey("server_port")
        val MAX_CLIENTS = intPreferencesKey("max_clients")
        val MANUAL_OFFSET_MS = longPreferencesKey("manual_offset_ms")
        val KEEP_DOWNLOADED_SONGS = booleanPreferencesKey("keep_downloaded_songs")
    }

    val deviceIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEVICE_ID] ?: ""
    }

    val displayNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.DISPLAY_NAME] ?: getDefaultDeviceName()
    }

    val networkModeFlow: Flow<NetworkMode> = context.dataStore.data.map { prefs ->
        val modeStr = prefs[Keys.NETWORK_MODE] ?: NetworkMode.IN_APP_HOTSPOT.name
        try {
            NetworkMode.valueOf(modeStr)
        } catch (e: Exception) {
            NetworkMode.IN_APP_HOTSPOT
        }
    }

    val manualSsidFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.MANUAL_SSID] ?: ""
    }

    val manualPasswordFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.MANUAL_PASSWORD] ?: ""
    }

    val serverPortFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.SERVER_PORT] ?: 8080
    }

    val maxClientsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.MAX_CLIENTS] ?: 10
    }

    val manualOffsetMsFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.MANUAL_OFFSET_MS] ?: 0L
    }

    val keepDownloadedSongsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.KEEP_DOWNLOADED_SONGS] ?: false
    }

    suspend fun getOrCreateDeviceId(): String {
        val currentId = context.dataStore.data.map { it[Keys.DEVICE_ID] }.first()
        if (!currentId.isNullOrBlank()) {
            return currentId
        }
        val newId = UUID.randomUUID().toString()
        context.dataStore.edit { prefs ->
            prefs[Keys.DEVICE_ID] = newId
        }
        return newId
    }

    suspend fun setDisplayName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = name.trim()
        }
    }

    suspend fun setNetworkMode(mode: NetworkMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NETWORK_MODE] = mode.name
        }
    }

    suspend fun setManualWifiCredentials(ssid: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MANUAL_SSID] = ssid.trim()
            prefs[Keys.MANUAL_PASSWORD] = password.trim()
        }
    }

    suspend fun setServerPort(port: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_PORT] = port
        }
    }

    suspend fun setMaxClients(max: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MAX_CLIENTS] = max
        }
    }

    suspend fun setManualOffsetMs(offsetMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MANUAL_OFFSET_MS] = offsetMs
        }
    }

    suspend fun setKeepDownloadedSongs(keep: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.KEEP_DOWNLOADED_SONGS] = keep
        }
    }

    private fun getDefaultDeviceName(): String {
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model".trim()
        }
    }
}
