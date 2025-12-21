package com.steamdeck.mobile.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Steam brand colors for themed UI components.
 *
 * Official Steam color palette:
 * - Used in Settings, Auth, and Steam-related screens
 * - Based on Steam's official design guidelines
 *
 * References:
 * - Steam community: https://steamcommunity.com
 * - Steam Store: https://store.steampowered.com
 */
object SteamColorPalette {
    /** Steam navbar background (#171A21) */
    val Navbar = Color(0xFF171A21)

    /** Steam dark background (#1B2838) - Primary container color */
    val Dark = Color(0xFF1B2838)

    /** Steam darker shade (#0D1217) - Gradient end color */
    val DarkerShade = Color(0xFF0D1217)

    /** Steam medium accent (#2A475E) */
    val Medium = Color(0xFF2A475E)

    /** Steam primary blue (#66C0F4) - Brand color for CTAs */
    val Blue = Color(0xFF66C0F4)

    /** Steam bright blue (#47BFFF) - Gradient start */
    val BrightBlue = Color(0xFF47BFFF)

    /** Steam deep blue (#1A9FFF) - Gradient end */
    val DeepBlue = Color(0xFF1A9FFF)

    /** Steam light text (#C7D5E0) */
    val LightText = Color(0xFFC7D5E0)

    /** Steam gray text (#8F98A0) - Secondary text */
    val Gray = Color(0xFF8F98A0)

    /** Steam success green (#5BA82E) - Online status, success states */
    val Green = Color(0xFF5BA82E)
}
