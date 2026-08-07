package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import java.awt.ComponentOrientation
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How a [Row] or [Column] places its children along the axis it arranges them on, and how much space it
 * leaves between two adjacent children.
 *
 * An arrangement is handed the extent the container has along that axis together with the extent of each
 * child, and answers with each child's position. The children keep the extent they asked for, so what an
 * arrangement decides is where the space left over goes: [Start], [End], [Top], [Bottom] and [Center]
 * keep the children together and put all of it on one side or split it evenly; [SpaceBetween],
 * [SpaceAround] and [SpaceEvenly] share it out between them; [spacedBy] holds a fixed gap between
 * adjacent children and places the group as a whole.
 *
 * `spacing` is the gap an arrangement holds between two adjacent children, in pixels. A container
 * reserves it before it measures, so it is part of the size the container asks for.
 */
@Immutable
public object Arrangement {
    /** Places children along a horizontal axis, resolved against the container's `ComponentOrientation`. */
    @Stable
    public interface Horizontal {
        /** The gap held between two adjacent children, in pixels. */
        public val spacing: Int get() = 0

        /**
         * Writes each child's offset from the left of [totalSize] into [outPositions], for children whose
         * widths are [sizes] in declaration order, in a container laid out under [orientation].
         * [outPositions] is as long as [sizes].
         */
        public fun arrange(
            totalSize: Int,
            sizes: IntArray,
            orientation: ComponentOrientation,
            outPositions: IntArray,
        )
    }

    /** Places children along a vertical axis. */
    @Stable
    public interface Vertical {
        /** The gap held between two adjacent children, in pixels. */
        public val spacing: Int get() = 0

        /**
         * Writes each child's offset from the top of [totalSize] into [outPositions], for children whose
         * heights are [sizes] in declaration order. [outPositions] is as long as [sizes].
         */
        public fun arrange(
            totalSize: Int,
            sizes: IntArray,
            outPositions: IntArray,
        )
    }

    /** An arrangement that reads the same on either axis, so it serves a [Row] and a [Column] alike. */
    @Stable
    public interface HorizontalOrVertical :
        Horizontal,
        Vertical {
        override val spacing: Int get() = 0
    }

    /** Packs the children against the leading edge - the left under a left-to-right orientation. */
    public val Start: Horizontal =
        object : Horizontal {
            override fun arrange(
                totalSize: Int,
                sizes: IntArray,
                orientation: ComponentOrientation,
                outPositions: IntArray,
            ) = if (orientation.isLeftToRight) {
                placeLeading(sizes, outPositions, reversed = false)
            } else {
                placeTrailing(totalSize, sizes, outPositions, reversed = true)
            }

            override fun toString(): String = "Arrangement#Start"
        }

    /** Packs the children against the trailing edge - the right under a left-to-right orientation. */
    public val End: Horizontal =
        object : Horizontal {
            override fun arrange(
                totalSize: Int,
                sizes: IntArray,
                orientation: ComponentOrientation,
                outPositions: IntArray,
            ) = if (orientation.isLeftToRight) {
                placeTrailing(totalSize, sizes, outPositions, reversed = false)
            } else {
                placeLeading(sizes, outPositions, reversed = true)
            }

            override fun toString(): String = "Arrangement#End"
        }

    /** Packs the children against the top. */
    public val Top: Vertical =
        object : Vertical {
            override fun arrange(
                totalSize: Int,
                sizes: IntArray,
                outPositions: IntArray,
            ) = placeLeading(sizes, outPositions, reversed = false)

            override fun toString(): String = "Arrangement#Top"
        }

    /** Packs the children against the bottom. */
    public val Bottom: Vertical =
        object : Vertical {
            override fun arrange(
                totalSize: Int,
                sizes: IntArray,
                outPositions: IntArray,
            ) = placeTrailing(totalSize, sizes, outPositions, reversed = false)

            override fun toString(): String = "Arrangement#Bottom"
        }

    /** Keeps the children together and puts half the space left over on either side of the group. */
    public val Center: HorizontalOrVertical =
        object : HorizontalOrVertical {
            override fun arrange(
                totalSize: Int,
                sizes: IntArray,
                orientation: ComponentOrientation,
                outPositions: IntArray,
            ) = placeCenter(totalSize, sizes, outPositions, reversed = !orientation.isLeftToRight)

            override fun arrange(
                totalSize: Int,
                sizes: IntArray,
                outPositions: IntArray,
            ) = placeCenter(totalSize, sizes, outPositions, reversed = false)

            override fun toString(): String = "Arrangement#Center"
        }

    /** Splits the space left over into equal gaps between the children, with none at either edge. */
    public val SpaceBetween: HorizontalOrVertical =
        object : HorizontalOrVertical {
            override fun arrange(
                totalSize: Int,
                sizes: IntArray,
                orientation: ComponentOrientation,
                outPositions: IntArray,
            ) = placeSpaceBetween(totalSize, sizes, outPositions, reversed = !orientation.isLeftToRight)

