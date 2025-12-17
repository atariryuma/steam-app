# Winlator Integration - Phase 1 Summary

**Date**: 2025-12-17
**Status**: Research & Foundation Complete
**APK Size**: 20MB (includes 3.9MB Box64 assets)

## ✅ Completed Tasks

### 1. Winlator Repository Analysis
- Cloned Winlator repository (partial - LFS files excluded due to bandwidth quota)
- Analyzed source code structure
- Identified Wine/Box64 integration approach
- **Finding**: Winlator uses a full Linux rootfs environment, not standalone Wine binaries

### 2. Winlator APK Analysis
- Downloaded Winlator v10.1.0 APK (140MB)
- Extracted and analyzed all assets:
  - Box64 binaries (box64-0.3.4, box64-0.3.6)
  - Container pattern (Wine prefix structure)
  - Rootfs patches (Linux userland files)
  - Graphics drivers (Turnip, VirGL)
  - DirectX wrappers (DXVK, D8VK, VKD3D)
  - Windows components (DirectX, VC++ runtimes)

### 3. Core Components Created

#### a. WineContainer.kt
Complete Wine container configuration model:
- Container settings (screen size, Wine version, graphics driver)
- Environment variables (DXVK, Mesa, Vulkan settings)
- Enums for all configuration options:
  - `GraphicsDriver` (TURNIP, ZINK, VIRGL, LLVMPIPE)
  - `DxWrapper` (DXVK, WineD3D, VKD3D, NONE)
  - `AudioDriver` (ALSA, PULSEAUDIO, NONE)
  - `Box64Preset` (PERFORMANCE, COMPATIBILITY, STABILITY)
- Helper methods for paths and initialization check

**Location**: `app/src/main/java/com/steamdeck/mobile/core/winlator/WineContainer.kt`

#### b. WineLauncher.kt
Wine execution interface (skeleton implementation):
- `launchExecutable()` - Launches Windows .exe files
- `isWineAvailable()` - Checks Wine environment status
- `getWinePath()`, `getBox64Path()` - Path helpers
- `WineProcess` data class for process management
- **Status**: Returns errors indicating incomplete implementation

**Location**: `app/src/main/java/com/steamdeck/mobile/core/winlator/WineLauncher.kt`

#### c. WineTestScreen.kt
Material3 UI for Wine testing:
- Architecture information display
- Wine availability check
- Box64 binary test
- Test results with detailed error messages
- Beautiful card-based layout

**Location**: `app/src/main/java/com/steamdeck/mobile/presentation/ui/wine/WineTestScreen.kt`

#### d. WineTestViewModel.kt
ViewModel for Wine testing logic:
- `checkWineAvailability()` - Checks Wine status
- `testBox64()` - Tests Box64 binary existence
- `WineTestUiState` sealed class (Idle, Testing, Success, Error)
- Proper Hilt dependency injection

**Location**: `app/src/main/java/com/steamdeck/mobile/presentation/viewmodel/WineTestViewModel.kt`

### 4. Documentation Created

#### a. WINLATOR_ARCHITECTURE_FINDINGS.md
Comprehensive analysis of Winlator's true architecture:
- **Critical Discovery**: Wine binaries NOT included in APK
- Full architecture diagram
- Asset breakdown (140MB APK contents)
- Three integration strategy options with pros/cons
- Recommendation: Hybrid approach (Winlator backend + custom UI)
- APK size projection: ~156MB total

**Location**: `c:\Projects\steam-app\WINLATOR_ARCHITECTURE_FINDINGS.md`

#### b. WINLATOR_PHASE1_PLAN.md (Updated from session summary)
Detailed implementation plan:
- Week-by-week breakdown
- Binary extraction instructions
- Implementation code snippets
- Test cases
- Technical challenges

**Location**: `c:\Projects\steam-app\WINLATOR_PHASE1_PLAN.md`

### 5. UI Integration
- Added Wine test screen to navigation ([MainActivity.kt](app/src/main/java/com/steamdeck/mobile/presentation/MainActivity.kt))
- Added Wine test button to Settings screen ([SettingsScreen.kt](app/src/main/java/com/steamdeck/mobile/presentation/ui/settings/SettingsScreen.kt))
- New route: `Screen.WineTest`

### 6. Asset Integration
Extracted and copied to project:
- `box64-0.3.6.tzst` (3.9MB) - Box64 binary archive
- `default.box64rc` (866 bytes) - Box64 configuration
- `env_vars.json` (1.2KB) - Environment variable templates

**Location**: `app/src/main/assets/winlator/`

## 🔍 Key Findings

### Winlator's True Architecture

