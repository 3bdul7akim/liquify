/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant
 *
 * Modified by the Liquify authors.
 */

package com.hakim.liquify.internal

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path

/**
 * Clips [outline] into the canvas. [path] is a scratch path reused across frames and is only
 * required for [Outline.Rounded]; pass `null` otherwise.
 */
internal fun Canvas.clipOutline(outline: Outline, path: Path?) {
    when (outline) {
        is Outline.Rectangle -> clipRect(outline.rect)

        is Outline.Rounded -> {
            val scratch = path ?: Path()
            scratch.rewind()
            scratch.addRoundRect(outline.roundRect)
            clipPath(scratch)
        }

        is Outline.Generic -> clipPath(outline.path)
    }
}
