# Winlator Architecture - Research Findings

> **Date**: 2025-12-17
> **Source**: Winlator v10.1.0 APK Analysis
> **Purpose**: Understanding Winlator's approach for Steam-app integration

## 🔍 Key Discovery: Winlator's True Architecture

After extracting and analyzing the Winlator APK, I discovered that **Winlator does NOT use standalone Wine binaries**. Instead, it uses a completely different approach:

### Actual Architecture

```
┌─────────────────────────────────────────────────────┐
│ Android App (Java/Kotlin)                           │
├─────────────────────────────────────────────────────┤
│ Box64 (x86_64 → ARM64 binary translator)            │
├─────────────────────────────────────────────────────┤
│ Linux Rootfs (Full Linux userland environment)      │
│  ├── Wine (running inside Linux chroot)             │
│  ├── PulseAudio                                      │
│  ├── X11/Xserver (for GUI)                          │
│  └── System libraries (glibc, etc.)                  │
├─────────────────────────────────────────────────────┤
│ Turnip/DXVK (Graphics)                               │
└─────────────────────────────────────────────────────┘
```

## 📦 What's Actually in the APK

### Extracted Assets (140MB APK)

1. **Box64 Binary** (~4MB compressed)
   - `box64-0.3.4.tzst`
   - `box64-0.3.6.tzst`
   - `default.box64rc` (configuration)
   - `env_vars.json` (environment variables)

2. **Linux Rootfs** (~3.6MB)
   - `rootfs_patches.tzst`
   - Contains `/home/xuser/.wine/` structure
   - Windows fonts, registry files
   - **NO Wine binaries** - these are downloaded separately

3. **Container Pattern** (~8MB)
   - `.wine/dosdevices/` (drive mappings)
   - `.wine/drive_c/windows/` (Windows system files)
   - Pre-configured Wine prefix

4. **Graphics Drivers**
   - `turnip-25.1.0.tzst` (Qualcomm Adreno)
   - `virgl-23.1.9.tzst` (fallback)

5. **DirectX Wrappers**
   - `dxvk-2.4.1.tzst`
   - `d8vk-1.0.tzst`
   - `vkd3d-2.13.tzst`

6. **Windows Components**
   - DirectX libraries (d3d, directsound, directplay, etc.)
   - Visual C++ runtimes (vcrun2005, vcrun2010)
   - Windows Media Decoder, XAudio

7. **Audio**
   - `pulseaudio.tzst` (~45KB)

## 🚨 Critical Realization

**Wine binaries are NOT included in the APK!**

Winlator downloads Wine at runtime from external sources. This explains:
- Why the APK is only 140MB (Wine alone is ~50MB+)
- Why we couldn't find `wine64` or `wineserver` binaries
- Why there's a "Wine installation" dialog in Winlator

## 🏗️ How Winlator Actually Works

### Initialization Flow

1. **First Launch**
   - Extract Box64 binary from assets to `/data/data/.../box64/`
   - Extract rootfs to `/data/data/.../rootfs/`
   - Download Wine from external repository
   - Setup chroot environment

2. **Creating a Container**
   - Extract container pattern to container directory
   - Run `wineboot --init` via Box64 inside chroot
   - Install selected Windows components (DirectX, VC++ runtimes)
   - Apply graphics driver (Turnip/Zink/VirGL)

3. **Launching an .exe**
   ```bash
   # Simplified command structure
   chroot /data/data/.../rootfs /bin/sh -c "
       export BOX64_*=...
       export WINE*=...
       box64 wine64 /path/to/game.exe
   "
   ```

## 📊 File Size Breakdown

| Component | Compressed | Uncompressed (est.) |
|-----------|------------|---------------------|
| Box64     | 3.9 MB     | ~12 MB              |
| Rootfs    | 3.6 MB     | ~15 MB              |
| Container | 8.1 MB     | ~30 MB              |
| Graphics  | Various    | ~50 MB              |
| DXVK      | Various    | ~20 MB              |
| **Wine** (NOT INCLUDED) | - | ~80-100 MB   |

## 🔄 Implications for steam-app

### Original Plan (❌ Won't Work)
```kotlin
// We thought we could do this:
val process = ProcessBuilder(
    "${getBox64Path()}/box64",
    "${getWinePath()}/bin/wine64",
    exePath
).start()
```