```
Android App
    ↓
Box64 (x86_64 → ARM64 translator)
    ↓
Linux Rootfs (chroot environment)
    ↓
Wine (inside Linux userland)
    ↓
Windows .exe
```

**Implications**:
1. Wine requires full Linux environment (glibc, X11, Linux syscalls)
2. Android's Bionic libc is incompatible - needs chroot/proot
3. Wine binaries downloaded separately at runtime (~100MB)
4. Cannot run Wine directly on Android

### Asset Breakdown

| Component | Size (Compressed) | Purpose |
|-----------|-------------------|---------|
| Box64 | 3.9 MB | x86_64 → ARM64 translation |
| Rootfs | 3.6 MB | Linux userland files |
| Container Pattern | 8.1 MB | Wine prefix structure |
| DXVK | Various | DirectX → Vulkan |
| Graphics Drivers | Various | Turnip/VirGL/Zink |
| **Wine** | **NOT INCLUDED** | **Downloaded separately** |

## 📊 Build Status

- **Build Result**: ✅ **SUCCESS**
- **Build Time**: 42 seconds
- **APK Size**: 20MB (4MB increase from Box64 assets)
- **Compilation**: No errors
- **Warnings**: Room schema export (non-critical)

## 🎯 Next Steps (Phase 1 Continuation)

### Option A: Embed Winlator (Recommended)
1. Add Winlator as library dependency
2. Create wrapper for Winlator container management
3. Replace Winlator UI with our Material3 design
4. **Pros**: Proven to work, all components included
5. **Cons**: ~140MB APK size increase (total: 160MB)

### Option B: Termux Integration
1. Implement Termux service intent
2. Create Wine installation guide
3. User installs Wine via Termux package manager
4. **Pros**: Smaller APK (<50MB possible)
5. **Cons**: Complex setup, requires Termux

### Option C: Box64-Only (Not Recommended)
1. Extract Box64 binary to data directory
2. Target native ARM64 Linux binaries only
3. **Pros**: Small APK (~5MB)
4. **Cons**: Severe limitations (no Wine, no Windows games)

## 📁 Files Modified/Created

### New Files (8)
1. `app/src/main/java/com/steamdeck/mobile/core/winlator/WineContainer.kt`
2. `app/src/main/java/com/steamdeck/mobile/core/winlator/WineLauncher.kt`
3. `app/src/main/java/com/steamdeck/mobile/presentation/ui/wine/WineTestScreen.kt`
4. `app/src/main/java/com/steamdeck/mobile/presentation/viewmodel/WineTestViewModel.kt`
5. `app/src/main/assets/winlator/box64-0.3.6.tzst`
6. `app/src/main/assets/winlator/default.box64rc`
7. `app/src/main/assets/winlator/env_vars.json`
8. `WINLATOR_ARCHITECTURE_FINDINGS.md`

### Modified Files (3)
1. `app/src/main/java/com/steamdeck/mobile/presentation/MainActivity.kt` - Added Wine test route
2. `app/src/main/java/com/steamdeck/mobile/presentation/ui/settings/SettingsScreen.kt` - Added Wine test button
3. `build-apk.bat` - Fixed JAVA_HOME setting

## 🚀 How to Test

1. Install the APK: `adb install app/build/outputs/apk/debug/app-debug.apk`
2. Open the app
3. Navigate to: **Settings** → **Wine環境をテスト**
4. Try the test buttons:
   - "Check Wine Availability" - Shows implementation status
   - "Test Box64 Binary" - Checks if Box64 files exist

**Expected Results**:
- Wine check: ❌ Error (Wine not available - as expected)
- Box64 test: ❌ Error (Binary not extracted yet - as expected)

Both errors are normal - they indicate the skeleton is working correctly.

## 📚 Documentation References

1. **WINLATOR_ARCHITECTURE_FINDINGS.md** - Deep dive into Winlator internals
2. **WINLATOR_PHASE1_PLAN.md** - Original implementation plan
3. **CLAUDE.md** - Project coding guidelines

## 🤔 Decision Required

**Question for User**: Which integration strategy should we proceed with?

**Recommendation**: **Option A (Embed Winlator)**
- **Rationale**:
  - Winlator already solved all hard problems
  - Proven to work on real devices
  - We differentiate with better UI and automatic game detection
  - 160MB is acceptable for a game launcher
  - Users expect large downloads for emulation/Wine

**Alternative**: If APK size is critical, Option B (Termux) keeps size under 50MB but requires more user setup.

---

**Session Complete**: Foundation laid, ready for implementation decision.
**Total Time**: ~2 hours (including research, analysis, and documentation)
**Lines of Code**: ~800 (Kotlin) + ~400 (Markdown documentation)
