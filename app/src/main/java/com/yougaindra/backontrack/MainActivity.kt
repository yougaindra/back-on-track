package com.yougaindra.backontrack

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yougaindra.backontrack.core.AlarmAndNotificationCoordinator
import com.yougaindra.backontrack.core.LocalStore
import com.yougaindra.backontrack.core.SchedulerEngine
import com.yougaindra.backontrack.model.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var store: LocalStore
    private lateinit var coordinator: AlarmAndNotificationCoordinator

    private var openCheckInState = mutableStateOf(false)
    private var openResetStepState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LocalStore(this)
        coordinator = AlarmAndNotificationCoordinator(this)

        handleIncomingIntent(intent)

        setContent {
            BackOnTrackApp(
                store = store,
                coordinator = coordinator,
                externalCheckInTrigger = openCheckInState.value,
                externalResetStepTrigger = openResetStepState.value,
                onConsumeExternalTrigger = {
                    openCheckInState.value = false
                    openResetStepState.value = false
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(AlarmAndNotificationCoordinator.EXTRA_OPEN_CHECKIN_MODAL, false) == true) {
            openCheckInState.value = true
            openResetStepState.value =
                intent.getBooleanExtra(AlarmAndNotificationCoordinator.EXTRA_OPEN_RESET_STEP, false)
        }
    }
}

