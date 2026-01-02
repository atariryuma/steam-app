package com.steamdeck.mobile.presentation.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.steamdeck.mobile.R
import com.steamdeck.mobile.domain.model.Game
import com.steamdeck.mobile.presentation.ui.common.AnimationDefaults
import com.steamdeck.mobile.presentation.viewmodel.GameDetailUiState
import com.steamdeck.mobile.presentation.viewmodel.GameDetailViewModel
import com.steamdeck.mobile.presentation.viewmodel.LaunchState
import com.steamdeck.mobile.presentation.viewmodel.SteamLaunchState
import com.steamdeck.mobile.presentation.ui.common.LaunchingDialogWithProgress
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.steamdeck.mobile.presentation.widget.XServerView
import com.steamdeck.mobile.core.xserver.XServer
import com.steamdeck.mobile.core.xserver.ScreenInfo

/**
 * Game Details Screen - BackboneOne style design
 *
 * Best Practices:
 * - No TopAppBar for immersive full-screen experience
 * - Large banner image with gradient overlay
 * - Steam color scheme with Material3
 * - Card elevation: 2dp
 * - Padding: 24dp (sections), 20dp (cards)
 *
 * References:
 * - https://m3.material.io/develop/android/jetpack-compose
 */
@Composable
fun GameDetailScreen(
 gameId: Long,
 onNavigateBack: () -> Unit,
 onNavigateToImport: () -> Unit = {},
 onNavigateToSettings: () -> Unit = {},
 viewModel: GameDetailViewModel = hiltViewModel()
) {
 val context = LocalContext.current
 val uiState by viewModel.uiState.collectAsStateWithLifecycle()
 val launchState by viewModel.launchState.collectAsStateWithLifecycle()
 val steamLaunchState by viewModel.steamLaunchState.collectAsStateWithLifecycle()
 val isSteamInstalled by viewModel.isSteamInstalled.collectAsStateWithLifecycle()
 val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
 var showDeleteDialog by remember { mutableStateOf(false) }
 var showLaunchErrorDialog by remember { mutableStateOf(false) }
 var showSteamLaunchErrorDialog by remember { mutableStateOf(false) }
 var showValidationErrorDialog by remember { mutableStateOf(false) }
 var launchErrorMessage by remember { mutableStateOf("") }
 var steamLaunchErrorMessage by remember { mutableStateOf("") }
 var validationErrors by remember { mutableStateOf<List<String>>(emptyList()) }
 var launchElapsedSeconds by remember { mutableStateOf(0) }
 val snackbarHostState = remember { SnackbarHostState() }

 // CRITICAL (2025-12-27): Use XServer from ViewModel (shared instance from XServerManager)
 // This connects UI to the XServer that games are actually rendering to
 val xServer by viewModel.xServer.collectAsStateWithLifecycle()

 // Create XServerView when XServer becomes available
 val xServerView = remember(xServer) {
  xServer?.let { XServerView(context, it) }
 }

 // FIXED (2025-12-27): Clean up XServerView on screen disposal
 // XServer instance is managed by XServerManager (survives screen navigation)
 // Only cleanup the View, not the XServer itself
 DisposableEffect(xServerView) {
  onDispose {
   xServerView?.let { view ->
    try {
     // Only pause View if game is NOT running
     val currentLaunchState = launchState
     if (currentLaunchState !is LaunchState.Running) {
      view.onPause()
      android.util.Log.d("GameDetailScreen", "XServerView paused (game not running)")
     } else {
      android.util.Log.d("GameDetailScreen", "XServerView cleanup skipped (game running)")
     }
    } catch (e: Exception) {
     // Catch ALL exceptions including NPE from StateFlow access during disposal
     android.util.Log.w("GameDetailScreen", "XServer cleanup failed (ViewModel cleared?)", e)
    }
   }
  }
 }

 // Load game details
 LaunchedEffect(gameId) {
  viewModel.loadGame(gameId)
 }

 // FIXED (2025-12-26): Wall clock-based timer for accurate timeout tracking
 // Previous bugs:
 //   1. while loop could become infinite if launchState stuck
 //   2. repeat() with delay() doesn't account for suspension (Doze mode)
 LaunchedEffect(launchState) {
  if (launchState is LaunchState.Launching) {
   val startTimeMillis = System.currentTimeMillis()
   val timeoutMillis = 90_000L // 90 seconds

   try {
    while (launchState is LaunchState.Launching) {
     val elapsedMillis = System.currentTimeMillis() - startTimeMillis
     launchElapsedSeconds = (elapsedMillis / 1000).toInt()

     // Check wall clock timeout (accurate even if coroutine suspended)
     if (elapsedMillis >= timeoutMillis) {
      viewModel.cancelLaunch()
      launchErrorMessage = context.getString(R.string.error_launch_timeout)
      showLaunchErrorDialog = true
      break
     }

     delay(1000) // Update UI every second
    }
   } catch (e: kotlinx.coroutines.CancellationException) {
    // LaunchedEffect cancelled (screen disposed) - expected behavior
    android.util.Log.d("GameDetailScreen", "Launch timer cancelled")
   }
  }
 }

 // Navigate back when delete is complete
 LaunchedEffect(uiState) {
  if (uiState is GameDetailUiState.Deleted) {
   onNavigateBack()
  }
 }

 // Monitor launch errors
 LaunchedEffect(launchState) {
  if (launchState is LaunchState.Error) {
   launchErrorMessage = (launchState as LaunchState.Error).message
   showLaunchErrorDialog = true
  }
 }

 // Monitor Steam launch state changes
 // FIXED (2025-12-26): Use state snapshot instead of direct state to prevent infinite loop
 // Key issue: LaunchedEffect re-runs on configuration change, but state reset happens in coroutine
 // Solution: Reset state IMMEDIATELY before launching coroutine
 LaunchedEffect(steamLaunchState) {
  when (val state = steamLaunchState) {
   is SteamLaunchState.Error -> {
    steamLaunchErrorMessage = state.message
    showSteamLaunchErrorDialog = true
    // FIXED: Reset immediately to prevent dialog re-showing on config change
    viewModel.resetSteamLaunchState()
   }
   is SteamLaunchState.ValidationFailed -> {
    validationErrors = state.errors
    showValidationErrorDialog = true
    // FIXED: Reset immediately to prevent dialog re-showing on config change
    viewModel.resetSteamLaunchState()
   }
   is SteamLaunchState.InstallComplete -> {
    // CRITICAL FIX: Capture game name BEFORE resetting state
    val gameName = state.gameName

    // FIXED: Reset state IMMEDIATELY to prevent infinite loop on configuration change
    // This ensures LaunchedEffect won't re-trigger with InstallComplete state
    viewModel.resetSteamLaunchState()

    // FIXED (2025-12-26): Removed nested launch{} to prevent orphaned coroutines
    // LaunchedEffect's scope auto-cancels on recomposition, no need for nested launch
    kotlinx.coroutines.delay(500) // Brief delay for smooth UX
    val snackbarResult = snackbarHostState.showSnackbar(
     message = "$gameName is ready to play!",
     actionLabel = "Launch",
     duration = androidx.compose.material3.SnackbarDuration.Long
    )
    if (snackbarResult == androidx.compose.material3.SnackbarResult.ActionPerformed) {
     // User clicked "Launch" - trigger game launch
     // FIXED (2025-12-27): Null-check for XServer and XServerView
     // Copy to local variables for smart cast
     val currentXServer = xServer
     val currentXServerView = xServerView
     if (currentXServer != null && currentXServerView != null) {
      viewModel.launchGame(gameId, currentXServer, currentXServerView)
     } else {
      android.util.Log.w("GameDetailScreen", "Cannot launch game: XServer not available")
     }
    }
   }
   else -> {}
  }
 }

 Scaffold(
  modifier = Modifier.fillMaxSize(),
  snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
 ) { paddingValues ->
  Box(modifier = Modifier
   .fillMaxSize()
   .padding(paddingValues)
  ) {
   when (val state = uiState) {
   is GameDetailUiState.Loading -> {
    LoadingContent()
   }
   is GameDetailUiState.Success -> {
    GameDetailContent(
     game = state.game,
     isSteamInstalled = isSteamInstalled,
     isScanning = isScanning,
     launchState = launchState,
     steamLaunchState = steamLaunchState,
     onLaunchGame = {
      // FIXED (2025-12-27): Null-check for XServer availability
      // Copy to local variables for smart cast
      val currentXServer = xServer
      val currentXServerView = xServerView
      if (currentXServer != null && currentXServerView != null) {
       viewModel.launchGame(gameId, currentXServer, currentXServerView)
      } else {
       android.util.Log.w("GameDetailScreen", "Cannot launch: XServer not available")
      }
     },
     onLaunchViaSteam = { viewModel.launchGameViaSteam(gameId) },
     onOpenSteamClient = { viewModel.openSteamClient(gameId) },
     onLaunchOrDownloadGame = {
      // FIXED (2025-12-27): Null-check for XServer availability
      // Copy to local variables for smart cast
      val currentXServer = xServer
      val currentXServerView = xServerView
      if (currentXServer != null && currentXServerView != null) {
       viewModel.launchOrDownloadGame(gameId, currentXServer, currentXServerView)
      } else {
       android.util.Log.w("GameDetailScreen", "Cannot launch: XServer not available")
      }
     },
     onScanForInstalledGame = { viewModel.scanForInstalledGame(gameId) },
     onNavigateBack = onNavigateBack,
     onNavigateToSettings = onNavigateToSettings,
     onToggleFavorite = { viewModel.toggleFavorite(state.game.id, !state.game.isFavorite) },
     onDeleteGame = { showDeleteDialog = true }
    )

    // Delete confirmation dialog
    AnimatedVisibility(
     visible = showDeleteDialog,
     enter = AnimationDefaults.DialogEnter,
     exit = AnimationDefaults.DialogExit
    ) {
     DeleteConfirmDialog(
      gameName = state.game.name,
      onConfirm = {
       viewModel.deleteGame(state.game)
       showDeleteDialog = false
      },
      onDismiss = { showDeleteDialog = false }
     )
    }

    // Launching dialog (including Winlator initialization)
    AnimatedVisibility(
     visible = launchState is LaunchState.Launching,
     enter = AnimationDefaults.DialogEnter,
     exit = AnimationDefaults.DialogExit
    ) {
     // FIXED (2025-12-26): Use strings.xml for launch dialog messages
     LaunchingDialogWithProgress(
     message = if (launchElapsedSeconds < 60) {
      stringResource(R.string.launch_dialog_initializing)
     } else {
      stringResource(R.string.launch_dialog_still_launching)
     },
     elapsedSeconds = launchElapsedSeconds
    )
    }

    // Launch error dialog
    AnimatedVisibility(
     visible = showLaunchErrorDialog,
     enter = AnimationDefaults.DialogEnter,
     exit = AnimationDefaults.DialogExit
    ) {
     LaunchErrorDialog(
      message = launchErrorMessage,
      onDismiss = { showLaunchErrorDialog = false }
     )
    }

    // Steam launch error dialog
    AnimatedVisibility(
     visible = showSteamLaunchErrorDialog,
     enter = AnimationDefaults.DialogEnter,
     exit = AnimationDefaults.DialogExit
    ) {
     SteamLaunchErrorDialog(
      message = steamLaunchErrorMessage,
      onDismiss = {
       showSteamLaunchErrorDialog = false
       viewModel.resetSteamLaunchState()
      },
      onNavigateToSettings = {
       showSteamLaunchErrorDialog = false
       onNavigateToSettings()
      }
     )
    }

    // Validation error dialog
    AnimatedVisibility(
     visible = showValidationErrorDialog,
     enter = AnimationDefaults.DialogEnter,
     exit = AnimationDefaults.DialogExit
    ) {
     ValidationErrorDialog(
      errors = validationErrors,
      onDismiss = {
       showValidationErrorDialog = false
       viewModel.resetSteamLaunchState()
      },
      onRescan = {
       showValidationErrorDialog = false
       viewModel.scanForInstalledGame(gameId)
      },
      onOpenSteam = {
       showValidationErrorDialog = false
       viewModel.openSteamClient(gameId)
      }
     )
    }
   }
   is GameDetailUiState.Error -> {
    ErrorContent(
     message = state.message,
     onNavigateBack = onNavigateBack
    )
   }
   is GameDetailUiState.Deleted -> {
    // After delete completes, LaunchedEffect navigates back
   }
  }

  // XServerView overlay for running game
  // FIXED (2025-12-27): Only show if XServerView is available
  if (launchState is LaunchState.Running && xServerView != null) {
   Box(modifier = Modifier.fillMaxSize()) {
    // Full-screen game display
    AndroidView(
     modifier = Modifier.fillMaxSize(),
     factory = {
      xServerView.apply {
       onResume() // Call once during initialization
      }
     },
     update = { view ->
      // No-op: lifecycle managed by DisposableEffect
     }
    )

    // FIXED (2025-12-26): Remove duplicate DisposableEffect
    // Lifecycle management now handled by the single DisposableEffect at screen level (line 93-115)
    // This prevents duplicate cleanup attempts and ensures consistent behavior

    // Close button overlay
    IconButton(
     onClick = {
      viewModel.stopGame() // Stop the game process
     },
     modifier = Modifier
      .align(Alignment.TopEnd)
      .padding(16.dp)
    ) {
     Icon(
      imageVector = Icons.Default.Close,
      contentDescription = "Close game",
      tint = Color.White,
      modifier = Modifier
       .background(Color.Black.copy(alpha = 0.5f), CircleShape)
       .padding(8.dp)
     )
    }
   }
  }
  } // End Box
 } // End Scaffold
}

