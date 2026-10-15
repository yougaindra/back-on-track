package com.yougaindra.backontrack.core

import android.content.Context
import android.content.SharedPreferences
import com.yougaindra.backontrack.model.*
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("back_on_track_store_v1", Context.MODE_PRIVATE)

    fun isOnboarded(): Boolean = prefs.getBoolean("onboarded", false)
    fun setOnboarded(value: Boolean) = prefs.edit().putBoolean("onboarded", value).apply()

    fun loadSettings(): UserSettings {
        val raw = prefs.getString("settings_json", null) ?: return UserSettings()
        return try {
            val obj = JSONObject(raw)
            val goalsArr = obj.optJSONArray("goals")
            val goals = mutableSetOf<String>()
            if (goalsArr != null) {
                for (i in 0 until goalsArr.length()) goals.add(goalsArr.getString(i))
            }
            val daysArr = obj.optJSONArray("activeDays")
            val days = mutableSetOf<Int>()
            if (daysArr != null) {
                for (i in 0 until daysArr.length()) days.add(daysArr.getInt(i))
            }

            UserSettings(
                goals = if (goals.isEmpty()) setOf("focus") else goals,
                intervalMinutes = obj.optInt("intervalMinutes", 45),
                activeStart = obj.optString("activeStart", "09:00"),
                activeEnd = obj.optString("activeEnd", "17:30"),
                activeDays = if (days.isEmpty()) setOf(1, 2, 3, 4, 5) else days,
                reminderStyle = ReminderStyle.valueOf(
                    obj.optString("reminderStyle", ReminderStyle.INTERACTIVE.name)
                ),
                pauseOutsideHours = obj.optBoolean("pauseOutsideHours", true),
                soundEnabled = obj.optBoolean("soundEnabled", true),
                vibrationEnabled = obj.optBoolean("vibrationEnabled", true),
                speakTimeEnabled = obj.optBoolean("speakTimeEnabled", true),
                speakIntentionEnabled = obj.optBoolean("speakIntentionEnabled", false),
                showIntentionInPrompt = obj.optBoolean("showIntentionInPrompt", true),
                darkTheme = obj.optBoolean("darkTheme", false)
            )
        } catch (e: Exception) {
            UserSettings()
        }
    }

    fun saveSettings(settings: UserSettings) {
        val obj = JSONObject().apply {
            put("goals", JSONArray(settings.goals.toList()))
            put("intervalMinutes", settings.intervalMinutes)
            put("activeStart", settings.activeStart)
            put("activeEnd", settings.activeEnd)
            put("activeDays", JSONArray(settings.activeDays.toList()))
            put("reminderStyle", settings.reminderStyle.name)
            put("pauseOutsideHours", settings.pauseOutsideHours)
            put("soundEnabled", settings.soundEnabled)
            put("vibrationEnabled", settings.vibrationEnabled)
            put("speakTimeEnabled", settings.speakTimeEnabled)
            put("speakIntentionEnabled", settings.speakIntentionEnabled)
            put("showIntentionInPrompt", settings.showIntentionInPrompt)
            put("darkTheme", settings.darkTheme)
        }
        prefs.edit().putString("settings_json", obj.toString()).apply()
    }

    fun loadIntention(): DailyIntention {
        val raw = prefs.getString("intention_json", null) ?: return DailyIntention()
        return try {
            val obj = JSONObject(raw)
            val secArr = obj.optJSONArray("secondary")
            val secList = mutableListOf<String>()
            if (secArr != null) {
                for (i in 0 until secArr.length()) secList.add(secArr.getString(i))
            }
            DailyIntention(
                dateKey = obj.optString("dateKey", ""),
                primary = obj.optString("primary", "Stay present with my current priority"),
                secondary = secList,
                nextSmallStep = obj.optString("nextSmallStep", "")
            )
        } catch (e: Exception) {
            DailyIntention()
        }
    }

    fun saveIntention(intention: DailyIntention) {
        val obj = JSONObject().apply {
            put("dateKey", intention.dateKey)
            put("primary", intention.primary)
            put("secondary", JSONArray(intention.secondary))
            put("nextSmallStep", intention.nextSmallStep)
        }
        prefs.edit().putString("intention_json", obj.toString()).apply()
    }

    fun loadSchedule(): ScheduleState {
        val raw = prefs.getString("schedule_json", null) ?: return ScheduleState()
        return try {
            val obj = JSONObject(raw)
            ScheduleState(
                status = SessionStatus.valueOf(obj.optString("status", SessionStatus.IDLE.name)),
                nextFireAtMillis = if (obj.has("nextFireAtMillis")) obj.getLong("nextFireAtMillis") else null,
                remainingMillisWhenPaused = if (obj.has("remainingMillisWhenPaused")) obj.getLong("remainingMillisWhenPaused") else null,
                scheduleId = if (obj.has("scheduleId")) obj.getString("scheduleId") else null
            )
        } catch (e: Exception) {
            ScheduleState()
        }
    }

    fun saveSchedule(schedule: ScheduleState) {
        val obj = JSONObject().apply {
            put("status", schedule.status.name)
            schedule.nextFireAtMillis?.let { put("nextFireAtMillis", it) }
            schedule.remainingMillisWhenPaused?.let { put("remainingMillisWhenPaused", it) }
            schedule.scheduleId?.let { put("scheduleId", it) }
        }
        prefs.edit().putString("schedule_json", obj.toString()).apply()
    }

    fun loadHistory(): List<CheckInRecord> {
        val raw = prefs.getString("history_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<CheckInRecord>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                list.add(
                    CheckInRecord(
                        id = item.getString("id"),
                        timestampMillis = item.getLong("timestampMillis"),
                        response = CheckInResponseType.valueOf(item.getString("response")),
                        intentionSnapshot = item.optString("intentionSnapshot", ""),
                        nextSmallStep = item.optString("nextSmallStep", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun appendHistory(record: CheckInRecord) {
        val updated = listOf(record) + loadHistory().take(499)
        val arr = JSONArray()
        updated.forEach { rec ->
            arr.put(
                JSONObject().apply {
                    put("id", rec.id)
                    put("timestampMillis", rec.timestampMillis)
                    put("response", rec.response.name)
                    put("intentionSnapshot", rec.intentionSnapshot)
                    put("nextSmallStep", rec.nextSmallStep)
                }
            )
        }
        prefs.edit().putString("history_json", arr.toString()).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
