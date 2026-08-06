@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.Graphics
import java.awt.Graphics2D
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JComponent

/**
 * A composable that hands you the raw [Graphics2D] of a blank Swing surface so you can draw whatever
 * you like.
 *
 * **Repaint is snapshot-observed.** Any snapshot state you read *directly inside* [onDraw] is tracked;
 * when such state changes the surface repaints and re-invokes [onDraw] automatically. Read your state
 * where you use it, at paint time:
 *
 * ```
 * var radius by remember { mutableStateOf(10) }
 * Canvas(modifier = SwingModifier.preferredSize(Dimension(200, 200))) { g, width, height ->
 *     // `radius` is read here, at paint time; the surface observes it and repaints when it changes.
 *     g.fillOval(width / 2 - radius, height / 2 - radius, radius * 2, radius * 2)
 * }
 * ```
 *
 * The surface is non-opaque and paints no background of its own: only what [onDraw] renders appears.
 * Size it with the preferred-size modifier (see
 * [org.jetbrains.compose.swing.modifier.preferredSize]).
 *
 * @param modifier the [SwingModifier] applied to the underlying component.
 * @param onDraw receives the surface's [Graphics2D] and its current pixel [width]/`height`; called on
 *   the Swing event dispatch thread during painting. Do not retain the [Graphics2D] beyond the call.
 */
@Composable
public fun Canvas(
    modifier: SwingModifier = SwingModifier,
    onDraw: (g: Graphics2D, width: Int, height: Int) -> Unit,
) {
    SwingNode(
        factory = { CanvasComponent() },
        update = {
            // The owner's shared observer, stamped onto this node's holder by the applier at insert and
            // shared by every Canvas in this composition. The surface paints under it and clears its
            // scope on release.
            ownerObserver { this.snapshotObserver = it }
            applyModifier(modifier)
            set(onDraw) {
                this.onDraw = it
                repaint()
            }
        },
        // Drop this surface's tracked reads from the shared observer when the node leaves the
        // composition for good, so it stops being notified and nothing leaks. The shared observer keeps
        // running for the other surfaces; it is disposed with the composition.
        onRelease = { snapshotObserver?.clear(this) },
    )
}

/**
 * The backing Swing surface for [Canvas]. Non-opaque (it contributes no background of its own) and
 * delegates painting to [onDraw] run under the composition owner's [SnapshotStateObserver], so
 * snapshot state read inside [onDraw] at paint time is tracked and a later change to it repaints this
 * surface and re-invokes the same lambda.
 */
private class CanvasComponent : JComponent() {
    var onDraw: (Graphics2D, Int, Int) -> Unit = { _, _, _ -> }

    /**
     * The composition owner's shared snapshot observer this surface paints under, adopted from its
     * holder's `ownerObserver` in [Canvas]'s update block. `null` only before the node is inserted: the
     * applier stamps the observer on its insert pass, before this surface can join the Swing tree and be
     * painted, so it is always set when [paintComponent] runs (which fails loudly otherwise).
     */
    var snapshotObserver: SnapshotStateObserver? = null

    init {
        // A canvas is a transparent overlay by convention: it must not paint a default background,
        // so whatever sits behind it shows through wherever onDraw leaves pixels untouched.
        isOpaque = false
    }

    override fun paintComponent(g: Graphics) {
        // Non-opaque: deliberately skip super.paintComponent (it would do nothing useful here).
        val graphics = g as Graphics2D
        // The applier stamps the owner's snapshot observer onto this node on its top-down insert pass,
        // before the node's update changes run and before the surface joins the Swing tree, so it is
        // always set by the time this surface is painted.
        val observer =
            checkNotNull(snapshotObserver) {
                "Canvas was painted before its snapshot observer was wired. The composition owner's " +
                    "observer is stamped onto each node on the applier's top-down insert pass, before " +
                    "the surface joins the Swing tree, so it must be set by paint time."
            }
        // Run onDraw under the owner observer so any snapshot state it reads is tracked against this
        // surface; when that state changes the observer repaints this surface, which re-enters here and
        // re-invokes the same lambda with the new values.
        observer.observeReads(scope = this, onValueChangedForScope = { it.repaint() }) {
            onDraw(graphics, width, height)
        }
    }

    /**
     * Reports the intrinsic [AccessibleRole.CANVAS] to assistive technologies. A plain [JComponent]
     * would otherwise report the generic [AccessibleRole.SWING_COMPONENT], which understates a drawing
     * surface.
     */
    override fun getAccessibleContext(): AccessibleContext {
        if (accessibleContext == null) {
            accessibleContext =
                object : AccessibleJComponent() {
                    override fun getAccessibleRole(): AccessibleRole = AccessibleRole.CANVAS
                }
        }
        return accessibleContext
    }
}
