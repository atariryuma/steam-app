# Contributing to SteamDeck Mobile

Thank you for your interest in contributing to SteamDeck Mobile! This document provides guidelines and instructions for contributing.

## 🎯 Project Goal

SteamDeck Mobile aims to be a lightweight, Steam-specialized Android game emulator using Winlator integration. Our target is a <50MB APK with excellent performance on Snapdragon devices.

## 🌟 How to Contribute

### Reporting Bugs

1. Check if the bug has already been reported in [Issues](https://github.com/atariryuma/steam-app/issues)
2. Use the **Bug Report** template
3. Provide detailed information:
   - Device specifications (model, Android version, SoC)
   - Steps to reproduce
   - Expected vs actual behavior
   - Logcat output
   - Screenshots if applicable

### Suggesting Features

1. Check [existing feature requests](https://github.com/atariryuma/steam-app/issues?q=is%3Aissue+is%3Aopen+label%3Aenhancement)
2. Use the **Feature Request** template
3. Explain the use case and benefits
4. Consider implementation complexity

### Code Contributions

#### Development Setup

1. **Prerequisites**:
   - Android Studio Ladybug 2024.2.1+
   - JDK 21+
   - Git

2. **Clone the repository**:
   ```bash
   git clone https://github.com/atariryuma/steam-app.git
   cd steam-app
   ```

3. **Open in Android Studio**:
   - File → Open → Select project directory
   - Wait for Gradle sync to complete

4. **Build the project**:
   ```bash
   ./gradlew assembleDebug
   ```

#### Branch Strategy

- `main` - Production-ready code (protected)
- `develop` - Integration branch for features
- `feature/*` - Feature branches
- `bugfix/*` - Bug fix branches

#### Workflow

1. **Create a feature branch**:
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**:
   - Follow coding standards (see below)
   - Write tests for new functionality
   - Update documentation

3. **Commit your changes**:
   ```bash
   git add .
   git commit -m "feat(scope): description"
   ```

4. **Push and create PR**:
   ```bash
   git push -u origin feature/your-feature-name
   ```
   - Create Pull Request on GitHub
   - Fill in the PR template
   - Link related issues

#### Commit Message Convention

We use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code formatting (no functional changes)
- `refactor`: Code refactoring
- `test`: Adding/updating tests
- `chore`: Build/config changes

**Scopes** (examples):
- `steam`: Steam integration
- `winlator`: Winlator/game execution
- `ui`: UI components
- `database`: Room database
- `import`: File import functionality

**Examples**:
```bash
feat(steam): Add Steam library sync with image caching
fix(winlator): Resolve Unity game detection bug
docs(readme): Update installation instructions
refactor(ui): Simplify HomeScreen state management
```

## 🏗️ Code Standards

### Kotlin Style Guide

Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html):

- **Naming**:
  - Classes: `PascalCase`
  - Functions/variables: `camelCase`
  - Constants: `UPPER_SNAKE_CASE`

- **Formatting**:
  - 4-space indentation
  - 120-character line limit
  - Use trailing commas

- **Documentation**:
  - Public APIs must have KDoc comments
  - Explain "why" not "what"

### Architecture Guidelines

We use **Clean Architecture** with **MVVM**:

```
presentation/     # UI layer (Compose, ViewModels)
  └── ui/
  └── viewmodel/
domain/           # Business logic (Use Cases, Models)
  └── model/
  └── usecase/
  └── repository/  (interfaces)
data/             # Data layer (Repositories, DAOs, API)
  └── local/
  └── remote/
  └── repository/  (implementations)
core/             # Core utilities (Winlator, file import)
di/               # Dependency Injection (Hilt modules)
```

**Best Practices**:
- ViewModels expose `StateFlow<UiState>`
- Use sealed classes for UI states
- Repository pattern for data abstraction
- Use Cases for business logic
- Hilt for dependency injection

### Jetpack Compose Guidelines

- **State Management**:
  ```kotlin
  @Composable
  fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
      val uiState by viewModel.uiState.collectAsState()
      // ...
  }
  ```

- **Composable Naming**: Functions in `PascalCase`
- **Preview**: Add `@Preview` for all screens
- **Reusability**: Extract reusable components to `common/`

### Testing

- Write unit tests for:
  - ViewModels
  - Use Cases
  - Repositories
  - Data mappers

- Run tests before committing:
  ```bash
  ./gradlew test
  ```

- Aim for 70%+ code coverage

## 🔍 Code Review Process

All submissions require review:

1. **Self-review** your code first
2. **CI checks** must pass (build + tests)
3. **Reviewer** will provide feedback
4. **Address comments** and push updates
5. **Merge** once approved

## 📝 Documentation

- Update README.md for user-facing changes
- Add KDoc comments for public APIs
- Update CHANGELOG.md (if exists)
- Include inline comments for complex logic

## 🎮 Testing Guidelines

### Real Device Testing

Test on Snapdragon devices when possible:
- Install debug APK: `./gradlew installDebug`
- Check Logcat: `adb logcat -s SteamDeckMobile`
- Test on different Android versions (8.0+)

### Game Compatibility Testing

When testing game execution:
- Test different engines (Unity, Unreal, .NET)
- Verify Winlator container creation
- Check performance on target devices
- Document compatibility in issues

## 🚀 Release Process

(For maintainers)

1. Update `versionName` and `versionCode` in `app/build.gradle.kts`
2. Update CHANGELOG.md
3. Create release branch: `release/vX.Y.Z`
4. Merge to `main` and tag
5. Build release AAB: `./gradlew bundleRelease`
6. Create GitHub Release

## 💬 Communication

- **Issues**: Bug reports and feature requests
- **Discussions**: Questions and ideas
- **Pull Requests**: Code contributions

## 📜 License

By contributing, you agree that your contributions will be licensed under the MIT License.

## 🙏 Thank You!

Your contributions make SteamDeck Mobile better for everyone!