@Composable
fun GameDetailContent(
 game: Game,
 isSteamInstalled: Boolean,
 isScanning: Boolean,
 launchState: LaunchState,
 steamLaunchState: SteamLaunchState,
 onLaunchGame: () -> Unit,
 onLaunchViaSteam: () -> Unit,
 onOpenSteamClient: () -> Unit,
 onLaunchOrDownloadGame: () -> Unit,
 onScanForInstalledGame: () -> Unit,
 onNavigateBack: () -> Unit,
 onNavigateToSettings: () -> Unit,
 onToggleFavorite: () -> Unit,
 onDeleteGame: () -> Unit,
 modifier: Modifier = Modifier
) {
 // Performance optimization (2025 best practice):
 // Remember scroll state to prevent recreation on recomposition
 val scrollState = rememberScrollState()

 Column(
  modifier = modifier
   .fillMaxSize()
   .verticalScroll(scrollState)
 ) {
  // BackboneOne-style large banner with overlay header
  Box(
   modifier = Modifier
    .fillMaxWidth()
    .height(300.dp)
  ) {
   // Banner image
   AsyncImage(
    model = game.bannerPath ?: game.iconPath,
    contentDescription = game.name,
    modifier = Modifier.fillMaxSize(),
    contentScale = ContentScale.Crop
   )

   // Gradient overlay
   Box(
    modifier = Modifier
     .fillMaxSize()
     .background(
      brush = Brush.verticalGradient(
       colors = listOf(
        Color.Black.copy(alpha = 0.3f),
        Color.Black.copy(alpha = 0.7f)
       ),
       startY = 0f
      )
     )
   )

   // Top header (back button + actions)
   Row(
    modifier = Modifier
     .fillMaxWidth()
     .padding(16.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
   ) {
    IconButton(onClick = onNavigateBack) {
     Icon(
      imageVector = Icons.AutoMirrored.Filled.ArrowBack,
      contentDescription = stringResource(R.string.content_desc_back),
      tint = Color.White
     )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
     IconButton(onClick = onNavigateToSettings) {
      Icon(
       imageVector = Icons.Default.Settings,
       contentDescription = stringResource(R.string.content_desc_settings),
       tint = Color.White
      )
     }
     IconButton(onClick = onToggleFavorite) {
      Icon(
       imageVector = if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
       contentDescription = stringResource(R.string.content_desc_favorite),
       tint = if (game.isFavorite) MaterialTheme.colorScheme.primary else Color.White
      )
     }
     IconButton(onClick = onDeleteGame) {
      Icon(
       imageVector = Icons.Default.Delete,
       contentDescription = stringResource(R.string.content_desc_delete),
       tint = Color.White
      )
     }
    }
   }

   // Bottom game title
   Column(
    modifier = Modifier
     .align(Alignment.BottomStart)
     .padding(24.dp)
   ) {
    Text(
     text = game.name,
     style = MaterialTheme.typography.headlineLarge,
     fontWeight = FontWeight.Bold,
     color = Color.White
    )
    if (game.playTimeMinutes > 0) {
     Spacer(modifier = Modifier.height(4.dp))
     Text(
      text = game.playTimeFormatted,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.primary
     )
    }
   }
  }

  Column(
   modifier = Modifier.padding(24.dp),
   verticalArrangement = Arrangement.spacedBy(20.dp)
  ) {
   // Installation Status Badge (show for Steam games)
   if (game.source == com.steamdeck.mobile.domain.model.GameSource.STEAM &&
       game.installationStatus != com.steamdeck.mobile.domain.model.InstallationStatus.NOT_INSTALLED) {
    InstallationStatusBadge(
     installationStatus = game.installationStatus,
     progress = game.installProgress,
     modifier = Modifier.fillMaxWidth()
    )
   }

   // Steam ToS Compliance: Guide users to download via official Steam client
   // Show button based on installation status (more reliable than executablePath)
   // FIXED (2025-12-26): Complete InstallationStatus coverage
   //   - NOT_INSTALLED: Show download button
   //   - DOWNLOADING/INSTALLING: Show progress
   //   - VALIDATION_FAILED: Allow retry
   //   - UPDATE_REQUIRED/UPDATE_PAUSED: Allow update/resume
   if (game.source == com.steamdeck.mobile.domain.model.GameSource.STEAM &&
    (game.installationStatus == com.steamdeck.mobile.domain.model.InstallationStatus.NOT_INSTALLED ||
     game.installationStatus == com.steamdeck.mobile.domain.model.InstallationStatus.DOWNLOADING ||
     game.installationStatus == com.steamdeck.mobile.domain.model.InstallationStatus.INSTALLING ||
     game.installationStatus == com.steamdeck.mobile.domain.model.InstallationStatus.VALIDATION_FAILED ||
     game.installationStatus == com.steamdeck.mobile.domain.model.InstallationStatus.UPDATE_REQUIRED ||
     game.installationStatus == com.steamdeck.mobile.domain.model.InstallationStatus.UPDATE_PAUSED)
   ) {
    Card(
     modifier = Modifier.fillMaxWidth(),
     colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer
     ),
     border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
     Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
     ) {
      Row(
       verticalAlignment = Alignment.CenterVertically,
       horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
       Icon(
        when (game.installationStatus) {
         com.steamdeck.mobile.domain.model.InstallationStatus.DOWNLOADING -> Icons.Default.Download
         com.steamdeck.mobile.domain.model.InstallationStatus.INSTALLING -> Icons.Default.Settings
         com.steamdeck.mobile.domain.model.InstallationStatus.VALIDATION_FAILED -> Icons.Default.Warning
         com.steamdeck.mobile.domain.model.InstallationStatus.UPDATE_REQUIRED,
         com.steamdeck.mobile.domain.model.InstallationStatus.UPDATE_PAUSED -> Icons.Default.CloudSync
         else -> Icons.Default.Download
        },
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(28.dp)
       )
       // FIXED (2025-12-26): Use strings.xml for installation status titles
       Text(
        stringResource(when (game.installationStatus) {
         com.steamdeck.mobile.domain.model.InstallationStatus.NOT_INSTALLED -> R.string.install_card_title_not_installed
         com.steamdeck.mobile.domain.model.InstallationStatus.DOWNLOADING -> R.string.install_card_title_downloading
         com.steamdeck.mobile.domain.model.InstallationStatus.INSTALLING -> R.string.install_card_title_installing
         com.steamdeck.mobile.domain.model.InstallationStatus.VALIDATION_FAILED -> R.string.install_card_title_validation_failed
         com.steamdeck.mobile.domain.model.InstallationStatus.UPDATE_REQUIRED -> R.string.install_card_title_update_required
         com.steamdeck.mobile.domain.model.InstallationStatus.UPDATE_PAUSED -> R.string.install_card_title_update_paused
         else -> R.string.install_card_title_not_installed
        }),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer
       )
      }

      HorizontalDivider(
       modifier = Modifier.padding(vertical = 4.dp),
       color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
      )

      // FIXED (2025-12-26): Use strings.xml for installation status descriptions
      Text(
       stringResource(when (game.installationStatus) {
        com.steamdeck.mobile.domain.model.InstallationStatus.NOT_INSTALLED -> R.string.install_card_desc_not_installed
        com.steamdeck.mobile.domain.model.InstallationStatus.DOWNLOADING -> R.string.install_card_desc_downloading
        com.steamdeck.mobile.domain.model.InstallationStatus.INSTALLING -> R.string.install_card_desc_installing
        com.steamdeck.mobile.domain.model.InstallationStatus.VALIDATION_FAILED -> R.string.install_card_desc_validation_failed
        com.steamdeck.mobile.domain.model.InstallationStatus.UPDATE_REQUIRED -> R.string.install_card_desc_update_required
        com.steamdeck.mobile.domain.model.InstallationStatus.UPDATE_PAUSED -> R.string.install_card_desc_update_paused
        else -> R.string.install_card_desc_not_installed
       }),
       style = MaterialTheme.typography.bodyMedium,
       color = MaterialTheme.colorScheme.onPrimaryContainer
      )

      // Single action button - Launch or Download Game
      Button(
       onClick = {
        android.util.Log.d("GameDetailScreen", "Launch/Download button clicked")
        onLaunchOrDownloadGame()
       },
       // FIXED (2025-12-26): Comprehensive state check - ALL 14 SteamLaunchState types checked
       // Enabled states:
       //   - Idle (initial state, allows launch)
       //   - Error (allows retry)
       //   - NotInstalled (allows download)
       //   - InstallComplete (allows launch after installation)
       //   - ValidationFailed (allows retry)
       // Disabled states:
       //   - CheckingInstallation, InitiatingDownload, InstallingSteam,
       //     Downloading, Installing, DownloadStartedManualTracking,
       //     Launching, Running (operations in progress)
       enabled = launchState !is LaunchState.Launching &&
                 launchState !is LaunchState.Running &&
                 steamLaunchState !is SteamLaunchState.CheckingInstallation &&
                 steamLaunchState !is SteamLaunchState.InstallingSteam &&
                 steamLaunchState !is SteamLaunchState.Downloading &&
                 steamLaunchState !is SteamLaunchState.Installing &&
                 steamLaunchState !is SteamLaunchState.InitiatingDownload &&
                 steamLaunchState !is SteamLaunchState.DownloadStartedManualTracking &&
                 steamLaunchState !is SteamLaunchState.Launching &&
                 steamLaunchState !is SteamLaunchState.Running,
       modifier = Modifier.fillMaxWidth(),
       colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary
       )
      ) {
       // FIXED (2025-12-26): Comprehensive state UI with all possible states
       if (steamLaunchState is SteamLaunchState.Launching ||
           steamLaunchState is SteamLaunchState.InitiatingDownload ||
           steamLaunchState is SteamLaunchState.CheckingInstallation) {
        CircularProgressIndicator(
         modifier = Modifier.size(20.dp),
         strokeWidth = 2.dp,
         color = MaterialTheme.colorScheme.onPrimary
        )
       } else {
        Icon(
         imageVector = when (steamLaunchState) {
          is SteamLaunchState.InstallingSteam -> Icons.Default.CloudDownload
          is SteamLaunchState.Downloading -> Icons.Default.Download
          is SteamLaunchState.Installing -> Icons.Default.Settings
          is SteamLaunchState.Running -> Icons.Default.Videocam
          is SteamLaunchState.InstallComplete -> Icons.Default.CheckCircle
          is SteamLaunchState.ValidationFailed -> Icons.Default.Warning
          is SteamLaunchState.Error -> Icons.Default.Error
          is SteamLaunchState.NotInstalled -> Icons.Default.Download
          is SteamLaunchState.Idle -> Icons.Default.PlayArrow
          is SteamLaunchState.DownloadStartedManualTracking -> Icons.Default.Download
          // All other states default to PlayArrow
          else -> Icons.Default.PlayArrow
         },
         contentDescription = null,
         modifier = Modifier.size(20.dp)
        )
       }
       Spacer(modifier = Modifier.width(8.dp))
       Text(
        text = when (steamLaunchState) {
         is SteamLaunchState.CheckingInstallation -> "Checking Installation..."
         is SteamLaunchState.InstallingSteam -> "Preparing Steam ${(steamLaunchState.progress * 100).toInt()}%"
         is SteamLaunchState.Downloading -> "Downloading ${steamLaunchState.progress}%"
         is SteamLaunchState.DownloadStartedManualTracking -> "Download Started (Check Steam)"
         is SteamLaunchState.Installing -> "Installing ${steamLaunchState.progress}%"
         is SteamLaunchState.InstallComplete -> "${steamLaunchState.gameName} Ready!"
         is SteamLaunchState.InitiatingDownload -> "Starting Download..."
         is SteamLaunchState.Launching -> "Launching..."
         is SteamLaunchState.Running -> "Running (PID: ${steamLaunchState.processId})"
         is SteamLaunchState.ValidationFailed -> "Retry Installation"
         is SteamLaunchState.Error -> "Retry"
         is SteamLaunchState.NotInstalled -> "Download Game"
         is SteamLaunchState.Idle -> "Launch Game"
         // All other states default to "Launch Game"
         else -> "Launch Game"
        },
        style = MaterialTheme.typography.titleSmall
       )
      }
     }
    }
   }

   // Launch button for installed Steam games
   // FIXED (2025-12-26): Added launch button for INSTALLED Steam games
   if (game.source == com.steamdeck.mobile.domain.model.GameSource.STEAM &&
       game.installationStatus == com.steamdeck.mobile.domain.model.InstallationStatus.INSTALLED) {
    Button(
     onClick = { onLaunchOrDownloadGame() },
     enabled = launchState !is LaunchState.Launching &&
               steamLaunchState !is SteamLaunchState.Launching &&
               steamLaunchState !is SteamLaunchState.Running,
     modifier = Modifier.fillMaxWidth(),
     colors = ButtonDefaults.buttonColors(
      containerColor = MaterialTheme.colorScheme.primary
     )
    ) {
     if (launchState is LaunchState.Launching || steamLaunchState is SteamLaunchState.Launching) {
      CircularProgressIndicator(
       modifier = Modifier.size(20.dp),
       strokeWidth = 2.dp,
       color = MaterialTheme.colorScheme.onPrimary
      )
     } else {
      Icon(
       imageVector = when (steamLaunchState) {
        is SteamLaunchState.Running -> Icons.Default.Videocam
        else -> Icons.Default.PlayArrow
       },
       contentDescription = null,
       modifier = Modifier.size(20.dp)
      )
     }
     Spacer(modifier = Modifier.width(8.dp))
     Text(
      text = when {
       launchState is LaunchState.Launching || steamLaunchState is SteamLaunchState.Launching -> "Launching..."
       steamLaunchState is SteamLaunchState.Running -> "Running (PID: ${steamLaunchState.processId})"
       else -> "Launch Game"
      },
      style = MaterialTheme.typography.titleSmall
     )
    }
   }

   // Launch button for imported games (non-Steam games)
   if (game.source == com.steamdeck.mobile.domain.model.GameSource.IMPORTED &&
       game.executablePath.isNotBlank()) {
    Button(
     onClick = onLaunchGame,
     enabled = launchState !is LaunchState.Launching,
     modifier = Modifier.fillMaxWidth(),
     colors = ButtonDefaults.buttonColors(
      containerColor = MaterialTheme.colorScheme.primary
     )
    ) {
     if (launchState is LaunchState.Launching) {
      CircularProgressIndicator(
       modifier = Modifier.size(20.dp),
       strokeWidth = 2.dp,
       color = MaterialTheme.colorScheme.onPrimary
      )
     } else {
      Icon(
       imageVector = Icons.Default.PlayArrow,
       contentDescription = null,
       modifier = Modifier.size(20.dp)
      )
     }
     Spacer(modifier = Modifier.width(8.dp))
     Text(
      text = if (launchState is LaunchState.Launching) "Launching..." else "Launch Game",
      style = MaterialTheme.typography.titleSmall
     )
    }
   }

   // Game info card
   InfoCard(
    title = "Game Info",
    items = listOf(
     "Play Time" to game.playTimeFormatted,
     "Last Played" to game.lastPlayedFormatted,
     "Source" to when (game.source) {
      com.steamdeck.mobile.domain.model.GameSource.STEAM -> "Steam"
      com.steamdeck.mobile.domain.model.GameSource.IMPORTED -> "Import"
     }
    )
   )

   // File paths card
   InfoCard(
    title = "File Paths",
    items = listOf(
     "Executable" to game.executablePath,
     "Installation Path" to game.installPath
    )
   )

   if (game.steamAppId != null) {
    InfoCard(
     title = "Steam Info",
     items = listOf(
      "Steam App ID" to game.steamAppId.toString()
     )
    )
   }
  }
 }
}

