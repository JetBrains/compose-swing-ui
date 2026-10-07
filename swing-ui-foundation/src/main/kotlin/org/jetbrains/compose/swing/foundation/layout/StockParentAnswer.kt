package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.invalidateLayout
import java.awt.Component

/**
 * What a container answered a parent that is not a Foundation container, which asks it for a preferred size with no
 * width or, through [Constrainable], for a height at a width; the width it was last laid out at; and the heights a
 * paint last found at its width. Swing drops the size it caches for the container when it resizes it.
 */
internal class StockParentAnswer {
    /** The inner width of the container's last layout. */
    private var laidOutWidth = 0

    /**
     * Whether the parent lays the container out at the width its preferred size asks for, as `FlowLayout` does. Only a
     * layout that follows a preferred or minimum size changes it. A layout at the width asked, which the container
     * already held when asked, leaves it as it was, unless the parent set that width before asking, as `BorderLayout`
     * does for its north and south children.
     */
    private var followsAnswer = true

    /** The inner width the last preferred size asked for: the policy's max intrinsic width. */
    private var askedWidth = 0

    /** The inner width the container held when it answered the last preferred size. */
    private var heldWidth = 0

    /** Which layouts a parent may lay the container out by the last preferred size. */
    private var preferred = Standing.Spent

    /**
     * The inner width the stock children held when the last heights were worked out, by an answer or by a paint, or
     * the width a parent laid the container out at after asking at another width before that layout.
     */
    private var width = 0

    /**
     * The inner width a parent asked the last heights for through [Constrainable], or -1 where they are for the width
     * the container is laid out at, as a preferred size's is.
     */
    var askedAt = -1
        private set

    /**
     * The inner max intrinsic height last worked out with the stock children at [width], or -1 where none was: a
     * preferred size answers one. A stock child answers it at the width it holds at the time.
     */
    var maxHeight = -1
        private set

    /** The inner min intrinsic height last worked out with the stock children at [width], or -1 where none was. */
    var minHeight = -1
        private set

    /** Whether the last heights were worked out with the stock children sized for the layout that follows. */
    private var answersNextLayout = false

    /** The inner [width] before the last one, or -1 once a paint finds the container at [width]. */
    private var previousWidth = -1

    /**
     * The width a paint revalidated the container away from, or -1. A paint finding the container back at that width
     * leaves the answer alone, as `ScrollPaneLayout` checks its scroll bars again once and stops.
     */
    private var revalidatedFrom = -1

    /**
     * The inner width a stock parent's question is answered at, for a container now [current] wide whose policy asks
     * for [asked]. A container not laid out yet, zero wide, has the height at the width asked. A parent that set
     * another width than the last layout's before asking, as `BorderLayout` does for its north and south children, has
     * it at that width. A parent that lays the container out at the width it asks for, as `FlowLayout` does, has it at
     * the width asked now. Any other parent has it at the current width, unless the parent has just laid the
     * container out there, away from the width answered before, and back: then the answer stays, as
     * `ScrollPaneLayout` checks its scroll bars again once and stops.
     */
    fun widthFor(
        current: Int,
        asked: Int,
    ): Int =
        when {
            current == 0 -> asked
            current != laidOutWidth -> current
            followsAnswer -> asked
            current == previousWidth -> width
            else -> current
        }

    /** The inner height of the last minimum size the container answered, or -1 where it answered none. */
    private var minimumSizeHeight = -1

    /** Which layouts a parent may lay the container out by the last minimum size. */
    private var minimum = Standing.Spent

    /**
     * The inner width [minimumSizeHeight] is worked out at, where that is not the width the container held when asked,
     * as for a container not laid out yet; -1 otherwise.
     */
    private var minimumAwayAt = -1

    /** Records the minimum size the container answered while [held] wide, with its [height] at the inner [width]. */
    fun answeredMinimum(
        held: Int,
        width: Int,
        height: Int,
    ) {
        val preferredHeight = maxHeight
        record(width, -1)
        maxHeight = preferredHeight
        minHeight = height
        minimumSizeHeight = height
        minimumAwayAt = if (width == held) -1 else width
        minimum = Standing.Cached
    }

    /**
     * Which of its sizes a parent lays the container out by at the inner [width] by [height]: [MeasureMode.Preferred]
     * for a preferred size answered with that height, [MeasureMode.Minimum] for a minimum size answered with it, or
     * null for neither and for heights a parent asked through [Constrainable]. A height below a minimum worked out at a
     * width the container did not hold, granted at another width, is also [MeasureMode.Minimum]: the parent squeezes
     * the container by a minimum that stands for no width it holds, as `GridBagLayout` does short of room, and grants
     * the minimum at [width] once it asks there. A parent that squeezes the container below the minimum at the width it
     * held sets the height itself, as `JSplitPane` does short of room.
     */
    private fun answeredAs(
        width: Int,
        height: Int,
    ): MeasureMode? =
        when {
            askedAt >= 0 -> null
            preferred != Standing.Spent && height == maxHeight -> MeasureMode.Preferred
            minimum == Standing.Spent -> null
            height == minimumSizeHeight -> MeasureMode.Minimum
            height < minimumSizeHeight && minimumAwayAt >= 0 && width != minimumAwayAt -> MeasureMode.Minimum
            else -> null
        }

