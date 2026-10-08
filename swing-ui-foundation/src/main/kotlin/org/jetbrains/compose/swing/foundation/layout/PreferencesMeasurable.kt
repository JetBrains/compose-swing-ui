package org.jetbrains.compose.swing.foundation.layout

import java.awt.Dimension

/** A framework measurable that carries the context of a preference read through its own adapter. */
internal sealed interface PreferencesMeasurable : Measurable {
    val maySizeStockChildren: Boolean get() = false

    override fun measure(constraints: Constraints): Placeable =
        measureWithStockChildSizing(constraints, maySizeStockChildren)

    override fun minIntrinsicHeight(width: Int): Int =
        intrinsicHeightUnder(IntrinsicSize.Min, 0, width, maySizeStockChildren)

    override fun maxIntrinsicHeight(width: Int): Int =
        intrinsicHeightUnder(IntrinsicSize.Max, 0, width, maySizeStockChildren)

    override fun intrinsicPlaceable(
        width: Int,
        height: Int,
    ): Placeable? = intrinsicPlaceable(width, height, maySizeStockChildren)

    fun intrinsicHeightUnder(
        intrinsicSize: IntrinsicSize,
        minWidth: Int,
        maxWidth: Int,
        maySizeStockChildren: Boolean,
    ): Int

    fun measureWithStockChildSizing(
        constraints: Constraints,
        maySizeStockChildren: Boolean,
    ): Placeable

    fun intrinsicPlaceable(
        width: Int,
        height: Int,
        maySizeStockChildren: Boolean,
    ): Placeable?
}

internal fun IntrinsicMeasurable.measureWithStockChildSizing(
    constraints: Constraints,
    maySizeStockChildren: Boolean,
): Placeable =
    when (this) {
        is PreferencesMeasurable -> measureWithStockChildSizing(constraints, maySizeStockChildren)
    }

internal fun IntrinsicMeasurable.intrinsicPlaceable(
    width: Int,
    height: Int,
    maySizeStockChildren: Boolean,
): Placeable? =
    when (this) {
        is PreferencesMeasurable -> intrinsicPlaceable(width, height, maySizeStockChildren)
    }

/** Carries a width offer and sizing permission through intrinsic calls that have no scope parameter. */
internal class OfferedMinWidthMeasurable(
    val source: IntrinsicMeasurable,
    override val maySizeStockChildren: Boolean,
    val minWidth: Int = 0,
) : PreferencesMeasurable {
    override val parentData: Any? get() = source.parentData

    override fun minIntrinsicWidth(height: Int): Int = source.minIntrinsicWidth(height)

    override fun maxIntrinsicWidth(height: Int): Int = source.maxIntrinsicWidth(height)

    override fun minIntrinsicHeight(width: Int): Int =
        source.intrinsicHeightUnder(IntrinsicSize.Min, minWidth.coerceAtMost(width), width, maySizeStockChildren)

    override fun maxIntrinsicHeight(width: Int): Int =
        source.intrinsicHeightUnder(IntrinsicSize.Max, minWidth.coerceAtMost(width), width, maySizeStockChildren)

    override fun intrinsicHeightUnder(
        intrinsicSize: IntrinsicSize,
        minWidth: Int,
        maxWidth: Int,
        maySizeStockChildren: Boolean,
    ): Int =
        source.intrinsicHeightUnder(
            intrinsicSize,
            minWidth,
            maxWidth,
            maySizeStockChildren || this.maySizeStockChildren,
        )

    override fun maximumSize(): Dimension? = source.maximumSize()

    override fun measureWithStockChildSizing(
        constraints: Constraints,
        maySizeStockChildren: Boolean,
    ): Placeable = source.measureWithStockChildSizing(constraints, maySizeStockChildren || this.maySizeStockChildren)

    override fun intrinsicPlaceable(
        width: Int,
        height: Int,
        maySizeStockChildren: Boolean,
    ): Placeable? = source.intrinsicPlaceable(width, height, maySizeStockChildren || this.maySizeStockChildren)
}

/**
 * What [read] works out from this container's policy over the children an intrinsic query walks. The result the
 * last pass settled on is set aside while it runs and restored after.
 */
internal inline fun <T> ChildMeasurables.readPolicyPreferences(
    maySizeStockChildren: Boolean = false,
    read: MeasurePolicy.(List<Measurable>) -> T,
): T =
    synchronized(panel.treeLock) {
        val children = intrinsicChildren.map { OfferedMinWidthMeasurable(it, maySizeStockChildren) }
        val retained = measured
        measured = null
        try {
            owner.policy.read(children)
        } finally {
            measured = retained
        }
    }

/** The width offer and sizing permission owned by one intrinsic query. */
internal class PreferencesScope(
    override val offeredMinWidth: Int = 0,
    val maySizeStockChildren: Boolean = false,
) : IntrinsicMeasureScope,
    () -> Unit {
    lateinit var policyLayout: MeasurePolicyLayout
    var intrinsicSize: IntrinsicSize = IntrinsicSize.Max
    var axis: IntrinsicWidthHeight = IntrinsicWidthHeight.Width
    var extent: Int = 0
    var result: Int = 0
        private set

    override fun invoke() {
        val height = axis == IntrinsicWidthHeight.Height
        val max = intrinsicSize == IntrinsicSize.Max
        result =
            policyLayout.measurables.readPolicyPreferences(maySizeStockChildren) { children ->
                with(this@PreferencesScope) {
                    when {
                        height && max -> maxIntrinsicHeight(children, extent)
                        height -> minIntrinsicHeight(children, extent)
                        max -> maxIntrinsicWidth(children, extent)
                        else -> minIntrinsicWidth(children, extent)
                    }
                }
            }
    }
}
