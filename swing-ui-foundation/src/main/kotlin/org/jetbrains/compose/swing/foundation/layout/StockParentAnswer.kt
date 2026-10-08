package org.jetbrains.compose.swing.foundation.layout

import java.awt.Component

/**
 * What a container answered a parent that is not a Foundation container, which asks it for a preferred size with no
 * width or, through [Constrainable], for a height at a width; the width it was last laid out at; and the heights a
 * paint last found at its width. Swing drops the size it caches for the container when it resizes it.
 */
internal class StockParentAnswer {
    /** The inner width of the container's last layout at a width other than zero; a layout at zero leaves it. */
    private var laidOutWidth = 0

    /**
     * Whether the parent lays the container out at the width its preferred size asks for. Only a layout that follows
     * a preferred size changes it, and a layout at zero does not. A layout at the width asked, which the container
     * already held when asked, leaves it as it was, unless the parent set that width before asking. A container that
     * held no width when asked counts as holding [laidOutWidth]. A container never laid out at a width other than zero
     * follows the answer.
     */
    private var followsAnswer = true

    /** The inner width the last preferred size asked for: the policy's max intrinsic width. */
    private var askedWidth = 0

    /** The inner width the container held when it answered the last preferred size. */
    private var heldWidth = 0

    /** Which layouts a parent may lay the container out by the last preferred size. */
    private var preferred = Standing.Spent

    /** Which layouts a parent may lay the container out by the last minimum size. */
    private var minimum = Standing.Spent

    /** The inner height of the last minimum size the container answered, or -1 where it answered none. */
    private var minimumSizeHeight = -1

    /** The inner width [minimumSizeHeight] is worked out at where that is not the width the container held; else -1. */
    private var minimumAwayAt = -1

    /**
     * The inner width the stock children held when the last heights were worked out, by an answer or by a paint, or
     * the width a parent laid the container out at after asking at another width.
     */
    private var heightsWidth = 0

    /** The inner width a parent asked the last heights for through [Constrainable]; -1 for the laid-out width. */
    private var askedAt = -1

    /**
     * The inner max intrinsic height last answered or checked, or -1 where none was. A minimum size read at another
     * width keeps it, as a parent may still lay the container out by the preferred answer.
     */
    private var maxIntrinsicHeight = -1

    /** The inner min intrinsic height last worked out at [heightsWidth], or -1 where none was. */
    private var minIntrinsicHeight = -1

    private var answersNextLayout = false

    /** The [heightsWidth] before the last one, or -1 once a paint finds the container at [heightsWidth]. */
    private var previousWidth = -1

    /** The width a paint revalidated the container away from, or -1. A paint back at that width leaves the answer. */
    private var revalidatedFrom = -1

    /**
     * The inner width a stock parent's question is answered at, for a container now [current] wide, or null for the
     * width a preferred size asks for: the policy's max intrinsic width.
     *
     * A container not laid out yet has the height at that width, for a minimum size too. A parent that set another
     * width than the last layout's before asking has it at that width. A parent that lays the container out at the
     * width its preferred size asks for has it at that width now. Any other parent has it at the current width,
     * unless the parent has just laid the container out there, away from the width answered before, and back: then
     * the answer stays.
     */
    fun grantedWidth(current: Int): Int? =
        when {
            current == 0 -> null
            current != laidOutWidth -> current
            followsAnswer -> null
            current == previousWidth -> heightsWidth
            else -> current
        }

    companion object {
        /**
         * The width [answer] grants a container now [current] wide. Where [answer] grants none, or a container that
         * records none yet holds no width, it is the policy's max intrinsic width, which [preferred] works out.
         */
        inline fun widthFor(
            answer: StockParentAnswer?,
            current: Int,
            preferred: () -> Int,
        ): Int {
            val width = if (answer != null) answer.grantedWidth(current) else current.takeIf { it != 0 }
            return width ?: preferred()
        }
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
        maxIntrinsicHeight = height
        preferred = Standing.Cached
    }

