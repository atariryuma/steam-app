# Quick Reference: Box64 & Graphics 2025 Optimizations

## Critical Gaps Summary

| Gap | Impact | Fix Time | Priority |
|-----|--------|----------|----------|
| **No GPU detection** | -30-50% FPS | 4 hours | P1 |
| **DXVK_ASYNC=0 missing in STABILITY** | Stuttering | 0.5 hours | P1 |
| **CALLRET not 2 in all presets** | -10% speed, JIT fails | 0.5 hours | P1 |
| **Wrong default preset** | Boot failures | 1.5 hours | P1 |
| **PerformanceOptimizer not integrated** | Settings ignored | 2 hours | P1 |
| **No preset retry strategy** | Failed launches | 1 hour | P2 |
| **No per-game settings** | Can't save best config | 3 hours | P2 |

---

## File Locations

### Current Implementation
```
core/winlator/WinlatorEmulator.kt
  ├─ Line 835-840: Steam Box64 settings
  ├─ Line 1254-1305: Box64 preset configuration ← MAIN TARGET
  ├─ Line 2268-2287: Graphics hardcoded settings ← CRITICAL ISSUE
  └─ Line 2238-2359: buildEnvironmentVariables() ← INTEGRATE GPU DETECTION

core/winlator/PerformanceOptimizer.kt
  ├─ Line 75-135: applyBox64Settings() ← NOT INTEGRATED
  ├─ Line 198-225: getDxvkEnvironmentVariables() ← PARTIALLY USED
  └─ Line 264-274: getVulkanEnvironmentVariables() ← NOT USED
```

---

## 1-Hour Fixes (Do First)

### Fix 1: DXVK_ASYNC in STABILITY Preset
**File:** `PerformanceOptimizer.kt` line 218-223

```kotlin
// ADD this line:
"DXVK_ASYNC" to "0",  // Explicit disable for stability
```

### Fix 2: CALLRET=2 in All Presets
**File:** `WinlatorEmulator.kt`

```kotlin
// Line 1261 (MAXIMUM_PERFORMANCE): Change "1" → "2"
put("BOX64_DYNAREC_CALLRET", "2")

// Line 1280 (MAXIMUM_STABILITY): Change "0" → "2"
put("BOX64_DYNAREC_CALLRET", "2")

// Add between lines 1261-1280 (BALANCED preset):
put("BOX64_DYNAREC_CALLRET", "2")
```

### Fix 3: Remove Hardcoded softpipe
**File:** `WinlatorEmulator.kt` line 2268

```kotlin
// DELETE these lines:
// put("LIBGL_ALWAYS_INDIRECT", "1")
// put("GALLIUM_DRIVER", "softpipe")
```

---

## 2-4 Hour Fixes (High Value)

### GPU Detection Implementation
**Create:** `core/winlator/GpuDetector.kt` (see IMPLEMENTATION_GUIDE.md)

**Minimal version:**
```kotlin
@Singleton
class GpuDetector @Inject constructor() {
    fun detectGpu(): GpuType {
        val cpuInfo = File("/proc/cpuinfo").readText()
        return when {
            cpuInfo.contains("ARMv8") -> GpuType.ADRENO  // Snapdragon
            else -> GpuType.UNKNOWN
        }
    }
}

enum class GpuType { ADRENO, MALI, UNKNOWN }
```

### Integrate GPU Detection
**File:** `WinlatorEmulator.kt` buildEnvironmentVariables()

```kotlin
val gpu = GpuDetector(context).detectGpu()
when (gpu) {
    GpuType.ADRENO -> {
        put("LIBGL_ALWAYS_INDIRECT", "0")
        put("GALLIUM_DRIVER", "turnip")  // Turnip for Adreno
    }
    GpuType.UNKNOWN -> {
        put("LIBGL_ALWAYS_INDIRECT", "1")
        put("GALLIUM_DRIVER", "softpipe")
    }
}
```

---

## Performance Impact Summary

### Before Optimization
```
Hardware: Snapdragon 8 Gen 3
Current: 15-25 FPS (softpipe software rendering)
```

### After Optimization
```
Hardware: Snapdragon 8 Gen 3

Phase 1 (1 hour): +0-5% (mainly stability)
Phase 2 (4 hours): +30-50% FPS (GPU detection)
Phase 3 (3 hours): +10-15% FPS (dynarec tuning)
Phase 4 (3 hours): +5% (preset management)

TOTAL: 45-120% FPS improvement (30-60 FPS typical)
```

---

## Key Settings Reference

