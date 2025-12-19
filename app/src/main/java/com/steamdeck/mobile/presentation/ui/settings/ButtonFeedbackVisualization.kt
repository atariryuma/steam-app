package com.steamdeck.mobile.presentation.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.steamdeck.mobile.core.input.ControllerState
import com.steamdeck.mobile.domain.model.GameAction
import kotlin.math.min

/**
 * リアルタイムボタンフィードバック可視化コンポーネント
 *
 * Research findings:
 * - ベストプラクティス: Material3デザインシステム 準拠
 * - アクセシビリティ: contentDescription + stateDescription対応
 * - パフォーマンス: derivedStateOf不要（StateFlow 最適化済み）
 * - タッチターゲット: 最小48dp x 48dp (Androidアクセシビリティガイドライン)
 */

/**
 * ボタン押下状態 視覚的フィードバック（グリッドレイアウト）
 */
@Composable
fun ButtonFeedbackGrid(
 controllerState: ControllerState?,
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
    "ボタンTest",
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.Bold
   )

   if (controllerState == null) {
    Text(
     "controller選択please",
     style = MaterialTheme.typography.bodySmall,
     color = MaterialTheme.colorScheme.onSurfaceVariant
    )
   } else {
    // Face buttons (A/B/X/Y)
    Row(
     modifier = Modifier.fillMaxWidth(),
     horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
     ButtonIndicatorChip("A", controllerState.buttonA, Modifier.weight(1f))
     ButtonIndicatorChip("B", controllerState.buttonB, Modifier.weight(1f))
     ButtonIndicatorChip("X", controllerState.buttonX, Modifier.weight(1f))
     ButtonIndicatorChip("Y", controllerState.buttonY, Modifier.weight(1f))
    }

    // D-Pad
    Row(
     modifier = Modifier.fillMaxWidth(),
     horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
     ButtonIndicatorChip("↑", controllerState.dpadUp, Modifier.weight(1f))
     ButtonIndicatorChip("↓", controllerState.dpadDown, Modifier.weight(1f))
     ButtonIndicatorChip("←", controllerState.dpadLeft, Modifier.weight(1f))
     ButtonIndicatorChip("→", controllerState.dpadRight, Modifier.weight(1f))
    }

    // Shoulder buttons
    Row(
     modifier = Modifier.fillMaxWidth(),
     horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
     ButtonIndicatorChip("L1", controllerState.buttonL1, Modifier.weight(1f))
     ButtonIndicatorChip("R1", controllerState.buttonR1, Modifier.weight(1f))
     ButtonIndicatorChip("L2", controllerState.buttonL2, Modifier.weight(1f))
     ButtonIndicatorChip("R2", controllerState.buttonR2, Modifier.weight(1f))
    }

    // Thumb + System buttons
    Row(
     modifier = Modifier.fillMaxWidth(),
     horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
     ButtonIndicatorChip("L3", controllerState.buttonThumbL, Modifier.weight(1f))
     ButtonIndicatorChip("R3", controllerState.buttonThumbR, Modifier.weight(1f))
     ButtonIndicatorChip("START", controllerState.buttonStart, Modifier.weight(1f))
     ButtonIndicatorChip("SELECT", controllerState.buttonSelect, Modifier.weight(1f))
    }
   }
  }
 }
}

/**
 * 個別ボタンインジケーターチップ
 *
 * アクセシビリティベストプラクティス:
 * - 48dp 最小タッチターゲット
 * - contentDescription + stateDescription
 * - 高コントラスト対応
 */
@Composable
private fun ButtonIndicatorChip(
 label: String,
 isPressed: Boolean,
 modifier: Modifier = Modifier
) {
 Surface(
  modifier = modifier
   .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
   .semantics {
    contentDescription = "$label button"
    stateDescription = if (isPressed) "Pressed" else "Released"
   },
  color = if (isPressed) {
   MaterialTheme.colorScheme.primary
  } else {
   MaterialTheme.colorScheme.surface
  },
  shape = RoundedCornerShape(8.dp),
  border = BorderStroke(
   width = 1.dp,
   color = if (isPressed) {
    MaterialTheme.colorScheme.primary
   } else {
    MaterialTheme.colorScheme.outline
   }
  )
 ) {
  Box(
   modifier = Modifier.fillMaxSize(),
   contentAlignment = Alignment.Center
  ) {
   Text(
    text = label,
    style = MaterialTheme.typography.labelSmall,
    fontWeight = if (isPressed) FontWeight.Bold else FontWeight.Normal,
    color = if (isPressed) {
     MaterialTheme.colorScheme.onPrimary
    } else {
     MaterialTheme.colorScheme.onSurface
    }
   )
  }
 }
}

