@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.Container

/**
 * A composable that measures and places its [content] by a [MeasurePolicy] of your own: a container
 * whose placement rules you write, with no `LayoutManager` to go with them.
 *
 * [Row], [Column] and [Box] are each one of these; a policy you write is asked the same questions they
 * are. Children are written plainly, and the policy is handed one [Measurable] per child in declaration
 * order:
 *
 * ```
 * Layout(
 *     content = {
 *         Label(text = "Status")
 *         Button(text = "Close", onClick = ::close)
 *     },
 *     measurePolicy = { measurables, constraints ->
 *         // A stack imposes no minimum of its own: each child takes the height left after earlier ones.
 *         var remainingHeight = constraints.maxHeight
 *         val placeables = measurables.map { measurable ->
 *             val placeable = measurable.measure(
 *                 Constraints(maxWidth = constraints.maxWidth, maxHeight = remainingHeight)
 *             )
 *             remainingHeight = (remainingHeight - placeable.height).coerceAtLeast(0)
 *             placeable
 *         }
 *         // The extent is named from the children and held inside the offer. Under an unbounded offer,
 *         // the same body answers the unbounded question `preferredLayoutSize` asks.
 *         val width = constraints.constrainWidth(placeables.maxOfOrNull { it.width } ?: 0)
 *         val height = constraints.constrainHeight(constraints.maxHeight - remainingHeight)
 *         layout(width, height) {
 *             var y = 0
 *             for (placeable in placeables) {
 *                 placeable.placeRelative(0, y)
 *                 y += placeable.height
 *             }
 *         }
 *     },
 * )
 * ```
 *
 * A child's own layout modifiers - a padding, an offset, an aspect ratio, a default minimum size -
 * stand between the constraints the policy offers and what the child is measured under, which is why
 * the content receiver is [ConstrainedScope]. Placements of your own go in a scope of your own
 * extending it, whose builders append a value the policy reads back through
 * [Measurable.parentData].
 *
 * A policy that reads a snapshot `State` is observed: a read made while measuring or answering an
 * intrinsic query measures the container and its ancestors again when the state changes, and a read made
 * only while placing places the children again, invalidating no ancestor. A read made while the container
 * paints its own decoration repaints it. A child's paint is observed by the child, so a plain component
 * reading state while it paints is not observed; draw such content in a `Canvas`. A policy that writes,
 * while measuring, a state it also reads is measured again after the write, and settles once a pass
 * writes the value the state already holds. A pass at the extent the container already settled on
 * places that result again without running the policy, until the container is invalidated.
 *
 * @param measurePolicy how the container measures and places its children
 * @param modifier the [SwingModifier] applied to the panel
 * @param parentDataProtocol the stable token for parent data that [measurePolicy] reads, or `null`
 *   where it reads none. Create it with [layoutParentDataProtocol] and reuse that same instance from
 *   the corresponding [org.jetbrains.compose.swing.layout.ParentDataModifier] implementations.
 * @param content the composable content of the container; see [ConstrainedScope]
 */
@Composable
public fun Layout(
    measurePolicy: MeasurePolicy,
    modifier: SwingModifier = SwingModifier,
    parentDataProtocol: LayoutParentDataProtocol? = null,
    content: @Composable ConstrainedScope.() -> Unit = {},
) {
    SwingNode(
        factory = { ConstrainedPanel(MeasurePolicyLayout(measurePolicy, parentDataProtocol)) },
        modifier = modifier then LayoutObservation,
        update = {
            update(parentDataProtocol) {
                policyLayout.requireSameParentDataProtocol(it)
            }
            update(measurePolicy, SetMeasurePolicy)
        },
        content = { ConstrainedScopeImpl.content() },
    )
}

/**
 * A [Layout] with no children that can participate in layout, drawing, and input through [modifier].
 *
 * @param measurePolicy how the panel measures and places its zero children
 * @param modifier the [SwingModifier] applied to the panel
 */
@Composable
public fun Layout(
    measurePolicy: MeasurePolicy,
    modifier: SwingModifier = SwingModifier,
) {
    SwingNode(
        factory = { ConstrainedPanel(MeasurePolicyLayout(measurePolicy)) },
        modifier = modifier then LayoutObservation,
        update = {
            update(measurePolicy, SetMeasurePolicy)
        },
    )
}

/** Sets a [Layout] panel's policy and revalidates the panel. */
private val SetMeasurePolicy: ConstrainedPanel.(MeasurePolicy) -> Unit = {
    policyLayout.policy = it
    revalidate()
}

/** Whether this is a Foundation container whose policy layout understands [protocol] for a composed child. */
internal fun Container.acceptsParentProtocol(protocol: ParentProtocol): Boolean {
    val layout = (this as? Decoratable)?.decoration?.childMeasurables?.owner ?: return false
    return protocol === layout.parentDataProtocol || protocol === LayoutModifierParentProtocol
}

/** The token is a stable layout-family identity, not a recomposable configuration value. */
internal fun MeasurePolicyLayout.requireSameParentDataProtocol(protocol: LayoutParentDataProtocol?) {
    require(protocol === parentDataProtocol) {
        "Layout's parentDataProtocol must remain the same instance while the layout is composed."
    }
}

/** The [ConstrainedScope] a [Layout] hands its content, which offers no placement of its own. */
internal object ConstrainedScopeImpl : ConstrainedScope
