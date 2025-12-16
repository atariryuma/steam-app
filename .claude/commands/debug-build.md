---
description: Debug common Android build errors
tags: [debug, gradle, build]
---

Analyze and fix Android build errors with this diagnostic approach:

1. **Read the build error** from latest `./gradlew assembleDebug` output

2. **Common issues and solutions:**

   **Gradle Sync Failed:**
   - Check `gradle/libs.versions.toml` for typos
   - Verify internet connection (dependency download)
   - Clear Gradle cache: `./gradlew clean --refresh-dependencies`

   **Compilation Errors:**
   - Check imports (use correct package paths)
   - Verify Kotlin version compatibility
   - Check for missing `@HiltViewModel` / `@Inject` annotations

   **Room Database Errors:**
   - Schema change requires version bump in `SteamDeckDatabase`
   - Run `./gradlew kspDebugKotlin` to regenerate code
   - Check entity field types match DAO queries

   **Compose Errors:**
   - Verify `@Composable` annotation on functions
   - Check state hoisting (no `remember` in stateless composables)
   - Ensure Material3 imports (not Material or Material2)

   **Hilt Errors:**
   - Check `@InstallIn` scope matches usage
   - Verify module `@Provides` methods have correct return types
   - Ensure `@HiltAndroidApp` on Application class

   **Dependency Conflicts:**
   - Check for duplicate dependencies
   - Verify BOM versions align
   - Use `./gradlew dependencies` to see resolution

3. **Diagnostic commands:**
   ```bash
   ./gradlew clean
   ./gradlew --refresh-dependencies
   ./gradlew dependencies
   ./gradlew kspDebugKotlin
   ```

4. **Provide fix with explanation**

Run diagnostics and suggest specific fixes based on error output.