/**
 * 2Dジョイスティック位置可視化（Canvasベース）
 *
 * Research findings:
 * - Canvas API customグラフィックス
 * - Deadzone表示（通常0.1 = 10%）
 * - スティック位置 リアルタイムUpdate
 */
@Composable
fun JoystickVisualization(
 leftX: Float,
 leftY: Float,
 rightX: Float,
 rightY: Float,
 deadzone: Float = 0.1f,
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
   verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
   Text(
    "ジョイスティック可視化",
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.Bold
   )

   Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(16.dp)
   ) {
    // Left Stick
    Column(
     modifier = Modifier.weight(1f),
     horizontalAlignment = Alignment.CenterHorizontally
    ) {
     Text(
      "Left Stick",
      style = MaterialTheme.typography.labelMedium
     )
     Spacer(modifier = Modifier.height(8.dp))
     JoystickCanvas(
      x = leftX,
      y = leftY,
      deadzone = deadzone,
      modifier = Modifier.size(150.dp)
     )
     Spacer(modifier = Modifier.height(4.dp))
     Text(
      "X: ${String.format("%.2f", leftX)} | Y: ${String.format("%.2f", leftY)}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
     )
    }

    // Right Stick
    Column(
     modifier = Modifier.weight(1f),
     horizontalAlignment = Alignment.CenterHorizontally
    ) {
     Text(
      "Right Stick",
      style = MaterialTheme.typography.labelMedium
     )
     Spacer(modifier = Modifier.height(8.dp))
     JoystickCanvas(
      x = rightX,
      y = rightY,
      deadzone = deadzone,
      modifier = Modifier.size(150.dp)
     )
     Spacer(modifier = Modifier.height(4.dp))
     Text(
      "X: ${String.format("%.2f", rightX)} | Y: ${String.format("%.2f", rightY)}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
     )
    }
   }
  }
 }
}

/**
 * ジョイスティック Canvas 描画
 */
@Composable
private fun JoystickCanvas(
 x: Float,
 y: Float,
 deadzone: Float,
 modifier: Modifier = Modifier
) {
 val primaryColor = MaterialTheme.colorScheme.primary
 val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
 val errorColor = MaterialTheme.colorScheme.error

 Canvas(modifier = modifier) {
  val center = Offset(size.width / 2, size.height / 2)
  val radius = min(size.width, size.height) / 2

  // 外側 円（range）
  drawCircle(
   color = onSurfaceVariant.copy(alpha = 0.3f),
   radius = radius,
   center = center,
   style = Stroke(width = 2.dp.toPx())
  )

  // Deadzone円
  drawCircle(
   color = errorColor.copy(alpha = 0.2f),
   radius = radius * deadzone,
   center = center
  )

  // 十字線
  drawLine(
   color = onSurfaceVariant.copy(alpha = 0.5f),
   start = Offset(center.x, 0f),
   end = Offset(center.x, size.height),
   strokeWidth = 1.dp.toPx()
  )
  drawLine(
   color = onSurfaceVariant.copy(alpha = 0.5f),
   start = Offset(0f, center.y),
   end = Offset(size.width, center.y),
   strokeWidth = 1.dp.toPx()
  )

  // スティック位置
  val stickX = x * radius
  val stickY = y * radius
  drawCircle(
   color = primaryColor,
   radius = 12.dp.toPx(),
   center = center + Offset(stickX, stickY)
  )

  // スティック from in心to 線
  drawLine(
   color = primaryColor.copy(alpha = 0.5f),
   start = center,
   end = center + Offset(stickX, stickY),
   strokeWidth = 2.dp.toPx()
  )
 }
}

/**
 * Trigger圧力バー（アナログL2/R2）
 */
@Composable
fun TriggerPressureBar(
 leftTrigger: Float,
 rightTrigger: Float,
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
    "Trigger圧力",
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.Bold
   )

   // L2Trigger
   TriggerIndicator(
    label = "L2",
    value = leftTrigger,
    modifier = Modifier.fillMaxWidth()
   )

   // R2Trigger
   TriggerIndicator(
    label = "R2",
    value = rightTrigger,
    modifier = Modifier.fillMaxWidth()
   )
  }
 }
}

@Composable
private fun TriggerIndicator(
 label: String,
 value: Float,
 modifier: Modifier = Modifier
) {
 Column(modifier = modifier) {
  Row(
   modifier = Modifier.fillMaxWidth(),
   horizontalArrangement = Arrangement.SpaceBetween
  ) {
   Text(
    text = label,
    style = MaterialTheme.typography.labelMedium
   )
   Text(
    text = "${(value * 100).toInt()}%",
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary
   )
  }
  Spacer(modifier = Modifier.height(4.dp))
  LinearProgressIndicator(
   progress = { value },
   modifier = Modifier.fillMaxWidth()
  )
 }
}
