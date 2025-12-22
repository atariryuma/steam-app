# Build Stability Guide

## Root Causes

Four factors were contributing to build instability:

### 1. Configuration Cache Side Effects
- Gradle 8.7's Configuration Cache has poor compatibility with KSP (Hilt/Room)
- Changes not reflected when cache is corrupted
- Unstable recovery after build failures

### 2. Gradle Daemon Memory Leaks
- Long-running daemons consume heap memory
- Race conditions occur with parallel builds
- Previous build state persists causing errors

### 3. Incremental Build Cache Inconsistencies
- KSP-generated code (Hilt, Room) cache remains stale
- `.gradle/caches/` and `.kotlin/` metadata corruption
- R8 optimization cache inconsistencies

### 4. Windows File Locking Issues
- Gradle daemon keeps files locked
- File deletion/overwriting fails during build

---

## Applied Fixes

### gradle.properties Changes
```properties
# Disable Configuration Cache (KSP compatibility issues)
# org.gradle.configuration-cache=true

# Disable KSP incremental compilation (stability priority)
ksp.incremental=false
ksp.incremental.intermodule=false

# Disable Gradle caching (clean build priority)
org.gradle.caching=false
```

### Build Scripts Added
- `clean-build.bat`: Full clean build (when issues occur)
- `quick-build.bat`: Normal development build
- `restart-gradle.bat`: Restart daemon

---

## Usage

### Normal Development Build
```batch
quick-build.bat
```

### When Changes Not Reflected
```batch
clean-build.bat
```

### When Build Hangs
```batch
restart-gradle.bat
```

---

## Build Failure Checklist

1. **Restart Gradle Daemon**
   ```batch
   gradlew.bat --stop
   ```

2. **Clear Caches**
   ```batch
   rmdir /s /q .gradle\caches
   rmdir /s /q .kotlin
   rmdir /s /q app\build
   ```

3. **Execute Clean Build**
   ```batch
   gradlew.bat clean --no-configuration-cache
   gradlew.bat assembleDebug --no-configuration-cache
   ```

4. **If Still Failing**
   - Restart Android Studio / IntelliJ IDEA
   - Restart PC (release Windows file locks)

---

## Performance Impact

### Trade-offs
- ❌ **Configuration Cache disabled**: Slightly increased initial build time (+5~10 seconds)
- ❌ **KSP incremental compilation disabled**: Slower rebuilds when Hilt/Room changes (+10~20 seconds)
- ❌ **Gradle cache disabled**: More frequent dependency re-downloads

### Benefits
- ✅ **Reliable change detection**: Code changes are 100% reflected
- ✅ **Improved build success rate**: Significantly fewer errors
- ✅ **Debugging efficiency**: No stale code remains

---

## Future Improvements

### Consider with Gradle 9.0+
- Wait for improved KSP support in Configuration Cache
- Re-enable when stable

### Consider with KSP 2.0+
- Re-enable incremental compilation when stability improves
- Verify with Kotlin 2.1+

### Current Recommended Settings
- **During development**: Maintain current settings (stability priority)
- **CI/CD**: Clean build with `--no-daemon` + `--no-configuration-cache`
- **Release**: Full clean build mandatory

---

## References

- [Gradle Configuration Cache](https://docs.gradle.org/current/userguide/configuration_cache.html)
- [KSP Incremental Processing](https://kotlinlang.org/docs/ksp-incremental.html)
- [Android Gradle Plugin DSL](https://developer.android.com/build/releases/gradle-plugin-dsl)