    /**
     * Records the preferred size the container answered while [held] wide: [asked] wide, with its [height] at the inner
     * [width].
     */
    fun answered(
        held: Int,
        asked: Int,
        width: Int,
        height: Int,
    ) {
        askedWidth = asked
        heldWidth = held
        record(width, -1)
        maxHeight = height
        preferred = Standing.Cached
    }

    /**
     * Records Swing dropping the sizes it caches for the container, which was valid where [wasValid] holds. A parent
     * may have read a size Swing cached until now, so the layout that follows still follows it. A size Swing dropped
     * before is spent once the container was valid in between: no parent read it since.
     */
    fun dropped(wasValid: Boolean) {
        preferred = preferred.dropped(wasValid)
        minimum = minimum.dropped(wasValid)
    }

    /**
     * Records the inner [intrinsicSize] [height] a parent asked for through [Constrainable] at the inner [asked] width,
     * worked out with the stock children at the inner [width]. Worked out [forNextLayout], with the stock children
     * sized to the width asked, it is the height the parent lays the container out at, and a paint has nothing to
     * check.
     */
    fun answeredHeight(
        width: Int,
        asked: Int,
        height: Int,
        intrinsicSize: IntrinsicSize,
        forNextLayout: Boolean,
    ) {
        record(width, asked)
        if (intrinsicSize == IntrinsicSize.Min) minHeight = height else maxHeight = height
        answersNextLayout = forNextLayout
    }

    /** Starts a record at the inner [width] for the [asked] width, keeping the heights already worked out there. */
    private fun record(
        width: Int,
        asked: Int,
    ) {
        answersNextLayout = false
        if (width == this.width && asked == askedAt) return
        if (width != this.width) previousWidth = this.width
        this.width = width
        askedAt = asked
        maxHeight = -1
        minHeight = -1
    }

    /**
     * Records the container laid out at the inner [width] by [height], and returns which of its sizes the parent lays
     * it out by, where the last heights do not stand for that width; see [answeredAs]. A size stands for the layouts a
     * parent may have read it for: while Swing caches it, and for the one layout after Swing drops it. A parent laying
     * the container out again without asking, as `BorderLayout` does its center child, sets the height itself.
     */
    fun laidOutAt(
        width: Int,
        height: Int,
    ): MeasureMode? {
        val laidOutBy = if (checksHeightAt(width)) answeredAs(width, height) else null
        if (preferred != Standing.Spent || minimum != Standing.Spent) {
            followsAnswer =
                when {
                    width != askedWidth -> false
                    width != heldWidth -> true
                    heldWidth != laidOutWidth -> false
                    else -> followsAnswer
                }
        }
        laidOutWidth = width
        preferred = preferred.laidOut()
        minimum = minimum.laidOut()
        // The parent kept the heights it asked for before this layout at this width, so a paint has nothing to check.
        if (answersNextLayout) this.width = width
        answersNextLayout = false
        if (width == this.width) revalidatedFrom = -1
        return laidOutBy
    }

    /**
     * Records a paint finding the container at the inner [width]. One at the width the last heights stand for ends the
     * back-and-forth for which [widthFor] keeps the answer.
     */
    fun paintedAt(width: Int) {
        if (width == this.width) previousWidth = -1
    }

    /**
     * Whether a paint finding the container at the inner [width] checks its heights: a width that is not zero, not the
     * one the last heights stand for, and not the one a paint revalidated the container away from.
     */
    fun checksHeightAt(width: Int): Boolean = width != this.width && width != 0 && width != revalidatedFrom

    /**
     * Records the heights a paint found with the stock children at the inner [width], which [checksHeightAt] accepted,
     * -1 for one not worked out before, and returns whether either differs from the last one: the container is then
     * revalidated away from the last width.
     */
    fun heightsChangedAt(
        width: Int,
        minHeight: Int,
        maxHeight: Int,
    ): Boolean {
        val changed = minHeight != this.minHeight || maxHeight != this.maxHeight
        if (changed) revalidatedFrom = this.width
        record(width, askedAt)
        this.minHeight = minHeight
        this.maxHeight = maxHeight
        return changed
    }
}

/** Which layouts a parent may lay a container out by a size it answered. */
private enum class Standing {
    /** Swing caches the size, so a parent may read it for any layout. */
    Cached,

    /** Swing dropped the size, after a parent last read it: the next layout follows it. */
    Dropped,

