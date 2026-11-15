package com.yougaindra.backontrack

import com.yougaindra.backontrack.core.SchedulerEngine
import com.yougaindra.backontrack.model.UserSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class SchedulerEngineTest {

    @Test
    fun `parseLocalTime parses valid string correctly`() {
        val parsed = SchedulerEngine.parseLocalTime("14:30")
        assert(parsed == LocalTime.of(14, 30))
    }

    @Test
    fun `parseLocalTime falls back on invalid input`() {
        val fallback = LocalTime.of(9, 0)
        val parsed = SchedulerEngine.parseLocalTime("invalid", fallback)
        assert(parsed == fallback)
    }

    @Test
    fun `isWithinActiveWindow respects pauseOutsideHours`() {
        val settings = UserSettings(
            activeStart = "09:00",
            activeEnd = "17:00",
            pauseOutsideHours = false
        )
        // With pauseOutsideHours = false, any weekday/time returns true
        val inWindow = SchedulerEngine.isWithinActiveWindow(System.currentTimeMillis(), settings)
        assertTrue(inWindow)
    }
}
