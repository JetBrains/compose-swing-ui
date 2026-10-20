package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.foundation.layout.fitted
import org.jetbrains.compose.swing.foundation.layout.writeFitted
import org.jetbrains.compose.swing.foundation.util.fastAny
import org.jetbrains.compose.swing.foundation.util.fastForEach
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.requestAfterValidation
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Rectangle

/**
 * A modifier node that is one step of its component's decoration, at the node's position in the modifier:
 * it wraps every step declared after it and the component's own painting, and is wrapped by every step
 * declared before it.
 *
 * The element creating it must be [additive][SwingModifier.NodeElement.additive], since that is what gives the
 * node its position among the other steps; a node whose element is not additive is refused as it attaches. A
 * child declares that element through [decoration].
 *
 * The library gathers [outsets] and [isOpaque] after each modifier pass that attaches, detaches, moves or writes a
 * step, so an element's `update` needs no call for them. Decoration nodes automatically invalidate their component's
 * declared-node consumer after an element update. A node with fixed metadata can opt out and own its repaint, as
 * [DrawModifierNode] does. A node that changes one of them between passes calls [invalidateDecoration]. A change that
 * only affects [paint] is a `component.repaint()`. The outsets a step reserves
 * reach the component's insets under a Foundation container. Under any other parent only the part of them the
 * component's [PaintOutsets][org.jetbrains.compose.swing.foundation.layout.PaintOutsets] value leaves in layout does,
 * none by default.
 */
