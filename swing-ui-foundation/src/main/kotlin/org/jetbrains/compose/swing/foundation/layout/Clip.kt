@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * Clips the child's paint, and its descendants', to the box the modifiers after this one report for it,
 * even where a modifier further along places something larger, such as a size transition mid-flight. A
 * decoration declared before it is not clipped, and one declared after it is.
 *
 * The clip is a step of the child's decoration, so the child must be
 * [Decoratable][org.jetbrains.compose.swing.foundation.graphics.Decoratable], as a [Row], [Column],
 * [Box] and a custom [Layout] are.
 *
 * @return this modifier with the clip declared on it.
 * @throws IllegalStateException as the child is first laid out, if it is not
 *   [Decoratable][org.jetbrains.compose.swing.foundation.graphics.Decoratable].
 */
context(scope: ConstrainedScope)
public fun SwingModifier.clipToBounds(): SwingModifier = with(scope) { layout(ClipToBoundsElement) }

/** The `clipToBounds` declaration. */
private data object ClipToBoundsElement : LayoutModifierNodeElement<ClipToBoundsNode>() {
    override val name: String get() = "clipToBounds"

    override fun create(): ClipToBoundsNode = ClipToBoundsNode()

    override fun update(node: ClipToBoundsNode) = Unit
}

/** Places its content where it is, with a layer clipping the content's paint to the content's box. */
private class ClipToBoundsNode : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0, layerBlock = ClipLayer)
        }
    }
}

/** One instance, so placing again with it repaints nothing. */
private val ClipLayer: PlacementLayerScope.() -> Unit = { clip = true }