### Actual Requirements (✅ What's Needed)

1. **Need full Linux rootfs environment**
   - Cannot run Wine directly on Android
   - Wine requires glibc, X11, Linux syscalls
   - Android uses Bionic libc (incompatible with Wine)

2. **Need chroot capability**
   - Android apps cannot chroot without root
   - Alternatives: proot, unshare (limited)

3. **Need to download Wine separately**
   - Cannot bundle 100MB+ Wine in APK
   - Must download on first run or use external installer

## 🎯 Revised Integration Strategies

### Option A: Embed Entire Winlator (Easiest)
**Approach**: Treat Winlator as a library/module

**Pros**:
- Proven to work
- All components included
- Handles Wine installation automatically

**Cons**:
- APK size: ~140MB base + runtime downloads
- Complex integration
- Limited UI customization

**Implementation**:
```kotlin
// Launch Winlator's container activity
val intent = Intent(Intent.ACTION_MAIN)
intent.setComponent(ComponentName(
    "com.winlator",
    "com.winlator.XServerDisplayActivity"
))
intent.putExtra("containerId", containerId)
intent.putExtra("executable", exePath)
startActivity(intent)
```

### Option B: Use Termux + Box64 + Wine (Medium)
**Approach**: Rely on Termux proot environment

**Pros**:
- Smaller APK (<50MB possible)
- User installs Wine via Termux package manager
- Better separation of concerns

**Cons**:
- Requires Termux installed
- Complex user setup
- Dependency management issues

**Implementation**:
```kotlin
// Execute command in Termux environment
val termuxIntent = Intent()
termuxIntent.setClassName(
    "com.termux",
    "com.termux.app.RunCommandService"
)
termuxIntent.putExtra("command", "box64 wine64 $exePath")
startService(termuxIntent)
```

### Option C: Minimal Box64-Only (Hardest)
**Approach**: Only use Box64, target native Linux ARM64 Windows games

**Pros**:
- Small APK (~5MB)
- No Wine complexity
- Faster performance

**Cons**:
- Very limited game compatibility
- Most Windows games are x86/x86_64, not ARM64
- Manual porting required

## 📋 Updated Phase 1 Plan

### Week 1: Decision & POC

**Choose Integration Strategy:**
- [ ] Evaluate all three options
- [ ] Create simple POC for chosen approach
- [ ] Test with minimal executable (notepad.exe or similar)

**If Option A (Winlator Embed)**:
- [ ] Add Winlator as library dependency or include source
- [ ] Create wrapper activity for Winlator
- [ ] Test launching Winlator's container

**If Option B (Termux)**:
- [ ] Implement Termux service intent
- [ ] Create Wine installation guide
- [ ] Test Box64 + Wine via Termux

**If Option C (Box64-Only)**:
- [ ] Extract Box64 binary to data directory
- [ ] Test running native ARM64 Linux binaries
- [ ] Document severe limitations

### Week 2-3: Integration

Based on chosen approach, implement:
- Container management
- Process lifecycle handling
- Error handling and logging
- UI for configuration

## 🤔 Recommendation

For **steam-app** project goals:

**Best Approach**: **Hybrid - Winlator Backend with Custom UI**

1. **Embed Winlator core** (Container, Wine management)
2. **Replace Winlator UI** with our Material3 UI
3. **Keep Winlator logic** for actual Wine execution

**Rationale**:
- Winlator already solved the hard problems (rootfs, Wine download, chroot alternatives)
- We provide better UX with Material3 design
- Automatic game detection differentiates us
- One-tap launch vs Winlator's multi-step process

**APK Size Projection**:
- Our app base: 16MB
- Winlator core: 140MB
- **Total: ~156MB** (acceptable for game launcher)

## 📚 Additional Resources

- Winlator source: https://github.com/brunodev85/winlator
- Box64: https://github.com/ptitSeb/box64
- Wine for Android (outdated): https://dl.winehq.org/wine-builds/android/
- proot (chroot alternative): https://github.com/termux/proot

## 🎬 Next Steps

1. ✅ Extracted Box64 binaries
2. ✅ Analyzed Winlator architecture
3. ⏳ Choose integration strategy
4. ⏳ Create proof-of-concept
5. ⏳ Update WineLauncher implementation

---

**Last Updated**: 2025-12-17
**Status**: Architecture research complete, awaiting implementation decision