    /** No parent lays the container out by the size. */
    Spent,
    ;

    fun dropped(wasValid: Boolean): Standing =
        when {
            this == Cached -> Dropped
            wasValid -> Spent
            else -> this
        }

    fun laidOut(): Standing = if (this == Dropped) Spent else this
}

/**
 * The inner height a layout pass lays this container's content out at, for the inner [width] by [height] it holds. A
 * stock parent that lays the container out by [laidOutBy], the preferred or minimum size it answered, at a width that
 * answer does not stand for, as `BoxLayout` and a filling `GridBagLayout` do, grants that size's height at [width] in
 * the next validation, which a paint asks for; see [StockParentAnswer.laidOutAt]. The content is laid out at that
 * height already, past the container's bounds where it is taller, so the next validation lays it out at the same
 * size: content that animates from the size it is first laid out at, such as a slide, would otherwise be retargeted
 * before its first frame. Any other layout pass is at [height]. A change to what the policy reads for that height lays
 * the container out again.
 */
internal fun ChildMeasurables.contentHeightAt(
    width: Int,
    height: Int,
    laidOutBy: MeasureMode?,
): Int {
    val size = laidOutBy?.takeIf { answersStockParent } ?: return height
    val intrinsicSize = if (size == MeasureMode.Minimum) IntrinsicSize.Min else IntrinsicSize.Max
    // The layout pass follows, so the stock children are sized to the width before they answer.
    return during(RunningCause.AnsweringBeforeLayout) {
        panel.observedIntrinsic(intrinsicSize, IntrinsicWidthHeight.Height, width, ContentHeightReads)
    }
}

/**
 * Revalidates this container where it answers a stock parent and is laid out at a width other than the one the last
 * heights it answered stand for, and either of them differs with the stock children at that width: the parent lays it
 * out at that height in the next validation, as it does a wrapping text area, which compares its width as it paints.
 * The heights are worked out at the width the parent asked them for, or at the width laid out at for a preferred size.
 * A change to what the policy reads for them lays the container out again.
 */
internal fun ChildMeasurables.revalidateWhereHeightChanged() {
    val answer = stockParentAnswer?.takeIf { answersStockParent } ?: return
    val current = panel.innerWidth()
    answer.paintedAt(current)
    if (!answer.checksHeightAt(current)) return
    val at = answer.askedAt.takeIf { it >= 0 } ?: current
    val min =
        if (answer.minHeight < 0) {
            -1
        } else {
            panel.observedIntrinsic(IntrinsicSize.Min, IntrinsicWidthHeight.Height, at, CheckedMinHeightReads)
        }
    val max =
        if (answer.maxHeight < 0) {
            -1
        } else {
            panel.observedIntrinsic(IntrinsicSize.Max, IntrinsicWidthHeight.Height, at, CheckedMaxHeightReads)
        }
    if (answer.heightsChangedAt(current, min, max)) panel.revalidate()
}

/**
 * Whether a validation of the validate root above this component is due or running, so a size it answers feeds its
 * parent's layout: a query while every layout is settled lays nothing out by the answer.
 */
internal val Component.answersForALayout: Boolean
    get() {
        var container = parent ?: return false
        while (!container.isValidateRoot) container = container.parent ?: break
        return !container.isValid
    }

/**
 * Whether the stock parent this container answers lays it out after the answer: the container is invalid, and its own
 * validation, which lays it out before anything inside it can ask, is not running.
 */
internal val ChildMeasurables.stockParentLaysOutNext: Boolean
    get() = answersStockParent && !panel.isValid && !isValidating

/**
 * Runs [record] on this container's [StockParentAnswer], made on the first call, where it answers a stock parent and a
 * layout follows the answer.
 */
internal inline fun ChildMeasurables.recordStockAnswer(record: StockParentAnswer.() -> Unit) {
    if (!answersStockParent || !panel.answersForALayout) return
    (stockParentAnswer ?: StockParentAnswer().also { stockParentAnswer = it }).record()
}

/** Whether this container answers its sizes to a parent that is not a Foundation container, which asks for them. */
internal val ChildMeasurables.answersStockParent: Boolean
    get() = panel.decoration.parentMeasurables == null && !panel.isPreferredSizeSet

/** Reads behind the height a layout pass lays the content out at, which the stock parent grants next. */
private val ContentHeightReads: (SwingComponentNode<*>) -> Unit = { it.invalidateLayout() }

/** Reads behind the min intrinsic height a paint last checked; see [revalidateWhereHeightChanged]. */
private val CheckedMinHeightReads: (SwingComponentNode<*>) -> Unit = { it.invalidateLayout() }

/** Reads behind the max intrinsic height a paint last checked; see [revalidateWhereHeightChanged]. */
private val CheckedMaxHeightReads: (SwingComponentNode<*>) -> Unit = { it.invalidateLayout() }
