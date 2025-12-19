package com.steamdeck.mobile.presentation.ui.wine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.steamdeck.mobile.presentation.viewmodel.WineTestUiState
import com.steamdeck.mobile.presentation.viewmodel.WineTestViewModel

/**
 * Wine/Winlator integration test screen - Landscape-optimized design
 *
 * Best Practices:
 * - NavigationRail + Content layout for landscape orientation
 * - No TopAppBar for immersive full-screen experience
 * - Material3 Card styling: elevation 2dp, padding 20dp, shapes.large
 * - Responsive two-column layout for efficient space usage
 *
 * Displays status of Wine environment and allows testing basic functionality.
 */
@Composable
fun WineTestScreen(
 onNavigateBack: () -> Unit,
 viewModel: WineTestViewModel = hiltViewModel()
) {
 val uiState by viewModel.uiState.collectAsState()

 Row(modifier = Modifier.fillMaxSize()) {
  // 左サイドバー（NavigationRail）
  WineTestNavigationRail(onNavigateBack = onNavigateBack)

  // 右側コンテンツ（2カラムレイアウト）
  WineTestContent(
   uiState = uiState,
   onCheckWine = viewModel::checkWineAvailability,
   onInitialize = viewModel::initializeEmulator,
   onCreateContainer = viewModel::testCreateContainer,
   onListContainers = viewModel::listContainers
  )
 }
}

@Composable
private fun WineTestNavigationRail(onNavigateBack: () -> Unit) {
 NavigationRail(
  modifier = Modifier.fillMaxHeight(),
  containerColor = MaterialTheme.colorScheme.surfaceVariant,
  contentColor = MaterialTheme.colorScheme.onSurfaceVariant
 ) {
  Spacer(modifier = Modifier.height(16.dp))

  // returnボタン
  NavigationRailItem(
   selected = false,
   onClick = onNavigateBack,
   icon = {
    Icon(
     imageVector = Icons.Default.ArrowBack,
     contentDescription = "return"
    )
   },
   label = { Text("return", fontSize = 11.sp) }
  )

  Spacer(modifier = Modifier.weight(1f))

  // タイトル（縦書き風）
  Column(
   modifier = Modifier.padding(vertical = 16.dp),
   horizontalAlignment = Alignment.CenterHorizontally,
   verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
   Text(
    text = "Wine Test",
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary,
    textAlign = TextAlign.Center,
    lineHeight = 14.sp
   )
  }

  Spacer(modifier = Modifier.height(16.dp))
 }
}

@Composable
private fun WineTestContent(
 uiState: WineTestUiState,
 onCheckWine: () -> Unit,
 onInitialize: () -> Unit,
 onCreateContainer: () -> Unit,
 onListContainers: () -> Unit
) {
 Column(
  modifier = Modifier
   .fillMaxSize()
   .padding(24.dp),
  verticalArrangement = Arrangement.spacedBy(16.dp)
 ) {
  // ステータス表示（コンパクト）
  CompactStatusRow(uiState = uiState)

  // Testボタン（横並び2列）
  if (uiState !is WineTestUiState.Testing) {
   CompactTestButtons(
    onCheckWine = onCheckWine,
    onInitialize = onInitialize,
    onCreateContainer = onCreateContainer,
    onListContainers = onListContainers
   )
  }

  // 実行結果
  when (val state = uiState) {
   is WineTestUiState.Testing -> {
    TestingProgressCard(message = state.message)
   }
   is WineTestUiState.Success -> {
    TestResultCard(
     title = "✓ TestSuccess",
     message = state.message,
     isSuccess = true
    )
   }
   is WineTestUiState.Error -> {
    TestResultCard(
     title = "✗ Error",
     message = state.message,
     isSuccess = false
    )
   }
   else -> {}
  }
 }
}