public abstract class DecorationModifierNode<T : Component> :
    SwingModifier.ComponentNode<T>(),
    Decorator {
    /** An element update may change the metadata its component gathers from the declared decoration nodes. */
    public override val shouldAutoInvalidate: Boolean get() = true

    /**
     * Refuses an element that is not additive. An element naming [Decoratable] as its target type is refused
     * before this node is created; otherwise this node is refused if the component is not a [Decoratable].
     *
     * @throws IllegalStateException if the element creating this node is not
     *   [additive][SwingModifier.NodeElement.additive], or the component does not paint through a decoration.
     */
    final override fun onAttach() {
        check(declaredNodes().fastAny { it === this }) {
            "A decoration step takes its place among the others from its element, so ${javaClass.name} must be " +
                "created by an additive element"
        }
        checkNotNull(component as? Decoratable) {
            "A decoration step requires a ${Decoratable::class.java.name} target, but the component is a " +
                "${component.javaClass.name}"
        }
    }

    /**
     * Runs [onRemovedFromDecoration] and releases the layers [obtainImageLayer] made. The component is handed the
     * decoration without this step once the modifier pass ends.
     */
    final override fun onDetach() {
        onRemovedFromDecoration()
        drawCaches.fastForEach { it.release() }
    }

    /** Runs where [onDetach] would, while the node is still attached and still part of the decoration. */
    protected open fun onRemovedFromDecoration() {}

    /**
     * A new [ImageLayer] for this step to keep a drawing in across paints, through [drawCached] and [recordCached]. A
     * recording made with [ImageLayer.record] directly is never reused. A repaint request on the component, or graphics
     * taken from it outside paint, makes the layer record again. The step owns the layer until it leaves the
     * decoration, which releases it. Call this before the node attaches, such as in a property initializer.
     *
     * @throws IllegalStateException if the node is attached.
     */
    protected fun obtainImageLayer(): ImageLayer {
        check(!isAttached) { "${javaClass.name} obtains its image layers before it attaches" }
        val host = cacheHost ?: StepCacheHost().also { cacheHost = it }
        val layer = ImageLayer()
        drawCaches = drawCaches + DrawCache(host, layer)
        return layer
    }

    private inner class StepCacheHost : DrawCacheHost {
        override val component: Component? get() = if (isAttached) this@DecorationModifierNode.component else null

        override val steps: DecorationSteps? get() = if (isAttached) decoratable.decoration.steps else null
    }

    private var cacheHost: DrawCacheHost? = null

    internal var drawCaches: List<DrawCache> = emptyList()
        private set

    /** The size of the paint under way; negative outside this node's paint. */
    private var paintWidth = -1
    private var paintHeight = -1

    /** Paints through [paint] at [width] by [height], the size [drawCached] and [recordCached] take for the paint. */
    internal fun paintAtSize(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        val outerWidth = paintWidth
        val outerHeight = paintHeight
        paintWidth = width
        paintHeight = height
        try {
            paint(graphics, width, height, content)
        } finally {
            paintWidth = outerWidth
            paintHeight = outerHeight
        }
    }

    private fun cacheOf(layer: ImageLayer): DrawCache {
        checkEventDispatchThread()
        check(paintWidth >= 0) { "${javaClass.name} draws its image layers from its paint" }
        drawCaches.fastForEach { if (it.layer === layer) return it }
        throw IllegalArgumentException("${javaClass.name} draws only the image layers it obtained")
    }

    /**
     * Draws this layer's cached image at [x], [y] and returns `true`, or draws nothing and returns `false`. On `false`,
     * the caller records in the same paint.
     *
     * The image is drawn only while nothing made the layer record again since [recordCached] began, the layer holds the
     * recording and the component is not printing. A component with children draws it only in the paint that
     * [invalidateLayerProperties] asks for. The paint must have the recorded size, an [area] inside the recorded one,
     * the recorded scale, shear and rotation, and a shift of whole device pixels.
     *
     * Call it from this node's [paint], on the event dispatch thread, for a layer from [obtainImageLayer].
     *
     * @param graphics the graphics to draw on.
     * @param area the area this paint needs, in the coordinates [recordCached] was given.
     * @param x the horizontal position of the recording's origin, as [ImageLayer.draw] takes it. Defaults to `0`.
     * @param y the vertical position of the recording's origin, as [ImageLayer.draw] takes it. Defaults to `0`.
     * @throws IllegalStateException if called off the event dispatch thread or outside this node's paint.
     * @throws IllegalArgumentException for a layer this node did not obtain.
     */
    protected fun ImageLayer.drawCached(
        graphics: Graphics2D,
        area: Rectangle,
        x: Int = 0,
        y: Int = 0,
    ): Boolean =
        with(cacheOf(this)) {
            isReusable(area, paintWidth, paintHeight) && drawCached(graphics, x, y)
        }

    /**
     * Records this layer through [block], on any destination, and caches it for paints over [area] under [graphics]'
     * transform, at the size of the paint under way. Nothing is cached while the component prints or when a request to
     * record again arrives during [block].
     *
     * Call it from this node's [paint], on the event dispatch thread, for a layer from [obtainImageLayer].
     *
     * @param graphics the graphics the paint is under; left as it was found.
     * @param area the area [block] records, in the coordinates [drawCached] is asked in.
     * @param block records the layer.
     * @throws IllegalStateException if called off the event dispatch thread or outside this node's paint.
     * @throws IllegalArgumentException for a layer this node did not obtain.
     */
    protected fun ImageLayer.recordCached(
        graphics: Graphics2D,
        area: Rectangle,
        block: (ImageLayer) -> Unit,
    ) {
        cacheOf(this).record(graphics, area, paintWidth, paintHeight, block)
    }

    /**
     * Makes every layer of this node, and of each step declared before it, record on its next draw. On a node with no
     * layer from [obtainImageLayer], it makes every layer of the component record. Requests no repaint.
     *
     * Call it on the event dispatch thread when something a recording is made from changes, such as a property of the
     * step. Off it, a repaint request of the component makes every layer of the component record.
     *
     * @throws IllegalStateException if called off the event dispatch thread.
     */
    public fun invalidateDrawCache() {
        checkEventDispatchThread()
        val owned = if (drawCaches.isEmpty() && isAttached) decoratable.decoration.steps.drawCaches else drawCaches
        owned.lastOrNull()?.invalidate()
    }

    /**
     * Asks for a paint that draws every layer of this node again with the properties now set on it, as a layer property
     * change does. That paint also shows the repaints of the component's children requested before the call.
     *
     * The layers of the steps declared before this node record again; this node's layers and those declared after it
     * are drawn again. A component with children reuses the images only in that paint.
     *
     * A further call before that paint, or a call on a component that is not valid, can call `repaint()` instead, which
     * makes every layer record again. Until that paint runs, a repaint request on the component, or a
     * `paintImmediately` of it, on the event dispatch thread covers its whole bounds and takes that paint's place.
     *
     * With no layer from [obtainImageLayer], it repaints the component. Does nothing while the node is not attached.
     *
     * Limits:
     * - Where an ancestor's paint paints an opaque component, the first graphics the ancestor takes from the component
     *   before the component paints make no layer record.
     * - A fade inside another fade on one component can show the inner fade one paint old, until the inner fade's own
     *   paint.
     * - A partial repaint of an ancestor between a property change and its redraw shows that part at the new value one
     *   event early.
     * - A transform that starts or stops may paint the content twice in one frame.
     *
     * @throws IllegalStateException if called off the event dispatch thread.
     */
    public fun invalidateLayerProperties() {
        checkEventDispatchThread()
        if (!isAttached) return
        val first = drawCaches.firstOrNull()
        if (first == null) component.repaint() else first.redraw()
    }

    /** Whether this step paints its content unchanged and takes no outsets, so the component is not decorated by it. */
    internal open val paintsNothing: Boolean get() = false

    /**
     * The component as a [Decoratable]; reading it before the node is attached, or after it has been detached, fails,
     * as [component] does.
     */
    protected val decoratable: Decoratable
        // onAttach refuses any other target.
        get() = component as Decoratable

    /**
     * Gathers the component's decoration again, after this step's [outsets] or [isOpaque] changed between modifier
     * passes, and repaints it. An element's `update` needs no call. Does nothing while the node is not attached.
     */
    public fun invalidateDecoration() {
        if (!isAttached) return
        if (!publishDecoration(
                decoratable,
                declaredNodes(),
                this,
                SwingModifier.ComponentNode<*>::revalidateComponentAfterValidation,
            )
        ) {
            component.repaint()
        }
    }
}

