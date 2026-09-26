package org.jetbrains.compose.swing.window

import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.annotations.WindowExtendedState
import java.awt.Dimension
import java.awt.Frame
import java.awt.Insets
import java.awt.Point
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.WindowStateListener

/**
 * Records the geometry (and, for frames, the extended state) that is currently in sync between a
 * hoisted state and its realized AWT window.
 *
 * Both the state-to-window apply and the window-to-state write-back update this holder, which is what
 * keeps the two directions from fighting: the apply skips whenever the declared value already matches
 * what was last applied, so a resize or move that originated from the user (and was written straight
 * back here) is never re-applied on the next recomposition.
 */
internal class AppliedGeometry {
    var position: WindowPosition? = null
    var width: Int? = null
    var height: Int? = null
    var extendedState: Int? = null

    /**
     * The coordinates the last apply asked the window to stand on, while a report of them is still
     * awaited; null while no apply has named any, and from the report that settles them onwards.
     *
     * A window system places a window asynchronously, and while it is doing so it answers a further
     * placement late or not at all. What was asked for is therefore not what the window reports in
     * that stretch, and holding on to it is what a report arriving in it is settled against. A
     * placement is settled by the report that confirms it and equally by one that supersedes it: a
     * window a user has since moved stands where they left it, whatever was asked of the window
     * system beforehand.
     */
    var placement: Point? = null

    /**
     * The coordinates that were in force before [placement] was asked for. A window system still
     * catching up reports the window standing on exactly these, which is what tells its report apart
     * from a move of the user's - a user leaves the window somewhere neither the composition nor the
     * window system has put it.
     */
    var supersededPlacement: Point? = null

    /**
     * Whether the window was already asked a second time to stand on [placement] after the window
     * system reported it still on [supersededPlacement]. A window system that keeps a window where it
     * has put it has the final say, so the composition insists once and then records where the window
     * actually stands rather than trading placements with it indefinitely.
     */
    var placementReasserted: Boolean = false

    /**
     * The insets the window's decorations took when [width] by [height] was asked for, while the window
     * system may still report the window off that size; null for a size the composition did not name -
     * one fitted to the content, or one the user left the window at. A window getting a peer records a
     * recorded size with the insets the peer starts with.
     *
     * A window manager that frames a window reports the insets its decorations take only once the window
     * is on screen. Until then the X11 toolkit sizes the window's content around guessed insets, so the
     * framed window first stands off the size asked for by the difference in insets, and a size replaced
     * in the meantime is reported late, off by that difference or at that size itself. Neither is a
     * resize of the user's. A report under the insets a size was asked for around is never such a
     * correction.
     *
     * A size stays recorded once the toolkit has reported it late, which it may do more than once, and
     * when the toolkit answers it with no late report at all. So after the window is framed, a report that
     * lands exactly on that size off by the difference in insets, or on the size it replaced, is still
     * taken for the toolkit's until any other resize.
     */
    var sizeRequestInsets: Insets? = null

    /**
     * Whether the toolkit has reported [width] by [height] late since it was asked for. A size asked for
     * after that does not keep it as [supersededSize]: the toolkit has already reported it, so a later
     * report of it is a user's resize.
     */
    var sizeReportedLate: Boolean = false

    /**
     * The size [width] by [height] replaced while [sizeRequestInsets] stood, with the insets it was asked
     * for around; null when there is none, or once the toolkit has reported it at itself. Only the latest
     * replaced size is kept, so one replaced before it is written back if the toolkit reports it late.
     */
    var supersededSize: SizeRequest? = null
}

/** A [width] by [height] asked of a window, and the [insets] its decorations took when it was. */
internal class SizeRequest(
    val width: Int,
    val height: Int,
    val insets: Insets,
)

/**
 * Whether a window [reportedWidth] by [reportedHeight] is the toolkit reporting this size late, now that
 * the window's decorations take [current] insets - see [AppliedGeometry.sizeRequestInsets].
 */
private fun SizeRequest.isReportedLateAs(
    reportedWidth: Int,
    reportedHeight: Int,
    current: Insets,
): Boolean {
    val widthChange = current.left + current.right - insets.left - insets.right
    val heightChange = current.top + current.bottom - insets.top - insets.bottom
    val insetsChanged = widthChange != 0 || heightChange != 0
    val offByTheChange = reportedWidth == width + widthChange && reportedHeight == height + heightChange
    return insetsChanged && (offByTheChange || (reportedWidth == width && reportedHeight == height))
}

