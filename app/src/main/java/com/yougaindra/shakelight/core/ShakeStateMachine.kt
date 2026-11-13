package com.yougaindra.shakelight.core

import kotlin.math.abs

enum class Sensitivity(val label: String, val threshold: Float) {
    LOW("Low (Firm Chop · Walk-Proof)", 25.0f),
    MEDIUM("Medium (Standard Moto)", 19.5f),
    HIGH("High (Light Shake)", 15.0f)
}

enum class GestureMode(val label: String, val requiredChops: Int) {
    DOUBLE_CHOP("Double Chop (Moto Style)", 2),
    SINGLE_SHAKE("Single Shake", 1)
}

/**
 * Pure state machine detecting the Motorola "Chop-Chop" double-stroke gesture or single shake.
 * Separated from Android APIs so it can be 100% verified with unit tests.
 * Includes walking/gait cadence suppression to avoid false positives when walking fast or jogging.
 */
class ShakeStateMachine(
    var sensitivity: Sensitivity = Sensitivity.MEDIUM,
    var gestureMode: GestureMode = GestureMode.DOUBLE_CHOP,
    var pocketProtectionEnabled: Boolean = true
) {
    companion object {
        const val MIN_TIME_BETWEEN_CHOPS_MS = 160L
        const val MAX_CHOP_WINDOW_MS = 750L
        const val TRIGGER_COOLDOWN_MS = 900L
        const val STANDARD_GRAVITY = 9.80665f

        // Gait / walking detection parameters
        const val STEP_DETECTION_THRESHOLD = 7.5f     // deltaA threshold to detect footstep impact
        const val MIN_STEP_INTERVAL_MS = 220L         // fastest possible cadence (~270 steps/min)
        const val MAX_STEP_INTERVAL_MS = 700L         // slowest walking cadence (~85 steps/min)
        const val GAIT_HISTORY_WINDOW_MS = 2200L      // rolling window to analyze walking rhythm
        const val MIN_STEPS_FOR_GAIT = 3              // 3 consecutive steps = active walking pattern
    }

    private var chopCount = 0
    private var firstChopTime = 0L
    private var lastChopTime = 0L
    private var lastTriggerTime = 0L

    // Gait detection state
    private val recentStepTimes = mutableListOf<Long>()
    private var lastStepTime = 0L

    var isPocketCovered: Boolean = false

    /**
     * Checks if current user movement matches a sustained walking or running cadence.
     */
    val isWalkingCadenceActive: Boolean
        get() = recentStepTimes.size >= MIN_STEPS_FOR_GAIT

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
        val deltaA = abs(magnitude - STANDARD_GRAVITY)
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

        // 1. Maintain gait / walking step history
        updateGaitTracking(deltaA, timeNowMs)

        // If below sensitivity threshold, check if active window expired
        if (deltaA < sensitivity.threshold) {
            if (chopCount > 0 && timeNowMs - firstChopTime > MAX_CHOP_WINDOW_MS) {
                reset()
            }
            return false
        }

        // 2. Walking cadence suppression:
        // If the user has been rhythmically walking/running (3+ steps in cadence),
        // suppress step impacts. Only allow an exceptionally firm intentional chop (>= threshold * 1.35)
        // to override walking.
        if (isWalkingCadenceActive && deltaA < sensitivity.threshold * 1.35f) {
            chopCount = 0
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

    private fun updateGaitTracking(deltaA: Float, timeNowMs: Long) {
        // Purge expired step history
        val iterator = recentStepTimes.iterator()
        while (iterator.hasNext()) {
            if (timeNowMs - iterator.next() > GAIT_HISTORY_WINDOW_MS) {
                iterator.remove()
            }
        }

        // Detect new step impact
        if (deltaA >= STEP_DETECTION_THRESHOLD) {
            if (timeNowMs - lastStepTime >= MIN_STEP_INTERVAL_MS) {
                val interval = timeNowMs - lastStepTime
                if (lastStepTime == 0L || interval <= MAX_STEP_INTERVAL_MS) {
                    recentStepTimes.add(timeNowMs)
                } else {
                    // Cadence was broken (e.g. paused), restart step count
                    recentStepTimes.clear()
                    recentStepTimes.add(timeNowMs)
                }
                lastStepTime = timeNowMs
            }
        }
    }

    fun reset() {
        chopCount = 0
        firstChopTime = 0L
        lastChopTime = 0L
    }

    fun resetAll() {
        reset()
        recentStepTimes.clear()
        lastStepTime = 0L
    }
}
