@file:JvmMultifileClass
@file:JvmName("SelectionComponentsKt")

package org.jetbrains.compose.swing.components.selection

import javax.swing.table.TableColumnModel

/**
 * The layout of a [Table]'s columns: which of the model's columns each view position shows, and how wide
 * each of those positions wants to be.
 *
 * [modelIndices] holds the model index of every view column, left to right, so it describes the order the
 * columns are in - reordering columns permutes it while the model behind them stays as it is.
 * [preferredWidths] holds the matching width of each view column, in the same order, so the two lists have
 * one entry each per view column. The width is the *preferred* width because that is the one a resize
 * leaves behind: a table spreads the space it has across its columns in proportion to their preferred
 * widths at every layout pass, so an exact width lasts only until the next one.
 *
 * @param modelIndices the model index of each view column, left to right
 * @param preferredWidths the preferred width in pixels of each view column, left to right
 * @throws IllegalArgumentException if the two lists are of different sizes
 */
public class TableColumnLayout(
    public val modelIndices: List<Int>,
    public val preferredWidths: List<Int>,
) {
    init {
        require(modelIndices.size == preferredWidths.size) {
            "A column layout needs one preferred width per column, but ${modelIndices.size} " +
                "model indices came with ${preferredWidths.size} widths"
        }
    }

    // A layout is compared by the two lists it names, and equals, hashCode and toString say so by hand
    // rather than by making this a data class, so that the properties above stay the whole of what the
    // type publishes. A data class would publish copy() and componentN() too, and both are welded to this
    // exact constructor: naming a further column property later would change copy()'s signature, and
    // giving the two lists another order would silently change what a destructuring declaration binds.
    // Adding a member to the class as it stands does neither.
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is TableColumnLayout &&
                    modelIndices == other.modelIndices &&
                    preferredWidths == other.preferredWidths
            )

    override fun hashCode(): Int = 31 * modelIndices.hashCode() + preferredWidths.hashCode()

    override fun toString(): String = "TableColumnLayout(modelIndices=$modelIndices, preferredWidths=$preferredWidths)"
}

/** The layout the columns of [this] model are currently in. */
internal fun TableColumnModel.readColumnLayout(): TableColumnLayout {
    val modelIndices = ArrayList<Int>(columnCount)
    val preferredWidths = ArrayList<Int>(columnCount)
    for (position in 0 until columnCount) {
        val column = getColumn(position)
        modelIndices += column.modelIndex
        preferredWidths += column.preferredWidth
    }
    return TableColumnLayout(modelIndices, preferredWidths)
}

/**
 * Puts the columns of [this] model into [layout], leaving a column the layout does not name where it is
 * and at the width it is. A `null` layout leaves the columns alone entirely, and a layout the columns are
 * already in is applied as no write at all, so a pass that changed nothing publishes no column event.
 */
internal fun TableColumnModel.applyColumnLayout(layout: TableColumnLayout?) {
    if (layout == null) return
    // Selection sort by model index: each named column is moved to the next free position from the left,
    // so the named columns end up in the layout's order and the rest keep their relative order behind
    // them. A column the model no longer holds is skipped, which is what makes a layout outlive a
    // structure change that dropped one of its columns.
    var position = 0
    for (index in layout.modelIndices.indices) {
        val modelIndex = layout.modelIndices[index]
        val current = (position until columnCount).firstOrNull { getColumn(it).modelIndex == modelIndex }
        if (current == null) continue
        if (current != position) moveColumn(current, position)
        getColumn(position).preferredWidth = layout.preferredWidths[index]
        position++
    }
}

/**
 * Whether [this] layout still holds all of [retained]: every column [retained] names, in that order, at
 * that width. Columns [retained] does not name are ignored, so a layout that gained a column still holds
 * the one it had.
 */
internal fun TableColumnLayout.holds(retained: TableColumnLayout): Boolean {
    val wanted = retained.modelIndices.toSet()
    val keptIndices = ArrayList<Int>(wanted.size)
    val keptWidths = ArrayList<Int>(wanted.size)
    for (position in modelIndices.indices) {
        if (modelIndices[position] !in wanted) continue
        keptIndices += modelIndices[position]
        keptWidths += preferredWidths[position]
    }
    return keptIndices == retained.modelIndices && keptWidths == retained.preferredWidths
}