/**
 * Pushes the declared [position], [width] and [height] onto this window when they differ from what is
 * already in sync, updating [applied] to match. Runs on the event dispatch thread (the composition's
 * Swing dispatcher), so the AWT mutations are thread-safe.
 *
 * An unspecified size (a 0x0 [width] by [height]) sizes the window to its content's preferred size via
 * [java.awt.Window.pack]; the realized size then flows back into the state through the geometry
 * write-back listener, so declaring 0x0 again fits the window to whatever its content has become. A
 * size of any other value is applied verbatim through [java.awt.Component.setSize].
 *
 * A [WindowPosition.PlatformDefault] position is left to the platform, while a centering position
 * resolves against the screen, the owning window or a named window. The position is applied after
 * the size, so centering measures the size the window has just been given.
 */
internal fun Window.applyGeometry(
    position: WindowPosition,
    width: Int,
    height: Int,
    applied: AppliedGeometry,
) {
    // A size settles like any other declared value: it is applied as it arrives and not asserted again
    // while it stands, so a window the user has resized is left where they left it. Sizing to the content
    // is the same declaration written 0 by 0, and the size the window takes from it flows back into the
    // state - which is what makes a later 0 by 0 a change again, and re-fits the window to content that
    // has since grown.
    if (applied.width != width || applied.height != height) applySize(width, height, applied)
    if (applied.position != position) {
        // Read before the placement below moves the window: these are the coordinates in force until
        // that placement takes.
        val locationInForce = location
        // The coordinates the window is asked to stand on, or null where the position names none and
        // leaves the window where it already is. A window system need not have moved the window by the
        // time it answers, so what was asked for - not what the window reports afterwards - is what a
        // later report has to be settled against. A centering position resolves against the screen, the
        // owner or a named window as the window system does it, so the coordinates it settles on are
        // read back.
        val placement =
            when (position) {
                WindowPosition.PlatformDefault -> {
                    null
                }

                WindowPosition.CenteredOnScreen -> {
                    setLocationRelativeTo(null)
                    location
                }

                // A null reference component centers on the screen, which is what an ownerless window
                // owes here, so the absence of an owner needs no case of its own.
                WindowPosition.CenteredOnOwner -> {
                    setLocationRelativeTo(owner)
                    location
                }

                is WindowPosition.CenteredOn -> {
                    setLocationRelativeTo(position.window)
                    location
                }

                is WindowPosition.Absolute -> {
                    setLocation(position.x, position.y)
                    Point(position.x, position.y)
                }
            }
        applied.position = position
        if (placement != null) {
            // What a window system that has not caught up reports the window standing on: a placement
            // still outstanding is the one it is performing, and with none outstanding it leaves the
            // window where it was until this placement takes.
            applied.supersededPlacement = applied.placement ?: locationInForce
            applied.placement = placement
            applied.placementReasserted = false
        }
    }
}

/** Pushes a [width] by [height] that differs from the one in sync onto this window, as [applyGeometry] describes. */
private fun Window.applySize(
    width: Int,
    height: Int,
    applied: AppliedGeometry,
) {
    if (width == 0 && height == 0) {
        // The toolkit keeping the content's size when it frames the window is what fitting the content
        // asks for, so there is nothing to hold the window to.
        Snapshot.withoutReadObservation { pack() }
        applied.supersededSize = null
        applied.sizeRequestInsets = null
        applied.sizeReportedLate = false
    } else {
        // Read before the resize: the toolkit thread may correct its guessed insets at any moment, and a size
        // recorded with insets corrected after it was sized would have its late report taken for a user's
        // resize.
        val requestInsets = insets
        setSize(width, height)
        val replacedUnder = applied.sizeRequestInsets.takeUnless { applied.sizeReportedLate }
        val replacedWidth = applied.width
        val replacedHeight = applied.height
        applied.supersededSize =
            if (replacedUnder != null && replacedWidth != null && replacedHeight != null) {
                SizeRequest(replacedWidth, replacedHeight, replacedUnder)
            } else {
                null
            }
        applied.sizeRequestInsets = requestInsets
        applied.sizeReportedLate = false
    }
    applied.width = width
    applied.height = height
}

