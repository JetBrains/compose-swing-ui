package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.State
import org.jetbrains.compose.swing.modifier.SwingModifier

/** Places its content with a layer whose properties [block] sets, as androidx's block `graphicsLayer` does. */
internal fun SwingModifier.placementLayer(block: PlacementLayerScope.() -> Unit): SwingModifier =
    this then PlacementLayerElement(block)

private data class PlacementLayerElement(
    private val block: PlacementLayerScope.() -> Unit,
) : LayoutModifierNodeElement<PlacementLayerNode>() {
    override fun create(): PlacementLayerNode = PlacementLayerNode(block)

    override fun update(node: PlacementLayerNode) {
        node.block = block
    }
}

/** Written against the public API alone, as a layout modifier node outside this library is. */
private class PlacementLayerNode(
    var block: PlacementLayerScope.() -> Unit,
) : LayoutModifierNode() {
    override val name: String get() = "placementLayer"

    override val declaredValues: Map<String, Any?> get() = emptyMap()

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0, layerBlock = block) }
    }
}

/**
 * Places its content with the layer [block] holds, read while placing, or without one while it holds `null`, from
 * the leading edge where [relative].
 */
internal data class PlacementReadLayerElement(
    private val block: State<(PlacementLayerScope.() -> Unit)?>,
    private val relative: Boolean,
) : LayoutModifierNodeElement<PlacementReadLayerNode>() {
    override fun create(): PlacementReadLayerNode = PlacementReadLayerNode(block, relative)

    override fun update(node: PlacementReadLayerNode) = Unit
}

internal class PlacementReadLayerNode(
    private val block: State<(PlacementLayerScope.() -> Unit)?>,
    private val relative: Boolean,
) : LayoutModifierNode() {
    override val name: String get() = "placementReadLayer"

    override val declaredValues: Map<String, Any?> get() = emptyMap()

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            val layerBlock = block.value
            when {
                layerBlock == null && relative -> placeable.placeRelative(0, 0)
                layerBlock == null -> placeable.place(0, 0)
                relative -> placeable.placeRelativeWithLayer(0, 0, layerBlock = layerBlock)
                else -> placeable.placeWithLayer(0, 0, layerBlock = layerBlock)
            }
        }
    }
}

/** Places its content with the layer [block] holds, and exposes the created [node] for direct inspection. */
internal class CapturingPlacementReadLayerElement(
    private val block: State<(PlacementLayerScope.() -> Unit)?>,
) : LayoutModifierNodeElement<PlacementReadLayerNode>() {
    lateinit var node: PlacementReadLayerNode
        private set

    override fun create(): PlacementReadLayerNode = PlacementReadLayerNode(block, relative = false).also { node = it }

    override fun update(node: PlacementReadLayerNode) = Unit

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}
