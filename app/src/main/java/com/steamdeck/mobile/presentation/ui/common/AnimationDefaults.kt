package com.steamdeck.mobile.presentation.ui.common

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * Standard animation specifications for consistent UI behavior across dialogs.
 *
 * Material3 Design compliance:
 * - Enter: 150ms fade + scale (0.9f → 1.0f) with FastOutSlowInEasing
 * - Exit: 100ms fade + scale (1.0f → 0.9f)
 */
object AnimationDefaults {
    /**
     * Default enter transition for dialogs (fade in + scale in).
     * Duration: 150ms with FastOutSlowInEasing for smooth appearance.
     */
    val DialogEnter: EnterTransition = fadeIn(
        animationSpec = tween(150)
    ) + scaleIn(
        initialScale = 0.9f,
        animationSpec = tween(150, easing = FastOutSlowInEasing)
    )

    /**
     * Default exit transition for dialogs (fade out + scale out).
     * Duration: 100ms for quick dismissal.
     */
    val DialogExit: ExitTransition = fadeOut(
        animationSpec = tween(100)
    ) + scaleOut(
        targetScale = 0.9f,
        animationSpec = tween(100)
    )
}