/**
 * Pushes the declared [extendedState] onto this frame when it differs from what is already in sync,
 * updating [applied] to match. Runs on the event dispatch thread, like [applyGeometry].
 */
internal fun Frame.applyExtendedState(
    @WindowExtendedState extendedState: Int,
    applied: AppliedGeometry,
) {
    if (applied.extendedState != extendedState) {
        this.extendedState = extendedState
        applied.extendedState = extendedState
    }
}

/**
 * Registers a listener that writes user-driven maximize, minimize and restore transitions of this
 * frame back through [setExtendedState], keeping [applied] equal to the value it hands to the state.
 * Recording [applied] here closes the feedback loop the same way [installGeometryWriteBack] does for
 * moves and resizes.
 *
 * Returns the registered listener so the caller can remove it when the window leaves the composition.
 */
internal fun Frame.installExtendedStateWriteBack(
    applied: AppliedGeometry,
    setExtendedState: (Int) -> Unit,
): WindowStateListener {
    val listener =
        WindowStateListener { event ->
            val newState = event.newState
            // A transition whose result already matches what was last applied is an echo of our own
            // apply (or a stale event); leaving the state untouched keeps a declared change from
            // being reverted before the next apply observes it.
            if (applied.extendedState != newState) {
                applied.extendedState = newState
                setExtendedState(newState)
            }
        }
    addWindowStateListener(listener)
    return listener
}

/**
 * Registers a listener that writes user-driven resizes and moves of this window back through [setSize]
 * and [setPosition], keeping [applied] equal to the value it hands to the state. Recording [applied]
 * here is what closes the feedback loop: the next apply sees the state and [applied] already agree and
 * does nothing.
 *
 * A move is only the user's once the window is on screen, so while the window is off screen the
 * coordinates the composition declared stand and the placement the platform gives the window instead is
 * left out of the state. A position that names no coordinates ([WindowPosition.PlatformDefault] and the
 * centering positions) hands the placement to the platform, so the coordinates it settles on are that
 * position resolving and do reach the state.
 *
 * A move is only the user's once the placement the composition asked for has settled, too: a window
 * system busy putting a window on screen reports the placement it is performing rather than one asked
 * of it in the meantime - see [AppliedGeometry.placement].
 *
 * A resize is only the user's once the toolkit has reported the sizes asked for - see
 * [AppliedGeometry.sizeRequestInsets].
 *
 * Returns a function that removes the registered listeners, for when the window leaves the composition.
 */
