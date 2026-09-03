package com.hakim.liquify

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the UI the glass sits in is dark, or `null` to follow the system setting.
 *
 * Only one thing about glass genuinely has to know: its **shadow**. Everything else the material
 * does — refraction, the specular rim, vibrancy — behaves the same either way, because it is
 * derived from the backdrop rather than painted on top of it. A drop shadow is not: black ink is
 * invisible on a dark background, and an element that was lifted off the page in one theme sits
 * flat on it in the other.
 *
 * `null` — the default — means "ask the system", which is right for an app whose theme follows it.
 * Set it with [ProvideGlassDarkTheme] when the app has its own switch, or when a stretch of the UI
 * is dark while the rest of it is not: a light app with one dark sheet wants
 * `ProvideGlassDarkTheme(true)` around that sheet's contents.
 */
public val LocalGlassDarkTheme: ProvidableCompositionLocal<Boolean?> = compositionLocalOf { null }

/**
 * Tells the glass in [content] which way its shadows should be cast, regardless of the system
 * setting.
 *
 * ```
 * ProvideGlassDarkTheme(appTheme.isDark) {
 *     App()
 * }
 * ```
 */
@Composable
public fun ProvideGlassDarkTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlassDarkTheme provides darkTheme, content = content)
}

/**
 * The theme the glass in this position resolves its shadow colours against: [LocalGlassDarkTheme]
 * when something has provided one, and the system setting otherwise.
 */
@Composable
public fun isGlassDarkTheme(): Boolean = LocalGlassDarkTheme.current ?: isSystemInDarkTheme()
