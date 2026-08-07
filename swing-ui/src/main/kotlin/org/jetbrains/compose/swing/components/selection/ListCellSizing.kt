package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.modifier.SwingModifier
import javax.swing.JList

/**
 * Folds the sizing of a list's cells into the chain: [prototype] is an item measured once to size every
 * cell, and [width] and [height] state a size in pixels outright, `-1` stating none.
 *
 * It goes into the chain behind the renderer a prototype is measured through, since the chain applies its
 * elements in the order they are declared. Removing the element puts the list back on the sizing it had
 * before one was declared - captured as the element attaches, written back as it detaches - so a released,
 * recycled or parked list measures its rows the way a list that was never given a prototype does.
 */
internal fun SwingModifier.listCellSizing(
    prototype: Any?,
    width: Int,
    height: Int,
): SwingModifier = this then CellSizingElement(CellSizing(prototype, width, height))

/**
 * The three declarations that together decide how large a list's cells are. They travel as one value
 * because they are applied in an order - the prototype's measurement first, a stated size over it - that
 * only holds when a change to any one of them re-applies all three.
 */
private data class CellSizing(
    val prototype: Any?,
    val width: Int,
    val height: Int,
)

/**
 * The [SwingModifier.NodeElement] behind [listCellSizing]. Two declaring the same three values are equal,
 * so a pass that sizes the list the way the last one did leaves the slot as it stands.
 */
private data class CellSizingElement(
    private val sizing: CellSizing,
) : SwingModifier.NodeElement<JList<Any?>, CellSizingNode>() {
    override val targetType: Class<JList<Any?>> get() = listType()

    override fun create(): CellSizingNode = CellSizingNode()

    override fun update(node: CellSizingNode) {
        node.apply(sizing)
    }
}

/**
 * The [SwingModifier.Node] behind [listCellSizing]. It sizes the list as its element declares, and puts
 * back the sizing the list had before the chain reached it.
 *
 * What it puts back is read off the list once, on attach, rather than tracked across writes: a dimension
 * no declaration states is left at whatever the prototype measured, and the list is the only thing that
 * knows what that came out as.
 */
private class CellSizingNode : SwingModifier.Node<JList<Any?>>() {
    private var restore: CellSizing? = null

    override fun onAttach() {
        restore = component.readCellSizing()
    }

    /**
     * Sizes the list as [sizing] declares; call from the owning element's `update`.
     *
     * There is no guard against re-sizing the list the way it is already sized, because the chain never
     * asks: [CellSizingElement] compares by value, so a slot holding an equal element skips this call.
     */
    fun apply(sizing: CellSizing): Unit = component.applyCellSizing(sizing)

    override fun onDetach() {
        restore?.let { component.applyCellSizing(it) }
        restore = null
    }
}

/** The sizing this list currently holds, as a declaration that would leave it holding the same. */
private fun JList<Any?>.readCellSizing(): CellSizing = CellSizing(prototypeCellValue, fixedCellWidth, fixedCellHeight)

/**
 * A list measures a prototype only for a value it is not already holding, so the prototype is given up
 * before it is declared again: measuring afresh is what gives a dimension its measured size back once the
 * caller withdraws the pixel size that had displaced it.
 *
 * With no prototype declared, `-1` has to be written through as well, so a withdrawn pixel size returns
 * the dimension to what the renderer reports for each row. Narrowing the guard to the stated-size case
 * alone would leave the list holding the withdrawn width.
 */
private fun JList<Any?>.applyCellSizing(sizing: CellSizing) {
    prototypeCellValue = null
    prototypeCellValue = sizing.prototype
    if (sizing.prototype == null || sizing.width != -1) fixedCellWidth = sizing.width
    if (sizing.prototype == null || sizing.height != -1) fixedCellHeight = sizing.height
}

/**
 * The class a modifier element names as the target of a list property, derived from the reified [T] so no
 * cast stands between the element and the accessors it writes through. A list's items are its own, and
 * erasure makes every `JList` a list of whatever the element declares its values over.
 */
private inline fun <reified T : JList<*>> listType(): Class<T> = T::class.java