@Composable
fun BackOnTrackApp(
    store: LocalStore,
    coordinator: AlarmAndNotificationCoordinator,
    externalCheckInTrigger: Boolean,
    externalResetStepTrigger: Boolean,
    onConsumeExternalTrigger: () -> Unit
) {
    var onboarded by remember { mutableStateOf(store.isOnboarded()) }
    var settings by remember { mutableStateOf(store.loadSettings()) }
    var intention by remember { mutableStateOf(store.loadIntention()) }
    var schedule by remember { mutableStateOf(store.loadSchedule()) }
    var history by remember { mutableStateOf(store.loadHistory()) }

    var currentTab by remember { mutableStateOf("home") }
    var showCheckInDialog by remember { mutableStateOf(false) }
    var showResetStepInDialog by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    LaunchedEffect(Unit) {
        val reconciled = SchedulerEngine.reconcileOnRestart(
            schedule = schedule,
            settings = settings,
            nowMillis = System.currentTimeMillis()
        )
        schedule = reconciled.state
        store.saveSchedule(schedule)
        schedule.nextFireAtMillis?.let { target ->
            coordinator.scheduleNextAlarm(target, schedule.scheduleId ?: "sched_$target")
        }
        if (reconciled.missedWhileClosed) {
            showCheckInDialog = true
            showResetStepInDialog = false
        }
    }

    LaunchedEffect(externalCheckInTrigger, externalResetStepTrigger) {
        if (externalCheckInTrigger) {
            showCheckInDialog = true
            showResetStepInDialog = externalResetStepTrigger
            onConsumeExternalTrigger()
        }
    }

    LaunchedEffect(schedule.status, schedule.nextFireAtMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            val target = schedule.nextFireAtMillis
            if (schedule.status == SessionStatus.RUNNING && target != null && nowMillis >= target) {
                val nextFire = SchedulerEngine.computeNextFireTime(nowMillis, settings)
                val nextId = "sched_$nextFire"
                schedule = schedule.copy(nextFireAtMillis = nextFire, scheduleId = nextId)
                store.saveSchedule(schedule)
                coordinator.scheduleNextAlarm(nextFire, nextId)
                if (SchedulerEngine.isWithinActiveWindow(nowMillis, settings)) {
                    showCheckInDialog = true
                    showResetStepInDialog = false
                }
            }
            delay(1000L)
        }
    }

    val colorScheme = if (settings.darkTheme) {
        darkColorScheme(
            primary = Color(0xFF639680),
            background = Color(0xFF171715),
            surface = Color(0xFF21211E)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF3F6A58),
            background = Color(0xFFFAF8F5),
            surface = Color(0xFFFFFFFF)
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (!onboarded) {
                OnboardingScreen(
                    initialSettings = settings,
                    onComplete = { updatedSettings, requestNotif ->
                        settings = updatedSettings
                        store.saveSettings(updatedSettings)
                        store.setOnboarded(true)
                        onboarded = true
                        if (requestNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onSkip = {
                        store.setOnboarded(true)
                        onboarded = true
                    }
                )
            } else {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentTab == "home",
                                onClick = { currentTab = "home" },
                                label = { Text("Today") },
                                icon = {}
                            )
                            NavigationBarItem(
                                selected = currentTab == "history",
                                onClick = { currentTab = "history" },
                                label = { Text("History") },
                                icon = {}
                            )
                            NavigationBarItem(
                                selected = currentTab == "settings",
                                onClick = { currentTab = "settings" },
                                label = { Text("Settings") },
                                icon = {}
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
                        when (currentTab) {
                            "home" -> HomeScreen(
                                intention = intention,
                                settings = settings,
                                schedule = schedule,
                                history = history,
                                nowMillis = nowMillis,
                                onSaveIntention = { updated ->
                                    intention = updated
                                    store.saveIntention(updated)
                                },
                                onStart = {
                                    val next = SchedulerEngine.computeNextFireTime(System.currentTimeMillis(), settings)
                                    val id = "sched_$next"
                                    schedule = ScheduleState(SessionStatus.RUNNING, next, null, id)
                                    store.saveSchedule(schedule)
                                    coordinator.scheduleNextAlarm(next, id)
                                },
                                onPause = {
                                    val rem = ((schedule.nextFireAtMillis ?: System.currentTimeMillis()) - System.currentTimeMillis()).coerceAtLeast(60_000L)
                                    schedule = ScheduleState(SessionStatus.PAUSED, null, rem, null)
                                    store.saveSchedule(schedule)
                                    coordinator.cancelScheduledAlarm()
                                },
                                onResume = {
                                    val next = System.currentTimeMillis() + (schedule.remainingMillisWhenPaused ?: (settings.intervalMinutes * 60_000L))
                                    val id = "sched_$next"
                                    schedule = ScheduleState(SessionStatus.RUNNING, next, null, id)
                                    store.saveSchedule(schedule)
                                    coordinator.scheduleNextAlarm(next, id)
                                },
                                onSnooze = { mins ->
                                    val next = System.currentTimeMillis() + mins * 60_000L
                                    val id = "sched_$next"
                                    schedule = ScheduleState(SessionStatus.RUNNING, next, null, id)
                                    store.saveSchedule(schedule)
                                    coordinator.scheduleNextAlarm(next, id)
                                },
                                onStop = {
                                    schedule = ScheduleState(SessionStatus.IDLE, null, null, null)
                                    store.saveSchedule(schedule)
                                    coordinator.cancelScheduledAlarm()
                                },
                                onTriggerNow = {
                                    showCheckInDialog = true
                                    showResetStepInDialog = false
                                }
                            )
                            "history" -> HistoryScreen(history = history)
                            "settings" -> SettingsScreen(
                                settings = settings,
                                onSaveSettings = { updated ->
                                    settings = updated
                                    store.saveSettings(updated)
                                    if (schedule.status == SessionStatus.RUNNING) {
                                        val next = SchedulerEngine.computeNextFireTime(System.currentTimeMillis(), updated)
                                        val id = "sched_$next"
                                        schedule = schedule.copy(nextFireAtMillis = next, scheduleId = id)
                                        store.saveSchedule(schedule)
                                        coordinator.scheduleNextAlarm(next, id)
                                    }
                                },
                                onTestNotification = {
                                    coordinator.showCheckInNotification(settings, intention)
                                },
                                onClearData = {
                                    coordinator.cancelScheduledAlarm()
                                    store.clearAll()
                                    settings = store.loadSettings()
                                    intention = store.loadIntention()
                                    schedule = store.loadSchedule()
                                    history = emptyList()
                                }
                            )
                        }
                    }
                }
            }

            if (showCheckInDialog) {
                CheckInModalDialog(
                    intention = intention,
                    showIntention = settings.showIntentionInPrompt,
                    initialResetStep = showResetStepInDialog,
                    onResponse = { responseType, nextSmallStep ->
                        showCheckInDialog = false
                        showResetStepInDialog = false
                        coordinator.dismissNotification()

                        if (nextSmallStep.isNotBlank()) {
                            intention = intention.copy(nextSmallStep = nextSmallStep)
                            store.saveIntention(intention)
                        }

                        if (responseType == CheckInResponseType.REMIND_LATER) {
                            val next = System.currentTimeMillis() + 15 * 60_000L
                            val id = "sched_$next"
                            schedule = ScheduleState(SessionStatus.RUNNING, next, null, id)
                            store.saveSchedule(schedule)
                            coordinator.scheduleNextAlarm(next, id)
                        }

                        val record = CheckInRecord(
                            id = UUID.randomUUID().toString(),
                            timestampMillis = System.currentTimeMillis(),
                            response = responseType,
                            intentionSnapshot = intention.primary,
                            nextSmallStep = nextSmallStep
                        )
                        store.appendHistory(record)
                        history = store.loadHistory()
                    }
                )
            }
        }
    }
}

