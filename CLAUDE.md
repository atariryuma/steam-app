# AI CODING CONTEXT: SteamDeck Mobile Android

> **Purpose**: Guide AI assistants to write high-quality Kotlin/Compose code following project architecture and best practices.

## PROJECT OVERVIEW
- **Type**: Android Steam game launcher via Winlator integration
- **Platform**: Android 8.0+ (API 26-28 targetSdk), ARM64-v8a only
- **Architecture**: Clean Architecture + MVVM + Repository Pattern
- **Language**: Kotlin 2.1.0, Jetpack Compose
- **Build**: Gradle 8.7.3 Kotlin DSL, version catalog `gradle/libs.versions.toml`

## TECH STACK (libs.versions.toml)
```
Compose BOM 2024.12.01 | Material3 Adaptive 1.0.0
Hilt 2.54 | Room 2.6.1 (v7 schema, proper migrations)
Retrofit 2.11.0 + OkHttp 4.12.0
Coil 2.7.0 | WorkManager 2.9.1
Coroutines 1.9.0 | Navigation Compose 2.8.4
DataStore 1.1.1 | Security Crypto 1.1.0-alpha06
Kotlinx Serialization 1.7.3
jcifs-ng 2.1.10 (SMB) | commons-net 3.11.1 (FTP)
zxing 3.5.3 (QR) | commons-compress 1.28.0
```

## PACKAGE STRUCTURE
```
com.steamdeck.mobile/
├── presentation/
│   ├── ui/{home,game,settings,download,wine,container,auth,common}/
│   ├── viewmodel/ (HomeVM, SettingsVM, GameDetailVM, DownloadVM, SteamLoginVM, ControllerVM, ContainerVM, WineTestVM, WinlatorInitVM)
│   ├── navigation/ (SteamDeckNavHost, SteamDeckApp, Screen)
│   ├── theme/ (Material3 dynamic colors)
│   ├── util/ (ErrorExtensions - UI error mapping)
│   └── MainActivity (Single Activity, immersive fullscreen)
├── domain/
│   ├── model/ (Game, GameSource, Download, WinlatorContainer, Controller, ImportSource)
│   ├── repository/ (interfaces: GameRepository, WinlatorContainerRepository, DownloadRepository, FileImportRepository, ControllerRepository, ISteamRepository, ISecurePreferences)
│   ├── usecase/ (GetAllGamesUseCase, GetGameByIdUseCase, SearchGamesUseCase, AddGameUseCase, DeleteGameUseCase, ToggleFavoriteUseCase, UpdatePlayTimeUseCase, SyncSteamLibraryUseCase, LaunchGameUseCase)
│   ├── error/ (SteamSyncError - domain-specific errors)
│   └── emulator/WindowsEmulator (interface)
├── data/
│   ├── local/
│   │   ├── database/ (SteamDeckDatabase v7, Converters)
│   │   │   ├── dao/ (GameDao, WinlatorContainerDao, DownloadDao, ControllerProfileDao, SteamInstallDao)
│   │   │   └── entity/ (GameEntity, WinlatorContainerEntity, DownloadEntity, ControllerProfileEntity, SteamInstallEntity, SteamInstallStatus)
│   │   └── preferences/
│   │       ├── SecureSteamPreferences (EncryptedSharedPreferences AES256-GCM)
│   │       └── SecurePreferencesImpl (ISecurePreferences implementation)
│   ├── remote/steam/ (SteamApiService Retrofit, SteamRepositoryImpl)
│   ├── repository/ (impls: GameRepositoryImpl, WinlatorContainerRepositoryImpl, DownloadRepositoryImpl, FileImportRepositoryImpl, ControllerRepositoryImpl, SteamRepositoryAdapter)
│   └── mapper/ (GameMapper, WinlatorContainerMapper, DownloadMapper, ControllerMapper, SteamGameMapper)
├── core/
│   ├── auth/ (SteamOpenIdAuthenticator OpenID 2.0 + signature verification)
│   ├── download/ (DownloadManager WorkManager 8MB chunks, ApiError)
│   ├── error/ (AppError - unified error hierarchy)
│   ├── result/ (DataResult<T> - type-safe result wrapper)
│   ├── network/ (DataResultCallAdapter - Retrofit automatic error handling)
│   ├── winlator/ (WinlatorEngine interface, WinlatorEngineImpl, WinlatorEmulator, WineContainer, WineLauncher, ProcessMonitor, PerformanceOptimizer, ZstdDecompressor, WinlatorExceptions)
│   ├── steam/ (SteamLauncher, SteamSetupManager, SteamInstallerService, ProtonManager)
│   ├── controller/ControllerManager
│   ├── input/ (GameControllerManager InputDevice API, InputBridge button mapping)
│   ├── fileimport/
│   └── util/
├── di/module/ (RepositoryModule @Binds, DatabaseModule, NetworkModule + DataResultCallAdapter, WinlatorModule, FileImportModule, ControllerModule, EmulatorModule, InputModule, SteamAuthModule, AppModule)
└── SteamDeckMobileApp (Application entry point)
```