/** The nodes declared on this node's component, in declaration order. */
internal fun SwingModifier.Node.declaredNodes(): List<SwingModifier.Node> {
    val nodes = ArrayList<SwingModifier.Node>()
    visitDeclaredNodes { nodes += it }
    return nodes
}

/** Publishes the decoration [nodes] declare without requesting revalidation. */
internal fun publishDecoration(
    decoratable: Decoratable,
    nodes: List<SwingModifier.Node>,
): Boolean = publishDecoration<Any>(decoratable, nodes)

/**
 * Writes to [decoratable] the decoration [nodes] declare, inside the layer its container places it with, fitted to
 * the Foundation containers around it; see [publishSteps]. Without a [requester], nothing is revalidated.
 */
internal fun <R : Any> publishDecoration(
    decoratable: Decoratable,
    nodes: List<SwingModifier.Node>,
    requester: R? = null,
    revalidate: ((R) -> Unit)? = null,
): Boolean =
    publishSteps(
        decoratable,
        DecorationSteps.of(nodes, decoratable.decoration.steps),
        requester,
        revalidate,
    )

/**
 * Writes to [decoratable] a decoration of [steps], fitted to the Foundation containers around it, and repaints it;
 * nothing where the value held equals it. Returns whether it wrote.
 * Where the fit revalidates the component, it runs [revalidate] on [requester]; see [writeFitted].
 * Without a [requester], nothing is revalidated.
 *
 * @throws IllegalStateException if [decoratable] is not a [Component].
 */
internal fun <R : Any> publishSteps(
    decoratable: Decoratable,
    steps: DecorationSteps,
    requester: R? = null,
    revalidate: ((R) -> Unit)? = null,
): Boolean {
    val component =
        checkNotNull(decoratable as? Component) {
            "A ${Decoratable::class.java.name} paints as a component, and ${decoratable.javaClass.name} is not one"
        }
    val held = decoratable.decoration
    val value = held.fitted(component, steps, steps.isOpaque)
    if (value === held) return false
    decoratable.writeFitted(held, value, requester = requester, revalidate = revalidate)
    component.repaint()
    return true
}

internal fun Decoratable.requireComponent(): Component =
    requireNotNull(this as? Component) {
        "A ${Decoratable::class.java.name} paints as a component, and ${javaClass.name} is not one"
    }

/** One instance, so repeated requests for a node coalesce. */
private val RevalidateNodeComponent: (SwingModifier.ComponentNode<*>) -> Unit = { it.component.revalidate() }

/** Revalidates this attached node's component once Swing finishes validation, unless the node detaches first. */
internal fun SwingModifier.ComponentNode<*>.revalidateComponentAfterValidation() {
    if (Thread.holdsLock(component.treeLock)) component.invalidate()
    requestAfterValidation(RevalidateNodeComponent)
}
