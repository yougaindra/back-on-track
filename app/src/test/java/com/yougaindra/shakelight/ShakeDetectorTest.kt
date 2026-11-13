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
        // Medium threshold is 19.5f; delta 12.0f should not trigger
        val triggered = sm.onDeltaSample(deltaA = 12.0f, timeNowMs = 1000L)
        assertFalse(triggered)
    }

    @Test
    fun `two chops within 750ms with valid gap triggers double chop`() {
        val sm = ShakeStateMachine(
            sensitivity = Sensitivity.MEDIUM,
            gestureMode = GestureMode.DOUBLE_CHOP
        )

        // 1st chop at 1000ms
        val chop1 = sm.onDeltaSample(deltaA = 21.0f, timeNowMs = 1000L)
        assertFalse("First chop should not trigger yet", chop1)

        // 2nd chop at 1300ms (gap 300ms, within 750ms window, >160ms min gap)
        val chop2 = sm.onDeltaSample(deltaA = 22.0f, timeNowMs = 1300L)
        assertTrue("Second chop should trigger", chop2)
    }

    @Test
    fun `two chops too close do not count as two distinct chops`() {
        val sm = ShakeStateMachine(
            sensitivity = Sensitivity.MEDIUM,
            gestureMode = GestureMode.DOUBLE_CHOP
        )

        // 1st chop
        sm.onDeltaSample(deltaA = 21.0f, timeNowMs = 1000L)

        // Sub-motion at 1050ms (only 50ms later < 160ms min separation)
        val tooClose = sm.onDeltaSample(deltaA = 22.0f, timeNowMs = 1050L)
        assertFalse("Sub-motion within 50ms should be filtered", tooClose)

        // Proper 2nd chop at 1350ms
        val chop2 = sm.onDeltaSample(deltaA = 21.5f, timeNowMs = 1350L)
        assertTrue("Proper second chop should trigger", chop2)
    }

    @Test
    fun `two chops with gap exceeding 750ms times out and resets`() {
        val sm = ShakeStateMachine(
            sensitivity = Sensitivity.MEDIUM,
            gestureMode = GestureMode.DOUBLE_CHOP
        )

        // 1st chop at 1000ms
        sm.onDeltaSample(deltaA = 21.0f, timeNowMs = 1000L)

        // 2nd chop at 2000ms (1000ms later > 750ms window)
        val timedOut = sm.onDeltaSample(deltaA = 21.0f, timeNowMs = 2000L)
        assertFalse("Second chop after window timeout should reset as a new first chop", timedOut)

        // 3rd chop at 2300ms (300ms after the reset chop)
        val chopAfterReset = sm.onDeltaSample(deltaA = 21.0f, timeNowMs = 2300L)
        assertTrue("Chop following the new first chop should trigger", chopAfterReset)
    }

    @Test
    fun `single shake mode triggers on one chop`() {
        val sm = ShakeStateMachine(
            sensitivity = Sensitivity.MEDIUM,
            gestureMode = GestureMode.SINGLE_SHAKE
        )

        val triggered = sm.onDeltaSample(deltaA = 21.0f, timeNowMs = 1000L)
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

        val chop1 = sm.onDeltaSample(deltaA = 22.0f, timeNowMs = 1000L)
        val chop2 = sm.onDeltaSample(deltaA = 22.0f, timeNowMs = 1300L)

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
        sm.onDeltaSample(deltaA = 21.0f, timeNowMs = 1000L)
        val triggered = sm.onDeltaSample(deltaA = 21.0f, timeNowMs = 1300L)
        assertTrue(triggered)

        // Immediate rebound shake 200ms after trigger (during 900ms cooldown)
        val rebound = sm.onDeltaSample(deltaA = 25.0f, timeNowMs = 1500L)
        assertFalse("Rebound shake within cooldown window should be blocked", rebound)
    }

    @Test
    fun `fast walking sequence does not trigger flashlight`() {
        val sm = ShakeStateMachine(
            sensitivity = Sensitivity.MEDIUM,
            gestureMode = GestureMode.DOUBLE_CHOP
        )

        // Simulate 8 fast walking steps (~135 steps/min, 440ms interval, ~12 m/s^2 impact delta)
        var stepTime = 1000L
        for (i in 1..8) {
            val triggered = sm.onDeltaSample(deltaA = 12.5f, timeNowMs = stepTime)
            assertFalse("Fast walking step $i should not trigger gesture", triggered)
            stepTime += 440L
        }
    }

    @Test
    fun `gait cadence suppression blocks accidental high-impact steps while running`() {
        val sm = ShakeStateMachine(
            sensitivity = Sensitivity.MEDIUM,
            gestureMode = GestureMode.DOUBLE_CHOP
        )

        // Step 1, 2, 3 establish walking/running rhythm (350ms interval)
        sm.onDeltaSample(deltaA = 10.0f, timeNowMs = 1000L)
        sm.onDeltaSample(deltaA = 11.0f, timeNowMs = 1350L)
        sm.onDeltaSample(deltaA = 11.5f, timeNowMs = 1700L)

        assertTrue("Gait cadence should now be recognized as active", sm.isWalkingCadenceActive)

        // Step 4 and 5 are high impacts above standard threshold (20.5f > 19.5f)
        val step4 = sm.onDeltaSample(deltaA = 20.5f, timeNowMs = 2050L)
        assertFalse("High impact step 4 during active gait should be suppressed", step4)

        val step5 = sm.onDeltaSample(deltaA = 21.0f, timeNowMs = 2400L)
        assertFalse("High impact step 5 during active gait should be suppressed", step5)
    }

    @Test
    fun `firm intentional chop successfully triggers even while walking`() {
        val sm = ShakeStateMachine(
            sensitivity = Sensitivity.MEDIUM,
            gestureMode = GestureMode.DOUBLE_CHOP
        )

        // Active gait pattern established
        sm.onDeltaSample(deltaA = 10.0f, timeNowMs = 1000L)
        sm.onDeltaSample(deltaA = 10.0f, timeNowMs = 1400L)
        sm.onDeltaSample(deltaA = 10.0f, timeNowMs = 1800L)
        assertTrue(sm.isWalkingCadenceActive)

        // User intentionally delivers strong Moto chops (>= threshold * 1.35)
        val chop1 = sm.onDeltaSample(deltaA = 28.0f, timeNowMs = 2200L)
        assertFalse("First firm chop registers", chop1)

        val chop2 = sm.onDeltaSample(deltaA = 29.0f, timeNowMs = 2500L)
        assertTrue("Second firm chop triggers successfully even during walk", chop2)
    }
}
