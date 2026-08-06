/*
 * Derived from Kyant0's AndroidLiquidGlass (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass
 * Copyright 2025 Kyant
 */

package com.hakim.liquify.interaction

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastFirstOrNull

/**
 * Drag detection that reports from the very first touch instead of after a slop threshold, and
 * without consuming anything.
 *
 * `detectDragGestures` only starts reporting once the finger has travelled far enough to be
 * unambiguously a drag, and it consumes the events. Liquid glass has to react to the *touch*, not
 * to the gesture being classified — the material flexes the instant you land on it, and a
 * `clickable` sitting alongside must still see the tap. Hence this variant: it fires `onDrag` once
 * with [Offset.Zero] on the down event and bows out the moment anyone else consumes the change.
 */
public suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    /**
     * Whether another gesture claiming the movement ends this one.
     *
     * `true` is right for anything that acts on the drag — it must step aside once a scroller or a
     * sibling control has taken it. Pass `false` for a purely observational listener, such as an
     * illumination that only needs to know where the finger is: those should keep following even
     * though the control they decorate is the one consuming the events.
     */
    stopOnConsumed: Boolean = true,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val down = awaitFirstDown(requireUnconsumed = false)

        onDragStart(down)
        onDrag(initialDown, Offset.Zero)

        val upEvent = drag(initialDown.id, stopOnConsumed) { onDrag(it, it.positionChange()) }
        if (upEvent == null) onDragCancel() else onDragEnd(upEvent)
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    stopOnConsumed: Boolean,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    if (currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true) return null

    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (stopOnConsumed && change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            // Another finger is still down: hand the gesture over to it rather than ending.
            val otherDown = event.changes.fastFirstOrNull { it.pressed } ?: return dragEvent
            pointer = otherDown.id
        } else if (dragEvent.previousPosition != dragEvent.position) {
            return dragEvent
        }
    }
}
