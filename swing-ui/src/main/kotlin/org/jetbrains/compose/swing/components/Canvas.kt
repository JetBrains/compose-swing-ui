@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JComponent
import javax.swing.Scrollable

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
 *     g.fillOval(width / 2 - radius, height / 2 - radius, radius * 2, radius * 2)
 * }
 * ```
 *
 * The surface is non-opaque and paints no background of its own: only what [onDraw] renders appears.
 * Size it with the preferred-size modifier (see
 * [org.jetbrains.compose.swing.modifier.layout.preferredSize]).
 *
 * @param modifier the [SwingModifier] applied to the underlying component.
 * @param onDraw receives the surface's [Graphics2D] and its current pixel `width`/`height`; called on
 *   the Swing event dispatch thread during painting. Do not retain the [Graphics2D] beyond the call.
 * @see javax.swing.JComponent.paintComponent
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
            // shared by every Canvas in this composition. It is handed over before the surface is
            // attached, and stays for the surface's whole life.
            ownerObserver { this.snapshotObserver = it }
            applyModifier(modifier)
            set(onDraw) {
                this.onDraw = it
                repaint()
            }
        },
    )
}

/**
 * The backing Swing surface for [Canvas]. Delegates painting to [onDraw] under the composition
 * owner's [SnapshotStateObserver]; see [Canvas] for the repaint contract.
 */
private class CanvasComponent :
    JComponent(),
    Scrollable {
    var onDraw: (Graphics2D, Int, Int) -> Unit = { _, _, _ -> }

    /**
     * The observer this surface registers its paint reads with, adopted from the holder's
     * `ownerObserver` in [Canvas]'s update block. The composition hands it over once, before the
     * surface is attached, and never withdraws it: parking and release leave it as it stands, since
     * neither reads through it again.
     *
     * It is `null` only before the node's first update, which no paint of a composed surface can
     * observe - [paintComponent] fails on it.
     */
    var snapshotObserver: SnapshotStateObserver? = null

    init {
        // Paints no background of its own: whatever sits behind shows through untouched pixels.
        isOpaque = false
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableUnitIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = scrollableLine()

    override fun getScrollableBlockIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = scrollablePage(visibleRect, orientation)

    override fun getScrollableTracksViewportWidth(): Boolean = fillsViewport { it.width }

    override fun getScrollableTracksViewportHeight(): Boolean = fillsViewport { it.height }

    override fun paintComponent(g: Graphics) {
        // Deliberately skips super.paintComponent: this component installs no UI delegate, so it would
        // paint nothing.
        val graphics = g as Graphics2D
        val observer =
            checkNotNull(snapshotObserver) {
                "This Canvas surface has no snapshot observer to paint under. The composition stamps " +
                    "the owner's observer onto the surface in the node's update block, which runs " +
                    "before the component is attached, so every surface a composition put in the " +
                    "Swing tree has one by its first paint."
            }
        // Track this paint's snapshot reads against this surface; a later change to one of them
        // triggers repaint(), which re-enters here and re-invokes onDraw.
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
