package com.yougaindra.shakelight.core

enum class Sensitivity(val label: String, val threshold: Float) {
    LOW("Low (Firm Chop)", 17.5f),
    MEDIUM("Medium (Standard)", 13.5f),
    HIGH("High (Light Shake)", 10.0f)
}

enum class GestureMode(val label: String, val requiredChops: Int) {
    DOUBLE_CHOP("Double Chop (Moto Style)", 2),
    SINGLE_SHAKE("Single Shake", 1)
}

/**
 * Pure state machine detecting the Motorola "Chop-Chop" double-stroke gesture or single shake.
 * Separated from Android APIs so it can be 100% verified with unit tests.
 */
class ShakeStateMachine(
    var sensitivity: Sensitivity = Sensitivity.MEDIUM,
    var gestureMode: GestureMode = GestureMode.DOUBLE_CHOP,
    var pocketProtectionEnabled: Boolean = true
) {
    companion object {
        const val MIN_TIME_BETWEEN_CHOPS_MS = 140L
        const val MAX_CHOP_WINDOW_MS = 800L
        const val TRIGGER_COOLDOWN_MS = 650L
        const val STANDARD_GRAVITY = 9.80665f
    }

    private var chopCount = 0
    private var firstChopTime = 0L
    private var lastChopTime = 0L
    private var lastTriggerTime = 0L

    var isPocketCovered: Boolean = false

    /**
     * Feed raw 3-axis acceleration.
     * Returns true when the gesture condition is met and triggered.
     */
    fun onSample(
        x: Float,
        y: Float,
        z: Float,
        timeNowMs: Long
    ): Boolean {
        val magnitude = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        val deltaA = kotlin.math.abs(magnitude - STANDARD_GRAVITY)
        return onDeltaSample(deltaA, timeNowMs)
    }

    /**
     * Feed pre-calculated acceleration deviation delta (|a - g|).
     */
    fun onDeltaSample(deltaA: Float, timeNowMs: Long): Boolean {
        // Enforce cooldown after a successful trigger to prevent bounce/rebound false triggers
        if (timeNowMs - lastTriggerTime < TRIGGER_COOLDOWN_MS) {
            return false
        }

        // Suppress if phone proximity sensor detects pocket or bag
        if (pocketProtectionEnabled && isPocketCovered) {
            reset()
            return false
        }

        // If below sensitivity threshold, check if active window expired
        if (deltaA < sensitivity.threshold) {
            if (chopCount > 0 && timeNowMs - firstChopTime > MAX_CHOP_WINDOW_MS) {
                reset()
            }
            return false
        }

        // Must respect minimum separation between chops to avoid 1 long motion counting as 2
        if (timeNowMs - lastChopTime < MIN_TIME_BETWEEN_CHOPS_MS) {
            return false
        }

        // Single Shake Mode
        if (gestureMode == GestureMode.SINGLE_SHAKE) {
            lastTriggerTime = timeNowMs
            reset()
            return true
        }

        // Double Chop Mode (Moto Style)
        if (chopCount == 0 || timeNowMs - firstChopTime > MAX_CHOP_WINDOW_MS) {
            // First chop
            firstChopTime = timeNowMs
            lastChopTime = timeNowMs
            chopCount = 1
            return false
        } else {
            // Second chop within window!
            chopCount = 0
            lastTriggerTime = timeNowMs
            return true
        }
    }

    fun reset() {
        chopCount = 0
        firstChopTime = 0L
        lastChopTime = 0L
    }
}
