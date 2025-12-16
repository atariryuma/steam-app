---
description: Create a new Compose screen following project conventions
tags: [compose, scaffold, ui]
---

Create a new Jetpack Compose screen with the following structure:

**Screen Setup:**
1. Create `presentation/ui/{screen_name}/{ScreenName}Screen.kt`
2. Create `presentation/viewmodel/{ScreenName}ViewModel.kt`
3. Create necessary domain models if needed
4. Add navigation route to navigation graph

**Required Components:**

```kotlin
// Screen composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun {ScreenName}Screen(
    viewModel: {ScreenName}ViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Title") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { paddingValues ->
        // Content
    }
}

// ViewModel
@HiltViewModel
class {ScreenName}ViewModel @Inject constructor(
    // dependencies
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
}

// UI State (sealed class)
sealed interface UiState {
    object Loading : UiState
    data class Success(val data: Data) : UiState
    data class Error(val message: String) : UiState
}
```

**Follow these conventions:**
- Material3 components only
- State hoisting pattern
- KDoc comments for public functions
- Error handling with user-friendly messages (Japanese)
- Use `hiltViewModel()` for DI
- Scaffold with TopAppBar
- Proper theming with MaterialTheme colors/typography

Ask for screen name and purpose, then implement.
