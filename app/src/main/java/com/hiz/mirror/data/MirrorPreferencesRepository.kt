package com.hiz.mirror.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hiz.mirror.model.AppMode
import com.hiz.mirror.model.QualityPreset
import com.hiz.mirror.network.LanProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.mirrorDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hiz_mirror_preferences"
)

data class PersistedMirrorSettings(
    val lastMode: AppMode? = null,
    val qualityPreset: QualityPreset = QualityPreset.BALANCED,
    val manualIp: String = "",
    val manualPort: String = LanProtocol.DEFAULT_STREAM_PORT.toString(),
    val receiverName: String = "Hız TV Alıcısı"
)

class MirrorPreferencesRepository(private val context: Context) {

    private object Keys {
        val LAST_MODE = stringPreferencesKey("last_mode")
        val QUALITY_PRESET = stringPreferencesKey("quality_preset")
        val MANUAL_IP = stringPreferencesKey("manual_ip")
        val MANUAL_PORT = stringPreferencesKey("manual_port")
        val RECEIVER_NAME = stringPreferencesKey("receiver_name")
    }

    val settingsFlow: Flow<PersistedMirrorSettings> = context.mirrorDataStore.data.map { prefs ->
        PersistedMirrorSettings(
            lastMode = AppMode.fromStorageKey(prefs[Keys.LAST_MODE]),
            qualityPreset = QualityPreset.fromStorageKey(prefs[Keys.QUALITY_PRESET]),
            manualIp = prefs[Keys.MANUAL_IP].orEmpty(),
            manualPort = prefs[Keys.MANUAL_PORT] ?: LanProtocol.DEFAULT_STREAM_PORT.toString(),
            receiverName = prefs[Keys.RECEIVER_NAME]?.takeIf { it.isNotBlank() } ?: "Hız TV Alıcısı"
        )
    }

    suspend fun readSnapshot(): PersistedMirrorSettings = settingsFlow.first()

    suspend fun saveLastMode(mode: AppMode) {
        context.mirrorDataStore.edit { prefs ->
            prefs[Keys.LAST_MODE] = mode.storageKey
        }
    }

    suspend fun clearLastMode() {
        context.mirrorDataStore.edit { prefs ->
            prefs.remove(Keys.LAST_MODE)
        }
    }

    suspend fun saveQualityPreset(preset: QualityPreset) {
        context.mirrorDataStore.edit { prefs ->
            prefs[Keys.QUALITY_PRESET] = preset.storageKey
        }
    }

    suspend fun saveManualEndpoint(ip: String, port: String) {
        context.mirrorDataStore.edit { prefs ->
            prefs[Keys.MANUAL_IP] = ip.trim()
            prefs[Keys.MANUAL_PORT] = port.trim()
        }
    }

    suspend fun saveReceiverName(name: String) {
        context.mirrorDataStore.edit { prefs ->
            prefs[Keys.RECEIVER_NAME] = name.trim().ifEmpty { "Hız TV Alıcısı" }
        }
    }
}
