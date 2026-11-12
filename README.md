# Shake Flashlight ⚡ (Moto Chop-Chop Gesture)

A lightweight native Android utility that turns on/off your phone's camera flashlight with Motorola's iconic double-chop shake gesture. Built with Kotlin, Jetpack Compose, Material 3, and Android Camera2 API.

---

## ✨ Features

- **Double-Chop Shake Gesture (Moto Style)**: Two quick wrist chops toggle the flashlight instantly.
- **Background & Lock Screen Ready**: Runs as a low-overhead foreground service with partial wake lock so it continues working even when your screen is locked.
- **Pocket Guard (Proximity Detection)**: Uses the proximity sensor to prevent the flashlight from turning on accidentally inside pockets or bags.
- **Quick Settings Tile**: Add the flashlight tile directly to your Android quick settings pull-down shade.
- **Customizable Sensitivity**: Low (firm chop), Medium (standard), High (light shake).
- **Auto Turn-Off Timer**: Automatically shuts off after 3, 5, or 10 minutes to protect battery and prevent LED overheating.
- **Tactile Haptic Feedback**: Crisp vibration confirmation on toggle.
- **Start on Boot**: Automatically resumes detection after device reboot.
- **Modern Jetpack Compose UI**: Hero toggle widget, dark theme, and gesture tuning.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 2.0
- **UI**: Jetpack Compose + Material 3
- **Hardware APIs**: `CameraManager.setTorchMode()`, `SensorManager` (Accelerometer & Proximity)
- **Background**: Foreground Service (`specialUse` for Android 14+), `TileService` for Quick Settings

---

## 🚀 Building & GitHub Actions

Every push to this branch triggers the GitHub Actions workflow (`.github/workflows/build-apk.yml`):
1. Runs unit tests (`ShakeDetectorTest`)
2. Assembles debug APK (`gradle assembleDebug`)
3. Uploads the installable APK as an artifact: `shake-flashlight-debug-apk`
