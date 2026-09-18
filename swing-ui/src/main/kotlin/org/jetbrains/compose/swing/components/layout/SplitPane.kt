@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.annotations.SplitOrientation
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.rememberMirrorState
import org.jetbrains.compose.swing.platform.LookAndFeelDefaults
import java.beans.PropertyChangeListener
import javax.swing.JSplitPane

/**
 * Two sides of one area, split by a divider the user drags to give one side room at the other's expense
 * - a `JSplitPane`. The pane holds the divider offset and reports the moves the user makes.
 *
 * The pane holds its children on two sides of its own, `first` and `second`, rather than among indexed
 * children, so every child names the side it occupies on its own modifier, through [SplitPaneScope]:
 * ```
 * SplitPane(orientation = JSplitPane.HORIZONTAL_SPLIT) {
 *     Navigator(modifier = SwingModifier.first())
 *     Editor(modifier = SwingModifier.second())
 * }
 * ```
 * A side hosts one child: dropping a child (e.g. behind an `if`) empties the side it occupied, a side no
 * child names stays empty, and a child that names no side at all is refused.
 *
 * Pass an offset as [dividerLocation] to place the divider; [onDividerLocationChange] fires with
 * the new offset when the user moves it. An offset is applied when it changes and is not asserted
 * again, so a divider the user has dragged stays where they left it. The default `-1` is
 * `JSplitPane`'s own initial divider location, asking the pane to derive the position from the sides'
 * preferred sizes (shaped by [resizeWeight]); the pane keeps that request as its divider location until
 * it is realized on screen, at which point it resolves the position itself - that resolution is the
 * look and feel settling the request, not a move, and is not reported.
 *
 * @param modifier the [SwingModifier] applied to the underlying `JSplitPane`
 * @param orientation the axis along which the two sides are arranged; the default
 *   `HORIZONTAL_SPLIT` puts them side by side, with the divider running top to bottom
 * @param dividerLocation the divider offset (controlled), a value the look and feel reads; a
 *   negative offset - the default `-1` is `JSplitPane`'s own initial divider location - resets the
 *   divider to honor the sides' preferred sizes
 * @param onDividerLocationChange callback invoked with the new offset when the user moves the
 *   divider; an offset the declaration itself applies is not reported, nor is the position a negative
 *   request resolves to once the pane is realized on screen
 * @param resizeWeight how extra space is shared when the pane resizes, from `0.0` (all to the second
 *   side) to `1.0` (all to the first side); the default `0.0` leaves the first side the size it has,
 *   and a weight outside `0.0`..`1.0` is refused
 * @param oneTouchExpandable whether the divider carries a widget that collapses either side in one
 *   click; `null` leaves the choice to the installed look and feel, withdrawing a declared choice hands
 *   it back, and a look and feel that does not support one-touch expanding ignores it
 * @param dividerSize the divider thickness; `null` leaves the size to the installed look and
 *   feel, and withdrawing a declared size hands it back
 * @param continuousLayout whether the two sides are laid out continuously as the divider is dragged
 *   rather than once it is released, where the drag draws an outline of where the divider is heading;
 *   `null` leaves the choice to the installed look and feel, and withdrawing a declared choice hands it
 *   back
 * @param content the composable content of the pane; see [SplitPaneScope]
 * @see javax.swing.JSplitPane
 */
@Composable
public fun SplitPane(
    modifier: SwingModifier = SwingModifier,
    @SplitOrientation orientation: Int = JSplitPane.HORIZONTAL_SPLIT,
    dividerLocation: Int = -1,
    onDividerLocationChange: (Int) -> Unit = {},
    resizeWeight: Double = 0.0,
    oneTouchExpandable: Boolean? = null,
    dividerSize: Int? = null,
    continuousLayout: Boolean? = null,
    content: @Composable SplitPaneScope.() -> Unit,
) {
    val mirror = rememberMirrorState(dividerLocation)
    // The pane publishes its new offset for every move, its own and the user's alike, including the
    // position a negative request resolves to once realized on screen. The binding answers which is
    // which by value: a move that lands on the declaration is the declaration arriving, and a move
    // answering a negative request the mirror still holds is that same resolution, settled into the
    // mirror without being reported. A move away from either is the user's, reported once, and every
    // later move is then measured against the resolved position.
    val onMoved: (Int) -> Unit = { moved ->
        if (dividerLocation < 0 && mirror.value == dividerLocation) {
            mirror.observed(moved)
        } else if (mirror.observed(moved)) {
            onDividerLocationChange(moved)
        }
    }
    SplitPaneImpl(
        modifier =
            modifier.propertyChangeListener<JSplitPane>(JSplitPane.DIVIDER_LOCATION_PROPERTY) {
                onMoved(this.dividerLocation)
            },
        orientation = orientation,
        dividerLocation = dividerLocation,
        mirror = mirror,
        resizeWeight = resizeWeight,
        oneTouchExpandable = oneTouchExpandable,
        dividerSize = dividerSize,
        continuousLayout = continuousLayout,
        content = content,
    )
}

