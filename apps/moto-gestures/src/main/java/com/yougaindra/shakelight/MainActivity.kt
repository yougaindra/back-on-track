package com.yougaindra.shakelight

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yougaindra.shakelight.core.CameraController
import com.yougaindra.shakelight.core.FlashlightController
import com.yougaindra.shakelight.core.GestureMode
import com.yougaindra.shakelight.core.Sensitivity
import com.yougaindra.shakelight.core.ShakeDetector
import com.yougaindra.shakelight.data.PreferencesManager
import com.yougaindra.shakelight.service.ShakeService

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var flashlightController: FlashlightController
    private var uiShakeDetector: ShakeDetector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferencesManager(this)
        flashlightController = FlashlightController(this)

        setContent {
            ShakeLightTheme {
                MainScreen(
                    preferencesManager = preferencesManager,
                    flashlightController = flashlightController,
                    onToggleService = { enable ->
                        val current = preferencesManager.loadSettings()
                        preferencesManager.updateSettings(current.copy(isServiceEnabled = enable))
                        if (enable) {
                            ShakeService.start(this)
                        } else {
                            ShakeService.stop(this)
                        }
                    },
                    onUpdateSettings = { updated ->
                        preferencesManager.updateSettings(updated)
                        ShakeService.updateSettings(this)
                    },
                    onTestCamera = {
                        CameraController.launchCamera(this)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Start UI gesture listener for live visualizer while app is open
        if (uiShakeDetector == null) {
            val settings = preferencesManager.loadSettings()
            uiShakeDetector = ShakeDetector(
                context = this,
                onShakeTriggered = {
                    val newState = !flashlightController.isTorchOn.value
                    flashlightController.setTorch(newState)
                },
                onTwistTriggered = {
                    val currentSettings = preferencesManager.loadSettings()
                    if (currentSettings.isCameraTwistEnabled) {
                        CameraController.launchCamera(this)
                    }
                }
            ).apply {
                shakeStateMachine.sensitivity = settings.sensitivity
                shakeStateMachine.gestureMode = settings.gestureMode
                shakeStateMachine.pocketProtectionEnabled = settings.pocketProtection
                twistStateMachine.sensitivity = settings.cameraSensitivity
                twistStateMachine.pocketProtectionEnabled = settings.pocketProtection
                startListening()
            }
        } else {
            uiShakeDetector?.startListening()
        }

        // Ensure background service is running if enabled
        if (preferencesManager.loadSettings().isServiceEnabled) {
            ShakeService.start(this)
        }
    }

    override fun onPause() {
        super.onPause()
        uiShakeDetector?.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiShakeDetector?.stopListening()
        flashlightController.cleanUp()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    preferencesManager: PreferencesManager,
    flashlightController: FlashlightController,
    onToggleService: (Boolean) -> Unit,
    onUpdateSettings: (com.yougaindra.shakelight.data.AppSettings) -> Unit,
    onTestCamera: () -> Unit
) {
    val context = LocalContext.current
    val settings by preferencesManager.settings.collectAsState()
    val isTorchOn by flashlightController.isTorchOn.collectAsState()
    val isFlashAvailable by flashlightController.isAvailable.collectAsState()

    // Request notification permission for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Moto Gestures", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            "Chop for Torch · Twist for Camera",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {
            // Flashlight Hero Button
            item {
                FlashlightHeroWidget(
                    isTorchOn = isTorchOn,
                    isAvailable = isFlashAvailable,
                    onToggle = {
                        flashlightController.toggleTorch()
                    }
                )
            }

            // Master Service Switch Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (settings.isServiceEnabled)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Background Gesture Service",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (settings.isServiceEnabled)
                                    "Active · Gestures work while screen is locked"
                                else
                                    "Inactive · Gestures paused",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.isServiceEnabled,
                            onCheckedChange = { onToggleService(it) }
                        )
                    }
                }
            }

            // Quick Capture (Camera Twist) Section Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (settings.isCameraTwistEnabled)
                            Color(0xFF0D47A1).copy(alpha = 0.25f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📸", fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "Quick Capture Camera",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        "Twist wrist twice to open camera",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = settings.isCameraTwistEnabled,
                                onCheckedChange = {
                                    onUpdateSettings(settings.copy(isCameraTwistEnabled = it))
                                }
                            )
                        }

                        if (settings.isCameraTwistEnabled) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                "Camera Twist Sensitivity",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Sensitivity.values().forEach { sens ->
                                    val selected = settings.cameraSensitivity == sens
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            onUpdateSettings(settings.copy(cameraSensitivity = sens))
                                        },
                                        label = { Text(sens.name) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (!CameraController.canDrawOverlays(context)) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("⚡", fontSize = 16.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Instant Camera Launch",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Allow 'Appear on top' so twisting your wrist pops up the camera directly without sending a notification.",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = Intent(
                                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        Uri.parse("package:${context.packageName}")
                                                    )
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {
                                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                                    context.startActivity(intent)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Allow 'Appear on Top'")
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            OutlinedButton(
                                onClick = onTestCamera,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Test Open Camera Now")
                            }
                        }
                    }
                }
            }

            // Gesture Guide Card
            item {
                GestureGuideCard(settings = settings)
            }

            // Torch Gesture Controls Section
            item {
                Text(
                    "⚡ Flashlight Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Torch Gesture Mode (Double Chop vs Single Shake)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Chop Gesture Style", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            "Two-stroke downward chop (Motorola standard) or single shake",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GestureMode.values().forEach { mode ->
                                val selected = settings.gestureMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        onUpdateSettings(settings.copy(gestureMode = mode))
                                    },
                                    label = { Text(mode.label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Torch Sensitivity Selector
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Chop Sensitivity", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            "Adjust the acceleration threshold needed to toggle the light",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Sensitivity.values().forEach { sens ->
                                val selected = settings.sensitivity == sens
                                val chipText = when (sens) {
                                    Sensitivity.LOW -> "Low (Firm)"
                                    Sensitivity.MEDIUM -> "Medium"
                                    Sensitivity.HIGH -> "High"
                                }
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        onUpdateSettings(settings.copy(sensitivity = sens))
                                    },
                                    label = { Text(chipText, fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🛡️", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Smart Walk-Guard active: sustained footsteps are automatically filtered to prevent false triggers.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Global Guard & Haptic Settings
            item {
                Text(
                    "🛡️ Guards & Feedback",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Pocket Protection Switch
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pocket Guard", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                "Uses proximity sensor to prevent flashlight or camera from triggering in pockets or bags",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.pocketProtection,
                            onCheckedChange = {
                                onUpdateSettings(settings.copy(pocketProtection = it))
                            }
                        )
                    }
                }
            }

            // Vibration & Auto-off Settings
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Vibrate on Torch Toggle", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    "Tactile haptic feedback when light turns on/off",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.vibrateOnToggle,
                                onCheckedChange = {
                                    onUpdateSettings(settings.copy(vibrateOnToggle = it))
                                }
                            )
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Vibrate on Camera Launch", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    "Triple haptic pulse when camera opens",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.vibrateOnCamera,
                                onCheckedChange = {
                                    onUpdateSettings(settings.copy(vibrateOnCamera = it))
                                }
                            )
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto Turn-Off Torch", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    "Prevents battery drain (${if (settings.autoTurnOffMinutes == 0) "Disabled" else "${settings.autoTurnOffMinutes} minutes"})",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { expanded = true }) {
                                    Text(if (settings.autoTurnOffMinutes == 0) "Never" else "${settings.autoTurnOffMinutes}m")
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    listOf(0 to "Never", 3 to "3 Minutes", 5 to "5 Minutes", 10 to "10 Minutes").forEach { (mins, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                onUpdateSettings(settings.copy(autoTurnOffMinutes = mins))
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Start on Phone Boot", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    "Automatically start detection after reboot",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.startOnBoot,
                                onCheckedChange = {
                                    onUpdateSettings(settings.copy(startOnBoot = it))
                                }
                            )
                        }
                    }
                }
            }

            // Battery Optimization Tip
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("💡 Smooth Background Performance", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "To ensure both gestures run smoothly when your phone is locked, set battery usage for this app to 'Unrestricted' in your Android Settings.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Battery Optimization Settings")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlashlightHeroWidget(
    isTorchOn: Boolean,
    isAvailable: Boolean,
    onToggle: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isTorchOn) 1.04f else 1.0f,
        animationSpec = tween(durationMillis = 300),
        label = "hero_scale"
    )

    val glowColor by animateColorAsState(
        targetValue = if (isTorchOn) Color(0xFFFFB300) else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "hero_glow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(190.dp)
                .scale(scale)
                .shadow(
                    elevation = if (isTorchOn) 28.dp else 4.dp,
                    shape = CircleShape,
                    spotColor = glowColor,
                    ambientColor = glowColor
                )
                .clip(CircleShape)
                .background(
                    if (isTorchOn)
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFF57F17))
                        )
                    else
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF2C2C2E), Color(0xFF1C1C1E))
                        )
                )
                .border(
                    width = 4.dp,
                    color = if (isTorchOn) Color(0xFFFFF8E1) else Color(0xFF3A3A3C),
                    shape = CircleShape
                )
                .clickable(enabled = isAvailable) {
                    onToggle()
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isTorchOn) "⚡" else "🔦",
                    fontSize = 46.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isTorchOn) "ON" else "OFF",
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = if (isTorchOn) Color(0xFF3E2723) else Color(0xFFE5E5EA)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = if (!isAvailable)
                "Camera Flash not detected on device"
            else if (isTorchOn)
                "Flashlight is Shining · Double chop to turn off"
            else
                "Flashlight is Off · Double chop phone to turn on",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isTorchOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GestureGuideCard(
    settings: com.yougaindra.shakelight.data.AppSettings
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Motorola Gesture Guide", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Top) {
                Text("⚡", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Chop Twice for Flashlight", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        "Hold phone upright and make two quick, firm downward chopping motions with your wrist.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.Top) {
                Text("📸", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Twist Twice for Camera", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        "Hold phone and twist your wrist back and forth twice (like turning a doorknob). Opens camera even from lock screen.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ShakeLightTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        primary = Color(0xFFFFB300),
        onPrimary = Color(0xFF261900),
        primaryContainer = Color(0xFFFFD54F),
        onPrimaryContainer = Color(0xFF261900),
        surface = Color(0xFF141416),
        onSurface = Color(0xFFEDEDED),
        surfaceVariant = Color(0xFF242428),
        onSurfaceVariant = Color(0xFFA5A5AB),
        background = Color(0xFF0C0C0E)
    )

    MaterialTheme(
        colorScheme = darkColors,
        content = content
    )
}