@Composable
private fun CompactStatusRow(uiState: WineTestUiState) {
 Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(
   containerColor = MaterialTheme.colorScheme.surfaceVariant
  ),
  shape = MaterialTheme.shapes.medium,
  elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
 ) {
  Row(
   modifier = Modifier
    .fillMaxWidth()
    .padding(16.dp),
   horizontalArrangement = Arrangement.SpaceBetween,
   verticalAlignment = Alignment.CenterVertically
  ) {
   Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
   ) {
    Icon(
     imageVector = when (uiState) {
      is WineTestUiState.Success -> Icons.Default.Check
      is WineTestUiState.Error -> Icons.Default.Close
      else -> Icons.Default.Info
     },
     contentDescription = null,
     tint = when (uiState) {
      is WineTestUiState.Success -> MaterialTheme.colorScheme.primary
      is WineTestUiState.Error -> MaterialTheme.colorScheme.error
      else -> MaterialTheme.colorScheme.onSurfaceVariant
     }
    )
    Text(
     text = when (uiState) {
      is WineTestUiState.Idle -> "Ready"
      is WineTestUiState.Testing -> "実行in..."
      is WineTestUiState.Success -> "Available"
      is WineTestUiState.Error -> "Error"
     },
     style = MaterialTheme.typography.titleSmall,
     fontWeight = FontWeight.Bold
    )
   }

   Text(
    text = "Wine/Winlator",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
   )
  }
 }
}

@Composable
private fun CompactTestButtons(
 onCheckWine: () -> Unit,
 onInitialize: () -> Unit,
 onCreateContainer: () -> Unit,
 onListContainers: () -> Unit
) {
 Column(
  modifier = Modifier.fillMaxWidth(),
  verticalArrangement = Arrangement.spacedBy(12.dp)
 ) {
  // 1行目: Check & Initialize
  Row(
   modifier = Modifier.fillMaxWidth(),
   horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
   Button(
    onClick = onCheckWine,
    modifier = Modifier.weight(1f)
   ) {
    Text("1. Check", style = MaterialTheme.typography.labelLarge)
   }

   Button(
    onClick = onInitialize,
    modifier = Modifier.weight(1f)
   ) {
    Text("2. Initialize", style = MaterialTheme.typography.labelLarge)
   }
  }

  // 2行目: Create & List
  Row(
   modifier = Modifier.fillMaxWidth(),
   horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
   Button(
    onClick = onCreateContainer,
    modifier = Modifier.weight(1f)
   ) {
    Text("3. Create", style = MaterialTheme.typography.labelLarge)
   }

   Button(
    onClick = onListContainers,
    modifier = Modifier.weight(1f)
   ) {
    Text("4. List", style = MaterialTheme.typography.labelLarge)
   }
  }
 }
}

@Composable
private fun TestingProgressCard(message: String) {
 Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(
   containerColor = MaterialTheme.colorScheme.primaryContainer
  ),
  shape = MaterialTheme.shapes.large,
  elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
 ) {
  Column(
   modifier = Modifier
    .fillMaxWidth()
    .padding(20.dp),
   horizontalAlignment = Alignment.CenterHorizontally,
   verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
   LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
   Text(
    text = message,
    style = MaterialTheme.typography.bodyMedium,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.onPrimaryContainer
   )
  }
 }
}

@Composable
private fun TestResultCard(
 title: String,
 message: String,
 isSuccess: Boolean
) {
 Card(
  modifier = Modifier.fillMaxWidth(),
  colors = CardDefaults.cardColors(
   containerColor = if (isSuccess)
    MaterialTheme.colorScheme.primaryContainer
   else
    MaterialTheme.colorScheme.errorContainer
  ),
  shape = MaterialTheme.shapes.large,
  elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
 ) {
  Column(
   modifier = Modifier
    .fillMaxWidth()
    .padding(20.dp),
   verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
   Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    color = if (isSuccess)
     MaterialTheme.colorScheme.onPrimaryContainer
    else
     MaterialTheme.colorScheme.onErrorContainer
   )
   Text(
    text = message,
    style = MaterialTheme.typography.bodyMedium,
    color = if (isSuccess)
     MaterialTheme.colorScheme.onPrimaryContainer
    else
     MaterialTheme.colorScheme.onErrorContainer
   )
  }
 }
}
