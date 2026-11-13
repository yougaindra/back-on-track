package com.yougaindra.shakelight

import com.yougaindra.shakelight.core.Sensitivity
import com.yougaindra.shakelight.core.TwistStateMachine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwistDetectorTest {

    @Test
    fun `single rotation in one direction does not trigger camera`() {
        val tm = TwistStateMachine(sensitivity = Sensitivity.MEDIUM)
        // Medium threshold is 4.2 rad/s
        val triggered = tm.onSample(omegaX = 0f, omegaY = 5.0f, omegaZ = 0f, timeNowMs = 1000L)
        assertFalse("Single direction rotation should not trigger", triggered)
    }

    @Test
    fun `double twist with rapid direction reversals triggers camera`() {
        val tm = TwistStateMachine(sensitivity = Sensitivity.MEDIUM)

        // Rotation 1: Positive roll (+5.0 rad/s) at 1000ms
        val r1 = tm.onSample(0f, 5.0f, 0f, 1000L)
        assertFalse(r1)

        // Reversal 1: Negative roll (-5.0 rad/s) at 1200ms (delta 200ms, within 400ms window)
        val r2 = tm.onSample(0f, -5.0f, 0f, 1200L)
        assertFalse(r2)

        // Reversal 2: Back to Positive roll (+5.2 rad/s) at 1400ms (delta 200ms)
        val r3 = tm.onSample(0f, 5.2f, 0f, 1400L)
        assertTrue("Double wrist twist reversal sequence should trigger camera", r3)
    }

    @Test
    fun `rotations below threshold do not trigger camera`() {
        val tm = TwistStateMachine(sensitivity = Sensitivity.MEDIUM)
        // Sub-threshold angular velocity (< 4.2 rad/s)
        tm.onSample(0f, 2.5f, 0f, 1000L)
        tm.onSample(0f, -2.5f, 0f, 1200L)
        val r3 = tm.onSample(0f, 2.5f, 0f, 1400L)
        assertFalse("Gentle turns below threshold must not trigger", r3)
    }

    @Test
    fun `slow reversals exceeding window timeout reset without triggering`() {
        val tm = TwistStateMachine(sensitivity = Sensitivity.MEDIUM)

        // Rotation 1
        tm.onSample(0f, 5.0f, 0f, 1000L)

        // Reversal after 600ms (> 400ms max reversal window)
        val r2 = tm.onSample(0f, -5.0f, 0f, 1600L)
        assertFalse("Slow reversal should reset timing window", r2)

        // Subsequent reversal after 200ms from the reset (only 2 counts so far)
        val r3 = tm.onSample(0f, 5.0f, 0f, 1800L)
        assertFalse(r3)

        // Final 3rd count from the new start
        val r4 = tm.onSample(0f, -5.0f, 0f, 2000L)
        assertTrue("Subsequent proper sequence triggers", r4)
    }

    @Test
    fun `pocket guard suppresses twist trigger when pocket is covered`() {
        val tm = TwistStateMachine(sensitivity = Sensitivity.MEDIUM, pocketProtectionEnabled = true)
        tm.isPocketCovered = true

        tm.onSample(0f, 5.0f, 0f, 1000L)
        tm.onSample(0f, -5.0f, 0f, 1200L)
        val r3 = tm.onSample(0f, 5.0f, 0f, 1400L)
        assertFalse("Twist while in pocket must be suppressed", r3)
    }

    @Test
    fun `trigger cooldown suppresses rebound motion`() {
        val tm = TwistStateMachine(sensitivity = Sensitivity.MEDIUM)

        // Trigger double twist
        tm.onSample(0f, 5.0f, 0f, 1000L)
        tm.onSample(0f, -5.0f, 0f, 1200L)
        val triggered = tm.onSample(0f, 5.0f, 0f, 1400L)
        assertTrue(triggered)

        // Motion right after trigger within 1200ms cooldown
        val rebound = tm.onSample(0f, -5.5f, 0f, 1600L)
        assertFalse("Rebound twist within cooldown window must be blocked", rebound)
    }
}
