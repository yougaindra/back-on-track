# Agent Guidelines: Monorepo (Moto Gestures & Back On Track)

## 📱 Environment & Mobile Constraints
- **Platform**: Termux on Android 14+.
- **Process Sandbox**:
  - The background agent process cannot spawn subprocesses via `run_command` because Android's kernel mounts `/data/data` with `noexec` (`fork/exec permission denied`).
  - **Rule**: Never attempt to run shell scripts or build commands from the background agent.
  - **Rule**: Always output concise, copy-pasteable single-line terminal commands for the user to execute in their interactive Termux session.
- **Git Workflow**:
  - Active feature branch: `shake-flashlight`.
  - The user has a 2-letter shortcut `gp` configured in Termux (`~/.local/bin/gp` and `~/.bashrc`) that automatically runs:
    `git add -A && git commit -m "Update" && git push origin $(git branch --show-current)`
  - When background auto-pushing is needed, the user can run `bash ~/back-on-track/autopush.sh &`.
- **CI / Build Pipeline**:
  - Android APK builds are performed remotely by GitHub Actions.
  - Path-filtered workflows:
    - `apps/moto-gestures/**` -> `.github/workflows/moto-gestures.yml`
    - `apps/back-on-track/**` -> `.github/workflows/back-on-track.yml`
  - Do not attempt local Gradle builds on the device.
  - When CI finishes, the user downloads the APK with:
    `gh run download --name moto-gestures-debug-apk`
    or
    `gh run download --name back-on-track-debug-apk`

## 🏗️ Monorepo Architecture & Coding Standards
1. **Repository Layout**:
   - `apps/moto-gestures`: Motorola gestures app (Chop for Torch, Twist for Camera).
   - `apps/back-on-track`: Personal accountability check-in app.
   - Gradle subprojects: `:apps:moto-gestures` and `:apps:back-on-track`.
2. **Decoupled Gesture State Machines**:
   - `ShakeStateMachine.kt` (chop-chop / flashlight) and `TwistStateMachine.kt` (double-twist / camera) MUST remain pure Kotlin with zero Android Context or hardware SDK dependencies.
   - All state machine logic, thresholds, and timers must be 100% unit-testable via standard JUnit 4.
3. **False Positive & Gait Protection**:
   - `ShakeStateMachine` must maintain the smart gait cadence filter to suppress rhythmic footstep impacts (cadence ~220–700ms) during fast walking and jogging.
   - Cooldown checks must always verify `if (lastTriggerTime > 0L && ...)` so initial samples (where `lastTriggerTime == 0L`) are never accidentally dropped.
4. **Android Permissions & Play Protect Compliance**:
   - **Do NOT** declare `android.permission.USE_FULL_SCREEN_INTENT`. On Android 14+, sideloaded apps with this permission are flagged as harmful by Google Play Protect.
   - For direct camera launching from the background service:
     - Use `SYSTEM_ALERT_WINDOW` ("Appear on top" / display over other apps).
     - Use `ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED` on Android 14+ (`Build.VERSION_CODES.UPSIDE_DOWN_CAKE`).
     - Acquire a temporary wake lock with `PowerManager.ACQUIRE_CAUSES_WAKEUP` to illuminate the screen if the display is asleep.
5. **Icons & AAPT Resource Rules**:
   - Application icons in `AndroidManifest.xml` must reference adaptive mipmaps (`@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`), NOT vector drawables directly (which causes AAPT compilation failures).
   - Launcher vector assets reside in `res/drawable/ic_launcher_background.xml` and `res/drawable/ic_launcher_foreground.xml`.

## 🧪 Testing Checklist
- Whenever updating gesture algorithms or thresholds, always update the corresponding unit tests:
  - `apps/moto-gestures/src/test/java/com/yougaindra/shakelight/ShakeDetectorTest.kt`
  - `apps/moto-gestures/src/test/java/com/yougaindra/shakelight/TwistDetectorTest.kt`
- **Rule**: Every `*Test.kt` file must contain active `@Test` methods. Never leave empty or fully-commented test classes, as JUnit 4 aborts Gradle builds with `java.lang.Exception: No runnable methods`.
