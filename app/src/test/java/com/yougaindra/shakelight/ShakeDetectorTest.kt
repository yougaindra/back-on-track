package com.yougaindra.shakelight

import com.yougaindra.shakelight.core.GestureMode
import com.yougaindra.shakelight.core.Sensitivity
import com.yougaindra.shakelight.core.ShakeStateMachine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeDetectorTest {

    @Test
    fun `single chop below threshold does not trigger double chop`() {
        val sm = ShakeStateMachine(sensitivity = Sensitivity.MEDIUM)
        // Medium threshold is 13.5
        val triggered = sm.onDeltaSample(deltaA = 8.0f, timeNowMs = 1000L)
        assertFalse(triggered)
    }

    @Test
    fun `two chops within 800ms with valid gap triggers double chop`() {
        val sm = ShakeStateMachine(
            sensitivity = Sensitivity.MEDIUM,
            gestureMode = GestureMode.DOUBLE_CHOP
        )

        // 1st chop at 1000ms
        val chop1 = sm.onDeltaSample(deltaA = 15.0f, timeNowMs = 1000L)
        assertFalse("First chop should not trigger yet", chop1)

        // 2nd chop at 1300ms (gap 300ms, within 800ms window, >140ms min gap)
        val chop2 = sm.onDeltaSample(deltaA = 16.0f, timeNowMs = 1300L)
        assertTrue("Second chop should trigger", chop2)
    }

    @Test
    fun `two chops too close do not count as two distinct chops`() {
        val sm = ShakeStateMachine(
            sensitivity = Sensitivity.MEDIUM,
            gestureMode = GestureMode.DOUBLE_CHOP
        )

        // 1st chop
        sm.onDeltaSample(deltaA = 15.0f, timeNowMs = 1000L)

        // Sub-motion at 1050ms (only 50ms later < 140ms min separation)
        val tooClose = sm.onDeltaSample(deltaA = 16.0f, timeNowMs = 1050L)
        assertFalse("Sub-motion within 50ms should be filtered", tooClose)

        // Proper 2nd chop at 1350ms
        val chop2 = sm.onDeltaSample(deltaA = 15.5f, timeNowMs = 1350L)
        assertTrue("Proper second chop should trigger", chop2)
    }

    @Test
    fun `two chops with gap exceeding 800ms times out and resets`() {
        val sm = ShakeStateMachine(
            sensitivity = Sensitivity.MEDIUM,
            gestureMode = GestureMode.DOUBLE_CHOP
        )

        // 1st chop at 1000ms
        sm.onDeltaSample(deltaA = 15.0f, timeNowMs = 1000L)

        // 2nd chop at 2000ms (1000ms later > 800ms window)
        val timedOut = sm.onDeltaSample(deltaA = 15.0f, timeNowMs = 2000L)
        assertFalse("Second chop after window timeout should reset as a new first chop", timedOut)

        // 3rd chop at 2300ms (300ms after the reset chop)
        val chopAfterReset = sm.onDeltaSample(deltaA = 15.0f, timeNowMs = 2300L)
        assertTrue("Chop following the new first chop should trigger", chopAfterReset)
    }

    @Test
    fun `single shake mode triggers on one chop`() {
        val sm = ShakeStateMachine(
            sensitivity = Sensitivity.MEDIUM,
            gestureMode = GestureMode.SINGLE_SHAKE
        )

        val triggered = sm.onDeltaSample(deltaA = 14.5f, timeNowMs = 1000L)
        assertTrue("Single shake mode triggers on first qualifying motion", triggered)
    }

    @Test
    fun `pocket guard suppresses triggers when pocket is covered`() {
        val sm = ShakeStateMachine(
            sensitivity = Sensitivity.MEDIUM,
            gestureMode = GestureMode.DOUBLE_CHOP,
            pocketProtectionEnabled = true
        )
        sm.isPocketCovered = true

        val chop1 = sm.onDeltaSample(deltaA = 16.0f, timeNowMs = 1000L)
        val chop2 = sm.onDeltaSample(deltaA = 16.0f, timeNowMs = 1300L)

        assertFalse("Chops while in pocket must be suppressed", chop1)
        assertFalse("Chops while in pocket must be suppressed", chop2)
    }

    @Test
    fun `trigger cooldown blocks immediate rebound false triggers`() {
        val sm = ShakeStateMachine(
            sensitivity = Sensitivity.MEDIUM,
            gestureMode = GestureMode.DOUBLE_CHOP
        )

        // Double chop triggers
        sm.onDeltaSample(deltaA = 15.0f, timeNowMs = 1000L)
        val triggered = sm.onDeltaSample(deltaA = 15.0f, timeNowMs = 1300L)
        assertTrue(triggered)

        // Immediate rebound shake 200ms after trigger (during 650ms cooldown)
        val rebound = sm.onDeltaSample(deltaA = 18.0f, timeNowMs = 1500L)
        assertFalse("Rebound shake within cooldown window should be blocked", rebound)
    }
}
