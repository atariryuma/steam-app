package com.steamdeck.mobile.presentation.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.steamdeck.mobile.presentation.viewmodel.GameDetailUiState
import com.steamdeck.mobile.presentation.viewmodel.GameDetailViewModel
import com.steamdeck.mobile.presentation.viewmodel.LaunchState
import com.steamdeck.mobile.presentation.viewmodel.SteamLaunchState

/**
 * game詳細画面 - BackboneOnestyle design
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
 val uiState by viewModel.uiState.collectAsState()
 val launchState by viewModel.launchState.collectAsState()
 val steamLaunchState by viewModel.steamLaunchState.collectAsState()
 val isSteamInstalled by viewModel.isSteamInstalled.collectAsState()
 var showDeleteDialog by remember { mutableStateOf(false) }
 var showLaunchErrorDialog by remember { mutableStateOf(false) }
 var showSteamLaunchErrorDialog by remember { mutableStateOf(false) }
 var launchErrorMessage by remember { mutableStateOf("") }
 var steamLaunchErrorMessage by remember { mutableStateOf("") }

 // game詳細読み込み
 LaunchedEffect(gameId) {
  viewModel.loadGame(gameId)
 }

 // deleteComplete時 return
 LaunchedEffect(uiState) {
  if (uiState is GameDetailUiState.Deleted) {
   onNavigateBack()
  }
 }

 // launchError監視
 LaunchedEffect(launchState) {
  if (launchState is LaunchState.Error) {
   launchErrorMessage = (launchState as LaunchState.Error).message
   showLaunchErrorDialog = true
  }
 }

 // SteamlaunchError監視
 LaunchedEffect(steamLaunchState) {
  if (steamLaunchState is SteamLaunchState.Error) {
   steamLaunchErrorMessage = (steamLaunchState as SteamLaunchState.Error).message
   showSteamLaunchErrorDialog = true
  }
 }

 Box(modifier = Modifier.fillMaxSize()) {
  when (val state = uiState) {
   is GameDetailUiState.Loading -> {
    LoadingContent()
   }
   is GameDetailUiState.Success -> {
    GameDetailContent(
     game = state.game,
     isSteamInstalled = isSteamInstalled,
     onLaunchGame = { viewModel.launchGame(gameId) },
     onLaunchViaSteam = { viewModel.launchGameViaSteam(gameId) },
     onOpenSteamClient = { viewModel.openSteamClient(gameId) },
     onNavigateBack = onNavigateBack,
     onNavigateToSettings = onNavigateToSettings,
     onToggleFavorite = { viewModel.toggleFavorite(state.game.id, !state.game.isFavorite) },
     onDeleteGame = { showDeleteDialog = true }
    )

    // deleteconfirmダイアログ
    if (showDeleteDialog) {
     DeleteConfirmDialog(
      gameName = state.game.name,
      onConfirm = {
       viewModel.deleteGame(state.game)
       showDeleteDialog = false
      },
      onDismiss = { showDeleteDialog = false }
     )
    }

    // launchinダイアログ (Winlator initialization含む)
    if (launchState is LaunchState.Launching) {
     LaunchingDialog()
    }

    // launchErrorダイアログ
    if (showLaunchErrorDialog) {
     LaunchErrorDialog(
      message = launchErrorMessage,
      onDismiss = { showLaunchErrorDialog = false }
     )
    }

    // SteamlaunchErrorダイアログ
    if (showSteamLaunchErrorDialog) {
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
   }
   is GameDetailUiState.Error -> {
    ErrorContent(
     message = state.message,
     onNavigateBack = onNavigateBack
    )
   }
   is GameDetailUiState.Deleted -> {
    // deleteComplete後 LaunchedEffect return
   }
  }
 }
}

@Composable
fun GameDetailContent(
 game: Game,
 isSteamInstalled: Boolean,
 onLaunchGame: () -> Unit,
 onLaunchViaSteam: () -> Unit,
 onOpenSteamClient: () -> Unit,
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
  // BackboneOne風 大画面バナー with オーバーレイヘッダー
  Box(
   modifier = Modifier
    .fillMaxWidth()
    .height(300.dp)
  ) {
   // バナー画像
   AsyncImage(
    model = game.bannerPath ?: game.iconPath,
    contentDescription = game.name,
    modifier = Modifier.fillMaxSize(),
    contentScale = ContentScale.Crop
   )

   // グラデーションオーバーレイ
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

   // 上部ヘッダー（returnボタン + アクション）
   Row(
    modifier = Modifier
     .fillMaxWidth()
     .padding(16.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
   ) {
    IconButton(onClick = onNavigateBack) {
     Icon(
      imageVector = Icons.Default.ArrowBack,
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

   // 下部gameタイトル
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
   // Steam ToS Compliance: Guide users to download via official Steam client
   if (game.source == com.steamdeck.mobile.domain.model.GameSource.STEAM &&
    game.executablePath.isBlank()
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
      verticalArrangement = Arrangement.spacedBy(12.dp)
     ) {
      Row(
       verticalAlignment = Alignment.CenterVertically,
       horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
       Icon(
        Icons.Outlined.Download,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(28.dp)
       )
       Text(
        "How to Download This Game",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer
       )
      }

      HorizontalDivider(
       modifier = Modifier.padding(vertical = 4.dp),
       color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
      )

      // Step-by-step instructions
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
       StepItem(number = 1, text = "Click the button below to launch Steam Client")
       StepItem(number = 2, text = "Search for \"${game.name}\" in your Library")
       StepItem(number = 3, text = "Click \"Install\" button in Steam")
       StepItem(number = 4, text = "Wait for download to complete")
       StepItem(number = 5, text = "Return to this app - the \"Launch Game\" button will activate")
      }

      Spacer(modifier = Modifier.height(4.dp))

      // Primary action button
      Button(
       onClick = onOpenSteamClient,
       modifier = Modifier.fillMaxWidth(),
       colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary
       )
      ) {
       Icon(
        Icons.Default.SportsEsports,
        contentDescription = null,
        modifier = Modifier.size(20.dp)
       )
       Spacer(modifier = Modifier.width(8.dp))
       Text("Open Steam Client", style = MaterialTheme.typography.titleSmall)
      }

      // Secondary action button (refresh)
      OutlinedButton(
       onClick = { /* TODO: Implement scan for installed games */ },
       modifier = Modifier.fillMaxWidth()
      ) {
       Icon(
        Icons.Default.Refresh,
        contentDescription = null,
        modifier = Modifier.size(18.dp)
       )
       Spacer(modifier = Modifier.width(8.dp))
       Text("Rescan for Installed Games", style = MaterialTheme.typography.bodyMedium)
      }

      // Info footer
      Text(
       "Steam ToS Compliance: All downloads must go through the official Steam client",
       style = MaterialTheme.typography.bodySmall,
       color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
       modifier = Modifier.padding(top = 4.dp)
      )
     }
    }
   }

   // スプリットlaunchボタン
   SplitLaunchButton(
    game = game,
    isSteamInstalled = isSteamInstalled,
    onDirectLaunch = onLaunchGame,
    onSteamLaunch = onLaunchViaSteam,
    onOpenSteamClient = onOpenSteamClient,
    onNavigateToSettings = onNavigateToSettings,
    modifier = Modifier.fillMaxWidth()
   )

   // game情報カード
   InfoCard(
    title = "game情報",
    items = listOf(
     "play time" to game.playTimeFormatted,
     "最終プレイdate and time" to game.lastPlayedFormatted,
     "Source" to when (game.source) {
      com.steamdeck.mobile.domain.model.GameSource.STEAM -> "Steam"
      com.steamdeck.mobile.domain.model.GameSource.IMPORTED -> "Import"
     }
    )
   )

   // File Pathsカード
   InfoCard(
    title = "File Paths",
    items = listOf(
     "Executable" to game.executablePath,
     "installationパス" to game.installPath
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
 * 情報カード - BackboneOnestyle design
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
  title = { Text("gamelaunchin...") },
  text = {
   Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("初回launch時 Winlator 初期化 2-3minutesかかる場合 あります。")
    Text("しばらくお待ちください。", style = MaterialTheme.typography.bodySmall)
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
  icon = { Icon(Icons.Default.Info, contentDescription = "情報") },
  title = { Text("gamelaunch きません") },
  text = { Text(message) },
  confirmButton = {
   TextButton(onClick = onDismiss) {
    Text("OK")
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
    text = "読み込みin...",
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
    contentDescription = "Error",
    modifier = Modifier.size(64.dp),
    tint = MaterialTheme.colorScheme.error
   )
   Text(
    text = "Error 発生しました",
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
    Text("return")
   }
  }
 }
}

/**
 * スプリットlaunchボタン
 *
 * Steam-first ロジック: Steam installationされていて、game Steam App ID持つ場合 Steam via launch
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

 // Steam-first ロジック: Steaminstallation済み & Steam App ID ある場合 Steam優先
 val shouldUseSteam = isSteamInstalled && game.steamAppId != null

 Row(
  modifier = modifier,
  horizontalArrangement = Arrangement.spacedBy(0.dp)
 ) {
  // プライマリボタン (80%)
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
   Text(if (shouldUseSteam) "Steam launch" else "gamelaunch")
  }

  // ドロップダウンメニューボタン (20%)
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
    contentDescription = "launchオプション"
   )
  }

 }

 // ドロップダウンメニュー（Row 外側 配置）
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
      Icon(Icons.Default.PlayArrow, contentDescription = "directlylaunch")
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
         text = "(未installation)",
         style = MaterialTheme.typography.bodySmall,
         color = MaterialTheme.colorScheme.error
        )
       } else if (game.steamAppId == null) {
        Text(
         text = "(Steam App ID未settings)",
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
      Icon(Icons.Default.SportsEsports, contentDescription = "Steamlaunch")
     },
     enabled = isSteamInstalled && game.steamAppId != null && isEnabled
    )

    // Steam Clientopen
    DropdownMenuItem(
     text = {
      Column {
       Text("Steam Clientopen")
       if (!isSteamInstalled) {
        Text(
         text = "(未installation)",
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
      Icon(Icons.Default.SportsEsports, contentDescription = "Steam Client")
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
     Icon(Icons.Default.Settings, contentDescription = "settings")
    }
   )
  }
 }
}

/**
 * Step item composable for download instructions
 */
@Composable
private fun StepItem(
 number: Int,
 text: String,
 modifier: Modifier = Modifier
) {
 Row(
  modifier = modifier,
  horizontalArrangement = Arrangement.spacedBy(12.dp),
  verticalAlignment = Alignment.Top
 ) {
  // Step number badge
  Surface(
   shape = CircleShape,
   color = MaterialTheme.colorScheme.primary,
   modifier = Modifier.size(24.dp)
  ) {
   Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier.fillMaxSize()
   ) {
    Text(
     text = number.toString(),
     style = MaterialTheme.typography.labelMedium,
     color = MaterialTheme.colorScheme.onPrimary,
     fontWeight = FontWeight.Bold
    )
   }
  }

  // Step text
  Text(
   text = text,
   style = MaterialTheme.typography.bodyMedium,
   color = MaterialTheme.colorScheme.onPrimaryContainer,
   modifier = Modifier.weight(1f)
  )
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
