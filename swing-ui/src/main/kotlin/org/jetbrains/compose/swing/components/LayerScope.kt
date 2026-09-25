package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.PanelScope
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.LayoutScopeMarker
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.slot
import java.awt.Component
import java.awt.Container
import javax.swing.JLayer

/**
 * The receiver of a [Layer]'s content, through which [GlassPane] declares the layer's glass pane.
 *
 * A layer holds its view, the child that names no region, as `new JLayer(view)` holds it, and the glass pane
 * painted over that view, which is a composable of this scope because the pane is the layer's own: the
 * declaration configures it and fills it with content.
 *
 * ```
 * Layer(
 *     onPaint = { g, width, height, paintView ->
 *         paintView()
 *         g.paint = veil
 *         g.fillRect(0, 0, width, height)
 *     },
 * ) {
 *     Table(model = rows)
 *     if (busy) {
 *         GlassPane(PanelLayout.Border()) { ProgressBar(value = 0, indeterminate = true) }
 *     }
 * }
 * ```
 *
 * A layer holds nothing besides the view and the glass pane, and each shows one component, so two children
 * that name no region, or two glass pane declarations, are refused. A view that goes away clears the view,
 * and a glass pane declaration that goes away hides the layer's pane again.
 *
 * @see javax.swing.JLayer
 */
@LayoutScopeMarker
public sealed interface LayerScope {
    /**
     * Shows the layer's own glass pane over the view, holding [content] under the pane's own
     * `FlowLayout`: a [PanelLayout.Flow] with its defaults.
     *
     * @param modifier the [SwingModifier] applied to the glass pane
     * @param content the composable content the glass pane shows over the view
     * @see javax.swing.JLayer.getGlassPane
     */
    @Composable
    public fun GlassPane(
        modifier: SwingModifier = SwingModifier,
        content: @Composable PanelScope.() -> Unit,
    )

    /**
     * Shows the layer's own glass pane over the view, holding [content] under [layout].
     *
     * The pane covers the layer, it is transparent where [content] paints nothing, and it paints after the
     * view, so an overlay declared here is drawn over the wrapped component. A mouse event goes to the
     * deepest component under the pointer that listens for it, and goes on to the view otherwise.
     * [modifier] reaches the pane itself: a veil's background, a wait cursor, a mouse listener that keeps
     * the mouse from the view.
     *
     * This is composed directly in the layer's content. The pane is shown for as long as this is in the
     * composition, whatever visibility [modifier] declares. Once this leaves, every property [modifier]
     * declared is given back, the pane is hidden again and the children [content] composed are removed;
     * [layout] stays installed on the pane. An overlay that comes and goes is an ordinary `if` around the
     * call, and an `if`/`else` that selects one of two declarations keeps the pane shown and applies the
     * selected one. A layer has one glass pane, so two declarations composed in the same layer at once are
     * refused.
     *
     * @param layout the layout the pane's children are laid out under; see [PanelLayout]
     * @param modifier the [SwingModifier] applied to the glass pane
     * @param content the composable content the glass pane shows over the view, with [layout]'s scope as
     *   its receiver
     * @see javax.swing.JLayer.getGlassPane
     */
    @Composable
    public fun <S : PanelScope> GlassPane(
        layout: PanelLayout<S>,
        modifier: SwingModifier = SwingModifier,
        content: @Composable S.() -> Unit,
    )
}

/** The [LayerScope] one [Layer] hands its content, remembered alongside the layer. */
internal class LayerScopeImpl : LayerScope {
    private val glassPane = ownedGlassPaneDeclaration(JLayer<*>::getGlassPane)

    @Composable
    override fun GlassPane(
        modifier: SwingModifier,
        content: @Composable PanelScope.() -> Unit,
    ) = GlassPane(glassPane.defaultLayout, modifier, content)

    @Composable
    override fun <S : PanelScope> GlassPane(
        layout: PanelLayout<S>,
        modifier: SwingModifier,
        content: @Composable S.() -> Unit,
    ) = glassPane.GlassPane(layout, modifier.slot(LayerParentProtocol, GLASS_PANE_DECLARATION), content)
}

private val LayerParentProtocol = parentProtocolOf("JLayer slot") { it is JLayer<*> }

/**
 * Installs a child as the layer's view via `setView`, as `new JLayer(view)` holds it; uninstall clears the
 * view only while the child is still the layer's view, so a replacement that arrived first keeps it.
 *
 * The view is never taken out by index: a layer holds its two children in its own index space but does
 * not override `remove(int)`, so removing by index would detach the component while the layer went on
 * reporting it as its view.
 */
private object ViewAttachment : SlotAttachment {
    override fun install(
        host: Container,
        component: Component,
        index: Int,
    ): () -> Unit {
        // Only a layer declares LayerRegions, and a layer erases its view type, so the cast reaches setView
        // with the component the composition holds for the layer's view.
        @Suppress("UNCHECKED_CAST")
        val layer = host as JLayer<Component>
        layer.view = component
        return {
            if (layer.view === component) layer.view = null
        }
    }
}

/**
 * The regions a [Layer] holds its children in, which it declares on its own node: the glass pane, written
 * as the composable that fills it since that is how a caller reaches it, and the view, which is the child
 * that names no region.
 */
internal val LayerRegions: ChildPlacement = ChildPlacement.Slots(GLASS_PANE_DECLARATION, content = ViewAttachment)

/** A glass pane's declaration as a caller writes it, which an error about the pane prints. */
internal const val GLASS_PANE_DECLARATION: String = "GlassPane { }"
