package com.hakim.liquify.catalog.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The catalog's own palette, light or dark — the system's choice unless the Looks & motion screen
 * says otherwise.
 *
 * Deliberately *not* a material theme: the glass itself is described by a
 * [GlassMaterial][com.hakim.liquify.GlassMaterial], and these are the ordinary Compose
 * colours around it — text, glyphs, tracks, dividers. Keeping the two apart is what lets the same
 * glass recipe sit in either mode.
 */
@Immutable
data class CatalogColors(
    val isLight: Boolean,
    /** Primary text and glyphs. */
    val content: Color,
    /** Body copy and captions. */
    val secondaryContent: Color,
    /** Painted behind the wallpaper, and visible wherever it does not reach. */
    val background: Color,
    val divider: Color,
    /** Sliders, the tab indicator's tint, selected segments, the back button. */
    val accent: Color,
    /** Destructive actions, and the error of a reset that waits for its second tap. */
    val danger: Color,
    /** Unfilled slider track and toggle track. */
    val track: Color,
    /** Toggle track once it is on. */
    val toggleOn: Color,
    /**
     * Tint blended into the glass itself under the Liquid look.
     *
     * This is the one colour that reaches the material, and it is what makes a card read as a
     * *light* pane in light mode rather than as a dark one with light text swapped out.
     */
    val glassTint: Color,
    /** The same under the Frosted look, a little denser: a blur alone does not say where a pane ends. */
    val frostedTint: Color,
    /**
     * And under the Solid look, where nothing is sampled and the tint *is* the pane — near-opaque, or
     * the page would show straight through it.
     */
    val solidTint: Color,
    /**
     * Fill of a plain, *non-glass* surface.
     *
     * Glass stacked on glass reads as one backdrop bleeding through another, so anything that
     * holds glass controls — and any text that would otherwise sit bare on the wallpaper — gets
     * this instead. Green because it belongs to the same scene as the wallpaper; near-opaque
     * because its whole job is to guarantee contrast.
     */
    val materialSurface: Color,
    /** The fill of the large panes laid over a page: panels and the open dropdown menu. */
    val sheetTint: Color,
    /** What dims the page behind an open panel. */
    val scrim: Color,
    /** Opaque wash on a resting slider thumb or toggle knob. */
    val knob: Color,
    /** Fill behind the tab bar. */
    val tabContainer: Color,
    /** Wash on the resting tab indicator. */
    val tabIndicator: Color,
    val controlShadow: Color,
    /** Member colours for the merge demos — distinct enough to see them mix in a bridge. */
    val memberColors: List<Color>,
)

val DarkCatalogColors: CatalogColors = CatalogColors(
    isLight = false,
    content = Color.White,
    secondaryContent = Color.White.copy(alpha = 0.78f),
    background = Color(0xFF0B0F16),
    divider = Color.White.copy(alpha = 0.12f),
    accent = Color(0xFF0091FF),
    danger = Color(0xFFFF5A52),
    track = Color(0xFF787880).copy(alpha = 0.36f),
    toggleOn = Color(0xFF3066D1),
    glassTint = Color.White.copy(alpha = 0.05f),
    frostedTint = Color(0xFF141B23).copy(alpha = 0.55f),
    solidTint = Color(0xFF1B232D).copy(alpha = 0.96f),
    materialSurface = Color(0xFF161D24).copy(alpha = 0.94f),
    sheetTint = Color(0xFF121212).copy(alpha = 0.72f),
    scrim = Color.Black.copy(alpha = 0.6f),
    knob = Color.White,
    tabContainer = Color(0xFF121212).copy(alpha = 0.4f),
    tabIndicator = Color.White.copy(alpha = 0.1f),
    controlShadow = Color.Black.copy(alpha = 0.05f),
    memberColors = listOf(
        Color(0xFFFF453A),
        Color(0xFF0A84FF),
        Color(0xFF30D158),
        Color(0xFFFFD60A),
    ),
)

val LightCatalogColors: CatalogColors = CatalogColors(
    isLight = true,
    content = Color(0xFF0B0F16),
    secondaryContent = Color(0xFF0B0F16).copy(alpha = 0.72f),
    background = Color(0xFFEDF1F5),
    divider = Color.Black.copy(alpha = 0.12f),
    accent = Color(0xFF0A7CFF),
    danger = Color(0xFFE5352B),
    track = Color(0xFF787880).copy(alpha = 0.28f),
    toggleOn = Color(0xFF346FC7),
    // Far stronger than its dark counterpart on purpose: the wallpaper behind the glass is the same
    // dark photograph in both modes, so a light pane has to be made light here rather than inherited.
    glassTint = Color.White.copy(alpha = 0.42f),
    frostedTint = Color.White.copy(alpha = 0.6f),
    solidTint = Color(0xFFF3F6FA).copy(alpha = 0.96f),
    materialSurface = Color(0xFFBDC9E7).copy(alpha = 0.94f),
    sheetTint = Color.White.copy(alpha = 0.55f),
    scrim = Color.Black.copy(alpha = 0.3f),
    knob = Color.White,
    tabContainer = Color(0xFFFAFAFA).copy(alpha = 0.4f),
    tabIndicator = Color.Black.copy(alpha = 0.1f),
    controlShadow = Color.Black.copy(alpha = 0.05f),
    memberColors = listOf(
        Color(0xFFFF3B30),
        Color(0xFF007AFF),
        Color(0xFF34C759),
        Color(0xFFFFCC00),
    ),
)

val LocalCatalogColors = staticCompositionLocalOf { DarkCatalogColors }

/** Light or dark: whatever the phone is set to, or one of the two regardless of it. */
enum class ThemeMode { System, Light, Dark }

/** Whether this mode is dark right now — the phone's setting decides [ThemeMode.System]. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/** Provides [CatalogColors] for the subtree, following the system setting unless [dark] says so. */
@Composable
fun CatalogTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalCatalogColors provides if (dark) DarkCatalogColors else LightCatalogColors,
        content = content
    )
}