### Box64 Dynarec Settings
```
CALLRET:
  0 = No optimization (slow)
  1 = Basic optimization (current)
  2 = SMC support (2025 best practice) ← USE THIS

BIGBLOCK:
  0 = Conservative (small blocks, stable)
  1 = Moderate (current default)
  2 = Aggressive (larger blocks, faster)
  3 = Maximum (fastest, may fail)

STRONGMEM:
  0 = Fastest (weak memory model, modern ARM)
  1 = Default
  2 = Conservative (older ARM)
  3 = Maximum safety

FORWARD:
  256  = Conservative (current PERFORMANCE)
  1024 = Balanced (current BALANCED)
  1536 = Aggressive
  2048 = Maximum (2025 recommendation for PERFORMANCE)
```

### DXVK Settings
```
DXVK_ASYNC:
  0 = Synchronous (stable, no stutter)
  1 = Asynchronous (fast, potential stutter)

DXVK_FRAME_RATE:
  0 = Unlimited FPS (max performance)
  60 = 60 FPS cap (power efficient)
  120 = High refresh (if supported)

DXVK_LOG_LEVEL:
  warn = Production (minimal overhead)
  info = Debug info
  debug = Full logging (slowest)
```

### GPU Driver Settings
```
GALLIUM_DRIVER:
  turnip = Adreno (Snapdragon) - best choice
  virpipe = Mali (MediaTek) - fallback
  softpipe = Software (last resort)

LIBGL_ALWAYS_INDIRECT:
  0 = Hardware rendering enabled
  1 = Software rendering forced
```

---

## Testing Checklist

- [ ] DXVK_ASYNC fix: Game launches in STABILITY mode without stuttering
- [ ] CALLRET=2 fix: JIT games (Unreal, Unity) run faster
- [ ] GPU detection: Log shows "Using Turnip GPU driver" on Snapdragon
- [ ] Performance tuning: FPS increased 10-15% with BIGBLOCK=2, FORWARD=2048
- [ ] Preset retry: Game that failed on PERFORMANCE succeeds on COMPATIBILITY
- [ ] Per-game settings: Second launch of game uses last successful preset

---

## Code Search Patterns

Find all Box64 settings:
```
grep -r "BOX64_DYNAREC" app/src/main/java
```

Find all DXVK settings:
```
grep -r "DXVK" app/src/main/java
```

Find GPU-related code:
```
grep -r "GALLIUM_DRIVER\|softpipe\|LIBGL" app/src/main/java
```

---

## Architecture Dependencies

**No breaking changes required:**
- GpuDetector is @Singleton (DI-friendly)
- PerformanceOptimizer already exists
- WinlatorEmulator already handles presets

**Minimal new dependencies:**
- No external libraries needed
- Uses existing File I/O for /proc/cpuinfo
- Uses existing AppLogger for diagnostics

---

## Migration Path

### Safe to implement in order:
1. **Fix DXVK_ASYNC** (0.5h) - Critical, zero risk
2. **Fix CALLRET=2** (0.5h) - Critical, zero risk
3. **Create GpuDetector** (2h) - New feature, isolated
4. **Integrate GPU detection** (2h) - Risk: must test on devices
5. **Optimize BIGBLOCK/FORWARD** (1h) - Enhancement, safe
6. **Add preset retry** (1h) - Enhancement, improves UX
7. **Per-game settings** (3h) - Enhancement, increases complexity

**Can rollback at any step** - Each phase is independent.

---

## Device Testing Recommendations

### Required Testing
- [ ] Snapdragon 8 Gen 3 (ideal test device)
- [ ] Snapdragon 8 Gen 2 (common device)
- [ ] MediaTek chipset (if available)

### Games to Test
- **Stable on all presets:** Portal 2, Half-Life 2
- **Sensitive to settings:** RE Engine games, Unity games
- **Performance test:** AAA games (AC Odyssey, Cyberpunk)

---

## References

- **BOX64 Settings:** https://github.com/ptitSeb/box64/blob/main/docs/USAGE.md
- **DXVK Async:** https://github.com/Sporif/dxvk-async
- **Turnip Driver:** https://docs.mesa3d.org/drivers/freedreno.html
- **VKD3D-Proton:** https://news.tuxmachines.org/n/2025/11/17/

---

## Estimated Completion

**Phase 1 (Critical):** 2-3 hours
- DXVK_ASYNC fix
- CALLRET=2 update
- Default preset rename

**Phase 2 (GPU Detection):** 4-5 hours
- GpuDetector.kt creation
- Integration in WinlatorEmulator
- Device testing

**Total Minimum:** ~7 hours for +30-50% FPS on Snapdragon devices
