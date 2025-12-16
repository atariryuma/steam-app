---
description: Review recent code changes for architecture compliance and best practices
tags: [review, quality]
---

Please review the most recent code changes (use git diff or git log) and check for:

1. **Architecture Compliance**
   - Clean Architecture boundaries respected?
   - Proper layer separation (presentation/domain/data)?
   - No domain layer depending on presentation/data implementation?

2. **Kotlin Best Practices**
   - No `!!` operator usage without justification?
   - Proper null safety handling?
   - Coroutines used correctly (proper dispatchers)?
   - No blocking calls on main thread?

3. **Jetpack Compose**
   - State hoisting properly implemented?
   - No business logic in Composables?
   - Material3 components used correctly?
   - `remember` and `LaunchedEffect` used appropriately?

4. **Testing**
   - Are tests needed for this change?
   - Are existing tests still passing?

5. **Documentation**
   - KDoc comments for public APIs?
   - Commit message follows Conventional Commits?

Provide specific feedback with file:line references.
