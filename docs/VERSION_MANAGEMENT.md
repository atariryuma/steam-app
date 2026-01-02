# Component Version Management System

## Overview

The Steam Deck Mobile app uses a centralized version management system for all Winlator components (Wine, Box64, DXVK, Proton, etc.). This allows easy updates without modifying source code.

## Architecture

```
component_versions.json (Configuration)
         ↓
ComponentVersionManager (Reader)
         ↓
  ┌──────┴──────┬─────────────┬──────────────┐
  ↓             ↓             ↓              ↓
WinlatorEmulator  ProtonManager  SteamLauncher  Future Components
         ↓
   Wine 10.10 OR Proton 10 ARM64EC (configurable)
```

## Benefits

### ✅ Simple Updates
Update `component_versions.json` to change component versions - no code changes required.

### ✅ Single Source of Truth
All version information in one file prevents inconsistencies.

### ✅ Future-Proof
Easy to add dynamic version checking or automatic updates later.

### ✅ No Over-Engineering
Simple JSON config + reader class - no complex dependency injection or update mechanisms.

## Configuration File

**Location**: `app/src/main/assets/winlator/component_versions.json`

**Structure**:
```json
{
  "wine": {
    "version": "10.10",
    "asset_path": "rootfs.tar.xz",
    "source": "Winlator 11.0 Beta"
  },
  "box64": {
    "version": "0.3.6",
    "asset_path": "box64/box64-0.3.6.tar.xz"
  }
}
```

## Usage

### Reading Component Versions

```kotlin
@Inject
class YourClass(
    private val versionManager: ComponentVersionManager
) {
    fun example() {
        // Load config (call once at initialization)
        versionManager.loadConfig()

        // Get versions
        val wineVersion = versionManager.getWineVersion()  // "10.10"
        val box64Version = versionManager.getBox64Version()  // "0.3.6"

        // Get asset paths
        val wineAsset = versionManager.getWineAssetPath()  // "rootfs.tar.xz"
        val box64Asset = versionManager.getBox64AssetPath()  // "box64/box64-0.3.6.tar.xz"

        // Get environment variables
        val mesaVars = versionManager.getMesaZinkEnvironmentVars()
    }
}
```

### Adding a New Component

1. Update `component_versions.json`:
```json
{
  ...
  "new_component": {
    "version": "1.0.0",
    "asset_path": "path/to/asset.tar.xz",
    "description": "Component description"
  }
}
```

2. Add data class in `ComponentVersionManager.kt`:
```kotlin
@Serializable
data class NewComponentConfig(
    val version: String,
    val assetPath: String,
    val description: String
)
```

3. Add to `ComponentVersionsConfig`:
```kotlin
@Serializable
data class ComponentVersionsConfig(
    ...
    val newComponent: NewComponentConfig
)
```

4. Add getter method:
```kotlin
fun getNewComponentVersion(): String {
    return config?.newComponent?.version ?: "1.0.0"
}
```

## Upgrading Components

### Example: Upgrading Box64 from 0.3.6 to 0.3.8

**Step 1**: Download Box64 0.3.8
```bash
# Download from GitHub releases or build from source
wget https://github.com/ptitSeb/box64/releases/download/v0.3.8/box64-0.3.8-arm64.tar.gz
```

**Step 2**: Convert to tar.xz format
```bash
tar -xzf box64-0.3.8-arm64.tar.gz
tar -cJf box64-0.3.8.tar.xz box64/
```

**Step 3**: Add to assets
```
app/src/main/assets/box64/box64-0.3.8.tar.xz
```

**Step 4**: Update `component_versions.json`
```json
{
  "box64": {
    "version": "0.3.8",
    "asset_path": "box64/box64-0.3.8.tar.xz",
    "description": "x86_64 to ARM64 binary translator",
    "source": "Official GitHub Release",
    "features": [
      "Dynamic recompilation",
      "WoW64 support",
      "DynaCache feature (NEW in 0.3.8)"
    ]
  }
}
```

**Step 5**: Rebuild
```bash
./gradlew.bat clean assembleDebug
```

