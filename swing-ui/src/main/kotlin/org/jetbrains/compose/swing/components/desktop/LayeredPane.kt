@file:JvmMultifileClass
@file:JvmName("DesktopComponentsKt")

package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SlotAttachment
import org.jetbrains.compose.swing.node.SlotNode
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JLayeredPane

/**
 * A composable wrapper for `JLayeredPane` that stacks children on integer depth layers.
 *
 * Declare the children you need in [block]; each `layer(z) { ... }` places its child at depth `z`. Higher
 * layers paint above lower ones, and within a layer later-declared children paint above earlier ones.
 * Children are positioned by their own bounds (`SwingModifier.bounds(...)`), since a layered pane does
 * not lay its children out.
 *
 * ```
 * LayeredPane {
 *     layer(JLayeredPane.DEFAULT_LAYER) { Background() }
 *     layer(JLayeredPane.PALETTE_LAYER) { FloatingToolbar() }
 * }
 * ```
 *
 * Adding or removing a `layer(...)` in the composition adds or removes the matching child, and a
 * child's layer updates on recomposition.
 *
 * @param modifier the [SwingModifier] applied to the underlying `JLayeredPane`
 * @param block declares the layered children; see [LayeredPaneScope]
 */
@Composable
public fun LayeredPane(
    modifier: SwingModifier = SwingModifier,
    block: LayeredPaneScope.() -> Unit,
) {
    // Collected fresh on every pass, so a child the caller stops declaring is uninstalled (see SwingNode).
    val scope = LayeredPaneScopeImpl().apply(block)

    SwingNode(
        factory = { JLayeredPane() },
        update = {
            applyModifier(modifier)
        },
        content = {
            scope.children.forEachIndexed { index, child ->
                // Keyed by position, the identity a container's content gives its children (see
                // SwingNode). The layer is part of the key so that changing a child's layer re-keys it,
                // and the applier uninstalls it from the old depth and installs it fresh at the new one.
                key(index, child.layer) {
                    val attachment = remember(child.layer) { layerAttachment(child.layer) }
                    SlotNode(attachment) {
                        child.content()
                    }
                }
            }
        },
    )
}

/** One declared child: the depth layer it sits on, plus its composable. */
private class LayerDeclaration(
    val layer: Int,
    val content: @Composable () -> Unit,
)

private class LayeredPaneScopeImpl : LayeredPaneScope {
    val children: MutableList<LayerDeclaration> = ArrayList()

    override fun layer(
        layer: Int,
        content: @Composable () -> Unit,
    ) {
        children.add(LayerDeclaration(layer, content))
    }
}

/**
 * Hosts one child of the host [JLayeredPane] on the depth layer [layer] via `add(component, layer)`;
 * uninstall detaches it by component identity so removing an earlier child never invalidates a later
 * child's uninstall.
 */
private fun layerAttachment(layer: Int): SlotAttachment =
    SlotAttachment { host, component, _ ->
        host as JLayeredPane
        // Record the depth on the component before attaching it, then add it: setLayer stamps the
        // layer client-property (the parent is still null, so it only stores), and the subsequent add
        // positions the component within that layer.
        host.setLayer(component, layer)
        host.add(component)
        return@SlotAttachment { host.remove(component) }
    }