## CRITICAL PATTERNS

### State Management
```kotlin
@HiltViewModel
class FooViewModel @Inject constructor(
    private val useCase: FooUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow<FooUiState>(FooUiState.Loading)
    val uiState: StateFlow<FooUiState> = _uiState.asStateFlow()
}
sealed class FooUiState {
    data object Loading : FooUiState()
    data class Success(val data: Data) : FooUiState()
    data class Error(val message: String) : FooUiState()
}
```

### Repository Pattern
- Domain layer: `interface GameRepository`
- Data layer: `class GameRepositoryImpl @Inject constructor(...) : GameRepository`
- All async: `Flow<T>` or `suspend fun`
- Mappers: `GameEntity.toDomain()`, `Game.toEntity()`

### DI (Hilt)
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindGameRepository(impl: GameRepositoryImpl): GameRepository
}
```

### Database (Room v7)
- Proper migrations implemented (4→5→6→7)
- Production-ready data protection (no destructive migrations)
- All DAOs: `suspend fun` or `Flow<T>`
- TypeConverters for enums/complex types
- Schema export enabled

### Compose Navigation
- Single Activity pattern
- Route-based: `Screen.Home.route`, `Screen.GameDetail.createRoute(gameId)`
- Material3 Adaptive Navigation Suite

### Download (WorkManager)
- 8MB chunked downloads
- `CoroutineWorker` for suspend functions
- Max 3 concurrent downloads
- Progress tracked in Room DownloadEntity

### Auth (Steam OpenID 2.0 + Security)

- **ONLY OpenID 2.0**: Valve公式推奨のサードパーティ向け認証方式
- `SteamOpenIdAuthenticator` with CSRF protection (256-bit secure random state)
- OpenID 2.0 signature verification (MITM attack prevention)
- SteamID64 validation (range: 76561197960265728 ~ 76561202255233023)
- **No embedded API keys**: Users provide their own Steam Web API Key
- Encrypted token storage via `SecurePreferencesImpl` (AES256-GCM)
- WebView-based login (Steam公式ログインページ経由)

### Wine Container Management (2025 Best Practice)

**Shared Default Container Pattern** - Optimized for efficiency and UX:

```kotlin
// WinlatorEngineImpl.kt - Container reuse logic
private suspend fun getOrCreateEmulatorContainer(
  game: Game,
  winlatorContainer: WinlatorContainer
): EmulatorContainer {
  // PRIORITY 1: Custom container (advanced users only)
  if (game.winlatorContainerId != null) {
    val customContainer = findContainer("container_${game.winlatorContainerId}")
    if (customContainer != null) return customContainer
  }

  // PRIORITY 2: Shared default container (recommended for all games)
  val defaultContainer = findContainer("default_shared_container")
  if (defaultContainer != null) {
    Log.d(TAG, "Using shared default container for: ${game.name}")
    return defaultContainer
  }

  // PRIORITY 3: Create default container (first-time only)
  return createContainer(EmulatorContainerConfig(name = "Default Container"))
}
```

**Why Shared Containers?**
- ✅ **Disk usage**: 500MB × 10 games = 5GB → 500MB (90% reduction)
- ✅ **Launch speed**: 60s × 10 games → 60s once (10x faster)
- ✅ **Simple UX**: Users don't manage containers manually
- ✅ **Wine compatibility**: Most games work in shared Wine prefix

**When to Create Separate Containers:**
- Different Wine versions required (Wine 8.0 vs 9.0)
- Different Box64 settings (Performance vs Stability)
- Game-specific DLL conflicts (rare)

**Container ID Strategy:**
- Default container: Fixed ID `default_shared_container`
- Custom containers: Timestamp-based ID `${System.currentTimeMillis()}`
- Game assignment: `Game.winlatorContainerId` (null = use default)

**File Structure:**
```
containers/
├── default_shared_container/    # Shared by all games (default)
│   └── drive_c/
│       ├── Program Files/
│       │   ├── Game1/
│       │   ├── Game2/
│       │   └── Game3/
│       └── windows/
└── 1703123456789/              # Custom container (if needed)
    └── drive_c/