/**
 * Info card - BackboneOne style design
 */
@Composable
fun InfoCard(
 title: String,
 items: List<Pair<String, String>>,
 modifier: Modifier = Modifier
) {
 Card(
  modifier = modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(
   containerColor = MaterialTheme.colorScheme.surfaceVariant
  ),
  shape = MaterialTheme.shapes.large,
  elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
 ) {
  Column(
   modifier = Modifier
    .fillMaxWidth()
    .padding(20.dp),
   verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
   Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary
   )
   items.forEach { (label, value) ->
    Row(
     modifier = Modifier.fillMaxWidth(),
     horizontalArrangement = Arrangement.SpaceBetween
    ) {
     Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
     )
     Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
     )
    }
   }
  }
 }
}

@Composable
fun DeleteConfirmDialog(
 gameName: String,
 onConfirm: () -> Unit,
 onDismiss: () -> Unit
) {
 AlertDialog(
  onDismissRequest = onDismiss,
  icon = { Icon(Icons.Default.Warning, contentDescription = null) },
  title = { Text(stringResource(R.string.dialog_delete_game_title, gameName)) },
  text = { Text(stringResource(R.string.dialog_delete_game_message)) },
  confirmButton = {
   TextButton(
    onClick = onConfirm,
    colors = ButtonDefaults.textButtonColors(
     contentColor = MaterialTheme.colorScheme.error
    )
   ) {
    Text(stringResource(R.string.button_delete))
   }
  },
  dismissButton = {
   TextButton(onClick = onDismiss) {
    Text(stringResource(R.string.button_cancel))
   }
  }
 )
}

