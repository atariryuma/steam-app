# AI CONTEXT: SteamDeck Mobile Android

## PROJECT META
- **Type**: Android Steam game launcher via Winlator integration
- **Platform**: Android 8.0+ (API 26-28 targetSdk), ARM64-v8a only
- **Architecture**: Clean Architecture + MVVM + Repository Pattern
- **Language**: Kotlin 2.1.0, Jetpack Compose
- **Target**: <80MB APK (Wine rootfs 53MB extracted at runtime)
- **Build**: Gradle 8.7.3 Kotlin DSL, version catalog at `gradle/libs.versions.toml`

## TECH STACK (libs.versions.toml)
```
Compose BOM 2024.12.01 | Material3 Adaptive 1.0.0
Hilt 2.54 | Room 2.6.1 (v4 schema, destructive migrations)
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
│   ├── model/ (Game, GameSource, Download, WinlatorContainer, Controller, ImportSource, SteamAuthResult)
│   ├── repository/ (interfaces: GameRepository, WinlatorContainerRepository, DownloadRepository, FileImportRepository, SteamAuthRepository, ControllerRepository, ISteamRepository, ISecurePreferences)
│   ├── usecase/ (GetAllGamesUseCase, GetGameByIdUseCase, SearchGamesUseCase, AddGameUseCase, DeleteGameUseCase, ToggleFavoriteUseCase, UpdatePlayTimeUseCase, SyncSteamLibraryUseCase, LaunchGameUseCase)
│   ├── error/ (SteamSyncError - domain-specific errors)
│   └── emulator/WindowsEmulator (interface)
├── data/
│   ├── local/
│   │   ├── database/ (SteamDeckDatabase v4, Converters)
│   │   │   ├── dao/ (GameDao, WinlatorContainerDao, DownloadDao, ControllerProfileDao, SteamInstallDao)
│   │   │   └── entity/ (GameEntity, WinlatorContainerEntity, DownloadEntity, ControllerProfileEntity, SteamInstallEntity, SteamInstallStatus)
│   │   └── preferences/
│   │       ├── SecureSteamPreferences (EncryptedSharedPreferences AES256-GCM)
│   │       └── SecurePreferencesImpl (ISecurePreferences implementation)
│   ├── remote/steam/ (SteamApiService Retrofit, SteamAuthenticationService, SteamRepositoryImpl, SteamCdnService, SteamCmdApiService)
│   ├── repository/ (impls: GameRepositoryImpl, WinlatorContainerRepositoryImpl, DownloadRepositoryImpl, FileImportRepositoryImpl, SteamAuthRepositoryImpl, ControllerRepositoryImpl, SteamRepositoryAdapter)
│   └── mapper/ (GameMapper, WinlatorContainerMapper, DownloadMapper, ControllerMapper, SteamGameMapper)
├── core/
│   ├── auth/ (SteamOpenIdAuthenticator OpenID 2.0 + signature verification, JwtDecoder)
│   ├── download/ (DownloadManager WorkManager 8MB chunks, SteamDownloadManager, ApiError)
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

### Database (Room v4)
- Destructive migrations enabled (MVP stage)
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

- `SteamOpenIdAuthenticator` with CSRF protection (256-bit secure random state)
- **NEW (2025)**: OpenID 2.0 signature verification (MITM attack prevention)
- SteamID64 validation (range: 76561197960265728 ~ 76561202255233023)
- **No embedded API keys**: Users provide their own Steam Web API Key
- Encrypted token storage via `SecurePreferencesImpl` (AES256-GCM)
- QR-based login (password login deprecated in UI)

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

### Build
- Use version catalog: `libs.androidx.core.ktx`
- Add deps to `gradle/libs.versions.toml` first
- Release build: R8, shrinkResources, ProGuard
- Target: <80MB APK (ARM64 only)

## CURRENT STATE (git status)
```
Clean working directory (last commit: 5149617)
```

## RECENT CHANGES (last commit 5149617 - 2025-12-19)

### Security & Architecture Improvements

- **Removed embedded Steam API key** (security & compliance)
  - Users now provide their own API keys
  - Updated `SyncSteamLibraryUseCase`, `AppModule`, `SteamAuthModule`
- **Added OpenID 2.0 signature verification** (MITM attack prevention)
  - `SteamOpenIdAuthenticator.kt` now verifies signatures via Steam provider
  - CSRF protection with 256-bit secure random state
- **Implemented unified error handling system**
  - `DataResult<T>` sealed interface (Success/Error/Loading)
  - `AppError` unified hierarchy (NetworkError, AuthError, etc.)
  - `SteamSyncError` domain-specific errors
  - Automatic Retrofit error wrapping via `DataResultCallAdapter`
- **Added encrypted preferences**
  - `SecurePreferencesImpl` with AES256-GCM
  - `ISecurePreferences` domain interface

### New Files (17 files, +1,209 lines)

- `core/result/DataResult.kt` - Type-safe result wrapper
- `core/error/AppError.kt` - Unified error hierarchy
- `core/network/DataResultCallAdapter.kt` - Retrofit error adapter
- `domain/error/SteamSyncError.kt` - Steam sync errors
- `domain/repository/ISteamRepository.kt` - Steam API abstraction
- `domain/repository/ISecurePreferences.kt` - Secure storage interface
- `data/repository/SteamRepositoryAdapter.kt` - Domain/data bridge
- `data/mapper/SteamGameMapper.kt` - Steam game mapping
- `data/local/preferences/SecurePreferencesImpl.kt` - Encrypted prefs impl
- `presentation/util/ErrorExtensions.kt` - UI error mapping
- `test/**/DataResultTest.kt` - Unit tests (3 test files)

### Previous Commit (93e7218)

- Removed password login UI (QR only)
- Added `SteamOpenIdAuthenticator.kt` (OpenID 2.0 + CSRF)
- Enhanced GameDetailScreen, SettingsScreen
- Added Winlator assets & documentation

## TESTING
- JUnit 4, Mockito, Mockk 1.13.13
- Turbine 1.1.0 (Flow testing)
- kotlinx-coroutines-test
- AndroidX Arch Core Testing 2.2.0
- Target: 70%+ coverage for domain/data layers
- Some test files skipped (.skip suffix)

## KNOWN ISSUES
- zstd-jni disabled (no ARM64 native libs)
- libaums USB migration to v0.10.0 pending
- QrCodeGenerator.kt pending deletion

## UI SPECS
- Material3 dynamic colors (Android 12+)
- Immersive fullscreen (no status/nav bars)
- Landscape optimized (2400×1080px)
- Primary language: Japanese (English fallback)

## PERFORMANCE
- Aggressive APK optimization (<80MB target)
- Wine rootfs 53MB extracted at runtime (NOT counted in APK size)
- ARM64-v8a single ABI
- R8/ProGuard enabled in release
- Image: WebP/vector drawables

## AI CODING INSTRUCTIONS

### When Writing Code
1. Read existing files first
2. Follow established patterns
3. Add KDoc for public APIs
4. Include error handling
5. Use proper imports (check package structure)

### When Refactoring
1. Maintain backward compatibility unless requested
2. Update tests if logic changes
3. Preserve functionality unless removing features
4. Explain breaking changes

### When Adding Dependencies
1. Add to `libs.versions.toml` first
2. Use version catalog refs in build.gradle.kts
3. Document if major dependency
4. Consider APK size impact

### File Operations
- ALWAYS use Read before Edit/Write
- ALWAYS prefer Edit over Write for existing files
- NEVER create files unnecessarily
- NO markdown files unless requested

### Tool Usage
- Use specialized tools (Read/Edit/Write) over bash (cat/sed/awk)
- Use Glob for file patterns
- Use Grep for code search
- Use Task tool for multi-step exploration

### Communication
- Be concise (CLI output)
- No emojis unless requested
- No colons before tool calls
- Output text directly (NO echo/printf)

## NAVIGATION ROUTES
```kotlin
Home, Downloads, Settings
GameDetail/{gameId}, SteamLogin, WineTest, ControllerSettings, Container
```

## KEY FILES MAP
| File | Purpose |
|------|---------|
| presentation/MainActivity.kt | Single Activity, immersive mode |
| presentation/navigation/SteamDeckNavHost.kt | Navigation graph |
| presentation/ui/home/HomeScreen.kt | Game library |
| presentation/ui/game/GameDetailScreen.kt | Game info, launch |
| presentation/ui/settings/SettingsScreen.kt | Auth, sync, settings |
| presentation/ui/auth/SteamStyleLoginScreen.kt | QR login |
| presentation/ui/download/DownloadScreen.kt | Download queue |
| domain/model/Game.kt | Game domain model |
| domain/model/Download.kt | Download state |
| domain/repository/GameRepository.kt | Data abstraction |
| data/local/database/dao/GameDao.kt | Database queries |
| data/mapper/GameMapper.kt | Entity↔Domain |
| core/download/DownloadManager.kt | WorkManager orchestration |
| core/winlator/WinlatorEngine.kt | Game launcher |
| data/remote/steam/SteamApiService.kt | Retrofit API |
| core/auth/SteamOpenIdAuthenticator.kt | OpenID 2.0 |
| data/local/preferences/SecureSteamPreferences.kt | Encrypted tokens |
| presentation/theme/Theme.kt | Material3 theme |
| di/module/RepositoryModule.kt | Hilt DI |

## SELinux WORKAROUND FOR BINARY EXECUTION

### Problem
Android 10+ (API 29+) enforces SELinux policies that block execution of binaries from `app_data_file` context, preventing Box64/Wine from running.

### Solution
Set `targetSdkVersion = 28` (Android 9.0) to bypass SELinux restrictions while maintaining compatibility with Android 10+ devices.

```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        targetSdk = 28  // Android 9.0 (SELinux workaround)
    }
}
```

### Technical Details
- Apps with targetSdk ≤ 28 can execute binaries from app data directories on Android 10+
- Binary extraction path: `/data/data/<package>/files/winlator/box64/box64`
- No root required, works on standard Android devices
- Verified working: Box64 v0.3.6, Wine 9.0+
- Box64 extracted from `.txz` archive (usr/local/bin/box64) and moved to expected location

### References
- [GitHub - okyes/app-data-file-exec](https://github.com/okyes/app-data-file-exec)
- Winlator 10.1 uses same approach
- Android SELinux validation: [source.android.com](https://source.android.com/docs/security/features/selinux/validate)

### Trade-offs
- ⚠️ Cannot use Android 10+ storage APIs (Scoped Storage)
  - Workaround: Use MediaStore API / Storage Access Framework (already implemented)
- ⚠️ Some Android 10+ features unavailable
  - Impact: Minimal for game launcher functionality

---

## CONTEXT EFFICIENCY NOTES
- This file optimized for AI token efficiency (no human readability focus)
- Focus on facts, patterns, constraints
- Minimal prose, maximum density
- Reference external docs where possible
- Update as codebase evolves

**Last updated**: 2025-12-19 (Added SELinux workaround documentation)
