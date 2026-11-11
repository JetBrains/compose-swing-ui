// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import javax.swing.JList

/**
 * Folds the sizing of a list's cells into the chain: [prototype] is an item measured once to size every
 * cell, and [width] and [height] state a size outright, `-1` stating none.
 *
 * A list measures its prototype again through every renderer it is given, which overwrites a stated size,
 * so the declared sizing is written again on each `cellRenderer` change, wherever the renderer is declared.
 * Removing the element puts the list back on the sizing it had before one was declared - captured as the
 * element attaches, written back as it detaches - so a released or parked list measures its rows the way a
 * list that was never given a prototype does.
 */
internal fun SwingModifier.listCellSizing(
    prototype: Any?,
    width: Int,
    height: Int,
): SwingModifier = property(CellSizingProperty, CellSizing(prototype, width, height), rewriteOn = "cellRenderer")

/**
 * The three declarations that together decide how large a list's cells are. They travel as one value
 * because they are applied in an order - the prototype's measurement first, a stated size over it - that
 * only holds when a change to any one of them re-applies all three.
 *
 * What a removal puts back is read off the list once, on attach, rather than tracked across writes: a
 * dimension no declaration states is left at whatever the prototype measured, and the list is the only
 * thing that knows what that came out as.
 */
private data class CellSizing(
    val prototype: Any?,
    val width: Int,
    val height: Int,
)

private val CellSizingProperty =
    ComponentPropertyDescriptor<JList<Any?>, CellSizing>(
        name = "listCellSizing",
        read = { it.readCellSizing() },
        write = { list, sizing -> list.applyCellSizing(sizing) },
    )

/** The sizing this list currently holds, as a declaration that would leave it holding the same. */
private fun JList<Any?>.readCellSizing(): CellSizing = CellSizing(prototypeCellValue, fixedCellWidth, fixedCellHeight)

/**
 * A list measures a prototype only for a value it is not already holding, so the prototype is given up
 * before it is declared again: measuring afresh is what gives a dimension its measured size back once the
 * caller withdraws the fixed size that had displaced it.
 *
 * With no prototype declared, `-1` has to be written through as well, so a withdrawn fixed size returns
 * the dimension to what the renderer reports for each row. Narrowing the guard to the stated-size case
 * alone would leave the list holding the withdrawn width.
 */
private fun JList<Any?>.applyCellSizing(sizing: CellSizing) {
    prototypeCellValue = null
    prototypeCellValue = sizing.prototype
    if (sizing.prototype == null || sizing.width != -1) fixedCellWidth = sizing.width
    if (sizing.prototype == null || sizing.height != -1) fixedCellHeight = sizing.height
}
