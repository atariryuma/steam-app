# Winlator Integration Plan

## Overview

SteamDeck Mobile uses the **Winlator approach** for running Steam Windows games on Android:

- ✅ **Wine + Box86/Box64** - Windows to ARM translation
- ✅ **Embedded Steam Client** - Full Windows Steam client running in Winlator
- ✅ **Container-based Architecture** - Isolated environments per game
- ✅ **Native Android UI** - Kotlin + Jetpack Compose for game library management

## Architecture

```
┌─────────────────────────────────────────────┐
│          Android UI Layer                   │
│  (Jetpack Compose + Material3)             │
│  - Game Library (Home Screen)               │
│  - Game Details & Launch                    │
│  - Settings & Configuration                 │
└───────────────┬─────────────────────────────┘
                │
┌───────────────▼─────────────────────────────┐
│        Winlator Container Manager           │
│  - Container lifecycle management           │
│  - Wine/Box64 configuration                 │
│  - Process management                       │
└───────────────┬─────────────────────────────┘
                │
┌───────────────▼─────────────────────────────┐
│       Wine + Box86/Box64 Layer              │
│  - Windows API translation                  │
│  - x86/x86_64 to ARM translation           │
│  - DirectX to Vulkan (DXVK)                │
│  - Graphics drivers (Turnip/Zink/VirGL)    │
└───────────────┬─────────────────────────────┘
                │
┌───────────────▼─────────────────────────────┐
│        Steam Windows Client                 │
│  - User authentication (native Steam login) │
│  - Game downloads & updates                 │
│  - Cloud saves & achievements               │
│  - Friend list & chat                       │
└─────────────────────────────────────────────┘
```

## Why Not OpenID/OAuth?

❌ **OpenID 2.0** - Web-only, returns `invalid return protocol` on mobile
❌ **OAuth 2.0** - Requires Valve partnership & Client ID
❌ **Steam Web API** - Cannot download game files (DRM protected)

✅ **Winlator Approach** - Runs actual Steam client, full functionality

## Implementation Phases

### Phase 1: Current State ✅
- [x] Android app scaffold (Jetpack Compose + Hilt)
- [x] Room database for game library
- [x] Steam Web API integration (profile/library info only)
- [x] File import system (USB/SMB/FTP)
- [x] Download management (WorkManager)

### Phase 2: Winlator Core Integration 🚧
- [ ] Integrate Winlator as library/submodule
- [ ] Container management system
- [ ] Wine/Box64 configuration UI
- [ ] Process lifecycle handling

### Phase 3: Steam Client Embedding 📅
- [ ] Bundle Steam installer in APK
- [ ] Auto-install Steam in default container
- [ ] Launch Steam client UI
- [ ] Handle Steam updates

### Phase 4: Game Management 📅
- [ ] Detect installed games from Steam directory
- [ ] Sync game metadata with Room DB
- [ ] Generate game shortcuts
- [ ] Launch games via Wine

### Phase 5: Optimization 📅
- [ ] Graphics driver selection (Turnip/Zink)
- [ ] Per-game Wine/DXVK profiles
- [ ] Performance monitoring
- [ ] Controller mapping

## Technical Details

### Winlator Components Needed

```kotlin
// Container configuration
data class WinlatorContainer(
    val id: String,
    val name: String,
    val wineVersion: String,      // e.g., "Wine 9.0 (Custom)"
    val box64Version: String,      // e.g., "Box64 0.3.3"
    val dxvkEnabled: Boolean,
    val vulkanDriver: String,      // "turnip" | "zink" | "virgl"
    val screenResolution: String   // "1920x1080"
)

// Wine process launcher
interface WineLauncher {
    suspend fun launchExecutable(
        container: WinlatorContainer,
        exePath: String,
        args: List<String> = emptyList()
    ): Result<Process>

    suspend fun killProcess(processId: Int)
}

// Steam integration
interface SteamClient {
    suspend fun install(container: WinlatorContainer): Result<Unit>
    suspend fun launch(container: WinlatorContainer): Result<Unit>
    suspend fun getInstalledGames(): List<SteamGame>
    suspend fun launchGame(appId: Long): Result<Unit>
}
```

