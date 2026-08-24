@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.constants.SplitOrientation
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.rememberAppliedValue
import java.beans.PropertyChangeListener
import javax.swing.JSplitPane
import javax.swing.UIManager

/**
 * A composable wrapper for `JSplitPane`, hosting two resizable sides separated by a draggable divider.
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
 * Pass a pixel offset as [dividerLocation] to place the divider; [onDividerLocationChange] fires with
 * the new offset when the user moves it. An offset is applied when it changes and is not asserted
 * again, so a divider the user has dragged stays where they left it. The default `-1` is
 * `JSplitPane`'s own initial divider location, asking the pane to derive the position from the sides'
 * preferred sizes (shaped by [resizeWeight]); the pane keeps that request as its divider location until
 * it is realized on screen, at which point it resolves the position itself - that resolution is the
 * look and feel settling the request, not a move, and is not reported.
 *
 * @param modifier the [SwingModifier] applied to the underlying `JSplitPane`
 * @param orientation the axis along which the two sides are arranged
 * @param dividerLocation the divider offset in pixels (controlled); a negative offset - the default
 *   `-1` is `JSplitPane`'s own initial divider location - resets the divider to honor the sides'
 *   preferred sizes
 * @param onDividerLocationChange callback invoked with the new offset when the user moves the
 *   divider; an offset the declaration itself applies is not reported, nor is the position a negative
 *   request resolves to once the pane is realized on screen
 * @param resizeWeight how extra space is shared when the pane resizes, from `0.0` (all to the second
 *   side) to `1.0` (all to the first side)
 * @param oneTouchExpandable whether the divider carries a widget that collapses either side in one
 *   click; `null` leaves the choice to the installed look and feel, and a choice withdrawn after being
 *   declared settles at its answer for good
 * @param dividerSize the divider thickness in pixels; `null` leaves the size to the installed look and
 *   feel, and a size withdrawn after being declared settles at its answer for good
 * @param continuousLayout whether the two sides are laid out continuously as the divider is dragged
 *   rather than once it is released, where the drag draws an outline of where the divider is heading;
 *   `null` leaves the choice to the installed look and feel, and a choice withdrawn after being declared
 *   settles at its answer for good
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
    val applied = rememberAppliedValue(dividerLocation)
    // The pane publishes its new offset for every move, its own and the user's alike, including the
    // position a negative request resolves to once realized on screen. The binding answers which is
    // which by value: a move that lands on the declaration is the declaration arriving, and a move
    // answering a negative request the mirror still holds is that same resolution, settled into the
    // mirror without being reported. A move away from either is the user's, reported once, and every
    // later move is then measured against the resolved position.
    val onMoved: (Int) -> Unit = { moved ->
        if (dividerLocation < 0 && applied.value == dividerLocation) {
            applied.observed(moved)
        } else if (applied.observed(moved)) {
            onDividerLocationChange(moved)
        }
    }
    SplitPaneImpl(
        modifier =
            modifier.propertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY) { event ->
                onMoved((event.source as JSplitPane).dividerLocation)
            },
        orientation = orientation,
        dividerLocation = dividerLocation,
        applied = applied,
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
 * @param orientation the axis along which the two sides are arranged
 * @param dividerLocation the divider offset in pixels (controlled); a negative offset - the default
 *   `-1` is `JSplitPane`'s own initial divider location - resets the divider to honor the sides'
 *   preferred sizes
 * @param resizeWeight how extra space is shared when the pane resizes, from `0.0` (all to the second
 *   side) to `1.0` (all to the first side)
 * @param oneTouchExpandable whether the divider carries a widget that collapses either side in one
 *   click; `null` leaves the choice to the installed look and feel, and a choice withdrawn after being
 *   declared settles at its answer for good
 * @param dividerSize the divider thickness in pixels; `null` leaves the size to the installed look and
 *   feel, and a size withdrawn after being declared settles at its answer for good
 * @param continuousLayout whether the two sides are laid out continuously as the divider is dragged
 *   rather than once it is released, where the drag draws an outline of where the divider is heading;
 *   `null` leaves the choice to the installed look and feel, and a choice withdrawn after being declared
 *   settles at its answer for good
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
    val applied = rememberAppliedValue(dividerLocation)
    SplitPaneImpl(
        modifier = modifier.propertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, dividerLocationListener),
        orientation = orientation,
        dividerLocation = dividerLocation,
        applied = applied,
        resizeWeight = resizeWeight,
        oneTouchExpandable = oneTouchExpandable,
        dividerSize = dividerSize,
        continuousLayout = continuousLayout,
        content = content,
    )
}

/** The `JSplitPane` node both public [SplitPane] overloads render. */
@Composable
private fun SplitPaneImpl(
    modifier: SwingModifier,
    @SplitOrientation orientation: Int,
    dividerLocation: Int,
    applied: AppliedValue<Int>,
    resizeWeight: Double,
    oneTouchExpandable: Boolean?,
    dividerSize: Int?,
    continuousLayout: Boolean?,
    content: @Composable SplitPaneScope.() -> Unit,
) {
    // No UIManager default names oneTouchExpandable or continuousLayout - a look and feel that wants
    // either sets it directly in its own installUI - so both answers are read straight off the pane's
    // own construction, before any declared choice overrides them, rather than off a widget built
    // solely to ask.
    var lookAndFeelOneTouchExpandable by remember { mutableStateOf(false) }
    var lookAndFeelContinuousLayout by remember { mutableStateOf(false) }

    SwingNode(
        factory = {
            // Built with both sides empty. `JSplitPane()` fills them with two placeholder buttons of the
            // look and feel's own, and a pane's sides hold what the composition declares there: a side no
            // child names stays empty rather than showing a widget nobody declared.
            JSplitPane(JSplitPane.HORIZONTAL_SPLIT, null, null).also { pane ->
                lookAndFeelOneTouchExpandable = pane.isOneTouchExpandable
                lookAndFeelContinuousLayout = pane.isContinuousLayout
                oneTouchExpandable?.let { pane.isOneTouchExpandable = it }
                dividerSize?.let { pane.dividerSize = it }
                continuousLayout?.let { pane.isContinuousLayout = it }
            }
        },
        update = {
            set(orientation) { this.orientation = it }
            set(resizeWeight) { this.resizeWeight = it }
            // Applied on change, never re-asserted: the default offset is a request to derive the
            // position from the sides' preferred sizes rather than a position to hold, so a pass that
            // redeclares it must leave a divider the user has since dragged where it stands.
            // setDividerLocation fires its property change synchronously, so the write below reaches
            // the attached listener exactly as a drag does; running it through applied is what marks it
            // as the wrapper's own, leaving the listener to report the user's moves alone.
            set(dividerLocation) { location ->
                if (this.dividerLocation != location) {
                    applied.write { this.dividerLocation = location }
                }
            }
            update(oneTouchExpandable) { declared ->
                settleOn(declared, lookAndFeelOneTouchExpandable, { isOneTouchExpandable }) {
                    isOneTouchExpandable = it
                }
            }
            update(dividerSize) { declared ->
                settleOn(declared, UIManager.get(DIVIDER_SIZE_DEFAULT) as? Int, { this.dividerSize }) {
                    this.dividerSize = it
                }
            }
            update(continuousLayout) { declared ->
                settleOn(declared, lookAndFeelContinuousLayout, { isContinuousLayout }) {
                    isContinuousLayout = it
                }
            }
            applyModifier(modifier)
        },
        childPlacement = SplitPaneSides,
        content = { SplitPaneScopeImpl.content() },
    )
}

/**
 * Puts a component on [declared], or on [lookAndFeelAnswer] where the declaration is withdrawn, and
 * writes nothing where the component already holds it or where neither names a value.
 *
 * This is what every look-and-feel-defaulted property of a split pane, and a scroll pane's viewport
 * border, does: a withdrawn declaration hands the property back rather than leaving the last declared
 * value standing.
 */
internal inline fun <V> settleOn(
    declared: V?,
    lookAndFeelAnswer: V?,
    read: () -> V,
    write: (V) -> Unit,
) {
    val target = declared ?: lookAndFeelAnswer ?: return
    if (read() != target) write(target)
}

/** The look-and-feel default a split pane's UI reads while the pane records no divider size of its own. */
private const val DIVIDER_SIZE_DEFAULT: String = "SplitPane.dividerSize"
