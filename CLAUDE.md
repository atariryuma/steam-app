# SteamDeck Mobile - AI Coding Guidelines

> **Note**: This file provides persistent context for Claude Code and other AI assistants.
> Last updated: 2025-01-16

## 📋 Project Overview

**Name**: SteamDeck Mobile
**Type**: Android Steam Game Launcher & Emulator
**Target**: Steam-specialized app (<80MB APK, 60% smaller than Winlator's 141MB)
**Platform**: Android 8.0+ (API 26+), ARM64-v8a only
**Primary Goal**: Run Steam Windows games on Android via Winlator integration

## 🏗️ Architecture & Tech Stack

### Architecture Pattern
- **Clean Architecture** with clear layer separation
- **MVVM** for presentation layer
- **Repository Pattern** for data abstraction
- **Single Activity** with Jetpack Compose Navigation

### Technology Stack
```
Language:        Kotlin 2.1.0
UI Framework:    Jetpack Compose (Material3)
DI:              Hilt 2.52
Database:        Room 2.6.1
Async:           Coroutines + Flow
Network:         Retrofit 2.11.0 + OkHttp 4.12.0
Image Loading:   Coil 2.7.0
Background Work: WorkManager 2.9.1
Build Tool:      Gradle 8.7.3 (Kotlin DSL)
Min SDK:         26 (Android 8.0)
Target SDK:      35 (Android 15)
```

## 📁 Project Structure

```
app/src/main/java/com/steamdeck/mobile/
├── presentation/           # UI Layer (Jetpack Compose)
│   ├── ui/
│   │   ├── home/          # Game library screen
│   │   ├── game/          # Game detail screen
│   │   ├── settings/      # Settings screen
│   │   ├── download/      # Download management screen
│   │   └── import/        # File import screen
│   ├── viewmodel/         # ViewModels (MVVM)
│   └── theme/             # Material3 theme
├── domain/                # Domain Layer (Business Logic)
│   ├── model/             # Domain models (immutable data classes)
│   ├── usecase/           # Use cases (single responsibility)
│   └── repository/        # Repository interfaces
├── data/                  # Data Layer
│   ├── local/
│   │   ├── database/      # Room database, DAOs, entities
│   │   └── preferences/   # DataStore preferences
│   ├── remote/            # API services (Steam Web API)
│   └── repository/        # Repository implementations
├── core/                  # Core Functionality
│   ├── winlator/          # Winlator integration
│   ├── fileimport/        # USB/SMB/FTP file import
│   ├── download/          # Download manager
│   └── controller/        # Game controller support
└── di/                    # Dependency Injection (Hilt modules)
    └── module/
```

## 🎯 Development Principles

### 1. **Clean Architecture Boundaries**
- **Never** import presentation classes in domain layer
- **Never** import data implementation in domain layer
- **Always** use repository interfaces in use cases
- **Always** map between entity/model/DTO layers

### 2. **Jetpack Compose Best Practices**
- **Use** `remember` for state that survives recomposition
- **Use** `LaunchedEffect` for side effects with keys
- **Avoid** business logic in Composables (use ViewModels)
- **Follow** Material3 design system guidelines
- **Prefer** stateless Composables with state hoisting

### 3. **Coroutines & Flow**
- **Use** `viewModelScope` for ViewModel coroutines
- **Use** `StateFlow` for UI state management
- **Use** `Flow` for streams (e.g., database queries, download progress)
- **Always** specify `Dispatchers.IO` for I/O operations
- **Use** `withContext` instead of `launch` for suspend functions

### 4. **Database (Room)**
- **Current version**: 2 (destructive migration enabled for MVP)
- **Entity naming**: `*Entity` suffix (e.g., `GameEntity`)
- **DAO naming**: `*Dao` suffix (e.g., `GameDao`)
- **Always** use suspend functions or Flow for async operations
- **Avoid** blocking database calls on main thread

### 5. **Error Handling**
- **Use** `Result<T>` for operations that can fail
- **Use** sealed classes for complex state (Loading/Success/Error)
- **Log** errors with proper tags via `android.util.Log`
- **Show** user-friendly error messages in Japanese/English

### 6. **Testing Strategy**
- **Unit tests**: Use JUnit 4, Mockito, kotlinx-coroutines-test
- **Coverage target**: 70%+ for domain/data layers
- **Mock** external dependencies (network, database)
- **Test** ViewModels with fake repositories

## 🚫 What NOT to Do

### DON'T Include in CLAUDE.md
- ❌ **Code style rules** (use ktlint/detekt instead)
- ❌ **Formatting preferences** (use .editorconfig)
- ❌ **Complete code examples** (keep concise)
- ❌ **Changelog or git history** (use git log)

### DON'T in Code
- ❌ **Don't** use `GlobalScope` (use structured concurrency)
- ❌ **Don't** block main thread with I/O operations
- ❌ **Don't** hardcode strings (use strings.xml)
- ❌ **Don't** suppress warnings without comments
- ❌ **Don't** use `!!` operator (handle nullability properly)
- ❌ **Don't** create God classes or ViewModels

## 🔧 Build Configuration

### Gradle Version Catalog
- **Location**: `gradle/libs.versions.toml`
- **Usage**: `libs.androidx.core.ktx` instead of direct versions
- **Update**: Bump versions in catalog, not build.gradle.kts

### Build Variants
```kotlin
debug {
    applicationIdSuffix = ".debug"
    versionNameSuffix = "-debug"
}

release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(...)
}
```

### APK Optimization (Target: <80MB)
- ✅ Enable R8/ProGuard in release builds
- ✅ Use Android App Bundle (AAB)
- ✅ Split by ABI (ARM64-v8a only)
- ✅ Compress images (WebP, vector drawables)
- ✅ Remove unused resources
- ℹ️ Wine rootfs (53MB) is extracted on first launch, not included in final APK size calculation

## 📦 Key Dependencies & Usage

### Hilt (Dependency Injection)
```kotlin
// Module example
@Module
@InstallIn(SingletonComponent::class)
object MyModule {
    @Provides
    @Singleton
    fun provideRepository(/* deps */): Repository = RepositoryImpl(/* deps */)
}
```

### Room (Database)
```kotlin
// Always use Flow or suspend
@Query("SELECT * FROM games")
fun getAllGames(): Flow<List<GameEntity>>

@Query("SELECT * FROM games WHERE id = :id")
suspend fun getGameById(id: Long): GameEntity?
```

### WorkManager (Background Downloads)
```kotlin
// Use CoroutineWorker for suspend functions
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: SteamDeckDatabase
) : CoroutineWorker(context, params)
```

## 🎨 UI Guidelines

### Material3 Components
- **Prefer**: `Card`, `Button`, `OutlinedButton`, `FilledTonalButton`
- **Progress**: Use `LinearProgressIndicator` for determinate, `CircularProgressIndicator` for indeterminate
- **Lists**: Use `LazyColumn` with `items()` for performance
- **Dialogs**: Use `AlertDialog` with Material3 styling

### State Management Pattern
```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repository: Repository
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadData()
    }
}

@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    // UI based on state
}
```

## 🔍 Common Patterns

### Repository Pattern
```kotlin
// Interface in domain layer
interface GameRepository {
    fun getGames(): Flow<List<Game>>
    suspend fun getGame(id: Long): Result<Game>
}

// Implementation in data layer
class GameRepositoryImpl @Inject constructor(
    private val gameDao: GameDao,
    private val steamApi: SteamApiService
) : GameRepository {
    override fun getGames(): Flow<List<Game>> =
        gameDao.getAllGames().map { entities -> entities.map { it.toDomain() } }
}
```

### Use Case Pattern
```kotlin
class GetGameDetailsUseCase @Inject constructor(
    private val repository: GameRepository
) {
    suspend operator fun invoke(gameId: Long): Result<GameDetails> {
        return repository.getGame(gameId)
    }
}
```

## 🐛 Debugging & Logging

### Log Tags
```kotlin
companion object {
    private const val TAG = "ClassName"
}

Log.d(TAG, "Debug message")
Log.e(TAG, "Error message", exception)
```

### Common Issues
1. **Database locked**: Ensure using `Dispatchers.IO`
2. **NetworkOnMainThreadException**: Use coroutines with `Dispatchers.IO`
3. **Context memory leak**: Use `ApplicationContext` in ViewModels
4. **Recomposition loops**: Check `remember` and state hoisting

## 🚀 Development Workflow

### Git Branching
- `main`: Production-ready code
- `develop`: Integration branch
- `feature/*`: New features
- `bugfix/*`: Bug fixes

### Commit Messages (Conventional Commits)
```
feat(steam): Add game library sync
fix(download): Handle network errors
docs: Update CLAUDE.md
refactor(ui): Extract common composables
test(viewmodel): Add GameViewModel tests
```

### Before Committing
1. ✅ Run `./gradlew ktlintCheck` (if configured)
2. ✅ Run `./gradlew test`
3. ✅ Ensure build succeeds (`./gradlew assembleDebug`)
4. ✅ No hardcoded TODOs without issue links

## 📚 External Resources

### Official Documentation
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material3 for Compose](https://m3.material.io/develop/android/jetpack-compose)
- [Room Persistence Library](https://developer.android.com/training/data-storage/room)
- [Hilt Dependency Injection](https://developer.android.com/training/dependency-injection/hilt-android)
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)

### Project-Specific APIs
- [Steam Web API](https://partner.steamgames.com/doc/webapi_overview)
- [Winlator GitHub](https://github.com/brunodev85/winlator)

## 🎯 Current Development Phase

**Status**: Phase 4 Complete (Download Management)
**Next**: Phase 5 - Controller Support

### Completed Phases
- ✅ Phase 1: MVP (Basic UI, Room DB, Winlator stub)
- ✅ Phase 2: Steam Integration (Web API, Auth, Library sync)
- ✅ Phase 3: File Import (USB OTG, SMB/CIFS, FTP, SAF)
- ✅ Phase 4: Download Management (WorkManager, Material3 UI)

### Active Phase 5 Focus
- [ ] InputDevice API integration
- [ ] Button mapping
- [ ] Controller profile management

## 🤖 AI Assistant Guidelines

### When Writing Code
1. **Read existing files** before creating new ones
2. **Follow established patterns** in the codebase
3. **Use proper imports** (check existing package structure)
4. **Add KDoc comments** for public APIs
5. **Include error handling** for operations that can fail

### When Refactoring
1. **Maintain backward compatibility** unless explicitly requested
2. **Update tests** if logic changes
3. **Preserve existing functionality** unless removing features
4. **Explain breaking changes** in commit messages

### When Adding Dependencies
1. **Add to `libs.versions.toml`** first
2. **Use version catalog references** in build.gradle.kts
3. **Document usage** in this CLAUDE.md if it's a major dependency
4. **Consider APK size impact** (target: <50MB)

### File Naming Conventions
- Entities: `*Entity.kt` (e.g., `GameEntity.kt`)
- DAOs: `*Dao.kt` (e.g., `GameDao.kt`)
- ViewModels: `*ViewModel.kt` (e.g., `GameViewModel.kt`)
- Repositories: `*Repository.kt` + `*RepositoryImpl.kt`
- Use Cases: `*UseCase.kt` (e.g., `GetGameDetailsUseCase.kt`)
- Screens: `*Screen.kt` (e.g., `HomeScreen.kt`)

## 📝 Notes

- **MVP Stage**: Destructive migrations are acceptable (`.fallbackToDestructiveMigration()`)
- **Language Support**: Japanese (primary) + English
- **Target Device**: Snapdragon 8 Gen 1+ recommended
- **APK Size Priority**: Aggressive optimization required (<50MB target)

---

**Best Practice Sources**:
- [Claude Code: Best practices for agentic coding](https://www.anthropic.com/engineering/claude-code-best-practices)
- [Using CLAUDE.MD files: Customizing Claude Code for your codebase](https://claude.com/blog/using-claude-md-files)
- [CLAUDE.md: Best Practices from Optimizing Claude Code](https://arize.com/blog/claude-md-best-practices-learned-from-optimizing-claude-code-with-prompt-learning/)
- [Writing a good CLAUDE.md](https://www.humanlayer.dev/blog/writing-a-good-claude-md)

**Last Review**: 2025-01-16
**Maintainer**: Update this file as project evolves
