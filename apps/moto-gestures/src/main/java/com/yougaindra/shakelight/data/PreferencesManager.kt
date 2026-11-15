package com.yougaindra.shakelight.data

import android.content.Context
import android.content.SharedPreferences
import com.yougaindra.shakelight.core.GestureMode
import com.yougaindra.shakelight.core.Sensitivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val isServiceEnabled: Boolean = true,
    val sensitivity: Sensitivity = Sensitivity.MEDIUM,
    val gestureMode: GestureMode = GestureMode.DOUBLE_CHOP,
    val isCameraTwistEnabled: Boolean = true,
    val cameraSensitivity: Sensitivity = Sensitivity.MEDIUM,
    val pocketProtection: Boolean = true,
    val vibrateOnToggle: Boolean = true,
    val vibrateOnCamera: Boolean = true,
    val startOnBoot: Boolean = true,
    val autoTurnOffMinutes: Int = 5
)

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("shake_flashlight_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun loadSettings(): AppSettings {
        val sensName = prefs.getString("sensitivity", Sensitivity.MEDIUM.name) ?: Sensitivity.MEDIUM.name
        val sensitivity = try {
            Sensitivity.valueOf(sensName)
        } catch (_: Exception) {
            Sensitivity.MEDIUM
        }

        val camSensName = prefs.getString("camera_sensitivity", Sensitivity.MEDIUM.name) ?: Sensitivity.MEDIUM.name
        val cameraSensitivity = try {
            Sensitivity.valueOf(camSensName)
        } catch (_: Exception) {
            Sensitivity.MEDIUM
        }

        val modeName = prefs.getString("gesture_mode", GestureMode.DOUBLE_CHOP.name) ?: GestureMode.DOUBLE_CHOP.name
        val gestureMode = try {
            GestureMode.valueOf(modeName)
        } catch (_: Exception) {
            GestureMode.DOUBLE_CHOP
        }

        return AppSettings(
            isServiceEnabled = prefs.getBoolean("service_enabled", true),
            sensitivity = sensitivity,
            gestureMode = gestureMode,
            isCameraTwistEnabled = prefs.getBoolean("camera_twist_enabled", true),
            cameraSensitivity = cameraSensitivity,
            pocketProtection = prefs.getBoolean("pocket_protection", true),
            vibrateOnToggle = prefs.getBoolean("vibrate_on_toggle", true),
            vibrateOnCamera = prefs.getBoolean("vibrate_on_camera", true),
            startOnBoot = prefs.getBoolean("start_on_boot", true),
            autoTurnOffMinutes = prefs.getInt("auto_turn_off_minutes", 5)
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.edit()
            .putBoolean("service_enabled", newSettings.isServiceEnabled)
            .putString("sensitivity", newSettings.sensitivity.name)
            .putString("gesture_mode", newSettings.gestureMode.name)
            .putBoolean("camera_twist_enabled", newSettings.isCameraTwistEnabled)
            .putString("camera_sensitivity", newSettings.cameraSensitivity.name)
            .putBoolean("pocket_protection", newSettings.pocketProtection)
            .putBoolean("vibrate_on_toggle", newSettings.vibrateOnToggle)
            .putBoolean("vibrate_on_camera", newSettings.vibrateOnCamera)
            .putBoolean("start_on_boot", newSettings.startOnBoot)
            .putInt("auto_turn_off_minutes", newSettings.autoTurnOffMinutes)
            .apply()

        _settings.value = newSettings
    }
}
