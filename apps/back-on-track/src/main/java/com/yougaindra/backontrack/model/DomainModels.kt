package com.yougaindra.backontrack.model

enum class ReminderStyle {
    SOUND_ONLY,
    QUESTION,
    INTERACTIVE
}

enum class SessionStatus {
    IDLE,
    RUNNING,
    PAUSED
}

enum class CheckInResponseType(val label: String) {
    ON_TRACK("On track"),
    DISTRACTED("Gentle reset"),
    BREAK("Intentional break"),
    REMIND_LATER("Snoozed"),
    DISMISSED("Not now"),
    SOUND_ONLY("Chime only")
}

data class UserSettings(
    val goals: Set<String> = setOf("focus"),
    val intervalMinutes: Int = 45,
    val activeStart: String = "09:00",
    val activeEnd: String = "17:30",
    val activeDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    val reminderStyle: ReminderStyle = ReminderStyle.INTERACTIVE,
    val pauseOutsideHours: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val speakTimeEnabled: Boolean = true,
    val speakIntentionEnabled: Boolean = false,
    val showIntentionInPrompt: Boolean = true,
    val alignToClockBoundaries: Boolean = true,
    val darkTheme: Boolean = false
)

data class DailyIntention(
    val dateKey: String = "",
    val primary: String = "Finish the first draft of the architecture overview",
    val secondary: List<String> = listOf("Review pull requests", "Take a short walk"),
    val nextSmallStep: String = ""
)

data class ScheduleState(
    val status: SessionStatus = SessionStatus.IDLE,
    val nextFireAtMillis: Long? = null,
    val remainingMillisWhenPaused: Long? = null,
    val scheduleId: String? = null
)

data class CheckInRecord(
    val id: String,
    val timestampMillis: Long,
    val response: CheckInResponseType,
    val intentionSnapshot: String,
    val nextSmallStep: String = ""
)