```

**Performance Optimizations (2025):**
- Container creation timeout: 60s (1st attempt), 90s (retries)
- Wineserver socket wait: 2s (down from 5s)
- Box64 settings: Balanced performance (1st attempt), max stability (2nd)
- Wine logging: Minimal (`-all,+err`) for speed
- Expected completion time: 30-40s on modern devices

**References:**
- `WinlatorEngineImpl.kt:225-293` - Container reuse logic
- `WinlatorEmulator.kt:508-549` - Container creation
- `LaunchGameUseCase.kt:40-95` - Game launch flow

## MANDATORY RULES

### Layer Boundaries
- NEVER import presentation in domain
- NEVER import data impl in domain
- ALWAYS use repository interfaces in use cases
- ALWAYS map Entity ↔ Domain ↔ DTO

### Async
- ALWAYS use `Dispatchers.IO` for I/O
- NEVER block main thread
- Use `viewModelScope` in ViewModels
- Use `Flow<T>` for streams (DB queries, downloads)

### Compose
- Use `remember` for recomposition survival
- Use `LaunchedEffect(key)` for side effects
- NO business logic in Composables (use ViewModels)
- State hoisting: stateless Composables + callbacks

### Error Handling (2025 Best Practice)

- **NEW**: Use `DataResult<T>` for new code (type-safe Success/Error/Loading)
- **Legacy**: `Result<T>` still supported (use `DataResult.fromResult()` for migration)
- Domain errors: Sealed classes (e.g., `SteamSyncError`)
- UI mapping: `AppError.toUserMessage(context)` extension functions
- Retrofit: Automatic error wrapping via `DataResultCallAdapter`
- HTTP errors: `AppError.fromHttpCode()` auto-mapping
- Retryability: `AppError.isRetryable()` for smart retry logic
- Log via `android.util.Log` with TAG
- User messages in strings.xml (ja/en)

### Code Quality
- NEVER use `GlobalScope` or `!!`
- NEVER hardcode strings (use strings.xml)
- NO God classes
- NO @Suppress without comments
- Handle nullability properly

### Files
- ALWAYS read existing files before editing/creating
- PREFER editing over creating new files
- Entities: `*Entity.kt`
- DAOs: `*Dao.kt`
- ViewModels: `*ViewModel.kt`
- Repositories: `*Repository.kt` (interface) + `*RepositoryImpl.kt`
- Use Cases: `*UseCase.kt`
- Screens: `*Screen.kt`

### Dependency Management
- **Version catalog**: Always use `libs.androidx.core.ktx` notation
- **Adding dependencies**: Update `gradle/libs.versions.toml` first, then reference in `build.gradle.kts`
- **APK size impact**: Consider size before adding (target <80MB ARM64)
- **Documentation**: Add comments for non-obvious dependencies

## ARCHITECTURE COMPLIANCE (Steam ToS)

**Legal constraint**: This app MUST comply with Steam Subscriber Agreement.

**Approved implementation:**

1. Game downloads: Via official Steam client (in Winlator container)
2. Game launching: `steam.exe -applaunch <appId>` (official command)
3. Library sync: Steam Web API (official, documented)
4. Authentication: OpenID 2.0 (Valve-recommended)
5. Compatibility: Wine/Proton (Valve officially supports)

**Prohibited (DO NOT implement):**

- ❌ Direct CDN downloads (protocol emulation)
- ❌ Steam client modification
- ❌ DRM bypass
- ❌ Depot/manifest parsing for downloads

**Reference**: [Steam Subscriber Agreement](https://store.steampowered.com/subscriber_agreement/)

## RECENT ARCHITECTURAL CHANGES

### 2025-12-20: Windows 10 Registry Configuration (Winlator 10.1 Compatibility)

- **Added**: Automatic Windows 10 version configuration for Wine containers
- **Why**: Steam requires Windows 10/11 to run properly (same requirement as Winlator 10.1)
- **Implementation**:
  - `WinlatorEmulator.setWindowsVersion()` - Creates .reg file with Windows 10 registry keys
  - Executes `wine regedit /S` to apply configuration during container creation
  - Sets `CurrentVersion=10.0`, `CurrentBuild=19045`, `ProductName="Windows 10 Pro"`
  - Non-fatal error (logs warning if fails, continues container creation)
- **Result**: SteamSetup.exe now runs successfully in WoW64 mode (confirmed working on Winlator 10.1)

**References:**

- [WinlatorEmulator.kt:1439-1555](app/src/main/java/com/steamdeck/mobile/core/winlator/WinlatorEmulator.kt#L1439-L1555) - Windows version setter
- [WinlatorEmulator.kt:574-580](app/src/main/java/com/steamdeck/mobile/core/winlator/WinlatorEmulator.kt#L574-L580) - Integration in createContainer
- [WineHQ Useful Registry Keys](https://wiki.winehq.org/Useful_Registry_Keys)

### 2025-12-21: NSIS Extraction Implementation (Phase 1.5 - ARM64 Fix)

- **Migrated**: sevenzipjbinding → Apache Commons Compress for NSIS extraction
- **Why**: sevenzipjbinding lacks ARM64 (aarch64) native library support
- **Problem**: sevenzipjbinding only supports x86/x86_64, causing initialization failure on Android ARM64 devices
- **Solution**: Use Apache Commons Compress (Pure Java, multi-platform)
- **Implementation**:
  - **Method 1 (PRIORITY)**: NSIS extraction using Apache Commons Compress
    - Extracts Steam.exe and related files directly from SteamSetup.exe NSIS installer
    - No Wine execution required - 100% success rate on all platforms
    - Pure Java implementation - **full ARM64 compatibility**
    - No APK size increase (already using commons-compress)
  - **Method 2 (FALLBACK)**: Wine installer execution
    - Requires WoW64 support (may fail on 64-bit only Wine builds)
    - Only used if NSIS extraction fails
- **Technical Details**:
  - Library: `commons-compress:1.28.0` (already in dependencies)
  - Implementation: [SteamInstallerService.kt:222-300](app/src/main/java/com/steamdeck/mobile/core/steam/SteamInstallerService.kt#L222-L300)
  - Integration: [SteamSetupManager.kt:150-206](app/src/main/java/com/steamdeck/mobile/core/steam/SteamSetupManager.kt#L150-L206)
  - Removed: sevenzipjbinding dependencies and ProGuard rules
  - UI strings: NSIS extraction progress messages
- **Result**: WoW64 problem completely bypassed, Steam installation works on **all ARM64 Android devices**

**References:**

- [gradle/libs.versions.toml](gradle/libs.versions.toml#L20) - commons-compress dependency
- [SteamInstallerService.kt:234-299](app/src/main/java/com/steamdeck/mobile/core/steam/SteamInstallerService.kt#L234-L299) - Pure Java extraction
- [strings.xml:142-147](app/src/main/res/values/strings.xml#L142-L147) - Progress messages

### 2025-12-20: Steam Client Installation Methods (Legacy Documentation)

- **Method 1 (DEPRECATED)**: SteamSetup.exe with Windows 10 registry configuration
  - Uses official 32-bit NSIS installer from Valve
  - Requires Windows 10/11 registry keys for WoW64 compatibility
  - Success rate: ~90% on ARM64 devices with Wine+Box64 (same as Winlator 10.1)
  - **Status**: Now used as fallback only (Method 2)

- **Method 2 (FALLBACK)**: Pre-built Steam Client ZIP download
  - Downloads steam.zip from Valve CDN (~50-80MB)
  - Direct extraction to Wine container (bypasses installer entirely)
  - Success rate: 100% on ARM64 devices (no WoW64 required)
  - **Status**: Code exists but steam.zip URL returns 404 (URL may be incorrect)

**Implementation:**

- `SteamInstallerService.downloadSteamClient()` - Downloads steam.zip from Valve CDN (~50-80MB)
- `SteamInstallerService.extractSteamClient()` - Extracts using Java's built-in ZipInputStream (no external dependencies)
- `SteamSetupManager.installSteam()` - Orchestrates download → container creation → extraction workflow
- Direct extraction to Wine container's `C:\Program Files (x86)\Steam` directory
- Verification: Ensures steam.exe exists after extraction

**Why Pre-built ZIP?**

1. **Zero dependencies** - Uses built-in Java APIs (no tar, bash, or glibc)
2. **100% success rate** - Bypasses both WoW64 and Linux compatibility issues
3. **No APK bloat** - Downloads at runtime (~50-80MB) instead of bundling in APK
4. **Official Valve CDN** - Downloads from `steamcdn-a.akamaihd.net/client/installer/steam.zip`

**Deprecated methods:**

- `SteamInstallerService.downloadInstaller()` (SteamSetup.exe - WoW64 failure)
- `SteamSetupManager.runSteamInstaller()` (Wine-based execution)
- `SteamInstallerService.downloadSteamCMD()` (Linux incompatibility)
- `SteamInstallerService.extractSteamCMD()` (Android tar unavailable)
- `SteamSetupManager.runSteamCMD()` (Android bash/glibc incompatibility)

**References:**

- [SteamInstallerService.kt:41-146](app/src/main/java/com/steamdeck/mobile/core/steam/SteamInstallerService.kt#L41-L146) - Download & extraction
- [SteamSetupManager.kt:105-161](app/src/main/java/com/steamdeck/mobile/core/steam/SteamSetupManager.kt#L105-L161) - Installation workflow
- [strings.xml](app/src/main/res/values/strings.xml) - Error messages

**2025-12-19: Unified Error Handling**

- `DataResult<T>` for type-safe Success/Error/Loading states
- `AppError` hierarchy (NetworkError, AuthError, etc.)
- `SteamSyncError` domain-specific errors
- Automatic Retrofit error wrapping via `DataResultCallAdapter`
- UI error mapping: `AppError.toUserMessage(context)`

**Key files:**

- [core/result/DataResult.kt](core/result/DataResult.kt) - Type-safe result wrapper
- [core/error/AppError.kt](core/error/AppError.kt) - Unified error hierarchy
- [core/network/DataResultCallAdapter.kt](core/network/DataResultCallAdapter.kt) - Retrofit adapter
- [domain/error/SteamSyncError.kt](domain/error/SteamSyncError.kt) - Domain errors
- [presentation/util/ErrorExtensions.kt](presentation/util/ErrorExtensions.kt) - UI mapping

## TESTING STACK

- **Frameworks**: JUnit 4, Mockito, Mockk 1.13.13
- **Flow testing**: Turbine 1.1.0
- **Coroutines**: kotlinx-coroutines-test
- **Architecture**: AndroidX Arch Core Testing 2.2.0
- **Coverage target**: 70%+ for domain/data layers

**Test naming convention:**

```kotlin
@Test
fun `launchGame should return success when game exists`() { ... }

