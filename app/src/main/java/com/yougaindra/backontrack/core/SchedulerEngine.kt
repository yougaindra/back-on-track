package com.yougaindra.backontrack.core

import com.yougaindra.backontrack.model.ScheduleState
import com.yougaindra.backontrack.model.SessionStatus
import com.yougaindra.backontrack.model.UserSettings
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.min

object SchedulerEngine {

    fun parseLocalTime(hhmm: String, fallback: LocalTime = LocalTime.of(9, 0)): LocalTime {
        return try {
            val parts = hhmm.trim().split(":")
            if (parts.size != 2) return fallback
            val h = parts[0].toInt()
            val m = parts[1].toInt()
            if (h !in 0..23 || m !in 0..59) fallback else LocalTime.of(h, m)
        } catch (e: Exception) {
            fallback
        }
    }

    fun isWithinActiveWindow(
        epochMillis: Long,
        settings: UserSettings,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val zdt = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        val activeDays = settings.activeDays.ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) }

        if (zdt.dayOfWeek.value !in activeDays) {
            return false
        }

        if (!settings.pauseOutsideHours) {
            return true
        }

        val currentMins = zdt.hour * 60 + zdt.minute
        val start = parseLocalTime(settings.activeStart, LocalTime.of(9, 0))
        val end = parseLocalTime(settings.activeEnd, LocalTime.of(17, 30))
        val startMins = start.hour * 60 + start.minute
        val endMins = end.hour * 60 + end.minute

        return if (startMins <= endMins) {
            currentMins in startMins until endMins
        } else {
            currentMins >= startMins || currentMins < endMins
        }
    }

    fun findNextActiveWindowStart(
        fromEpochMillis: Long,
        settings: UserSettings,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val startLocalTime = parseLocalTime(settings.activeStart, LocalTime.of(9, 0))
        val activeDays = settings.activeDays.ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) }
        val baseZdt = Instant.ofEpochMilli(fromEpochMillis).atZone(zoneId)

        for (dayOffset in 0..7) {
            val candidateDate = baseZdt.toLocalDate().plusDays(dayOffset.toLong())
            val candidateZdt = ZonedDateTime.of(candidateDate, startLocalTime, zoneId)
            val candidateMillis = candidateZdt.toInstant().toEpochMilli()

            if (candidateMillis > fromEpochMillis && candidateZdt.dayOfWeek.value in activeDays) {
                return candidateMillis
            }
        }

        val safeInterval = clampInterval(settings.intervalMinutes)
        return fromEpochMillis + safeInterval * 60_000L
    }

    fun computeNextFireTime(
        baseEpochMillis: Long,
        settings: UserSettings,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val safeInterval = clampInterval(settings.intervalMinutes)
        val rawCandidateMillis = baseEpochMillis + safeInterval * 60_000L

        if (!settings.pauseOutsideHours || isWithinActiveWindow(rawCandidateMillis, settings, zoneId)) {
            return rawCandidateMillis
        }

        return findNextActiveWindowStart(rawCandidateMillis, settings, zoneId)
    }

    data class ReconciliationResult(
        val state: ScheduleState,
        val missedWhileClosed: Boolean
    )

    fun reconcileOnRestart(
        schedule: ScheduleState,
        settings: UserSettings,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ReconciliationResult {
        if (schedule.status != SessionStatus.RUNNING) {
            return ReconciliationResult(schedule, false)
        }

        val existingTarget = schedule.nextFireAtMillis
        if (existingTarget != null && existingTarget > nowMillis) {
            return ReconciliationResult(schedule, false)
        }

        val nextFire = computeNextFireTime(nowMillis, settings, zoneId)
        val updatedState = schedule.copy(
            status = SessionStatus.RUNNING,
            nextFireAtMillis = nextFire,
            remainingMillisWhenPaused = null,
            scheduleId = "sched_$nextFire"
        )
        val missed = isWithinActiveWindow(nowMillis, settings, zoneId)
        return ReconciliationResult(updatedState, missed)
    }

    private fun clampInterval(minutes: Int): Int = min(240, max(5, minutes))
}
