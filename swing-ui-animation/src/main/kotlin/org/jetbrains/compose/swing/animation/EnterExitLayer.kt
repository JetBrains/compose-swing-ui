package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.foundation.graphics.DecorationModifierNode
import org.jetbrains.compose.swing.foundation.graphics.ImageLayer
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.observeReads
import java.awt.Component
import java.awt.Container
import java.awt.FocusTraversalPolicy
import java.awt.Graphics2D
import java.awt.KeyboardFocusManager
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * The fade of a container that runs its enter/exit over its own children, as a decoration step. A Foundation parent
 * applies scale to a scoped container through its placement layer.
 */
internal class EnterExitLayerElement(
    val layout: EnterExitTransitionLayout,
) : SwingModifier.NodeElement<JComponent, EnterExitLayerNode>() {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override val additive: Boolean get() = true

    override val name: String get() = "enterExitLayer"

    override fun create(): EnterExitLayerNode = EnterExitLayerNode(layout)

    override fun update(node: EnterExitLayerNode) {
        node.layout = layout
        node.component.repaint()
    }

    override fun equals(other: Any?): Boolean = other is EnterExitLayerElement && other.layout === layout

    override fun hashCode(): Int = System.identityHashCode(layout)
}

/**
 * Paints the content faded as one image: the content is recorded into an [ImageLayer] aligned to the graphics it paints
 * on and drawn back at the alpha.
 */
internal class EnterExitLayerNode(
    var layout: EnterExitTransitionLayout,
) : DecorationModifierNode<JComponent>() {
    override val isOpaque: Boolean get() = false

    /** The block [readLayer] last read, so a paint observes the block itself along with what it combines. */
    private var observedBlock: GraphicsLayerBlockForEnterExit? = null

    // Stored once, so observing the layer allocates nothing per paint.
    private val readLayer: () -> Unit = { observedBlock = layout.graphicsLayerBlock?.also { it.update() } }

    /** Created by the first fade and kept for this node's lifetime. */
    private var fadeLayer: ImageLayer? = null

    override fun onRemovedFromDecoration() {
        fadeLayer?.release()
        fadeLayer = null
    }

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        observeReads(RepaintOnLayerChange, readLayer)
        val block = observedBlock ?: return content(graphics, width, height)
        // NaN compares false either way, so it is refused along with 0 rather than reaching AlphaComposite.
        val alpha = block.alpha.coerceAtMost(1f)
        if (!(alpha > 0f)) return
        if (alpha == 1f) {
            content(graphics, width, height)
        } else {
            paintFaded(graphics, width, height, alpha, content)
        }
    }

    private fun paintFaded(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        alpha: Float,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        val layer = fadeLayer ?: ImageLayer().also { fadeLayer = it }
        layer.alpha = alpha
        fade(layer, graphics, width, height, content)
        // A surface lost between recording and drawing is recorded and drawn once more.
        if (!Snapshot.withoutReadObservation { layer.hasContent }) fade(layer, graphics, width, height, content)
    }

    private fun fade(
        layer: ImageLayer,
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        layer.record(graphics, width, height) { content(it, width, height) }
        // Drawing reads the layer's own state, which this recording has just written: observed, the write would
        // repaint the component and record again, without end.
        Snapshot.withoutReadObservation { layer.draw(graphics) }
    }
}

/** A change to a read the layer made repaints the component it paints on. */
private val RepaintOnLayerChange: (EnterExitLayerNode) -> Unit = { it.component.repaint() }

/**
 * Takes an animated container's content out of focus traversal while it [exits], and passes on the focus a
 * component inside it holds as the exit starts.
 *
 * `setFocusable(false)` and `setEnabled(false)` leave a lightweight container's children in traversal. A focus
 * traversal policy provider whose policy accepts nothing removes them, because traversal asks the provider's policy
 * instead of descending into it.
 */
internal class ExitFocusElement(
    val exits: Boolean,
) : SwingModifier.NodeElement<Container, ExitFocusElement.Node>() {
    override val targetType: Class<Container> get() = Container::class.java

    override val name: String get() = "exitFocus"

    override val declaredValues: Map<String, Any?> get() = mapOf("exits" to exits)

    override fun create(): Node = Node(exits)

    override fun update(node: Node) {
        node.exits = exits
    }

    override fun equals(other: Any?): Boolean = other is ExitFocusElement && other.exits == exits

    override fun hashCode(): Int = exits.hashCode()

    class Node(
        exits: Boolean,
    ) : SwingModifier.ComponentNode<Container>() {
        var exits: Boolean = exits
            set(value) {
                if (field == value) return
                field = value
                if (isAttached) apply()
            }

        override fun onAttach() {
            if (exits) apply()
        }

        override fun onDetach() {
            if (exits) restore()
        }

        /** The provider flag and the policy the container held before the exit, handed back when it ends. */
        private var heldProvider = false
        private var heldPolicy: FocusTraversalPolicy? = null

        private fun restore() {
            component.focusTraversalPolicy = heldPolicy
            component.isFocusTraversalPolicyProvider = heldProvider
            heldPolicy = null
        }

        private fun apply() {
            if (!exits) return restore()
            val container = component
            heldProvider = container.isFocusTraversalPolicyProvider
            heldPolicy = if (container.isFocusTraversalPolicySet) container.focusTraversalPolicy else null
            container.isFocusTraversalPolicyProvider = true
            container.focusTraversalPolicy = ExitingContentFocusPolicy
            val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return
            if (SwingUtilities.isDescendingFrom(owner, container)) owner.transferFocus()
        }
    }
}

/**
 * A policy that offers no component. Traversal reads `null` as the end of this cycle and continues after the
 * container.
 */
private object ExitingContentFocusPolicy : FocusTraversalPolicy() {
    override fun getComponentAfter(
        container: Container,
        component: Component,
    ): Component? = null

    override fun getComponentBefore(
        container: Container,
        component: Component,
    ): Component? = null

    override fun getFirstComponent(container: Container): Component? = null

    override fun getLastComponent(container: Container): Component? = null

    override fun getDefaultComponent(container: Container): Component? = null
}