            override fun arrange(
                totalSize: Int,
                sizes: IntArray,
                outPositions: IntArray,
            ) = placeSpaceBetween(totalSize, sizes, outPositions, reversed = false)

            override fun toString(): String = "Arrangement#SpaceBetween"
        }

    /** Gives every child an equal gap of its own, so the gaps at the edges are half the gaps between. */
    public val SpaceAround: HorizontalOrVertical =
        object : HorizontalOrVertical {
            override fun arrange(
                totalSize: Int,
                sizes: IntArray,
                orientation: ComponentOrientation,
                outPositions: IntArray,
            ) = placeSpaceAround(totalSize, sizes, outPositions, reversed = !orientation.isLeftToRight)

            override fun arrange(
                totalSize: Int,
                sizes: IntArray,
                outPositions: IntArray,
            ) = placeSpaceAround(totalSize, sizes, outPositions, reversed = false)

            override fun toString(): String = "Arrangement#SpaceAround"
        }

    /** Splits the space left over into equal gaps between the children and at both edges. */
    public val SpaceEvenly: HorizontalOrVertical =
        object : HorizontalOrVertical {
            override fun arrange(
                totalSize: Int,
                sizes: IntArray,
                orientation: ComponentOrientation,
                outPositions: IntArray,
            ) = placeSpaceEvenly(totalSize, sizes, outPositions, reversed = !orientation.isLeftToRight)

            override fun arrange(
                totalSize: Int,
                sizes: IntArray,
                outPositions: IntArray,
            ) = placeSpaceEvenly(totalSize, sizes, outPositions, reversed = false)

            override fun toString(): String = "Arrangement#SpaceEvenly"
        }

    /**
     * Holds [space] pixels between two adjacent children and packs the group against the leading edge.
     * A negative [space] overlaps them.
     */
    @Stable
    public fun spacedBy(space: Int): HorizontalOrVertical =
        SpacedAligned(space, mirrored = true, alignment = HorizontalAxisAlignment(Alignment.Start))

    /**
     * Holds [space] pixels between two adjacent children and places the group as a whole at [alignment]
     * along the row. A negative [space] overlaps them.
     */
    @Stable
    public fun spacedBy(
        space: Int,
        alignment: Alignment.Horizontal,
    ): Horizontal = SpacedAligned(space, mirrored = true, alignment = HorizontalAxisAlignment(alignment))

    /**
     * Holds [space] pixels between two adjacent children and places the group as a whole at [alignment]
     * along the column. A negative [space] overlaps them.
     */
    @Stable
    public fun spacedBy(
        space: Int,
        alignment: Alignment.Vertical,
    ): Vertical = SpacedAligned(space, mirrored = false, alignment = VerticalAxisAlignment(alignment))

    /** Keeps the children together and places the group as a whole at [alignment] along the row. */
    @Stable
    public fun aligned(alignment: Alignment.Horizontal): Horizontal =
        SpacedAligned(space = 0, mirrored = true, alignment = HorizontalAxisAlignment(alignment))

    /** Keeps the children together and places the group as a whole at [alignment] along the column. */
    @Stable
    public fun aligned(alignment: Alignment.Vertical): Vertical =
        SpacedAligned(space = 0, mirrored = false, alignment = VerticalAxisAlignment(alignment))
}

/**
 * Holds [space] pixels between adjacent children and places the group at [alignment] in whatever room is
 * left. [mirrored] reverses the packing direction under a right-to-left orientation, which a horizontal
 * arrangement does and a vertical one leaves alone.
 *
 * Both the position of a child and the gap after it are capped at the trailing edge, so a run of children
 * longer than the container stacks up there rather than running past it.
 */