@Composable
fun LaunchingDialog() {
 AlertDialog(
  onDismissRequest = {}, // Non-dismissible
  icon = { CircularProgressIndicator() },
  title = { Text(stringResource(R.string.game_launching)) },
  text = {
   Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.launch_dialog_initializing))
    Text(stringResource(R.string.launch_dialog_please_wait), style = MaterialTheme.typography.bodySmall)
   }
  },
  confirmButton = {} // No button, non-dismissible
 )
}

@Composable
fun LaunchErrorDialog(
 message: String,
 onDismiss: () -> Unit
) {
 AlertDialog(
  onDismissRequest = onDismiss,
  icon = { Icon(Icons.Default.Info, contentDescription = stringResource(R.string.content_desc_info)) },
  title = { Text(stringResource(R.string.dialog_launch_error_title)) },
  text = { Text(message) },
  confirmButton = {
   TextButton(onClick = onDismiss) {
    Text(stringResource(R.string.button_ok))
   }
  }
 )
}

@Composable
fun LoadingContent(modifier: Modifier = Modifier) {
 Box(
  modifier = modifier.fillMaxSize(),
  contentAlignment = Alignment.Center
 ) {
  Column(
   horizontalAlignment = Alignment.CenterHorizontally,
   verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
   CircularProgressIndicator()
   Text(
    text = stringResource(R.string.loading_text),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
   )
  }
 }
}

