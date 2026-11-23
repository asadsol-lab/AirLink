package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.StreamQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.random.Random

private val Context.airLinkDataStore by preferencesDataStore(name = "airlink_preferences")

data class DeviceConfig(
    val deviceId: String,
    val securityPin: String,
    val signalingUrl: String,
    val streamQuality: StreamQuality,
    val requirePinApproval: Boolean
)

class DevicePreferences(private val context: Context) {

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val SECURITY_PIN = stringPreferencesKey("security_pin")
        val SIGNALING_URL = stringPreferencesKey("signaling_url")
        val STREAM_QUALITY = stringPreferencesKey("stream_quality")
        val REQUIRE_PIN_APPROVAL = booleanPreferencesKey("require_pin_approval")
    }

    val configFlow: Flow<DeviceConfig> = context.airLinkDataStore.data.map { prefs ->
        val id = prefs[Keys.DEVICE_ID] ?: DEFAULT_TEMP_ID
        val pin = prefs[Keys.SECURITY_PIN] ?: DEFAULT_TEMP_PIN
        val url = prefs[Keys.SIGNALING_URL] ?: DEFAULT_SIGNALING_URL
        val quality = prefs[Keys.STREAM_QUALITY]?.let { name ->
            StreamQuality.entries.firstOrNull { it.name == name }
        } ?: StreamQuality.HD_720_60
        val requireApproval = prefs[Keys.REQUIRE_PIN_APPROVAL] ?: true
        DeviceConfig(
            deviceId = id,
            securityPin = pin,
            signalingUrl = url,
            streamQuality = quality,
            requirePinApproval = requireApproval
        )
    }

    suspend fun ensureInitialized(): DeviceConfig {
        val current = context.airLinkDataStore.data.first()
        var changed = false
        val id = current[Keys.DEVICE_ID] ?: generateNineDigitId().also { changed = true }
        val pin = current[Keys.SECURITY_PIN] ?: generateFourDigitPin().also { changed = true }
        val url = current[Keys.SIGNALING_URL] ?: DEFAULT_SIGNALING_URL
        val qualityName = current[Keys.STREAM_QUALITY] ?: StreamQuality.HD_720_60.name
        val requireApproval = current[Keys.REQUIRE_PIN_APPROVAL] ?: true

        if (changed) {
            context.airLinkDataStore.edit { prefs ->
                if (!prefs.contains(Keys.DEVICE_ID)) prefs[Keys.DEVICE_ID] = id
                if (!prefs.contains(Keys.SECURITY_PIN)) prefs[Keys.SECURITY_PIN] = pin
            }
        }

        return DeviceConfig(
            deviceId = id,
            securityPin = pin,
            signalingUrl = url,
            streamQuality = StreamQuality.entries.firstOrNull { it.name == qualityName }
                ?: StreamQuality.HD_720_60,
            requirePinApproval = requireApproval
        )
    }

    suspend fun updateAssignedDeviceId(deviceId: String) {
        context.airLinkDataStore.edit { prefs ->
            prefs[Keys.DEVICE_ID] = deviceId
        }
    }

    suspend fun regenerateSecurityPin(): String {
        val newPin = generateFourDigitPin()
        context.airLinkDataStore.edit { prefs ->
            prefs[Keys.SECURITY_PIN] = newPin
        }
        return newPin
    }

    suspend fun updateSecurityPin(pin: String) {
        val sanitized = pin.filter { it.isDigit() }.take(4).padStart(4, '0')
        context.airLinkDataStore.edit { prefs ->
            prefs[Keys.SECURITY_PIN] = sanitized
        }
    }

    suspend fun updateSignalingUrl(url: String) {
        context.airLinkDataStore.edit { prefs ->
            prefs[Keys.SIGNALING_URL] = url.trim()
        }
    }

    suspend fun updateStreamQuality(quality: StreamQuality) {
        context.airLinkDataStore.edit { prefs ->
            prefs[Keys.STREAM_QUALITY] = quality.name
        }
    }

    suspend fun updateRequirePinApproval(requireApproval: Boolean) {
        context.airLinkDataStore.edit { prefs ->
            prefs[Keys.REQUIRE_PIN_APPROVAL] = requireApproval
        }
    }

    companion object {
        const val DEFAULT_SIGNALING_URL = "ws://10.0.2.2:8080"
        private const val DEFAULT_TEMP_ID = "000000000"
        private const val DEFAULT_TEMP_PIN = "0000"

        fun generateNineDigitId(): String =
            Random.nextInt(100_000_000, 1_000_000_000).toString()

        fun generateFourDigitPin(): String =
            Random.nextInt(1000, 10000).toString()
    }
}
