# SteamDeck Mobile User Manual

**Version**: 0.9.0 (MVP)
**Last Updated**: 2025-01-16

---

## 📱 Introduction

**SteamDeck Mobile** is an app for running Windows games on Android devices.
It includes Winlator (Wine + Box64) and can run non-Steam games as well.

### ⚠️ Important: This App is Under Development

- Currently an **MVP (Minimum Viable Product)**
- Not all features are fully functional
- Bugs and issues may occur

### 🎯 What You Can and Cannot Do

| Can Do ✅ | Cannot Do ❌ |
|------------|---------------|
| Run local Windows games (.exe) | Authenticate as official Steam client |
| Manage and organize game library | Direct downloads from Steam store |
| Play with Bluetooth controllers | Online multiplayer (some possible) |
| Copy game files via USB/network | Smooth performance with latest AAA games |
| Customize game launch settings | Run DRM-protected games |

---

## 📋 Requirements

### 1. Android Device Requirements

| Item | Minimum | Recommended |
|------|---------|-------------|
| **Android OS** | 8.0 (API 26) | 11.0 or higher |
| **CPU** | ARM64-v8a compatible | Snapdragon 8 Gen 1 or higher |
| **RAM** | 4GB or more | 8GB or more |
| **Storage** | 3GB free space | 10GB or more |
| **Screen Resolution** | 1280x720 (HD) | 1920x1080 (FHD) |

### 2. Compatible Devices

**Verified (Recommended)**:
- Xiaomi Pad 6 Pro (Snapdragon 8+ Gen 1)
- Samsung Galaxy Tab S8/S9
- OnePlus Pad
- Lenovo Legion Y700

**May Work**:
- Smartphones/tablets with Snapdragon 845 or higher
- MediaTek Dimensity 9000 or higher

**Will Not Work**:
- Intel/AMD x86 devices
- 32-bit ARM devices (ARMv7)
- Android emulators (BlueStacks, etc.)

### 3. Optional Accessories

- **Bluetooth game controller** (Xbox/PlayStation/8BitDo, etc.)
- **USB storage/external drive** (for transferring game files)
- **Wi-Fi environment** (for file transfer, Steam integration)

---

## 🚀 Installation

### Method 1: Install from APK File (Recommended)

