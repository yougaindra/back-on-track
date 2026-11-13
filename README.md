# Moto Gestures ⚡📸 (Torch & Quick Capture)

A lightweight native Android utility that brings Motorola's two most loved gestures to any Android device:
1. **Double-Chop for Flashlight** ⚡
2. **Double-Twist for Camera (Quick Capture)** 📸

Built with Kotlin, Jetpack Compose, Material 3, Android Camera2, and hardware Sensor APIs.

---

## ✨ Features

- **Double-Chop (Flashlight)**: Two quick wrist chops toggle the flashlight instantly.
- **Double-Twist (Quick Capture Camera)**: Twist your wrist back and forth twice (like turning a doorknob) to open the camera, even when your phone is locked.
- **Lock Screen Ready**: Uses secure camera intents (`INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE`) and full-screen intents so the camera opens directly without needing to unlock first.
- **Smart Walk-Guard & Gait Filter**: Automatically tracks rhythmic footstep cadence to filter out fast walking, running, and bouncing, preventing accidental flashlight triggers.
- **Background Service**: Low-overhead foreground service (`specialUse` for Android 14+) with partial wake lock so both gestures respond immediately with screen off.
- **Pocket Guard (Proximity Sensor)**: Suppresses both gestures when the phone is in your pocket, purse, or face-down on a desk.
- **Quick Settings Tile**: Android pull-down quick settings tile to toggle the flashlight with a single tap.
- **Customizable Sensitivity**: Independent sensitivity tuning for chop-chop and camera twist (Low, Medium, High).
- **Haptic Feedback**: Single pulse on torch ON, double pulse on torch OFF, triple buzz on camera launch.
- **Auto Turn-Off**: Shuts off flashlight after 3, 5, or 10 minutes to protect battery and prevent LED overheating.
- **Start on Boot**: Automatically resumes detection after phone reboot.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 2.0
- **UI**: Jetpack Compose + Material 3
- **Sensors**: Accelerometer (Chop-Chop), Gyroscope (Twist), Proximity (Pocket Guard)
- **Background Execution**: Foreground Service with `specialUse`, `TileService` for Quick Settings

---

## 🚀 Building & GitHub Actions

Every push to this branch triggers the GitHub Actions CI workflow (`.github/workflows/build-apk.yml`):
1. Runs automated unit tests (`ShakeDetectorTest` & `TwistDetectorTest`)
2. Assembles debug APK (`gradle assembleDebug`)
3. Uploads the installable APK as an artifact: `shake-flashlight-debug-apk`