### File Structure

```
/data/data/com.steamdeck.mobile/
├── winlator/
│   ├── wine/                    # Wine binaries
│   ├── box86/                   # Box86/64 binaries
│   ├── dxvk/                    # DXVK files
│   └── drivers/                 # Graphics drivers
├── containers/
│   └── default/
│       ├── drive_c/
│       │   └── Program Files (x86)/
│       │       └── Steam/       # Steam installation
│       │           ├── steam.exe
│       │           └── steamapps/
│       │               ├── common/    # Game installations
│       │               └── appmanifest_*.acf  # Game metadata
│       └── wine.cfg             # Wine configuration
└── downloads/                   # Downloaded game files
```

### Steam Game Detection

```kotlin
// Parse Steam appmanifest files to detect installed games
fun parseAppManifest(file: File): SteamGame? {
    val content = file.readText()
    val appId = Regex("\"appid\"\\s+\"(\\d+)\"").find(content)?.groupValues?.get(1)
    val name = Regex("\"name\"\\s+\"([^\"]+)\"").find(content)?.groupValues?.get(1)
    val installDir = Regex("\"installdir\"\\s+\"([^\"]+)\"").find(content)?.groupValues?.get(1)

    return if (appId != null && name != null && installDir != null) {
        SteamGame(
            appId = appId.toLong(),
            name = name,
            installPath = "steamapps/common/$installDir"
        )
    } else null
}
```

## User Flow

### First Launch
1. User opens app
2. App detects no Winlator container
3. Shows "Setup Required" dialog
4. Downloads Wine/Box64 components (~200MB)
5. Creates default container
6. Installs Steam client (~20MB)
7. Launches Steam login screen
8. User logs in with Steam credentials
9. Steam downloads game list

### Launching a Game
1. User selects game from library
2. App checks if game is installed
   - If not: Shows "Download in Steam" button → Launches Steam
   - If yes: Shows "Play" button
3. User taps "Play"
4. App launches game via Wine:
   ```
   wine "C:/Program Files (x86)/Steam/steamapps/common/GameName/game.exe"
   ```
5. Game runs in fullscreen

### Settings
- Wine version selection
- Box64 profile (Performance/Stability)
- DXVK on/off
- Vulkan driver (Turnip/Zink/VirGL)
- Screen resolution
- Per-game overrides

## APK Size Considerations

Target: <200MB (excluding game files)

| Component | Size |
|-----------|------|
| Android APK (base) | ~20MB |
| Wine binaries | ~50MB |
| Box86/Box64 | ~30MB |
| DXVK | ~10MB |
| Graphics drivers | ~40MB |
| Steam installer | ~20MB |
| **Total** | **~170MB** |

Use Android App Bundle (AAB) to reduce per-device size.

## Performance Expectations

| Device | Performance |
|--------|-------------|
| Snapdragon 8 Gen 1+ | Good (30-60 FPS on most games) |
| Snapdragon 7 series | Fair (playable, 20-40 FPS) |
| Snapdragon 6 series | Poor (may struggle) |

Recommended: 8GB+ RAM, ARM64, Adreno GPU

## References

- [Winlator GitHub](https://github.com/brunodev85/winlator)
- [Steamlator (Winlator + Steam)](https://steamlator.en.uptodown.com/android)
- [Wine for Android](https://www.devicemag.com/wine-for-android/)
- [Box86/Box64 Documentation](https://github.com/ptitSeb/box86)

## Next Steps

1. **Study Winlator source code** - Understand container management
2. **Prototype Wine launcher** - Test launching simple .exe files
3. **Steam installer integration** - Automate Steam setup
4. **Game detection** - Parse appmanifest files
5. **UI polish** - Seamless game library experience

---

**Status**: Planning Phase
**Last Updated**: 2025-01-17