1. **Download APK file**
   - Download latest version from [Releases](https://github.com/atariryuma/steam-app/releases)
   - Select `app-release.apk` or `app-debug.apk`

2. **Allow installation from unknown sources**
   - Settings → Security → Unknown sources
   - Grant permission to file manager app

3. **Tap APK to install**
   - Tap downloaded APK file
   - Tap "Install"

### Method 2: Build with Android Studio (For Developers)

See [SETUP.md](SETUP.md) or [README.md](README.md) for details.

---

## 📖 Basic Usage

### First Launch

1. **Launch the app**
   - Tap the icon on your home screen

2. **Wait for initialization** (first time only, 1-3 minutes)
   - Winlator environment setup runs automatically
   - Screen displays "Initializing..."
   - **Do not close the screen during this process**

3. **Game library screen appears**
   - Empty library shown on first launch

### Adding Games

#### Method A: Manual Add from Local Files

1. **Tap "+" button on home screen**

2. **Enter game information**
   - **Game Name**: Any name (e.g., "Stardew Valley")
   - **Executable**: Tap "Select" button to choose .exe file
   - **Install Folder**: Select game folder (optional)

3. **Tap "Add"**

4. **Game appears in library**

#### Method B: Copy from USB/External Storage

1. **Connect USB storage**
   - Use OTG-compatible USB cable

2. **Find .exe file with file manager**
   - Recommended: Solid Explorer, FX File Explorer

3. **Copy game folder to device**
   - Destination: `/sdcard/Games/` etc.

4. **Add game using Method A above**

#### Method C: Steam Integration (Experimental Feature)

⚠️ **Note**: Steam integration is currently under development with the following limitations:
- **View only** of Steam library
- **Game downloads not implemented**
- Does not use Steam authentication (public API only)

**How to use**:
1. Tap "⚙️" → "Settings" in upper right of home screen
2. Enter "Steam ID" in "Steam Integration" section
   - Example: `76561198012345678` (17-digit number)
   - How to find: Search on [steamid.io](https://steamid.io/)
3. Tap "Sync"
4. Owned games are **displayed only** in library

---

## 🎮 Playing Games

### Basic Launch Procedure

1. **Tap game from library**

2. **Tap "▶ Play" on detail screen**

3. **First launch notes**
   - Wine container creation takes 30 seconds to 1 minute
   - Screen may show nothing (this is normal)

4. **Game launches**

### Using Controllers

#### Connecting

1. **Pair Bluetooth controller**
   - Android Settings → Bluetooth → Search for devices
   - Enable pairing mode on controller

2. **Verify connection**
   - Auto-detected in app
   - Can verify in Settings → Controller Settings

#### Supported Controllers

| Brand | Auto-detect | Verified |
|---------|----------|---------|
| Xbox (One/Series) | ✅ | ✅ |
| PlayStation (DualShock 4/5) | ✅ | ✅ |
| Nintendo Switch Pro | ✅ | ⚠️ Needs setup |
| 8BitDo | ✅ | ✅ |
| Gamesir | ⚠️ | ⚠️ |

#### Button Mapping

1. **Settings → Controller Settings**

2. **Tap "Button Mapping"**

3. **Press buttons to configure**
   - Follow on-screen instructions
   - Can also configure sticks/D-pad

4. **Tap "Save"**

### Troubleshooting

#### Game Won't Launch

**Causes and Solutions**:

1. **"Executable not found" error**
   - Verify .exe file path is correct
   - Check file hasn't been deleted

2. **Black screen with nothing displayed**
   - Wait 10-30 seconds (Wine initializing)
   - If still won't launch:
     - Force close app and restart
     - Settings → Wine Settings → Recreate container

3. **"Failed to initialize Winlator" error**
   - Check storage space (minimum 2GB required)
   - Reinstall app
   - Clear app data: Settings → Apps → SteamDeck Mobile → Clear data

4. **Game launches but no controls work**
   - Verify controller is connected properly
   - Try opening menu with touch controls
   - Some games only support mouse/keyboard

#### Slow Performance/Stuttering

**Solutions**:

1. **Lower graphics settings**
   - Reduce resolution in game settings
   - Lower anti-aliasing, shadow quality

2. **Change Wine settings** (Advanced users)
   - Detail screen → Container settings
   - Change Box64 Preset to "Compatibility Priority"
   - Change Wine Version (9.0 → 8.0, etc.)

3. **Close background apps**
   - Close other apps from Android's recent apps

#### Controller Not Recognized

**Solutions**:

1. **Check in Android settings**
   - Settings → Bluetooth → Verify device is "Connected"

2. **Re-pair**
   - Delete device from Bluetooth
   - Pair again

3. **Restart app**
   - Force close and relaunch app

4. **Update controller firmware**
   - Update via manufacturer's official app

---

## ⚙️ Settings Guide

### Basic Settings

#### Steam Integration

- **How to get Steam ID**:
  1. Visit [steamid.io](https://steamid.io/)
  2. Enter your Steam profile URL
  3. Copy the 17-digit "steamID64 (Dec)" number

- **Notes**:
  - Profile must be set to public
  - Cannot sync if set to private

#### Storage Settings

- **Game install location**: Default is `/sdcard/SteamDeckMobile/games/`
- **How to change**: Settings → Storage → Change install location

### Advanced Settings (For Advanced Users)

#### Wine Settings

- **Wine Version**: 9.0 (default)
  - Can change to 8.0 etc. if compatibility issues

- **Box64 Preset**:
  - **Fast**: For latest games, high-performance devices
  - **Balanced**: Recommended for most games (default)
  - **Compatibility Priority**: For old games, unstable operation

#### Per-Game Settings

1. **Tap game in library**
2. **Tap "Details" tab**
3. **Change "Container Settings"**
   - Wine version
   - Box64 Preset
   - Custom arguments (-dx11, -opengl, etc.)

---

## 🎯 FAQ

### Q1: What games will run?

**A**: The following games are relatively likely to work:

**Likely to Work**:
- 2D/lightweight indie games (Terraria, Stardew Valley, etc.)
- Older 3D games (Half-Life 2, Portal, Fallout 3, etc.)
- DirectX 9/10 generation games

**Difficult to Run**:
- Latest AAA titles (Cyberpunk 2077, Elden Ring, etc.)
- DRM-protected games (Denuvo, EAC, etc.)
- DirectX 12-only games

### Q2: Can I download Steam games?

**A**: **Not currently supported**. Obtain game files using one of these methods:
- Download from Steam on PC → Transfer via USB/network
- Purchase DRM-free version (GOG.com, etc.)

### Q3: Does online multiplayer work?

**A**: **Depends on the game**:
- ✅ **Direct IP connection**: Possible (Minecraft, etc.)
- ✅ **P2P connection**: Partially possible
- ❌ **Official Steam servers**: Not possible (requires Steam authentication)
- ❌ **Anti-cheat games**: Not possible (EAC, BattlEye, etc.)

### Q4: Can I use mods?

**A**: **Yes, you can**:
- Copy mod files to game folder
- Mod loaders (Nexus Mod Manager, Valhalla, etc.) may also work
- Some mods may not function

### Q5: Where are save files stored?

**A**: Saved inside Wine container:
- Path: `/sdcard/Android/data/com.steamdeck.mobile/files/winlator/`
- **Deleted when app is uninstalled**
- Backups recommended

### Q6: Is it free?

**A**: **Yes, completely free**:
- Open source (MIT License)
- No ads
- No in-app purchases

### Q7: Is it illegal?

**A**: **Legal when used appropriately**:
- ✅ Running legally purchased games: **Legal**
- ✅ Running DRM-free games: **Legal**
- ❌ Running pirated games: **Illegal**

**Important**: This app is a legal game execution tool. It does not recommend or support using illegal copies.

### Q8: Battery drains quickly

**A**: This is normal operation:
- Windows game emulation is high load
- Solutions:
  - Play while charging
  - Lower graphics settings
  - Reduce screen brightness

### Q9: Device gets hot

**A**: Heat during game execution is unavoidable:
- Solutions:
  - Use cooling fan case
  - Take breaks (every 30 minutes)
  - Let device cool down

---

## 🐛 Bug Reports & Feature Requests

### If You Find a Bug

1. **Report on GitHub Issues**
   - [Issues](https://github.com/atariryuma/steam-app/issues) page
   - Select "New Issue" → "Bug Report"

2. **Report contents**:
   - Device name & Android version
   - Steps to reproduce
   - Error message (if any)
   - Screenshots

### To Suggest New Features

1. **Suggest on GitHub Issues**
   - Select "New Issue" → "Feature Request"

2. **Suggestion contents**:
   - Description of desired feature
   - Use case
   - Implementation examples in similar apps (if any)

---

## 📚 References & Related Links

### Official Resources

- [GitHub Repository](https://github.com/atariryuma/steam-app)
- [Developer Documentation](README.md)
- [Setup Guide](SETUP.md)

### External Resources

- [Winlator Project](https://github.com/brunodev85/winlator) - Emulation engine
- [WineHQ](https://www.winehq.org/) - Wine official site
- [ProtonDB](https://www.protondb.com/) - Game compatibility information

### Community

No official community currently, but discussions possible on GitHub Discussions.

---

## 📝 Update History

### Version 0.9.0 (2025-01-16)
- ✅ Phase 5 complete: Controller support implemented
- ✅ Xbox/PlayStation/Switch Pro auto-detection
- ✅ Button mapping functionality
- ✅ Deadzone adjustment

### Version 0.8.0 (2025-01-12)
- ✅ Phase 4 complete: Download management implemented
- ✅ Wine 9.0 + Box64 0.3.6 integration
- ✅ APK size optimization (63MB achieved)

### Version 0.7.0 (2025-01-08)
- ✅ Phase 3 complete: File import features
- ✅ USB OTG/SMB/FTP support

### Version 0.6.0 (2025-01-05)
- ✅ Phase 2 complete: Steam Web API integration
- ✅ Library sync functionality (view only)

---

## ⚖️ License & Disclaimer

### License

This project is released under the MIT License.
See [LICENSE](LICENSE) file for details.

### Disclaimer

- This app is **unofficial**. Not affiliated with Valve Corporation or Steam
- Does not guarantee game operation
- Not responsible for device damage or data loss
- Does not recommend or support use of illegal copies

### Trademarks

- "Steam" is a registered trademark of Valve Corporation
- "PlayStation" is a registered trademark of Sony Interactive Entertainment Inc.
- "Xbox" is a registered trademark of Microsoft Corporation
- "Nintendo Switch" is a registered trademark of Nintendo Co., Ltd.

---

## 🙏 Acknowledgments

This app is based on the following open source projects:

- **Winlator** by Bruno Guerreiro - Windows emulation engine
- **Wine** - Windows compatibility layer
- **Box64** - x86_64 emulator
- **Jetpack Compose** - Android UI framework

---

**Finally**: This app is still under development. Thank you for your patience 😊

If you encounter any problems, please don't hesitate to report them on GitHub Issues!

---

Made with ❤️ for Steam gamers on Android