@Composable
fun ErrorContent(
 message: String,
 onNavigateBack: () -> Unit,
 modifier: Modifier = Modifier
) {
 Box(
  modifier = modifier.fillMaxSize(),
  contentAlignment = Alignment.Center
 ) {
  Column(
   horizontalAlignment = Alignment.CenterHorizontally,
   verticalArrangement = Arrangement.spacedBy(16.dp),
   modifier = Modifier.padding(24.dp)
  ) {
   Icon(
    imageVector = Icons.Default.Warning,
    contentDescription = stringResource(R.string.content_desc_error),
    modifier = Modifier.size(64.dp),
    tint = MaterialTheme.colorScheme.error
   )
   Text(
    text = stringResource(R.string.error_occurred_title),
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.error
   )
   Text(
    text = message,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
   )
   Button(onClick = onNavigateBack) {
    Text(stringResource(R.string.button_back))
   }
  }
 }
}

/**
 * Split launch button
 *
 * Steam-first logic: If Steam is installed and game has Steam App ID, launch via Steam
 */
@Composable
fun SplitLaunchButton(
 game: Game,
 isSteamInstalled: Boolean,
 onDirectLaunch: () -> Unit,
 onSteamLaunch: () -> Unit,
 onOpenSteamClient: () -> Unit,
 onNavigateToSettings: () -> Unit,
 modifier: Modifier = Modifier
) {
 var showDropdownMenu by remember { mutableStateOf(false) }
 val isEnabled = game.executablePath.isNotBlank()

 // Steam-first logic: Prefer Steam if installed and game has Steam App ID
 val shouldUseSteam = isSteamInstalled && game.steamAppId != null

 Row(
  modifier = modifier,
  horizontalArrangement = Arrangement.spacedBy(0.dp)
 ) {
  // Primary button (80%)
  Button(
   onClick = {
    if (shouldUseSteam) {
     onSteamLaunch()
    } else {
     onDirectLaunch()
    }
   },
   modifier = Modifier.weight(0.8f),
   enabled = isEnabled,
   shape = RoundedCornerShape(
    topStart = 20.dp,
    bottomStart = 20.dp,
    topEnd = 0.dp,
    bottomEnd = 0.dp
   )
  ) {
   Icon(
    imageVector = if (shouldUseSteam) Icons.Default.SportsEsports else Icons.Default.PlayArrow,
    contentDescription = if (shouldUseSteam) "Steamlaunch" else "directlylaunch"
   )
   Spacer(modifier = Modifier.width(8.dp))
   Text(if (shouldUseSteam) "Steam Launch" else "Direct Launch")
  }

  // Dropdown menu button (20%)
  Button(
   onClick = { showDropdownMenu = true },
   modifier = Modifier.weight(0.2f),
   enabled = isEnabled,
   shape = RoundedCornerShape(
    topStart = 0.dp,
    bottomStart = 0.dp,
    topEnd = 20.dp,
    bottomEnd = 20.dp
   )
  ) {
   Icon(
    imageVector = Icons.Default.ArrowDropDown,
    contentDescription = stringResource(R.string.content_desc_launch_options)
   )
  }

 }

 // Dropdown menu (positioned outside Row)
 Box {
  DropdownMenu(
   expanded = showDropdownMenu,
   onDismissRequest = { showDropdownMenu = false }
  ) {
    // directlylaunch (Winlator)
    DropdownMenuItem(
     text = {
      Column {
       Text("directlylaunch (Winlator)")
      }
     },
     onClick = {
      showDropdownMenu = false
      onDirectLaunch()
     },
     leadingIcon = {
      Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.content_desc_launch_directly))
     },
     enabled = isEnabled
    )

    HorizontalDivider()

    // Steamvia launch
    DropdownMenuItem(
     text = {
      Column {
       Text("Steamvia launch")
       if (!isSteamInstalled) {
        Text(
         text = "(Not Installed)",
         style = MaterialTheme.typography.bodySmall,
         color = MaterialTheme.colorScheme.error
        )
       } else if (game.steamAppId == null) {
        Text(
         text = "(Steam App ID not set)",
         style = MaterialTheme.typography.bodySmall,
         color = MaterialTheme.colorScheme.error
        )
       }
      }
     },
     onClick = {
      showDropdownMenu = false
      if (isSteamInstalled && game.steamAppId != null) {
       onSteamLaunch()
      } else {
       onNavigateToSettings()
      }
     },
     leadingIcon = {
      Icon(Icons.Default.SportsEsports, contentDescription = stringResource(R.string.content_desc_launch_steam))
     },
     enabled = isSteamInstalled && game.steamAppId != null && isEnabled
    )

    // Steam Clientopen
    DropdownMenuItem(
     text = {
      Column {
       Text("Open Steam Client")
       if (!isSteamInstalled) {
        Text(
         text = "(Not Installed)",
         style = MaterialTheme.typography.bodySmall,
         color = MaterialTheme.colorScheme.error
        )
       }
      }
     },
     onClick = {
      showDropdownMenu = false
      if (isSteamInstalled) {
       onOpenSteamClient()
      } else {
       onNavigateToSettings()
      }
     },
     leadingIcon = {
      Icon(Icons.Default.SportsEsports, contentDescription = stringResource(R.string.content_desc_steam_client))
     },
     enabled = isSteamInstalled && isEnabled
    )

    HorizontalDivider()

   // settingsopen
   DropdownMenuItem(
    text = { Text("settingsopen") },
    onClick = {
     showDropdownMenu = false
     onNavigateToSettings()
    },
    leadingIcon = {
     Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.content_desc_settings))
    }
   )
  }
 }
}