internal fun Window.installGeometryWriteBack(
    applied: AppliedGeometry,
    setPosition: (WindowPosition) -> Unit,
    setSize: (width: Int, height: Int) -> Unit,
): () -> Unit {
    val listener =
        object : ComponentAdapter(), HierarchyListener {
            override fun hierarchyChanged(e: HierarchyEvent) {
                // A size asked for before the window had a peer is sized around the insets the peer
                // starts with, and one replaced before then never reached it.
                val becameDisplayable = (e.changeFlags and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong()) != 0L
                if (becameDisplayable && isDisplayable && applied.sizeRequestInsets != null) {
                    applied.sizeRequestInsets = insets
                    applied.sizeReportedLate = false
                    applied.supersededSize = null
                }
            }

            /**
             * Whether the window is on screen, as the window's own show and hide notifications report
             * it. This trails [java.awt.Window.isVisible], which already answers `true` while the
             * window is being realized: the placement the platform gives a window it is putting on
             * screen is notified before the show itself is.
             */
            private var onScreen = false

            override fun componentShown(e: ComponentEvent) {
                onScreen = true
            }

            override fun componentHidden(e: ComponentEvent) {
                onScreen = false
            }

            override fun componentResized(e: ComponentEvent) {
                val newWidth = width
                val newHeight = height
                // A resize whose result already matches what was last applied is an echo of our own
                // apply (or a stale event); leaving the state untouched keeps a declared change from
                // being reverted before the next apply observes it.
                if (applied.width == newWidth && applied.height == newHeight) return
                val requested = requestedSizeResizedTo(newWidth, newHeight)
                if (requested != null) {
                    this@installGeometryWriteBack.size = requested
                    return
                }
                applied.width = newWidth
                applied.height = newHeight
                applied.supersededSize = null
                applied.sizeRequestInsets = null
                applied.sizeReportedLate = false
                setSize(newWidth, newHeight)
            }

            /**
             * The size the composition asked for, when a window [reportedWidth] by [reportedHeight] is
             * the toolkit reporting it, or the size it replaced, late - see
             * [AppliedGeometry.sizeRequestInsets]; null when the report is anything else. The size asked
             * for stands, so the caller asks for it again.
             */
            private fun requestedSizeResizedTo(
                reportedWidth: Int,
                reportedHeight: Int,
            ): Dimension? {
                val requestedUnder = applied.sizeRequestInsets
                val requestedWidth = applied.width
                val requestedHeight = applied.height
                if (requestedUnder == null || requestedWidth == null || requestedHeight == null) return null
                val current = insets
                val requested = SizeRequest(requestedWidth, requestedHeight, requestedUnder)
                val superseded = applied.supersededSize
                return when {
                    superseded?.isReportedLateAs(reportedWidth, reportedHeight, current) == true -> {
                        // Reported off by the change in insets first, and at itself last, once the toolkit
                        // has resized the window back to it.
                        if (reportedWidth == superseded.width && reportedHeight == superseded.height) {
                            applied.supersededSize = null
                        }
                        Dimension(requestedWidth, requestedHeight)
                    }

                    requested.isReportedLateAs(reportedWidth, reportedHeight, current) -> {
                        // The toolkit reports the sizes asked for in the order they were asked for, so
                        // the size replaced has been reported for the last time. This one may be
                        // reported so again, so it stays recorded.
                        applied.supersededSize = null
                        applied.sizeReportedLate = true
                        Dimension(requestedWidth, requestedHeight)
                    }

                    else -> {
                        null
                    }
                }
            }

            override fun componentMoved(e: ComponentEvent) {
                if (!reconcileWithOutstandingPlacement()) return
                val newPosition = WindowPosition.Absolute(x, y)
                // An echo of our own apply, like the resize case above: skip it so a declared change is
                // not reverted before the next apply observes it.
                val echoesTheLastApply = applied.position == newPosition
                // Nobody can drag a window that is not on screen, so a move that contradicts declared
                // coordinates while the window is off screen is the platform placing the window; the
                // declaration stands. A position that names no coordinates is resolved by exactly such
                // a placement, so the coordinates it lands on are written back.
                val isPlatformPlacement = !onScreen && applied.position is WindowPosition.Absolute
                if (echoesTheLastApply || isPlatformPlacement) return
                applied.position = newPosition
                setPosition(newPosition)
            }

            /**
             * Settles this report against the placement the composition is still waiting on, and
             * answers whether it is one the state should hear about.
             *
             * A report standing on those coordinates is the window system confirming them, and
             * nothing is outstanding from then on. One standing on the coordinates they superseded is
             * the window system reporting the placement it was performing when the newer one was asked
             * for - it answers a placement asked for in that stretch late or not at all - so the window
             * is asked again, which is what makes the declared position stand. Everything else is the
             * window standing where neither was going to put it, which is a move of the user's, and a
             * user's move supersedes the placement outstanding at the time as surely as a report of it
             * confirms it.
             */
            private fun reconcileWithOutstandingPlacement(): Boolean {
                val outstanding = applied.placement
                return when {
                    outstanding == null -> {
                        true
                    }

                    location == outstanding -> {
                        applied.placement = null
                        true
                    }

                    location != applied.supersededPlacement || applied.placementReasserted -> {
                        // The window stands where the composition is not going to put it: the
                        // outstanding placement has been superseded, and the coordinates the next one
                        // supersedes are the ones the window stands on now.
                        applied.placement = null
                        true
                    }

                    else -> {
                        applied.placementReasserted = true
                        setLocation(outstanding.x, outstanding.y)
                        false
                    }
                }
            }
        }
    addComponentListener(listener)
    addHierarchyListener(listener)
    return {
        removeComponentListener(listener)
        removeHierarchyListener(listener)
    }
}
