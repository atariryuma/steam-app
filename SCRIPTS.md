# 🚀 SteamDeck Mobile - Build Scripts Guide

## Quick Reference

```bash
# Daily development (recommended)
quick-build.bat

# When build errors occur
clean-build.bat

# View real-time logs
view-logs.bat
```

---

## 📦 Build Scripts

### `quick-build.bat` - Fast Development Cycle
**Use for:** Daily development (90% of the time)

**Process:**
1. Build APK (with Gradle cache)
2. Install to device
3. Launch app

**Speed:** ⚡ Fast (1-3 min)

**When to use:**
- Code changes
- UI updates
- Testing features

---

### `clean-build.bat` - Full Stack Rebuild
**Use for:** Build errors, stale cache issues

**Process:**
1. Stop Gradle daemons
2. Clear all build caches
3. Clean build
4. Build APK (no cache)
5. Install to device
6. Launch app

**Speed:** 🐢 Slow (5-10 min)

**When to use:**
- Build errors persist
- After major refactoring
- After dependency updates
- Gradle cache corruption

---

## 🔍 Debug & Logging Scripts

### `view-logs.bat` - Real-time Log Viewer
- Displays Steam authentication logs in real-time
- Filters: `SteamAuth`, `SteamLogin`, `SteamDeckNavHost`
- Press Ctrl+C to stop

### `save-logs.bat` - Save Logs to File
- Saves full logcat to timestamped file
- Creates filtered Steam logs
- Useful for bug reports

### `debug-info.bat` - Debug Info Collector
- Collects device info, Android version, app version
- Gathers network status
- Saves recent Steam logs
- Output: `debug_info.txt`

---

## ⚙️ Environment Scripts

### `check-environment.bat` - Environment Checker
Checks for:
- Java JDK 17
- JAVA_HOME variable
- Android SDK
- ADB (Android Debug Bridge)
- Gradle Wrapper

**Run this first** if you encounter build issues.

### `setup-java-env.bat` - Java Environment Setup
- Sets JAVA_HOME system-wide
- Adds Java to PATH
- **Requires:** Administrator privileges
- **Requires:** Terminal restart after running

### `restart-gradle.bat` - Restart Gradle Daemon
**Use when:** Gradle builds hang or become unresponsive

---

## 📊 Comparison Table

| Script | Build | Install | Launch | Cache Clear | Speed |
|--------|-------|---------|--------|-------------|-------|
| **quick-build.bat** | ✅ | ✅ | ✅ | ❌ | ⚡⚡⚡ 1-3 min |
| **clean-build.bat** | ✅ | ✅ | ✅ | ✅ | 🐢 5-10 min |

---

## 🎯 Recommended Workflow

### 1. Initial Setup
```bash
check-environment.bat  # Verify environment
```

### 2. Daily Development
```bash
quick-build.bat        # Build → Install → Launch
view-logs.bat          # Monitor logs in another terminal
```

### 3. When Errors Occur
```bash
clean-build.bat        # Full clean rebuild
```

### 4. When Gradle Hangs
```bash
restart-gradle.bat     # Restart Gradle daemon
quick-build.bat        # Retry build
```

---

## 🔧 Troubleshooting

### "BUILD FAILED" in quick-build.bat
→ Run `clean-build.bat`

### "adb not found"
→ Run `check-environment.bat`
→ Install Android SDK Platform-Tools

### "JAVA_HOME is NOT SET"
→ Run `setup-java-env.bat` (as Admin)
→ Restart terminal

### Gradle daemon unresponsive
→ Run `restart-gradle.bat`

### Need bug report data
→ Run `debug-info.bat`
→ Share `debug_info.txt`

---

## 📝 Environment Variables

All scripts automatically set:
- `JAVA_HOME`: `C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot`
- `ADB`: `C:\Android\sdk\platform-tools\adb.exe`
- `PACKAGE`: `com.steamdeck.mobile.debug`

---

## ⚡ Performance Tips

1. **Use quick-build.bat by default** - 3x faster than clean-build
2. **Only use clean-build.bat when necessary** - Clearing cache is slow
3. **Keep Gradle daemon running** - Don't manually kill it
4. **Run view-logs.bat in separate terminal** - Monitor while developing

---

**Last updated:** 2025-12-20
