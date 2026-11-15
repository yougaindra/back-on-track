package com.yougaindra.shakelight.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max

class ShakeDetector(
    context: Context,
    val shakeStateMachine: ShakeStateMachine = ShakeStateMachine(),
    val twistStateMachine: TwistStateMachine = TwistStateMachine(),
    private val onShakeTriggered: () -> Unit,
    private val onTwistTriggered: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private val _currentDeltaA = MutableStateFlow(0f)
    val currentDeltaA: StateFlow<Float> = _currentDeltaA.asStateFlow()

    private val _currentAngularSpeed = MutableStateFlow(0f)
    val currentAngularSpeed: StateFlow<Float> = _currentAngularSpeed.asStateFlow()

    private val _isNearPocket = MutableStateFlow(false)
    val isNearPocket: StateFlow<Boolean> = _isNearPocket.asStateFlow()

    val hasGyroscope: Boolean = gyroscope != null

    private var isListening = false

    fun startListening() {
        if (isListening) return
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
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
        shakeStateMachine.resetAll()
        twistStateMachine.reset()
        _currentDeltaA.value = 0f
        _currentAngularSpeed.value = 0f
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val now = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                val deltaA = abs(magnitude - ShakeStateMachine.STANDARD_GRAVITY)
                _currentDeltaA.value = deltaA

                val triggered = shakeStateMachine.onSample(x, y, z, now)
                if (triggered) {
                    onShakeTriggered()
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                _currentAngularSpeed.value = max(abs(gy), abs(gz))

                val triggered = twistStateMachine.onSample(gx, gy, gz, now)
                if (triggered) {
                    onTwistTriggered()
                }
            }

            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = event.sensor.maximumRange
                val isCovered = distance < 3.0f || (maxRange > 0 && distance < maxRange)
                shakeStateMachine.isPocketCovered = isCovered
                twistStateMachine.isPocketCovered = isCovered
                _isNearPocket.value = isCovered
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