/**
 * A [SplitPane] driven by a raw [PropertyChangeListener] instead of an `onDividerLocationChange`
 * lambda. The listener is attached for the `dividerLocation` property as-is and removed on the same
 * instance; pass a stable instance (e.g. `remember {}`) to avoid churn. Attached as-is, it hears every
 * `dividerLocation` change the pane publishes, including the pane's own writes and, for a negative
 * (default) [dividerLocation], the position that request resolves to once realized on screen -
 * `old=-1 new=<resolved>` - indistinguishable from a user's move.
 *
 * @param dividerLocationListener the listener notified when the `dividerLocation` property changes
 * @param modifier the [SwingModifier] applied to the underlying `JSplitPane`
 * @param orientation the axis along which the two sides are arranged; the default
 *   `HORIZONTAL_SPLIT` puts them side by side, with the divider running top to bottom
 * @param dividerLocation the divider offset (controlled), a value the look and feel reads; a
 *   negative offset - the default `-1` is `JSplitPane`'s own initial divider location - resets the
 *   divider to honor the sides' preferred sizes
 * @param resizeWeight how extra space is shared when the pane resizes, from `0.0` (all to the second
 *   side) to `1.0` (all to the first side); the default `0.0` leaves the first side the size it has,
 *   and a weight outside `0.0`..`1.0` is refused
 * @param oneTouchExpandable whether the divider carries a widget that collapses either side in one
 *   click; `null` leaves the choice to the installed look and feel, withdrawing a declared choice hands
 *   it back, and a look and feel that does not support one-touch expanding ignores it
 * @param dividerSize the divider thickness; `null` leaves the size to the installed look and
 *   feel, and withdrawing a declared size hands it back
 * @param continuousLayout whether the two sides are laid out continuously as the divider is dragged
 *   rather than once it is released, where the drag draws an outline of where the divider is heading;
 *   `null` leaves the choice to the installed look and feel, and withdrawing a declared choice hands it
 *   back
 * @param content the composable content of the pane; see [SplitPaneScope]
 * @see javax.swing.JSplitPane
 */
@Composable
public fun SplitPane(
    dividerLocationListener: PropertyChangeListener,
    modifier: SwingModifier = SwingModifier,
    @SplitOrientation orientation: Int = JSplitPane.HORIZONTAL_SPLIT,
    dividerLocation: Int = -1,
    resizeWeight: Double = 0.0,
    oneTouchExpandable: Boolean? = null,
    dividerSize: Int? = null,
    continuousLayout: Boolean? = null,
    content: @Composable SplitPaneScope.() -> Unit,
) {
    val mirror = rememberMirrorState(dividerLocation)
    SplitPaneImpl(
        modifier = modifier.propertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, dividerLocationListener),
        orientation = orientation,
        dividerLocation = dividerLocation,
        mirror = mirror,
        resizeWeight = resizeWeight,
        oneTouchExpandable = oneTouchExpandable,
        dividerSize = dividerSize,
        continuousLayout = continuousLayout,
        content = content,
    )
}

/**
 * The `JSplitPane` node both public [SplitPane] overloads render.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun SplitPaneImpl(
    modifier: SwingModifier,
    @SplitOrientation orientation: Int,
    dividerLocation: Int,
    mirror: MirrorState<Int>,
    resizeWeight: Double,
    oneTouchExpandable: Boolean?,
    dividerSize: Int?,
    continuousLayout: Boolean?,
    noinline content: @Composable SplitPaneScope.() -> Unit,
) {
    SwingNode(
        // Built with both sides empty. `JSplitPane()` fills them with two placeholder buttons of the
        // look and feel's own, and a pane's sides hold what the composition declares there: a side no
        // child names stays empty rather than showing a widget nobody declared.
        factory = { JSplitPane(JSplitPane.HORIZONTAL_SPLIT, null, null) },
        modifier = modifier.declaredPaneProperties(oneTouchExpandable, dividerSize),
        update = {
            set(orientation) { this.orientation = it }
            set(resizeWeight) { this.resizeWeight = it }
            // Applied on change, never re-asserted: the default offset is a request to derive the
            // position from the sides' preferred sizes rather than a position to hold, so a pass that
            // redeclares it must leave a divider the user has since dragged where it stands.
            // setDividerLocation fires its property change synchronously, so the write below reaches
            // the attached listener exactly as a drag does; running it through mirror is what marks it
            // as the wrapper's own, leaving the listener to report the user's moves alone.
            set(dividerLocation) { location ->
                if (this.dividerLocation != location) {
                    mirror.write { this.dividerLocation = location }
                }
            }
            set(continuousLayout) { isContinuousLayout = it ?: LookAndFeelDefaults.splitPaneContinuousLayout }
        },
        childPlacement = SplitPaneSides,
        content = { SplitPaneScopeImpl.content() },
    )
}

/**
 * The divider properties a `JSplitPane`'s UI delegate installs directly onto the pane: the one-touch
 * expander and the divider thickness. Withdrawing a declared value therefore hands back whatever the
 * pane is already carrying, read straight off it, rather than re-deriving either from the look and
 * feel's own defaults.
 */
private fun SwingModifier.declaredPaneProperties(
    oneTouchExpandable: Boolean?,
    dividerSize: Int?,
): SwingModifier {
    var properties = this
    if (oneTouchExpandable != null) {
        properties =
            properties.property<JSplitPane, Boolean>(
                name = "oneTouchExpandable",
                value = oneTouchExpandable,
                read = { it.isOneTouchExpandable },
                write = { pane, value -> pane.isOneTouchExpandable = value },
            )
    }
    if (dividerSize != null) {
        properties =
            properties.property<JSplitPane, Int>(
                name = "dividerSize",
                value = dividerSize,
                read = { it.dividerSize },
                write = { pane, value -> pane.dividerSize = value },
            )
    }
    return properties
}
