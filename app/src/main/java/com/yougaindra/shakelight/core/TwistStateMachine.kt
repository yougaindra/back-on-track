package com.yougaindra.shakelight.core

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure state machine detecting the Motorola "Quick Capture" double wrist-twist gesture.
 * Uses Gyroscope angular velocity (rad/s) across the roll (Y) and yaw (Z) axes.
 * Completely decoupled from Android Context so it can be 100% verified with unit tests.
 */
class TwistStateMachine(
    var sensitivity: Sensitivity = Sensitivity.MEDIUM,
    var pocketProtectionEnabled: Boolean = true
) {
    companion object {
        const val MIN_TIME_BETWEEN_REVERSALS_MS = 120L
        const val MAX_REVERSAL_WINDOW_MS = 400L
        const val MAX_GESTURE_WINDOW_MS = 900L
        const val TRIGGER_COOLDOWN_MS = 1200L
    }

    // Angular velocity thresholds (rad/s) for gyro twist
    val angularThreshold: Float
        get() = when (sensitivity) {
            Sensitivity.LOW -> 5.8f
            Sensitivity.MEDIUM -> 4.2f
            Sensitivity.HIGH -> 3.0f
        }

    private var lastDirection = 0 // +1 for positive rotation, -1 for negative rotation
    private var reversalCount = 0
    private var firstReversalTime = 0L
    private var lastReversalTime = 0L
    private var lastTriggerTime = 0L

    var isPocketCovered: Boolean = false

    /**
     * Feed gyroscope angular velocity (rad/s) for x, y, and z at timeNowMs.
     * Returns true when the double-twist gesture is completed.
     */
    fun onSample(
        omegaX: Float,
        omegaY: Float,
        omegaZ: Float,
        timeNowMs: Long
    ): Boolean {
        // Enforce cooldown after a successful trigger
        if (timeNowMs - lastTriggerTime < TRIGGER_COOLDOWN_MS) {
            return false
        }

        // Suppress if phone is covered in pocket or purse
        if (pocketProtectionEnabled && isPocketCovered) {
            reset()
            return false
        }

        // Wrist twist combines roll (Y) and yaw (Z) rotation
        val dominantRot = if (abs(omegaZ) >= abs(omegaY)) omegaZ else omegaY
        val currentSpeed = abs(dominantRot)
        val thresh = angularThreshold

        if (currentSpeed < thresh) {
            // Check if active gesture window timed out
            if (reversalCount > 0 && timeNowMs - firstReversalTime > MAX_GESTURE_WINDOW_MS) {
                reset()
            }
            return false
        }

        val currentDirection = if (dominantRot > 0) 1 else -1

        // First strong rotation in either direction
        if (lastDirection == 0) {
            lastDirection = currentDirection
            lastReversalTime = timeNowMs
            firstReversalTime = timeNowMs
            reversalCount = 1
            return false
        }

        // Check if rotation has reversed direction
        if (currentDirection != lastDirection) {
            val deltaFromLast = timeNowMs - lastReversalTime

            // Must satisfy minimum separation (not sensor noise) and maximum window
            if (deltaFromLast in MIN_TIME_BETWEEN_REVERSALS_MS..MAX_REVERSAL_WINDOW_MS) {
                lastDirection = currentDirection
                lastReversalTime = timeNowMs
                reversalCount++

                // Motorola Quick Capture requires 2 direction reversals (flick forward -> flick back -> return)
                if (reversalCount >= 3) {
                    reset()
                    lastTriggerTime = timeNowMs
                    return true
                }
            } else if (deltaFromLast > MAX_REVERSAL_WINDOW_MS) {
                // Window timed out; restart as new first motion
                lastDirection = currentDirection
                firstReversalTime = timeNowMs
                lastReversalTime = timeNowMs
                reversalCount = 1
            }
        }

        return false
    }

    fun reset() {
        lastDirection = 0
        reversalCount = 0
        firstReversalTime = 0L
        lastReversalTime = 0L
    }
}
