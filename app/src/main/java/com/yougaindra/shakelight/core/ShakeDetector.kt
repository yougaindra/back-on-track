package com.yougaindra.shakelight.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ShakeDetector(
    context: Context,
    val stateMachine: ShakeStateMachine = ShakeStateMachine(),
    private val onShakeTriggered: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private val _currentDeltaA = MutableStateFlow(0f)
    val currentDeltaA: StateFlow<Float> = _currentDeltaA.asStateFlow()

    private val _isNearPocket = MutableStateFlow(false)
    val isNearPocket: StateFlow<Boolean> = _isNearPocket.asStateFlow()

    private var isListening = false

    fun startListening() {
        if (isListening) return
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        isListening = true
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        stateMachine.reset()
        _currentDeltaA.value = 0f
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                val deltaA = kotlin.math.abs(magnitude - ShakeStateMachine.STANDARD_GRAVITY)
                _currentDeltaA.value = deltaA

                val triggered = stateMachine.onDeltaSample(deltaA, System.currentTimeMillis())
                if (triggered) {
                    onShakeTriggered()
                }
            }

            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = event.sensor.maximumRange
                // If distance is near (typically 0 or < 5cm / maxRange), phone is covered or in pocket
                val isCovered = distance < 3.0f || (maxRange > 0 && distance < maxRange)
                stateMachine.isPocketCovered = isCovered
                _isNearPocket.value = isCovered
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
