package com.yougaindra.shakelight.core

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FlashlightController(context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val handler = Handler(Looper.getMainLooper())

    private var cameraId: String? = null

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private var autoOffRunnable: Runnable? = null
    private var autoOffMinutes: Int = 5

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(camId: String, enabled: Boolean) {
            if (camId == cameraId) {
                _isTorchOn.value = enabled
                if (enabled) {
                    scheduleAutoOff()
                } else {
                    cancelAutoOff()
                }
            }
        }

        override fun onTorchModeUnavailable(camId: String) {
            if (camId == cameraId) {
                _isTorchOn.value = false
            }
        }
    }

    init {
        findCameraWithFlash()
        cameraManager.registerTorchCallback(torchCallback, handler)
    }

    private fun findCameraWithFlash() {
        try {
            // First priority: Back-facing camera with flash
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id
                    _isAvailable.value = true
                    return
                }
            }
            // Second priority: Any camera with flash
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                if (hasFlash) {
                    cameraId = id
                    _isAvailable.value = true
                    return
                }
            }
        } catch (e: Exception) {
            _isAvailable.value = false
        }
    }

    fun setAutoOffMinutes(minutes: Int) {
        autoOffMinutes = minutes
        if (_isTorchOn.value) {
            scheduleAutoOff()
        }
    }

    fun toggleTorch(): Boolean {
        return setTorch(!_isTorchOn.value)
    }

    fun setTorch(enable: Boolean): Boolean {
        val id = cameraId ?: return false
        return try {
            cameraManager.setTorchMode(id, enable)
            _isTorchOn.value = enable
            if (enable) {
                scheduleAutoOff()
            } else {
                cancelAutoOff()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun scheduleAutoOff() {
        cancelAutoOff()
        if (autoOffMinutes > 0) {
            val delayMs = autoOffMinutes * 60 * 1000L
            val runnable = Runnable {
                setTorch(false)
            }
            autoOffRunnable = runnable
            handler.postDelayed(runnable, delayMs)
        }
    }

    private fun cancelAutoOff() {
        autoOffRunnable?.let { handler.removeCallbacks(it) }
        autoOffRunnable = null
    }

    fun cleanUp() {
        cancelAutoOff()
        try {
            cameraManager.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {
        }
    }
}
