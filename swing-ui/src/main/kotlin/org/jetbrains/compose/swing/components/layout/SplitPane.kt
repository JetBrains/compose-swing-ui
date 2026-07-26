@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.AppliedValue
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.constants.SplitOrientation
import org.jetbrains.compose.swing.core.SlotAttachment
import org.jetbrains.compose.swing.core.SlotNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import org.jetbrains.compose.swing.rememberAppliedValue
import java.beans.PropertyChangeListener
import javax.swing.JSplitPane
import javax.swing.UIManager

/**
 * A composable wrapper for `JSplitPane`, hosting two resizable sides separated by a draggable divider.
 *
 * Declare the two sides in [block]:
 * ```
 * SplitPane(orientation = JSplitPane.HORIZONTAL_SPLIT) {
 *     first { Navigator() }
 *     second { Editor() }
 * }
 * ```
 * Each side hosts exactly one child; redeclaring a side replaces its child, and dropping a side (e.g.
 * behind an `if`) clears it.
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
 * @param block declares the two sides; see [SplitPaneScope]
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
    block: SplitPaneScope.() -> Unit,
) {
    val callback = rememberUpdatedState(onDividerLocationChange)
    val declaredOffset = rememberUpdatedState(dividerLocation)
    val applied = rememberAppliedValue(dividerLocation)
    // The pane publishes its new offset for every move, its own and the user's alike, including the
    // position a negative request resolves to once realized on screen. The binding answers which is
    // which by value: a move that lands on the declaration is the declaration arriving, and a move
    // answering a negative request the mirror still holds is that same resolution, settled into the
    // mirror without being reported. A move away from either is the user's, reported once, and every
    // later move is then measured against the resolved position.
    val listener =
        remember(applied) {
            PropertyChangeListener { event ->
                val moved = (event.source as JSplitPane).dividerLocation
                val declared = declaredOffset.value
                if (declared < 0 && applied.current == declared) {
                    applied.observed(moved)
                } else if (applied.observed(moved)) {
                    callback.value(moved)
                }
            }
        }
    SplitPaneImpl(
        modifier = modifier.propertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, listener),
        orientation = orientation,
        dividerLocation = dividerLocation,
        applied = applied,
        resizeWeight = resizeWeight,
        oneTouchExpandable = oneTouchExpandable,
        dividerSize = dividerSize,
        block = block,
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
 * @param resizeWeight how extra space is shared when the pane resizes
 * @param oneTouchExpandable whether the divider carries a widget that collapses either side in one
 *   click; `null` leaves the choice to the installed look and feel, and a choice withdrawn after being
 *   declared settles at its answer for good
 * @param dividerSize the divider thickness in pixels; `null` leaves the size to the installed look and
 *   feel, and a size withdrawn after being declared settles at its answer for good
 * @param block declares the two sides; see [SplitPaneScope]
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
    block: SplitPaneScope.() -> Unit,
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
        block = block,
    )
}

/**
 * The `JSplitPane` node both public [SplitPane] overloads render. [dividerLocation] is applied on change
 * and marked through [applied], so the offset the pane publishes for the wrapper's own write is
 * recognizable as such and only the user's moves are reported.
 */
@Composable
private fun SplitPaneImpl(
    modifier: SwingModifier,
    @SplitOrientation orientation: Int,
    dividerLocation: Int,
    applied: AppliedValue<Int>,
    resizeWeight: Double,
    oneTouchExpandable: Boolean?,
    dividerSize: Int?,
    block: SplitPaneScope.() -> Unit,
) {
    // Collected fresh on every pass, so a side the caller stops declaring is cleared (see SwingNode).
    val scope = SplitPaneScopeImpl().apply(block)
    // No UIManager default names oneTouchExpandable - a look and feel that wants it on sets it directly
    // in its own installUI - so the answer is read straight off the pane's own construction, before any
    // declared choice overrides it, rather than off a widget built solely to ask.
    var lookAndFeelOneTouchExpandable by remember { mutableStateOf(false) }

    SwingNode(
        factory = {
            JSplitPane().also { pane ->
                lookAndFeelOneTouchExpandable = pane.isOneTouchExpandable
                oneTouchExpandable?.let { pane.isOneTouchExpandable = it }
                dividerSize?.let { pane.dividerSize = it }
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
                if (declared != null) {
                    isOneTouchExpandable = declared
                } else {
                    if (isOneTouchExpandable != lookAndFeelOneTouchExpandable) {
                        isOneTouchExpandable = lookAndFeelOneTouchExpandable
                    }
                }
            }
            update(dividerSize) { declared ->
                if (declared != null) {
                    this.dividerSize = declared
                } else {
                    val lookAndFeelAnswer = UIManager.get(DIVIDER_SIZE_DEFAULT) as? Int
                    if (lookAndFeelAnswer != null && this.dividerSize != lookAndFeelAnswer) {
                        this.dividerSize = lookAndFeelAnswer
                    }
                }
            }
            applyModifier(modifier)
        },
        content = {
            scope.first?.let { first ->
                val attachment = remember { splitSideAttachment(SplitSide.First) }
                SlotNode(attachment) { first() }
            }
            scope.second?.let { second ->
                val attachment = remember { splitSideAttachment(SplitSide.Second) }
                SlotNode(attachment) { second() }
            }
        },
    )
}

private class SplitPaneScopeImpl : SplitPaneScope {
    var first: (@Composable () -> Unit)? = null
        private set
    var second: (@Composable () -> Unit)? = null
        private set

    override fun first(block: @Composable () -> Unit) {
        first = block
    }

    override fun second(block: @Composable () -> Unit) {
        second = block
    }
}

/** The look-and-feel default a split pane's UI reads while the pane records no divider size of its own. */
private const val DIVIDER_SIZE_DEFAULT: String = "SplitPane.dividerSize"

/** Whether a side is the leading (`setLeftComponent`) or trailing (`setRightComponent`) one. */
private enum class SplitSide { First, Second }

/**
 * Installs a side's view into [side] of the host `JSplitPane`; uninstall clears that side.
 */
private fun splitSideAttachment(side: SplitSide): SlotAttachment =
    SlotAttachment { host, component, _ ->
        val pane = host as JSplitPane
        when (side) {
            SplitSide.First -> pane.leftComponent = component
            SplitSide.Second -> pane.rightComponent = component
        }
        return@SlotAttachment {
            when (side) {
                SplitSide.First -> pane.leftComponent = null
                SplitSide.Second -> pane.rightComponent = null
            }
        }
    }