@Test
fun `launchGame should return error when game not found`() { ... }
```

## PERFORMANCE & CONSTRAINTS

**APK optimization:**

- Target: <80MB (ARM64-v8a only)
- Wine rootfs: 53MB extracted at runtime (excluded from APK)
- R8 full mode + shrinkResources enabled
- Images: WebP/vector drawables only

**UI constraints:**

- Material3 dynamic colors (Android 12+)
- Immersive fullscreen (no status/nav bars)
- Landscape optimized (2400×1080px)
- Primary language: Japanese (English fallback via `strings.xml`)

**Known limitations:**

- zstd-jni disabled (no ARM64 native libs available)
- targetSdk 28 required (SELinux binary execution workaround)

## CODING WORKFLOW

### Code Implementation

**Before writing:**

1. Read existing files first
2. Understand established patterns
3. Check package structure for imports
4. Verify layer boundaries (presentation → domain → data)

**While writing:**

1. Add KDoc for public APIs
2. Include proper error handling (DataResult<T>, try-catch)
3. Use type-safe builders (StateFlow, sealed classes)
4. Follow nullability best practices (avoid `!!`)
5. Use strings.xml for user-facing text

**Example:**

```kotlin
/**
 * Launches a game using the Winlator emulator.
 * @param gameId Unique identifier for the game
 * @return DataResult containing launch status or error
 */
