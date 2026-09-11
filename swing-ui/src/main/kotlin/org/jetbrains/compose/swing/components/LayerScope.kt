package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.wrongSlotHost
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Point
import javax.swing.JLayer
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * The receiver of a [Layer]'s content, through which a child declares the region of the layer it fills.
 *
 * A layer holds two regions: the view it wraps, which a child names on its own `modifier`, and the
 * glass pane painted over that view, which is a composable of this scope rather than a builder because
 * the Swing slot behind it takes a panel the layer has to be given.
 *
 * ```
 * Layer(
 *     onPaint = { g, width, height, paintView ->
 *         paintView()
 *         g.paint = veil
 *         g.fillRect(0, 0, width, height)
 *     },
 * ) {
 *     Table(model = rows, modifier = SwingModifier.view())
 *     if (busy) {
 *         GlassPane { ProgressBar(value = 0, indeterminate = true) }
 *     }
 * }
 * ```
 *
 * A layer holds nothing besides the two regions, so every child names one and a child that names none is
 * refused, naming the layer and the builders that would place it. Each region shows one component, so
 * two children naming the same region are refused too. A child that goes away releases the region it
 * held: the view clears, and the glass pane is the one the layer carried before any declaration reached
 * it.
 *
 * @see javax.swing.JLayer
 */
public sealed interface LayerScope {
    /**
     * Installs the child as the layer's view: the component the layer lays out, takes its own
     * preferred size from, and hands to its `LayerUI` to paint and to observe events for.
     *
     * The view is held in a slot of the layer rather than added to a container, so a widget that answers
     * a scroll pane about its own scrolling - a table, a list, a tree, a text area - goes on answering it
     * through the layer, which a panel wrapping the widget would not.
     *
     * @return this chain with the view region declared on it.
     * @see javax.swing.JLayer.setView
     */
    public fun SwingModifier.view(): SwingModifier

    /**
     * Declares the sheet painted over the view, in a pane the layer owns.
     *
     * The pane covers the layer, it is transparent where [content] paints nothing, and it paints after
     * the view, so an overlay declared here is drawn over the wrapped component rather than under it.
     * [content] fills the pane, so a layout composable inside it places what the overlay is made of.
     *
     * The pane is over the view for as long as this is in the composition; once this leaves, the layer
     * carries the glass pane it carried before, shown as it was shown - so an overlay that comes and goes
     * is an ordinary `if` around the call.
     *
     * This is the sheet [org.jetbrains.compose.swing.window.GlassPane] declares over a whole window, at
     * the scope of one wrapped component instead.
     *
     * @param content the composable content the glass pane shows over the view.
     * @see javax.swing.JLayer.setGlassPane
     */
    @Composable
    public fun GlassPane(content: @Composable () -> Unit)
}

/**
 * The regions a [Layer] holds its children in, which it declares on its own node so that a child naming
 * none of them is refused there. The glass pane is written as the composable that fills it, since that is
 * how a caller reaches it.
 */
internal val LayerRegions: ChildPlacement = ChildPlacement.Slots(VIEW_REGION, GLASS_PANE_REGION)

/**
 * The [LayerScope] one [Layer] hands its content. It is remembered alongside the layer, because it holds
 * the glass pane that layer carried before any declaration took the slot, and what it holds outlives the
 * declaration that displaced it.
 *
 * A pass that swaps one declaration for another need not take the outgoing one out before installing the
 * arriving one, so neither order is relied on here: the carried pane is recorded only while none is
 * recorded already, and an outgoing declaration restores only while the layer still holds the pane that
 * declaration installed. Whichever way round the pass runs, the pane put back at the end is the layer's
 * own rather than the dead panel of a declaration that has itself gone away.
 */
internal class LayerScopeImpl : LayerScope {
    private var displaced: DisplacedGlassPane? = null

    override fun SwingModifier.view(): SwingModifier = slot(LayerParentProtocol, VIEW_REGION, ViewAttachment)

    @Composable
    override fun GlassPane(content: @Composable () -> Unit) {
        SwingNode(
            factory = { LayerGlassPane() },
            modifier = SwingModifier.slot(LayerParentProtocol, GLASS_PANE_REGION, glassPaneAttachment),
            content = content,
        )
    }

