---
description: Add a new dependency following project conventions
tags: [gradle, dependency]
---

Add a new dependency to the project following these steps:

1. **Add to Version Catalog** (`gradle/libs.versions.toml`):
   ```toml
   [versions]
   library-name = "x.y.z"

   [libraries]
   library-artifact = { group = "com.example", name = "library", version.ref = "library-name" }
   ```

2. **Add to app/build.gradle.kts**:
   ```kotlin
   dependencies {
       implementation(libs.library.artifact)
       // or
       ksp(libs.library.artifact) // for annotation processors
   }
   ```

3. **Check APK Size Impact**:
   - Run `./gradlew assembleDebug`
   - Check APK size (target: <50MB)
   - If significant increase, document justification

4. **Update CLAUDE.md** if it's a major dependency:
   - Add to "Key Dependencies & Usage" section
   - Document common usage patterns
   - Add to tech stack list if appropriate

5. **Sync Gradle** and verify build succeeds

**Before adding, verify:**
- Is this dependency actively maintained?
- Does it conflict with existing dependencies?
- Is there a lighter alternative?
- Does it support Android minSdk 26?

Provide the library name, and I'll add it following this process.
