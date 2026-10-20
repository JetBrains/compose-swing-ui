package org.jetbrains.compose.swing.foundation.graphics

import java.awt.AWTEvent
import java.awt.ActiveEvent
import java.awt.Component
import java.awt.Container
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.PaintEvent
import java.awt.geom.AffineTransform
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import kotlin.math.round

/**
 * An [ImageLayer] a decoration step keeps across paints, and the record of what it holds, so a later paint can draw
 * the cached image again instead of painting the component's content again.
 *
 * [DecorationSteps.drawCaches] lists the caches outermost first, and the recording of a cache embeds the drawing of
 * every cache after it. [invalidate] therefore reaches the cache and the caches before it.
 *
 * Members run on the event dispatch thread, except [onContentMayChange], which runs on any thread.
 */
internal class DrawCache(
    val host: DrawCacheHost,
    private var layerOrNull: ImageLayer? = null,
) {
    /** The layer that holds the cached image; the properties it draws with are set on it. */
    val layer: ImageLayer
        get() {
            checkEventDispatchThread()
            return layerOrNull ?: ImageLayer().also { layerOrNull = it }
        }

    private val redrawKeep = RedrawKeep()

    /** Advances for an invalidation, which may arrive off the event dispatch thread. */
    private val invalidationCount = AtomicInteger()

    private var cachedRecording: CachedRecording? = null

    private var redrawEvent: RedrawEvent? = null

    internal fun takeOwedRedraw(): Boolean {
        val event = redrawEvent ?: return false
        val owed = event.isOwed
        event.isOwed = false
        return owed
    }

    /**
     * Whether the cached image may be drawn again for a paint of [width] by [height] over [area], given in the
     * coordinates [record] took. When it may not, the caller records in the same paint.
     */
    fun isReusable(
        area: Rectangle,
        width: Int,
        height: Int,
    ): Boolean {
        checkEventDispatchThread()
        val cachedRecording = cachedRecording
        return cachedRecording != null &&
            cachedRecording.isValid &&
            width == cachedRecording.width &&
            height == cachedRecording.height &&
            cachedRecording.invalidationCount == invalidationCount.get() &&
            layer.recordingCount == cachedRecording.recordingCount &&
            host.component?.cachesDraw(redrawKeep.isKept) == true &&
            area in cachedRecording.area &&
            layer.hasContent
    }

    /**
     * Draws the cached image again at [x], [y], moved to this transform's device pixels, and returns `true` when it
     * drew it. The scale, shear and rotation must be unchanged and the shift whole device pixels; otherwise it draws
     * nothing and returns `false`, and the caller records in the same paint.
     *
     * Call it only when [isReusable] has returned `true` for the same paint.
     */
    fun drawCached(
        graphics: Graphics2D,
        x: Int = 0,
        y: Int = 0,
    ): Boolean {
        checkEventDispatchThread()
        val cachedRecording = cachedRecording ?: return false
        val recordedTransform = cachedRecording.transform
        val transform = graphics.transform
        val shiftX = transform.translateX - recordedTransform.translateX
        val shiftY = transform.translateY - recordedTransform.translateY
        val reusable =
            transform.scaleX == recordedTransform.scaleX &&
                transform.scaleY == recordedTransform.scaleY &&
                transform.shearX == recordedTransform.shearX &&
                transform.shearY == recordedTransform.shearY &&
                shiftX == round(shiftX) &&
                shiftY == round(shiftY)
        if (reusable) {
            if (cachedRecording.isAligned) {
                layer.draw(graphics, x + shiftX.toInt(), y + shiftY.toInt())
            } else {
                layer.draw(graphics, x, y)
            }
        }
        return reusable
    }

    /**
     * Records [layer] through [block] and caches it for paints over [area], in the coordinates [isReusable] is asked
     * in, under [graphics]' transform and at the size of the paint under way. Nothing is cached while printing or when
     * an invalidation arrives during [block].
     */
    fun record(
        graphics: Graphics2D,
        area: Rectangle,
        width: Int,
        height: Int,
        block: (ImageLayer) -> Unit,
    ) {
        checkEventDispatchThread()
        val cachedRecording = cachedRecording ?: CachedRecording().also { cachedRecording = it }
        cachedRecording.isValid = false
        // Read before the content paints, so an invalidation during the recording voids it.
        val before = invalidationCount.get()
        val lossesBefore = layer.lossCount
        val transform = graphics.transform
        block(layer)
        val component = host.component
        if (component == null || component.isPaintingForPrint) return
        cachedRecording.invalidationCount = before
        cachedRecording.recordingCount = layer.recordingCount
        cachedRecording.isAligned = layer.isAligned
        cachedRecording.lossCount = lossesBefore
        cachedRecording.width = width
        cachedRecording.height = height
        cachedRecording.area.setBounds(area)
        cachedRecording.transform.setTransform(transform)
        cachedRecording.isValid = true
    }

    /**
     * Requests a repaint when the recording this cache holds was lost by the time the paint that drew it is done: its
     * [layer] lost a surface since the recording began. The recording is dropped, so the repaint records again.
     *
     * Call it once a paint is done, never between a recording and its draw. A surface lost while it is drawn has
     * already composited onto the destination, so recording again in the same paint would composite twice.
     */
    fun repaintIfLost() {
        checkEventDispatchThread()
        val cachedRecording = cachedRecording?.takeIf { it.isValid } ?: return
        // Reading it drops a surface the graphics device discarded, which advances the loss count.
        layer.hasContent
        if (layer.lossCount == cachedRecording.lossCount) return
        cachedRecording.isValid = false
        host.component?.repaint()
    }

    /** Makes the next draw of this cache and of every cache before it record again. */
    fun invalidate() {
        checkEventDispatchThread()
        invalidationCount.incrementAndGet()
        invalidateBefore()
    }

    /** Makes every cache before this one record again; every cache of the steps where they do not list this one. */
    internal fun invalidateBefore() {
        checkEventDispatchThread()
        val caches = host.steps?.drawCaches ?: return
        val listed = caches.indexOf(this)
        // Innermost first, as in [DecorationSteps.onContentMayChange].
        val outer = if (listed < 0) caches.size else listed
        for (index in outer - 1 downTo 0) caches[index].invalidationCount.incrementAndGet()
    }

    /**
     * Invalidates for a content signal unless the redraw under way excuses it: its own repaint request, then the
     * graphics Swing takes for its paint ([outsidePaint]), once each.
     */
    internal fun onContentMayChange(
        outsidePaint: Boolean,
        onEventDispatchThread: Boolean,
    ) {
        if (onEventDispatchThread) {
            if (outsidePaint && redrawKeep.ignoresGetGraphics && !redrawKeep.ignoresRepaint) {
                redrawKeep.ignoresGetGraphics = false
                return
            }
            if (!outsidePaint && redrawKeep.ignoresRepaint) {
                redrawKeep.ignoresRepaint = false
                return
            }
        }
        invalidationCount.incrementAndGet()
    }

    internal fun onPaintBegun() {
        redrawKeep.ignoresGetGraphics = false
    }

    /**
     * Posts the redraw that [DecorationModifierNode.invalidateLayerProperties] states. Caches before this one record
     * again; this cache and the ones after it are kept.
     */
    fun redraw() {
        checkEventDispatchThread()
        val component = host.component ?: return
        invalidateBefore()
        if (redrawEvent?.isQueued == true || !component.isValid) {
            component.repaint()
        } else {
            val event = redrawEvent ?: RedrawEvent(this).also { redrawEvent = it }
            event.isOwed = true
            event.isQueued = true
            component.toolkit.systemEventQueue.postEvent(event)
        }
    }

    /** Drops the recording and frees [layer]; a redraw still queued paints nothing. */
    internal fun release() {
        invalidate()
        cachedRecording?.isValid = false
        redrawEvent?.isOwed = false
        layerOrNull?.release()
    }

    /** The event [redraw] posts. Its source is the cache so that AWT never coalesces it. */
    private class RedrawEvent(
        private val cache: DrawCache,
    ) : AWTEvent(cache, PaintEvent.UPDATE),
        ActiveEvent {
        /**
         * Whether a redraw is owed. A repaint request or a paint at once of the component settles it, as does
         * releasing the cache.
         */
        var isOwed = false

        /**
         * Whether this event waits in the event queue. Cleared only as it dispatches, so it stays set after a repaint
         * request or a paint at once settled the redraw it was posted for.
         */
        var isQueued = false

        override fun dispatch() {
            isQueued = false
            if (!cache.takeOwedRedraw()) return
            val component = cache.host.component ?: return
            val caches = cache.host.steps?.drawCaches
            val first = caches?.indexOf(cache) ?: -1
            val immediate = (component as? JComponent)?.takeIf { it.isShowing }
            val takesGraphics = immediate?.isOpaque == true
            cache.forEachKept(caches, first) {
                it.redrawKeep.isKept = true
                it.redrawKeep.ignoresRepaint = true
                it.redrawKeep.ignoresGetGraphics = takesGraphics
            }
            try {
                if (immediate != null) {
                    immediate.paintImmediately(0, 0, immediate.width, immediate.height)
                } else {
                    component.repaint()
                }
            } finally {
                // The same caches as were marked: the steps may have been replaced during the paint.
                cache.forEachKept(caches, first) {
                    it.redrawKeep.isKept = false
                    it.redrawKeep.ignoresRepaint = false
                    it.redrawKeep.ignoresGetGraphics = false
                }
            }
        }
    }
}