    /**
     * Installs the declared pane through `setGlassPane`, and shows it: a layer hands an arriving pane the
     * visibility of the pane it replaces, and the pane a layer builds for itself is hidden, so a pane
     * that is not shown right after the swap is installed and invisible.
     *
     * Uninstall puts back the pane the layer carried before, which `setGlassPane(null)` does not do - it
     * empties the slot, and a layer builds its own pane only in its constructor.
     */
    private val glassPaneAttachment =
        SlotAttachment { host, component, _ ->
            val layer = layerHost(host, GLASS_PANE_REGION)
            // The pane is the one this scope's own GlassPane built, which is what lets this slot take a
            // JPanel where a modifier a caller writes could only promise a Component.
            val pane = component as JPanel
            if (displaced == null) {
                val carried = layer.glassPane
                displaced = DisplacedGlassPane(carried, carried != null && carried.isVisible)
            }
            layer.glassPane = pane
            pane.isVisible = true
            return@SlotAttachment {
                if (layer.glassPane === pane) restoreGlassPane(layer) else displaced = null
            }
        }

    /** Gives [layer] back the pane it carried before, shown as it was shown, and forgets it. */
    private fun restoreGlassPane(layer: JLayer<*>) {
        val carried = displaced ?: return
        displaced = null
        layer.glassPane = carried.pane
        carried.pane?.isVisible = carried.visible
    }
}

private val LayerParentProtocol = parentProtocolOf("JLayer slot") { it is JLayer<*> }

/** The glass pane a layer carried before a [LayerScope.GlassPane] took the slot, and whether it was shown. */
private class DisplacedGlassPane(
    val pane: JPanel?,
    val visible: Boolean,
)

/**
 * The pane a [LayerScope.GlassPane] fills, which answers for a point the way the pane a layer builds for
 * itself does.
 *
 * A pane is laid out over the whole layer, so a plain panel would contain every point in it, and the
 * things that find a component by geometry - `findComponentAt`, and with it the cursor shown and the drop
 * target found - would stop at the pane and never reach the view. A layer's own pane answers for a point
 * only where one of its children is, or where it has been given a reason of its own to take the pointer,
 * and this one answers the same way. Mouse events reach the view either way: a pane carrying no mouse
 * listener is passed over as an event target and the search goes on to the view.
 *
 * The pane is transparent where its content paints nothing, where a plain panel is opaque and would hide
 * the view.
 */
private class LayerGlassPane : JPanel(BorderLayout()) {
    init {
        isOpaque = false
    }

    override fun contains(
        x: Int,
        y: Int,
    ): Boolean {
        if (!super.contains(x, y)) return false
        val hitsChild =
            components.any { child ->
                child.isVisible && child.contains(SwingUtilities.convertPoint(this, Point(x, y), child))
            }
        return hitsChild ||
            mouseListeners.isNotEmpty() ||
            mouseMotionListeners.isNotEmpty() ||
            mouseWheelListeners.isNotEmpty() ||
            isCursorSet
    }
}

/**
 * Installs a child as the layer's view via `setView`; uninstall clears the view slot, which is what
 * releases the component.
 *
 * The view is never taken out by index: a layer holds its two children in its own index space but does
 * not override `remove(int)`, so removing by index would detach the component while the layer went on
 * reporting it as its view.
 */
private val ViewAttachment =
    SlotAttachment { host, component, _ ->
        // A layer erases its view type, and this is the layer the enclosing Layer's own factory built, so
        // the cast reaches setView with the component that layer's declaration named for its view slot.
        @Suppress("UNCHECKED_CAST")
        val layer = layerHost(host, VIEW_REGION) as JLayer<Component>
        layer.view = component
        return@SlotAttachment {
            if (layer.view === component) layer.view = null
        }
    }

/**
 * The layer a region-filling child is installed into. Both [LayerScope] regions reach their child through
 * a `JLayer` setter, so a child carrying one of them under another container - a `SwingModifier.view()`
 * composed under a panel, say - is refused here by name, rather than reaching the setter and failing as a
 * bare `ClassCastException` naming neither the region nor the host.
 */
private fun layerHost(
    host: Container,
    builder: String,
): JLayer<*> = host as? JLayer<*> ?: error(wrongSlotHost(host, JLayer::class.java, builder))

/** The component the layer wraps, as a child names it and as an error about it prints. */
private const val VIEW_REGION: String = "SwingModifier.view()"

/** The sheet painted over the view, as a caller declares it and as an error about it prints. */
private const val GLASS_PANE_REGION: String = "GlassPane { }"
