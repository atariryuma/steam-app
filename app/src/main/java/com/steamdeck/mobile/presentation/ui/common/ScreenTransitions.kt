package com.steamdeck.mobile.presentation.ui.common

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

/**
 * Common animation transitions used across screens.
 * Centralizes animation specs for consistency and easy modification.
 */
object ScreenTransitions {
    /**
     * Standard fade transition (300ms).
     * Used for content state changes (loading → success, empty → content, etc.)
     */
    val standardFade: AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
    }

    /**
     * Fast fade transition (150ms).
     * Used for quick UI updates.
     */
    val fastFade: AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
    }

    /**
     * Slow fade transition (500ms).
     * Used for important state changes that need emphasis.
     */
    val slowFade: AnimatedContentTransitionScope<*>.() -> ContentTransform = {
        fadeIn(tween(500)) togetherWith fadeOut(tween(500))
    }
}

/**
 * Extension property for Boolean-based AnimatedContent states.
 * Provides type-safe access to standard fade transition.
 *
 * Usage:
 * ```
 * AnimatedContent(
 *     targetState = isEmpty,
 *     transitionSpec = ScreenTransitions.standardFade,
 *     label = "ContentTransition"
 * )
 * ```
 */
fun standardFadeTransition() = ScreenTransitions.standardFade
