package com.hakim.liquify.catalog.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import com.hakim.liquify.catalog.ui.CatalogText
import com.hakim.liquify.catalog.ui.LabelStyle
import com.hakim.liquify.liquify
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay

/** How long a message stays up before it leaves on its own. */
private const val SnackbarMillis = 2600L

/** What [LiquidSnackbarHost] shows. A new message replaces the one on screen and restarts its time. */
@Stable
class LiquidSnackbarState {
    internal var message by mutableStateOf("")
        private set
    internal var visible by mutableStateOf(false)
    internal var serial by mutableIntStateOf(0)
        private set

    fun show(message: String) {
        this.message = message
        visible = true
        serial++
    }
}

@Composable
fun rememberLiquidSnackbarState(): LiquidSnackbarState = remember { LiquidSnackbarState() }

/**
 * A pill of floating chrome for short confirmations. It grows out of its own leading corner — the way
 * it is anchored — and keeps its text through the exit fade, so it never blanks out mid-animation.
 *
 * Place it where it should appear; it takes no room while hidden.
 */
@Composable
fun LiquidSnackbarHost(state: LiquidSnackbarState, modifier: Modifier = Modifier) {
    LaunchedEffect(state.serial) {
        if (state.visible) {
            delay(SnackbarMillis)
            state.visible = false
        }
    }

    AnimatedVisibility(
        visible = state.visible,
        enter = fadeIn() + scaleIn(initialScale = 0.6f, transformOrigin = TransformOrigin(0f, 1f)),
        exit = fadeOut() + scaleOut(targetScale = 0.6f, transformOrigin = TransformOrigin(0f, 1f)),
        modifier = modifier,
    ) {
        Row(
            Modifier
                // The same floating chrome as the tab bar, so it lifts off the page the same way.
                .liquify(shape = Capsule(), tint = barTint(), material = barMaterial())
                .padding(horizontal = 20f.dp, vertical = 14f.dp),
        ) {
            CatalogText(state.message, LabelStyle)
        }
    }
}
