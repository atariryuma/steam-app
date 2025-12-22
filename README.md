# SteamDeck Mobile

**Steam-focused Android game emulator - Lightweight app with Winlator integration**

[![Android CI](https://github.com/atariryuma/steam-app/workflows/Android%20CI/badge.svg)](https://github.com/atariryuma/steam-app/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg?logo=android)](https://android.com)
[![Architecture](https://img.shields.io/badge/Architecture-ARM64--v8a-blue.svg)](https://developer.android.com/ndk/guides/abis)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.12.01-brightgreen.svg)](https://developer.android.com/jetpack/compose)

## 🔗 Quick Links

- [📥 Download Latest Release](https://github.com/atariryuma/steam-app/releases)
- [🐛 Report a Bug](https://github.com/atariryuma/steam-app/issues/new?template=bug_report.md)
- [💡 Request a Feature](https://github.com/atariryuma/steam-app/issues/new?template=feature_request.md)
- [📚 Contributing Guide](CONTRIBUTING.md)
- [🔒 Security Policy](SECURITY.md)
- [🎮 Winlator Project](https://github.com/brunodev85/winlator)

## 📱 Overview

SteamDeck Mobile is a lightweight app that enables running Windows games from your Steam library on Android devices.
It integrates Winlator (Wine + Box86/Box64) to provide a smooth gaming experience on Snapdragon-powered devices.

### Key Features

- ✅ **Steam Library Integration**: Automatic sync via Steam Web API
- ✅ **Windows Game Execution**: Native execution using Winlator
- ✅ **File Import**: Supports USB OTG, SMB, FTP, and local storage
- ✅ **Game Controller Support**: Bluetooth/USB connected controllers
- ✅ **Fast Download Management**: Multi-threaded with pause/resume functionality

## 🚀 Tech Stack

- **Language**: Kotlin 2.1.0
- **UI**: Jetpack Compose (Material3)
- **Architecture**: Clean Architecture + MVVM
- **DI**: Hilt 2.52
- **DB**: Room 2.6.1
- **Async**: Coroutines + Flow
- **Network**: Retrofit 2.11.0 + OkHttp 4.12.0
- **Image Loading**: Coil 2.7.0
- **Emulation**: Winlator (Wine + Box86/Box64)

## 📋 System Requirements

- **Android**: 8.0 (API 26) or higher
- **Architecture**: ARM64-v8a
- **Recommended Device**: Snapdragon 8 Gen 1 or higher
- **Minimum Resolution**: 1280x720 (HD)
- **Storage**: At least 1GB free space

## 🛠️ Development Environment

### Required Tools

- Android Studio Ladybug 2024.2.1+
- JDK 21+
- Git

### Setup Instructions

For detailed instructions, see [SETUP.md](SETUP.md).

#### Quick Start

```bash
# 1. Check environment (first time only)
check-environment.bat

# 2. (Optional) Set up development API Key
# Add to local.properties:
# STEAM_API_KEY=YOUR_32_CHAR_HEX_KEY

# 3. Build Debug APK + Install
build-debug.bat
```

#### Using Android Studio

```bash
# Clone repository
git clone https://github.com/atariryuma/steam-app.git
cd steam-app

# Open project in Android Studio
# File > Open > Select "steam-app" folder

# Dependencies will be downloaded automatically
# Run > Run 'app' (Shift+F10) to execute
```

### Build Methods

#### Available Build Scripts

```bash
# Debug APK (for development - recommended)
build-debug.bat                # Build + adb install

# Release APK (for distribution - R8 optimized)
build-release.bat              # Build only
build-and-install.bat          # Build + adb install

# Reinstall existing APK
install-debug.bat              # Install pre-built Debug APK
```

#### Gradle Commands

```bash
# Build Debug APK (for development)
./gradlew assembleDebug

# Build Release APK (for distribution, optimized)
./gradlew assembleRelease

# Run tests
./gradlew test

# Run instrumentation tests
./gradlew connectedAndroidTest
```

#### Build Output Locations

- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `app/build/outputs/apk/release/app-release.apk`
- **Android App Bundle**: `app/build/outputs/bundle/release/app-release.aab`

## 📂 Project Structure

```
SteamDeckMobile/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/steamdeck/mobile/
│   │   │   │   ├── presentation/       # UI layer (Compose)
│   │   │   │   │   ├── ui/            # Screens
│   │   │   │   │   ├── viewmodel/     # ViewModels
│   │   │   │   │   └── theme/         # Theme
│   │   │   │   ├── domain/            # Domain layer
│   │   │   │   │   ├── model/         # Domain models
│   │   │   │   │   ├── usecase/       # Use cases
│   │   │   │   │   └── repository/    # Repository interfaces
│   │   │   │   ├── data/              # Data layer
│   │   │   │   │   ├── local/         # Local data
│   │   │   │   │   ├── remote/        # Remote data
│   │   │   │   │   └── repository/    # Repository implementations
│   │   │   │   ├── core/              # Core functionality
│   │   │   │   │   ├── winlator/      # Winlator integration
│   │   │   │   │   ├── fileimport/    # File import
│   │   │   │   │   ├── download/      # Download management
│   │   │   │   │   └── controller/    # Controller
│   │   │   │   └── di/                # Dependency injection
│   │   │   └── AndroidManifest.xml
│   │   └── test/                       # Unit tests
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml              # Version catalog
├── build.gradle.kts
└── settings.gradle.kts
```

## 🎯 Development Roadmap

### Phase 1: MVP ✅ Completed
- [x] Project setup
- [x] Clean Architecture package structure
- [x] Room DB implementation (games, container management)
- [x] Basic UI (home, detail screens)
- [x] Winlator integration (stub implementation)

### Phase 2: Steam Integration ✅ Completed

- [x] Steam Web API integration
- [x] Steam API Service implementation
- [x] Library sync functionality
- [x] Settings screen and Steam authentication UI
- [x] DataStore integration

### Phase 3: File Import ✅ Completed
- [x] USB OTG support (libaums)
- [x] SMB/CIFS integration (jcifs-ng SMB2/3)
- [x] FTP/FTPS integration (Apache Commons Net)
- [x] SAF local storage

### Phase 4: Download Management ✅ Completed

- [x] WorkManager integration
- [x] Multi-threaded downloads (8MB chunks)
- [x] Pause/resume functionality
- [x] Background downloads
- [x] Download UI implementation (Material3)

### Phase 4C: Wine Integration ✅ Completed

- [x] Extract Wine 9.0+ from Winlator 10.1 APK
- [x] XZ compression/decompression support (Apache Commons Compress)
- [x] Wine rootfs (53MB) extraction implementation
- [x] Box64 0.3.6 binary integration
- [x] R8 optimization (63MB Release APK)
- [x] ProGuard rules (JNI/security protection)

**Result**: 63MB APK (55% of Winlator size, 141MB→63MB)

### Phase 5: Controller Support ✅ Completed

- [x] InputDevice API integration (auto-detection)
- [x] Button mapping system (16 buttons + 4 axes)
- [x] Profile management (Room Database v3)
- [x] Real-time joystick preview
- [x] Xbox/PlayStation/Nintendo auto-detection (Vendor ID)
- [x] Deadzone adjustment (0-50%)
- [x] Material3 UI implementation (ControllerSettingsScreen)
- [ ] Vibration support (planned for Phase 5.1)

**Result**: 11 files added (~1,813 lines), APK size maintained (76MB)

### Phase 6: Release Preparation
- [x] APK optimization (target: <80MB) ✅ Achieved (63MB)
- [x] R8 optimization (-17% size reduction)
- [ ] Full UI test coverage
- [ ] Real device verification (Wine execution test)

## 🤝 Contributing

Currently a personal development project, but issue reports are welcome.

### How to Report

1. Open the [Issues](https://github.com/atariryuma/steam-app/issues) page
2. Click "New Issue"
3. Select bug report or feature request template
4. Fill in details and submit

## 📄 License

This project is released under the MIT License. See the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [Winlator](https://github.com/brunodev85/winlator) - Windows emulation
- [Steam Web API](https://steamcommunity.com/dev) - Steam library integration
- Android Jetpack Compose - Modern UI framework

## 📞 Support

If you encounter issues:

1. Search [existing Issues](https://github.com/atariryuma/steam-app/issues)
2. Create a new Issue if none exists
3. Refer to [Contributing Guide](CONTRIBUTING.md)

---

**Current Status**: Phase 5 (Controller Support) completed - MVP + Steam integration + File import + Download management + Wine integration + Controller support complete

Made with ❤️ for Steam gamers on Android