    /** Records the minimum size the container answered while [held] wide, with its [height] at the inner [width]. */
    fun answeredMinimum(
        held: Int,
        width: Int,
        height: Int,
    ) {
        val preferredHeight = maxIntrinsicHeight
        record(width, -1)
        maxIntrinsicHeight = preferredHeight
        minIntrinsicHeight = height
        minimumSizeHeight = height
        minimumAwayAt = if (width == held) -1 else width
        minimum = Standing.Cached
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
        if (intrinsicSize == IntrinsicSize.Min) minIntrinsicHeight = height else maxIntrinsicHeight = height
        answersNextLayout = forNextLayout
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
     * Records the container laid out at the inner [width] by [height], and returns which of its sizes the parent lays
     * it out by, where the last heights do not stand for that width. The preferred size answered with that height
     * gives [MeasureMode.Preferred], and the minimum size answered with it [MeasureMode.Minimum]. A height below a
     * minimum worked out at a width the container did not hold, granted at another width, is also
     * [MeasureMode.Minimum]: the parent squeezes the container by a minimum that stands for no width it holds, as
     * `GridBagLayout` does short of room, and grants the minimum at [width] once it asks there. A parent that squeezes
     * the container below the minimum at the width it held sets the height itself, as `JSplitPane` does short of room.
     * Heights a parent asked through [Constrainable] are none of its sizes. A size stands for the layouts a parent may
     * have read it for: while Swing caches it, and for the one layout after Swing drops it. A parent laying the
     * container out again without asking, as `BorderLayout` does its center child, sets the height itself.
     */
    fun laidOutAt(
        width: Int,
        height: Int,
    ): MeasureMode? {
        // A layout advances the sizes the parent may have read, so select the answer before updating the record.
        val size = sizeForLayout(width, height)
        recordLaidOutWidth(width)
        preferred = preferred.laidOut()
        minimum = minimum.laidOut()
        // The parent kept the heights it asked for before this layout at this width, so a paint has nothing to check.
        if (answersNextLayout) heightsWidth = width
        answersNextLayout = false
        if (width == heightsWidth) revalidatedFrom = -1
        return size
    }

    private fun sizeForLayout(
        width: Int,
        height: Int,
    ): MeasureMode? {
        val checks = checksHeightAt(width) && askedAt < 0
        // Heights no parent asked through Constrainable are a preferred size's, with the max height its answer or a
        // paint since worked out.
        return when {
            !checks -> null
            preferred != Standing.Spent && height == maxIntrinsicHeight -> MeasureMode.Preferred
            minimum == Standing.Spent -> null
            height == minimumSizeHeight -> MeasureMode.Minimum
            height < minimumSizeHeight && minimumAwayAt >= 0 && width != minimumAwayAt -> MeasureMode.Minimum
            else -> null
        }
    }

    private fun recordLaidOutWidth(width: Int) {
        val bySize = preferred != Standing.Spent || minimum != Standing.Spent
        if (width != 0) {
            if (bySize) {
                val held = if (heldWidth == 0) laidOutWidth else heldWidth
                followsAnswer =
                    when {
                        width != askedWidth -> false
                        width != held -> true
                        held != laidOutWidth -> false
                        else -> followsAnswer
                    }
            }
            laidOutWidth = width
        }
    }

    /**
     * Records a paint finding the container at the inner [current] width, and returns whether the heights the policy of
     * [panel] works out there, where [checksHeightAt] accepts it, differ from the last ones: the container is then
     * revalidated away from the last width. Only a height worked out before is worked out again. A paint at the width
     * the last heights stand for ends the back-and-forth for which [grantedWidth] keeps the answer. A change to what
     * the policy reads for the heights worked out here lays the container out again.
     */
    fun heightsChangedAt(
        current: Int,
        panel: ConstrainedPanel,
    ): Boolean {
        if (current == heightsWidth) previousWidth = -1
        if (!checksHeightAt(current)) return false
        val at = if (askedAt >= 0) askedAt else current
        val min =
            if (minIntrinsicHeight < 0) {
                -1
            } else {
                panel.observedIntrinsic(
                    IntrinsicSize.Min,
                    IntrinsicWidthHeight.Height,
                    at,
                    AnswerReads.CheckedMinHeight,
                )
            }
        val max =
            if (maxIntrinsicHeight < 0) {
                -1
            } else {
                panel.observedIntrinsic(
                    IntrinsicSize.Max,
                    IntrinsicWidthHeight.Height,
                    at,
                    AnswerReads.CheckedMaxHeight,
                )
            }
        val changed = min != minIntrinsicHeight || max != maxIntrinsicHeight
        if (changed) revalidatedFrom = heightsWidth
        record(current, askedAt)
        minIntrinsicHeight = min
        maxIntrinsicHeight = max
        return changed
    }

    /** Starts a record at the inner [width] for the [asked] width, keeping the heights already worked out there. */
    private fun record(
        width: Int,
        asked: Int,
    ) {
        answersNextLayout = false
        if (width == heightsWidth && asked == askedAt) return
        if (width != heightsWidth) previousWidth = heightsWidth
        heightsWidth = width
        askedAt = asked
        maxIntrinsicHeight = -1
        minIntrinsicHeight = -1
    }

    /**
     * Whether a paint finding the container at the inner [width] checks its heights: a width that is not zero, not the
     * one the last heights stand for, and not the one a paint revalidated the container away from.
     */
    private fun checksHeightAt(width: Int): Boolean = width != heightsWidth && width != 0 && width != revalidatedFrom
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
        panel.observedIntrinsic(intrinsicSize, IntrinsicWidthHeight.Height, width, AnswerReads.ContentHeight)
    }
}

/**
 * Revalidates this container where it answers a stock parent and is laid out at a width other than the one the last
 * heights it answered stand for, and either of them differs with the stock children at that width: the parent lays it
 * out at that height in the next validation, as it does a wrapping text area, which compares its width as it paints.
 * The heights are worked out at the width the parent asked them for, or at the width laid out at for a preferred size.
 * The heights checked are a preferred size's, a minimum size's and those asked through [Constrainable].
 */
internal fun ChildMeasurables.revalidateWhereHeightChanged() {
    val answer = stockParentAnswer?.takeIf { answersStockParent } ?: return
    if (answer.heightsChangedAt(panel.innerWidth(), panel)) panel.revalidate()
}

/**
 * Whether a validation of the validate root above this component is due or running, so a size it answers feeds its
 * parent's layout: a query while every layout is settled lays nothing out by the answer.
 */
internal val Component.isValidationPendingAbove: Boolean
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
    if (!answersStockParent || !panel.isValidationPendingAbove) return
    (stockParentAnswer ?: StockParentAnswer().also { stockParentAnswer = it }).record()
}

/** Whether this container answers its sizes to a parent that is not a Foundation container, which asks for them. */
internal val ChildMeasurables.answersStockParent: Boolean
    get() = panel.decoration.parentMeasurables == null && !panel.isPreferredSizeSet