/** What a [DrawCache] asks of the step that owns it. */
internal interface DrawCacheHost {
    /** The component the cache draws for; null with no component. */
    val component: Component?

    /** The steps the component paints through now, which hold the caches outside the cache; null with no component. */
    val steps: DecorationSteps?
}

/** What the last [DrawCache.record] left to check a draw against; [isValid] tells whether it may be drawn again. */
private class CachedRecording {
    var isValid = false
    var invalidationCount = 0
    var recordingCount = 0
    var isAligned = false

    /** [ImageLayer.lossCount] as the recording began; a larger count since means a surface was lost. */
    var lossCount = 0
    var width = 0
    var height = 0
    val area = Rectangle()
    val transform = AffineTransform()
}

/** The marks a redraw's event puts on one cache while the redraw paints. */
private class RedrawKeep {
    /** Whether the redraw under way keeps this cache's image: the redrawing node's caches and those after them. */
    var isKept = false

    /** Whether the redraw under way has yet to make its own repaint request, which invalidates nothing it keeps. */
    var ignoresRepaint = false

    /**
     * Whether the redraw under way has yet to take the graphics Swing's `paintImmediately` takes from an opaque
     * component after the redraw's request and before its paint, which invalidates nothing it keeps.
     */
    var ignoresGetGraphics = false
}

private val Component.isPaintingForPrint: Boolean get() = this is JComponent && isPaintingForPrint

/**
 * Whether a cached image may be drawn again for this component: it is not printing, and it holds no children or the
 * paint is [redrawn], the one a [DrawCache.redraw] event makes. Swing merges a child's dirty region into a dirty
 * ancestor's, so a step learns of no repaint when what a child paints changes. That event runs only once the repaints
 * Swing queued before it have painted, which is when a child's change has made the component record.
 */
private fun Component.cachesDraw(redrawn: Boolean): Boolean =
    !isPaintingForPrint && (redrawn || this !is Container || componentCount == 0)

private inline fun DrawCache.forEachKept(
    caches: List<DrawCache>?,
    first: Int,
    action: (DrawCache) -> Unit,
) {
    if (caches == null || first < 0) return action(this)
    for (index in first until caches.size) action(caches[index])
}