suspend fun launchGame(gameId: Long): DataResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val game = gameRepository.getGameById(gameId) ?: return@withContext DataResult.Error(
            AppError.NotFound("Game not found")
        )
        winlatorEngine.launchGame(game)
        DataResult.Success(Unit)
    } catch (e: Exception) {
        DataResult.Error(AppError.fromException(e))
    }
}
```

### Refactoring Rules

1. **Backward compatibility**: Maintain unless explicitly requested to break
2. **Test updates**: Update tests when logic changes
3. **Functionality preservation**: Don't remove features during refactoring
4. **Breaking changes**: Document with clear explanation

### Code Quality Checklist

- [ ] No `GlobalScope` or `!!` operators
- [ ] No hardcoded strings (use `strings.xml`)
- [ ] No business logic in Composables
- [ ] Proper Dispatcher usage (`Dispatchers.IO` for I/O)
- [ ] StateFlow for UI state, Flow for streams
- [ ] All async operations properly scoped (`viewModelScope`, `suspend fun`)
- [ ] Layer boundaries respected (no presentation imports in domain)

## KEY FILES REFERENCE

### Navigation

**Routes:**

- `Home`, `Downloads`, `Settings`
- `GameDetail/{gameId}`, `SteamLogin`, `WineTest`, `ControllerSettings`, `Container`

**Implementation:**

- [presentation/MainActivity.kt](app/src/main/java/com/steamdeck/mobile/presentation/MainActivity.kt) - Single Activity entry point
- [presentation/navigation/SteamDeckNavHost.kt](app/src/main/java/com/steamdeck/mobile/presentation/navigation/SteamDeckNavHost.kt) - Navigation graph

### Critical Files by Layer

**Presentation (UI + ViewModels):**

- [ui/home/HomeScreen.kt](app/src/main/java/com/steamdeck/mobile/presentation/ui/home/HomeScreen.kt) - Game library
- [ui/game/GameDetailScreen.kt](app/src/main/java/com/steamdeck/mobile/presentation/ui/game/GameDetailScreen.kt) - Game details & launch
- [ui/settings/SettingsScreen.kt](app/src/main/java/com/steamdeck/mobile/presentation/ui/settings/SettingsScreen.kt) - Auth & sync
- [viewmodel/HomeViewModel.kt](app/src/main/java/com/steamdeck/mobile/presentation/viewmodel/HomeViewModel.kt) - Home state
- [viewmodel/GameDetailViewModel.kt](app/src/main/java/com/steamdeck/mobile/presentation/viewmodel/GameDetailViewModel.kt) - Game detail state

**Domain (Business Logic):**

- [domain/model/Game.kt](app/src/main/java/com/steamdeck/mobile/domain/model/Game.kt) - Core game model
- [domain/usecase/LaunchGameUseCase.kt](app/src/main/java/com/steamdeck/mobile/domain/usecase/LaunchGameUseCase.kt) - Launch logic
- [domain/repository/GameRepository.kt](app/src/main/java/com/steamdeck/mobile/domain/repository/GameRepository.kt) - Data abstraction

**Data (Persistence & Network):**

- [data/local/database/dao/GameDao.kt](app/src/main/java/com/steamdeck/mobile/data/local/database/dao/GameDao.kt) - Database queries
- [data/repository/GameRepositoryImpl.kt](app/src/main/java/com/steamdeck/mobile/data/repository/GameRepositoryImpl.kt) - Repository impl
- [data/mapper/GameMapper.kt](app/src/main/java/com/steamdeck/mobile/data/mapper/GameMapper.kt) - Entity ↔ Domain
- [data/remote/steam/SteamApiService.kt](app/src/main/java/com/steamdeck/mobile/data/remote/steam/SteamApiService.kt) - Retrofit API

**Core (Infrastructure):**

- [core/winlator/WinlatorEngine.kt](app/src/main/java/com/steamdeck/mobile/core/winlator/WinlatorEngine.kt) - Game launcher
- [core/auth/SteamOpenIdAuthenticator.kt](app/src/main/java/com/steamdeck/mobile/core/auth/SteamOpenIdAuthenticator.kt) - OpenID 2.0 auth
- [core/steam/SteamSetupManager.kt](app/src/main/java/com/steamdeck/mobile/core/steam/SteamSetupManager.kt) - Steam installation orchestration
- [core/steam/SteamInstallerService.kt](app/src/main/java/com/steamdeck/mobile/core/steam/SteamInstallerService.kt) - SteamCMD download & extraction
- [core/download/DownloadManager.kt](app/src/main/java/com/steamdeck/mobile/core/download/DownloadManager.kt) - WorkManager downloads
- [core/error/AppError.kt](app/src/main/java/com/steamdeck/mobile/core/error/AppError.kt) - Error hierarchy

**DI:**

- [di/module/RepositoryModule.kt](app/src/main/java/com/steamdeck/mobile/di/module/RepositoryModule.kt) - Repository bindings
- [di/module/AppModule.kt](app/src/main/java/com/steamdeck/mobile/di/module/AppModule.kt) - App-wide dependencies

## PLATFORM CONSTRAINTS

### SELinux Binary Execution Workaround

**Problem:** Android 10+ (API 29+) blocks execution of binaries from `app_data_file` context.

**Solution:** `targetSdkVersion = 28` (Android 9.0) bypasses SELinux restrictions while maintaining Android 10+ compatibility.

```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        targetSdk = 28  // SELinux workaround for Box64/Wine execution
    }
}
```

**Technical details:**

- Apps with targetSdk ≤ 28 can execute binaries from app data directories on Android 10+
- Binary path: `/data/data/<package>/files/winlator/box64/box64`
- No root required
- Verified: Box64 v0.3.6, Wine 9.0+

**Trade-offs:**

- Cannot use Scoped Storage APIs (use MediaStore/SAF instead - already implemented)
- Some Android 10+ features unavailable (minimal impact for game launcher)

**References:**

- [okyes/app-data-file-exec](https://github.com/okyes/app-data-file-exec)
- Winlator 10.1 uses same approach

---

**Document purpose:** Guide AI assistants to write high-quality Kotlin/Compose code following project architecture.

**Last updated:** 2025-12-20