/**
 * Steam Launch Error Dialog
 */
@Composable
fun SteamLaunchErrorDialog(
 message: String,
 onDismiss: () -> Unit,
 onNavigateToSettings: () -> Unit
) {
 AlertDialog(
  onDismissRequest = onDismiss,
  icon = {
   Icon(
    imageVector = Icons.Default.Warning,
    contentDescription = stringResource(R.string.content_desc_error),
    tint = MaterialTheme.colorScheme.error
   )
  },
  title = {
   Text(stringResource(R.string.dialog_steam_launch_error_title))
  },
  text = {
   Column(
    verticalArrangement = Arrangement.spacedBy(8.dp)
   ) {
    Text(message)

    if (message.contains("install", ignoreCase = true) || message.contains("container", ignoreCase = true)) {
     Text(
      text = stringResource(R.string.dialog_steam_launch_error_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
     )
    }
   }
  },
  confirmButton = {
   if (message.contains("install", ignoreCase = true) || message.contains("container", ignoreCase = true)) {
    FilledTonalButton(onClick = onNavigateToSettings) {
     Text(stringResource(R.string.button_open_settings))
    }
   }
  },
  dismissButton = {
   TextButton(onClick = onDismiss) {
    Text(stringResource(R.string.button_close))
   }
  }
 )
}

/**
 * Installation Status Badge
 * Shows current installation/download status with progress
 */
@Composable
fun InstallationStatusBadge(
 installationStatus: com.steamdeck.mobile.domain.model.InstallationStatus,
 progress: Int,
 modifier: Modifier = Modifier
) {
 val statusText = when (installationStatus) {
  com.steamdeck.mobile.domain.model.InstallationStatus.NOT_INSTALLED ->
   stringResource(R.string.game_status_not_installed)
  com.steamdeck.mobile.domain.model.InstallationStatus.DOWNLOADING ->
   stringResource(R.string.game_status_downloading)
  com.steamdeck.mobile.domain.model.InstallationStatus.INSTALLING ->
   stringResource(R.string.game_status_installing)
  com.steamdeck.mobile.domain.model.InstallationStatus.INSTALLED ->
   stringResource(R.string.game_status_installed)
  com.steamdeck.mobile.domain.model.InstallationStatus.VALIDATION_FAILED ->
   stringResource(R.string.game_status_validation_failed)
  com.steamdeck.mobile.domain.model.InstallationStatus.UPDATE_REQUIRED ->
   stringResource(R.string.game_status_update_required)
  com.steamdeck.mobile.domain.model.InstallationStatus.UPDATE_PAUSED ->
   stringResource(R.string.game_status_update_paused)
 }

 val statusColor = when (installationStatus) {
  com.steamdeck.mobile.domain.model.InstallationStatus.INSTALLED ->
   MaterialTheme.colorScheme.primary
  com.steamdeck.mobile.domain.model.InstallationStatus.DOWNLOADING,
  com.steamdeck.mobile.domain.model.InstallationStatus.INSTALLING ->
   MaterialTheme.colorScheme.secondary
  com.steamdeck.mobile.domain.model.InstallationStatus.VALIDATION_FAILED ->
   MaterialTheme.colorScheme.error
  com.steamdeck.mobile.domain.model.InstallationStatus.UPDATE_REQUIRED,
  com.steamdeck.mobile.domain.model.InstallationStatus.UPDATE_PAUSED ->
   MaterialTheme.colorScheme.tertiary
  else -> MaterialTheme.colorScheme.onSurfaceVariant
 }

 Surface(
  modifier = modifier,
  shape = RoundedCornerShape(8.dp),
  color = statusColor.copy(alpha = 0.1f),
  border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
 ) {
  Row(
   modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
   horizontalArrangement = Arrangement.spacedBy(8.dp),
   verticalAlignment = Alignment.CenterVertically
  ) {
   // Show progress indicator for active downloads/installs
   if (installationStatus == com.steamdeck.mobile.domain.model.InstallationStatus.DOWNLOADING ||
       installationStatus == com.steamdeck.mobile.domain.model.InstallationStatus.INSTALLING) {
    CircularProgressIndicator(
     modifier = Modifier.size(16.dp),
     strokeWidth = 2.dp,
     color = statusColor
    )
   }

   Column {
    Text(
     text = statusText,
     style = MaterialTheme.typography.labelMedium,
     color = statusColor,
     fontWeight = FontWeight.SemiBold
    )

    // Show progress percentage
    if (progress > 0 && (installationStatus == com.steamdeck.mobile.domain.model.InstallationStatus.DOWNLOADING ||
                         installationStatus == com.steamdeck.mobile.domain.model.InstallationStatus.INSTALLING)) {
     Text(
      text = stringResource(R.string.download_progress_text, progress),
      style = MaterialTheme.typography.bodySmall,
      color = statusColor.copy(alpha = 0.7f)
     )
    }
   }
  }
 }
}

/**
 * Validation Error Dialog
 * Shows validation errors with actionable buttons
 */
@Composable
fun ValidationErrorDialog(
 errors: List<String>,
 onDismiss: () -> Unit,
 onRescan: () -> Unit,
 onOpenSteam: () -> Unit
) {
 AlertDialog(
  onDismissRequest = onDismiss,
  icon = {
   Icon(
    imageVector = Icons.Default.Warning,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.error
   )
  },
  title = {
   Text(stringResource(R.string.dialog_validation_error_title))
  },
  text = {
   Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    errors.forEach { error ->
     Text(
      text = "• $error",
      style = MaterialTheme.typography.bodyMedium
     )
    }
   }
  },
  confirmButton = {
   Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick = onRescan) {
     Text(stringResource(R.string.button_rescan))
    }
    FilledTonalButton(onClick = onOpenSteam) {
     Text(stringResource(R.string.button_open_steam))
    }
   }
  },
  dismissButton = {
   TextButton(onClick = onDismiss) {
    Text(stringResource(R.string.button_close))
   }
  }
 )
}