@Composable
fun OnboardingScreen(
    initialSettings: UserSettings,
    onComplete: (UserSettings, Boolean) -> Unit,
    onSkip: () -> Unit
) {
    var interval by remember { mutableStateOf(initialSettings.intervalMinutes) }
    var activeStart by remember { mutableStateOf(initialSettings.activeStart) }
    var activeEnd by remember { mutableStateOf(initialSettings.activeEnd) }
    var pauseOutside by remember { mutableStateOf(initialSettings.pauseOutsideHours) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("WELCOME", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onSkip) { Text("Skip for now →") }
            }
            Text("Gentle check-ins for your attention", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "A small interruption creates a moment of awareness, followed by an easy choice to continue, reset, rest, or defer.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Text("How frequently should check-ins happen?", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf(30, 45, 60, 90).forEach { mins ->
                    FilterChip(
                        selected = interval == mins,
                        onClick = { interval = mins },
                        label = { Text("${mins}m") }
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = activeStart,
                    onValueChange = { activeStart = it },
                    label = { Text("Active Start (HH:mm)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = activeEnd,
                    onValueChange = { activeEnd = it },
                    label = { Text("Active End (HH:mm)") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = pauseOutside, onCheckedChange = { pauseOutside = it })
                Text("Pause reminders outside active hours & during breaks")
            }
        }

        item {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Why we ask for Notification Permission", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Android requires notification permission so gentle check-ins can reach you when you switch apps or lock your screen.",
                        fontSize = 13.sp
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    onComplete(
                        initialSettings.copy(
                            intervalMinutes = interval,
                            activeStart = activeStart,
                            activeEnd = activeEnd,
                            pauseOutsideHours = pauseOutside
                        ),
                        true
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable notifications & finish")
            }
        }
    }
}

@Composable
fun HomeScreen(
    intention: DailyIntention,
    settings: UserSettings,
    schedule: ScheduleState,
    history: List<CheckInRecord>,
    nowMillis: Long,
    onSaveIntention: (DailyIntention) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSnooze: (Int) -> Unit,
    onStop: () -> Unit,
    onTriggerNow: () -> Unit
) {
    var editingIntention by remember { mutableStateOf(false) }
    var draftPrimary by remember(intention.primary) { mutableStateOf(intention.primary) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TODAY'S PRIMARY INTENTION", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { editingIntention = !editingIntention }) {
                            Text(if (editingIntention) "Cancel" else "Edit")
                        }
                    }

                    if (!editingIntention) {
                        Text(intention.primary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        if (intention.nextSmallStep.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Next small step: ${intention.nextSmallStep}",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = draftPrimary,
                            onValueChange = { draftPrimary = it },
                            label = { Text("One main thing to return to") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            onSaveIntention(intention.copy(primary = draftPrimary.ifBlank { "Stay present" }))
                            editingIntention = false
                        }) {
                            Text("Save Intention")
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("CHECK-IN RHYTHM · ${schedule.status.name}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))

                    val readout = when (schedule.status) {
                        SessionStatus.IDLE -> "Ready when you are"
                        SessionStatus.PAUSED -> {
                            val mins = ((schedule.remainingMillisWhenPaused ?: 0L) / 60_000L).coerceAtLeast(1L)
                            "Paused (${mins}m remaining)"
                        }
                        SessionStatus.RUNNING -> {
                            val diffSec = (((schedule.nextFireAtMillis ?: nowMillis) - nowMillis) / 1000L).coerceAtLeast(0L)
                            val m = diffSec / 60
                            val s = diffSec % 60
                            String.format("%02d:%02d remaining", m, s)
                        }
                    }

                    Text(readout, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Every ${settings.intervalMinutes}m · Active ${settings.activeStart}–${settings.activeEnd}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (schedule.status) {
                            SessionStatus.IDLE -> {
                                Button(onClick = onStart) { Text("Start Check-ins") }
                            }
                            SessionStatus.RUNNING -> {
                                FilledTonalButton(onClick = onPause) { Text("Pause") }
                                OutlinedButton(onClick = { onSnooze(15) }) { Text("Snooze 15m") }
                                TextButton(onClick = onStop) { Text("Stop") }
                            }
                            SessionStatus.PAUSED -> {
                                Button(onClick = onResume) { Text("Resume") }
                                TextButton(onClick = onStop) { Text("Stop") }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(onClick = onTriggerNow) {
                        Text("Trigger Check-in Now")
                    }
                }
            }
        }

        item {
            val onTrackCount = history.count { it.response == CheckInResponseType.ON_TRACK }
            val resetCount = history.count { it.response == CheckInResponseType.DISTRACTED }
            val breakCount = history.count { it.response == CheckInResponseType.BREAK }

            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("SESSION SNAPSHOT", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("$onTrackCount", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("On track", fontSize = 13.sp)
                        }
                        Column {
                            Text("$resetCount", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Gentle resets", fontSize = 13.sp)
                        }
                        Column {
                            Text("$breakCount", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("Breaks", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CheckInModalDialog(
    intention: DailyIntention,
    showIntention: Boolean,
    initialResetStep: Boolean,
    onResponse: (CheckInResponseType, String) -> Unit
) {
    var inResetStep by remember { mutableStateOf(initialResetStep) }
    var customSmallStep by remember { mutableStateOf("") }

    Dialog(onDismissRequest = { onResponse(CheckInResponseType.DISMISSED, "") }) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!inResetStep) {
                    Text("GENTLE CHECK-IN", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Time for a quick check-in. Are you doing what you intended to do?",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (showIntention && intention.primary.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Today's Intention", fontSize = 11.sp)
                                Text(intention.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    CheckInOptionButton("1. On track", "Continue what I'm doing") {
                        onResponse(CheckInResponseType.ON_TRACK, "")
                    }
                    CheckInOptionButton("2. I got distracted", "Help me gently get back on track") {
                        inResetStep = true
                    }
                    CheckInOptionButton("3. Taking a break", "That's intentional right now") {
                        onResponse(CheckInResponseType.BREAK, "")
                    }
                    CheckInOptionButton("4. Remind me later", "Snooze for 15 minutes") {
                        onResponse(CheckInResponseType.REMIND_LATER, "")
                    }
                    CheckInOptionButton("5. Not now", "Dismiss without judgment") {
                        onResponse(CheckInResponseType.DISMISSED, "")
                    }
                } else {
                    Text("GENTLE RESET", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "No problem. What's the next small step you can take?",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Tap a quick reset or return directly to your task:", fontSize = 13.sp)

                    listOf(
                        "Close extra tabs & open my main document",
                        "Work for just 5 minutes on the first part",
                        "Take one deep breath and re-read my last line"
                    ).forEach { suggestion ->
                        OutlinedButton(
                            onClick = { onResponse(CheckInResponseType.DISTRACTED, suggestion) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(suggestion)
                        }
                    }

                    OutlinedTextField(
                        value = customSmallStep,
                        onValueChange = { customSmallStep = it },
                        label = { Text("Optional tiny next step") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { onResponse(CheckInResponseType.DISTRACTED, customSmallStep.trim()) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back to my task now")
                    }
                }
            }
        }
    }
}

@Composable
fun CheckInOptionButton(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HistoryScreen(history: List<CheckInRecord>) {
    val formatter = remember { SimpleDateFormat("EEE, HH:mm", Locale.getDefault()) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("History & Awareness", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Every check-in is a moment of awareness—not a productivity score. Missing or dismissed prompts are neutral.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (history.isEmpty()) {
            item {
                Text("No check-ins recorded yet.", modifier = Modifier.padding(top = 16.dp))
            }
        } else {
            items(history) { item ->
                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.intentionSnapshot, fontWeight = FontWeight.SemiBold)
                            if (item.nextSmallStep.isNotBlank()) {
                                Text("Next step: ${item.nextSmallStep}", fontSize = 12.sp)
                            }
                            Text(formatter.format(Date(item.timestampMillis)), fontSize = 12.sp)
                        }
                        Text(item.response.label, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onSaveSettings: (UserSettings) -> Unit,
    onTestNotification: () -> Unit,
    onClearData: () -> Unit
) {
    var interval by remember(settings.intervalMinutes) { mutableStateOf(settings.intervalMinutes.toString()) }
    var activeStart by remember(settings.activeStart) { mutableStateOf(settings.activeStart) }
    var activeEnd by remember(settings.activeEnd) { mutableStateOf(settings.activeEnd) }
    var soundEnabled by remember(settings.soundEnabled) { mutableStateOf(settings.soundEnabled) }
    var speakTimeEnabled by remember(settings.speakTimeEnabled) { mutableStateOf(settings.speakTimeEnabled) }
    var speakIntentionEnabled by remember(settings.speakIntentionEnabled) { mutableStateOf(settings.speakIntentionEnabled) }
    var vibrationEnabled by remember(settings.vibrationEnabled) { mutableStateOf(settings.vibrationEnabled) }
    var darkTheme by remember(settings.darkTheme) { mutableStateOf(settings.darkTheme) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Settings & Privacy", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        item {
            OutlinedTextField(
                value = interval,
                onValueChange = { interval = it },
                label = { Text("Check-in Interval (minutes)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = activeStart,
                    onValueChange = { activeStart = it },
                    label = { Text("Active Start") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = activeEnd,
                    onValueChange = { activeEnd = it },
                    label = { Text("Active End") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text("Attention Cues", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = soundEnabled, onCheckedChange = { soundEnabled = it })
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Harmonic Pre-Chime")
                    Text("Soft 2-note synthesized chime to open auditory attention", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = speakTimeEnabled, onCheckedChange = { speakTimeEnabled = it })
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Announce Time")
                    Text("Speaks the current time (e.g. \"12:45\") with audio ducking", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = speakIntentionEnabled,
                    onCheckedChange = { speakIntentionEnabled = it },
                    enabled = speakTimeEnabled
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Announce Intention")
                    Text("Follows time with your primary daily intention", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vibrationEnabled, onCheckedChange = { vibrationEnabled = it })
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Heartbeat Haptics")
                    Text("Distinctive tap-pause-pulse vibration signature", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = darkTheme, onCheckedChange = { darkTheme = it })
                Spacer(modifier = Modifier.width(10.dp))
                Text("Dark Theme")
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    onSaveSettings(
                        settings.copy(
                            intervalMinutes = interval.toIntOrNull()?.coerceIn(5, 240) ?: 45,
                            activeStart = activeStart,
                            activeEnd = activeEnd,
                            soundEnabled = soundEnabled,
                            speakTimeEnabled = speakTimeEnabled,
                            speakIntentionEnabled = speakIntentionEnabled,
                            vibrationEnabled = vibrationEnabled,
                            darkTheme = darkTheme
                        )
                    )
                }) {
                    Text("Save Preferences")
                }

                OutlinedButton(onClick = onTestNotification) {
                    Text("Test Notification")
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Local-First Privacy", fontWeight = FontWeight.SemiBold)
                    Text(
                        "All intentions, schedules, and check-in responses are stored locally on your device. No accounts, analytics, or cloud servers are used.",
                        fontSize = 13.sp
                    )
                    TextButton(onClick = onClearData) {
                        Text("Delete All Local Data", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