private data class SpacedAligned(
    private val space: Int,
    private val mirrored: Boolean,
    private val alignment: AxisAlignment,
) : Arrangement.HorizontalOrVertical {
    override val spacing: Int get() = space

    override fun arrange(
        totalSize: Int,
        sizes: IntArray,
        orientation: ComponentOrientation,
        outPositions: IntArray,
    ) {
        if (sizes.isEmpty()) return
        val rightToLeft = mirrored && !orientation.isLeftToRight
        val freeSpace =
            if (rightToLeft) {
                packTrailing(totalSize, sizes, outPositions)
            } else {
                packLeading(totalSize, sizes, outPositions)
            }
        alignGroup(freeSpace, rightToLeft, orientation, outPositions)
    }

    override fun arrange(
        totalSize: Int,
        sizes: IntArray,
        outPositions: IntArray,
    ): Unit = arrange(totalSize, sizes, ComponentOrientation.LEFT_TO_RIGHT, outPositions)

    /** Packs from the leading edge and returns the room left after the last child. */
    private fun packLeading(
        totalSize: Int,
        sizes: IntArray,
        outPositions: IntArray,
    ): Int {
        var occupied = 0
        var lastSpace = 0
        sizes.forEachIndexed { index, size ->
            outPositions[index] = min(occupied, totalSize - size)
            lastSpace = min(space, totalSize - outPositions[index] - size)
            occupied = outPositions[index] + size + lastSpace
        }
        return totalSize - (occupied - lastSpace)
    }

    /** Packs from the trailing edge and returns the room left before the first child. */
    private fun packTrailing(
        totalSize: Int,
        sizes: IntArray,
        outPositions: IntArray,
    ): Int {
        var freeSpace = totalSize
        var lastSpace = 0
        sizes.forEachIndexed { index, size ->
            outPositions[index] = maxOf(0, freeSpace - size)
            lastSpace = min(space, outPositions[index])
            freeSpace = outPositions[index] - lastSpace
        }
        return freeSpace + lastSpace
    }

    /** Shifts the packed group to where [alignment] puts it in [freeSpace]. */
    private fun alignGroup(
        freeSpace: Int,
        rightToLeft: Boolean,
        orientation: ComponentOrientation,
        outPositions: IntArray,
    ) {
        if (freeSpace <= 0) return
        val group = alignment.align(0, freeSpace, orientation)
        val offset = if (rightToLeft) group - freeSpace else group
        for (index in outPositions.indices) {
            outPositions[index] += offset
        }
    }
}

/**
 * Walks [sizes] with their own indices, from the last to the first when [reversed], starting the cursor
 * at [first] and advancing it after each child by its size and [gap]. Every position is rounded once, so
 * a run whose cursor carries a fraction - a centered or evenly-spaced one - stays consistent with itself
 * from one child to the next.
 */
private fun placeRun(
    first: Float,
    gap: Float,
    sizes: IntArray,
    outPositions: IntArray,
    reversed: Boolean,
) {
    var current = first
    sizes.forEachIndexed(reversed) { index, size ->
        outPositions[index] = current.roundToInt()
        current += size.toFloat() + gap
    }
}

/** Packs the children edge to edge from the leading edge. */
private fun placeLeading(
    sizes: IntArray,
    outPositions: IntArray,
    reversed: Boolean,
): Unit = placeRun(first = 0f, gap = 0f, sizes, outPositions, reversed)

/** Packs the children edge to edge against the trailing edge. */
private fun placeTrailing(
    totalSize: Int,
    sizes: IntArray,
    outPositions: IntArray,
    reversed: Boolean,
): Unit = placeRun(first = (totalSize - sizes.sum()).toFloat(), gap = 0f, sizes, outPositions, reversed)

/** Packs the children edge to edge and centers the group. */
private fun placeCenter(
    totalSize: Int,
    sizes: IntArray,
    outPositions: IntArray,
    reversed: Boolean,
): Unit = placeRun(first = (totalSize - sizes.sum()).toFloat() / 2, gap = 0f, sizes, outPositions, reversed)

/** Splits the room left over into one gap between each pair of children. */
private fun placeSpaceBetween(
    totalSize: Int,
    sizes: IntArray,
    outPositions: IntArray,
    reversed: Boolean,
) {
    if (sizes.isEmpty()) return
    val gap = (totalSize - sizes.sum()).toFloat() / maxOf(sizes.lastIndex, 1)
    // A lone child has no gap to sit between, so it goes to whichever edge the reading order starts at.
    val first = if (reversed && sizes.size == 1) gap else 0f
    placeRun(first, gap, sizes, outPositions, reversed)
}

/** Gives each child a gap of its own, half of it before the child and half after. */
private fun placeSpaceAround(
    totalSize: Int,
    sizes: IntArray,
    outPositions: IntArray,
    reversed: Boolean,
) {
    if (sizes.isEmpty()) return
    val gap = (totalSize - sizes.sum()).toFloat() / sizes.size
    placeRun(first = gap / 2, gap, sizes, outPositions, reversed)
}

/** Splits the room left over into equal gaps between the children and at both edges. */
private fun placeSpaceEvenly(
    totalSize: Int,
    sizes: IntArray,
    outPositions: IntArray,
    reversed: Boolean,
) {
    val gap = (totalSize - sizes.sum()).toFloat() / (sizes.size + 1)
    placeRun(first = gap, gap, sizes, outPositions, reversed)
}

/**
 * Walks the sizes with their own indices, from the last to the first when [reversed], so a placement
 * routine that runs forwards produces the mirror image without a second implementation.
 */
private inline fun IntArray.forEachIndexed(
    reversed: Boolean,
    action: (index: Int, size: Int) -> Unit,
) {
    if (reversed) {
        for (index in lastIndex downTo 0) {
            action(index, this[index])
        }
    } else {
        forEachIndexed(action)
    }
}
