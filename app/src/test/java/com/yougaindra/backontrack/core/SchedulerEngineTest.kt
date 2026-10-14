package com.yougaindra.backontrack.core

import com.yougaindra.backontrack.model.ScheduleState
import com.yougaindra.backontrack.model.SessionStatus
import com.yougaindra.backontrack.model.UserSettings
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SchedulerEngineTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val baseSettings = UserSettings(
        intervalMinutes = 45,
        activeStart = "09:00",
        activeEnd = "17:00",
        activeDays = setOf(1, 2, 3, 4, 5),
        pauseOutsideHours = true
    )

    @Test
    fun `adds exact configured minutes when inside active hours`() {
        val wed10am = ZonedDateTime.of(2026, 10, 14, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val next = SchedulerEngine.computeNextFireTime(wed10am, baseSettings, zone)
        assertEquals(wed10am + 45 * 60_000L, next)
    }

    @Test
    fun `rolls over to next active morning when interval crosses activeEnd`() {
        val wed430pm = ZonedDateTime.of(2026, 10, 14, 16, 30, 0, 0, zone).toInstant().toEpochMilli()
        val next = SchedulerEngine.computeNextFireTime(wed430pm, baseSettings, zone)
        val nextZdt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(next), zone)

        assertEquals(15, nextZdt.dayOfMonth)
        assertEquals(9, nextZdt.hour)
        assertEquals(0, nextZdt.minute)
    }

    @Test
    fun `skips Saturday and Sunday when rolling past Friday activeEnd`() {
        val fri445pm = ZonedDateTime.of(2026, 10, 16, 16, 45, 0, 0, zone).toInstant().toEpochMilli()
        val next = SchedulerEngine.computeNextFireTime(fri445pm, baseSettings, zone)
        val nextZdt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(next), zone)

        assertEquals(1, nextZdt.dayOfWeek.value)
        assertEquals(9, nextZdt.hour)
    }

    @Test
    fun `reconcileOnRestart preserves future scheduleId without duplicating`() {
        val now = ZonedDateTime.of(2026, 10, 14, 11, 0, 0, 0, zone).toInstant().toEpochMilli()
        val future = now + 20 * 60_000L
        val state = ScheduleState(
            status = SessionStatus.RUNNING,
            nextFireAtMillis = future,
            scheduleId = "sched_$future"
        )

        val reconciled = SchedulerEngine.reconcileOnRestart(state, baseSettings, now, zone)
        assertFalse(reconciled.missedWhileClosed)
        assertEquals("sched_$future", reconciled.state.scheduleId)
    }
}