**Done!** No code changes required.

## Fallback Behavior

If the JSON file fails to load or is missing values, the `ComponentVersionManager` provides default fallback values:

```kotlin
fun getWineVersion(): String {
    return config?.wine?.version ?: "10.10"  // Fallback to 10.10
}
```

This ensures the app continues to work even if the config file is corrupted or missing.

## Switching Between Wine and Proton

The app supports both Wine 10.10 and Proton 10 ARM64EC. You can switch between them by editing `component_versions.json`:

### To Use Proton 10 (Current Default)

```json
{
  "wine": {
    "version": "10.10",
    "enabled": false
  },
  "proton": {
    "version": "10.0",
    "enabled": true
  }
}
```

### To Use Wine 10.10

```json
{
  "wine": {
    "version": "10.10",
    "enabled": true
  },
  "proton": {
    "version": "10.0",
    "enabled": false
  }
}
```

**Important**: Only one should be enabled at a time. The app will use Proton if `proton.enabled: true`, otherwise Wine.

## Integration Points

### WinlatorEmulator

- Uses `versionManager.getActiveRootfsPath()` to extract active rootfs (Wine or Proton)
- Uses `versionManager.getBox64AssetPath()` to extract Box64
- Logs version info: "Rootfs extraction successful (Proton 10.0)" or "(Wine 10.10)"
- Automatically selects correct rootfs based on `component_versions.json`

### ProtonManager

- Uses `versionManager.isProtonEnabled()` to check active configuration
- Uses `versionManager.getMesaZinkEnvironmentVars()` for Mesa/Zink configuration
- Provides optimized environment variables for Steam games
- Allows updating OpenGL/Vulkan settings without code changes

## Future Enhancements

### Possible Future Features (Not Currently Implemented)

1. **Dynamic Version Checking**
```kotlin
suspend fun checkForUpdates(): List<ComponentUpdate> {
    // Fetch latest versions from GitHub API
    // Compare with current versions
    // Return list of available updates
}
```

2. **Automatic Download**
```kotlin
suspend fun downloadComponent(component: ComponentType): Result<File> {
    val url = config.upgradeUrls[component]
    // Download and verify checksum
}
```

3. **Version History**
```kotlin
fun getVersionHistory(): List<VersionChange> {
    // Track component version changes over time
}
```

These features can be added later without changing the core architecture.

## Best Practices

### ✅ DO
- Update `component_versions.json` when adding new assets
- Include version number in asset filename (e.g., `box64-0.3.8.tar.xz`)
- Test after every version upgrade
- Document version changes in git commit messages

### ❌ DON'T
- Don't hardcode versions in source code
- Don't skip the JSON config when adding components
- Don't change asset paths without updating JSON
- Don't over-engineer - keep it simple

## Troubleshooting

### Build Fails After Version Update

**Check**:
1. Asset file exists at specified path in `assets/` directory
2. Filename in JSON matches actual file in assets
3. JSON syntax is valid (use online JSON validator)

### Component Not Loading

**Check logs**:
```bash
adb logcat | grep ComponentVersionManager
```

Look for:
- "Component versions loaded successfully"
- "Failed to load component versions, using defaults"

### APK Size Too Large

After adding new components, check APK size:
```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

Target: < 80MB for ARM64

## Related Files

- Configuration: [`app/src/main/assets/winlator/component_versions.json`](../app/src/main/assets/winlator/component_versions.json)
- Manager: [`ComponentVersionManager.kt`](../app/src/main/java/com/steamdeck/mobile/core/winlator/ComponentVersionManager.kt)
- Integration: [`WinlatorEmulator.kt`](../app/src/main/java/com/steamdeck/mobile/core/winlator/WinlatorEmulator.kt)
- Environment: [`ProtonManager.kt`](../app/src/main/java/com/steamdeck/mobile/core/proton/ProtonManager.kt)

## Version

- **System Version**: 1.0
- **Last Updated**: 2025-12-26
- **Maintainer**: Steam Deck Mobile Team
