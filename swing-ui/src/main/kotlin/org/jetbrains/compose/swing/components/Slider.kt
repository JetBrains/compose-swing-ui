@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.AppliedValue
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.constants.Orientation
import org.jetbrains.compose.swing.declare
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.changeListener
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.rememberAppliedValue
import java.beans.PropertyChangeListener
import java.util.Hashtable
import javax.swing.BoundedRangeModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSlider
import javax.swing.SwingConstants
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/**
 * A composable wrapper for JSlider.
 *
 * @param value the current value
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param onValueChange callback invoked with the value the user moves the slider to, and with the value
 *   the slider is left on where it cannot hold [value] - one outside the range, or one off the grid
 *   [snapToTicks] resolves to; applying a [value] the slider can hold is not itself reported
 * @param min the minimum value
 * @param max the maximum value
 * @param orientation the orientation of the slider (an [Orientation] `SwingConstants` value)
 * @param inverted whether the value axis runs backwards, with the maximum at the left or bottom end
 * @param majorTickSpacing the value distance between major tick marks, `0` for none
 * @param minorTickSpacing the value distance between minor tick marks, `0` for none
 * @param paintTicks whether the tick marks are painted
 * @param paintLabels whether the value labels are painted
 * @param labels the labels to paint, keyed by the value each one sits at; `null` leaves the labels to
 *   Swing, which draws one at every major tick mark when [paintLabels] is `true` and
 *   [majorTickSpacing] is positive
 * @param snapToTicks whether a value the user picks resolves to the closest tick mark
 */
@Composable
public fun Slider(
    value: Int,
    modifier: SwingModifier = SwingModifier,
    onValueChange: (Int) -> Unit = {},
    min: Int = 0,
    max: Int = 100,
    @Orientation orientation: Int = SwingConstants.HORIZONTAL,
    inverted: Boolean = false,
    majorTickSpacing: Int = 0,
    minorTickSpacing: Int = 0,
    paintTicks: Boolean = false,
    paintLabels: Boolean = false,
    labels: Map<Int, String>? = null,
    snapToTicks: Boolean = false,
) {
    val callback = rememberUpdatedState(onValueChange)
    val applied = rememberAppliedValue(value)
    // The slider publishes every value it moves to, the wrapper's own writes included. What it last held is
    // what tells them apart, and a write of the wrapper's is silent whatever value it lands on.
    val listener =
        remember(applied) {
            ChangeListener { event ->
                val moved = (event.source as JSlider).value
                if (applied.observed(moved)) callback.value(moved)
            }
        }
    SliderNode(
        value = value,
        applied = applied,
        modifier = modifier.changeListener(listener),
        // A slider left on a value of its own is where the composition's declaration ended up, and the
        // callback is the only way the caller learns of it.
        onSettled = { settled -> callback.value(settled) },
        min = min,
        max = max,
        orientation = orientation,
        inverted = inverted,
        majorTickSpacing = majorTickSpacing,
        minorTickSpacing = minorTickSpacing,
        paintTicks = paintTicks,
        paintLabels = paintLabels,
        labels = labels,
        snapToTicks = snapToTicks,
    )
}

/**
 * A composable wrapper for JSlider driven by a raw [ChangeListener] instead of an `onValueChange`
 * lambda. The [changeListener] is attached as-is and removed on the same instance; pass a stable
 * instance (e.g. `remember {}`) to avoid a detach/re-attach on every recomposition. Being attached
 * as-is, it is notified of every change to the slider's value, including the one that applies [value].
 *
 * @param value the current value
 * @param changeListener the listener notified when the value changes
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param min the minimum value
 * @param max the maximum value
 * @param orientation the orientation of the slider (an [Orientation] `SwingConstants` value)
 * @param inverted whether the value axis runs backwards, with the maximum at the left or bottom end
 * @param majorTickSpacing the value distance between major tick marks, `0` for none
 * @param minorTickSpacing the value distance between minor tick marks, `0` for none
 * @param paintTicks whether the tick marks are painted
 * @param paintLabels whether the value labels are painted
 * @param labels the labels to paint, keyed by the value each one sits at; `null` leaves the labels to
 *   Swing, which draws one at every major tick mark when [paintLabels] is `true` and
 *   [majorTickSpacing] is positive
 * @param snapToTicks whether a value the user picks resolves to the closest tick mark
 */
@Composable
public fun Slider(
    value: Int,
    changeListener: ChangeListener,
    modifier: SwingModifier = SwingModifier,
    min: Int = 0,
    max: Int = 100,
    @Orientation orientation: Int = SwingConstants.HORIZONTAL,
    inverted: Boolean = false,
    majorTickSpacing: Int = 0,
    minorTickSpacing: Int = 0,
    paintTicks: Boolean = false,
    paintLabels: Boolean = false,
    labels: Map<Int, String>? = null,
    snapToTicks: Boolean = false,
) {
    val applied = rememberAppliedValue(value)
    // The caller's listener is attached as-is, and is the only change listener on the slider. The mirror
    // rides the model's own channel instead, so the slider's listener list is the caller's alone.
    val mirror =
        remember(applied) {
            ChangeListener { event -> applied.observed((event.source as BoundedRangeModel).value) }
        }
    SliderNode(
        value = value,
        applied = applied,
        modifier =
            modifier
                .changeListener(changeListener)
                .listener<JSlider, ChangeListener>(
                    mirror,
                    { slider, listener -> slider.model.addChangeListener(listener) },
                    { slider, listener -> slider.model.removeChangeListener(listener) },
                ),
        // The listener is attached as-is, so the slider has already told it where it settled.
        onSettled = {},
        min = min,
        max = max,
        orientation = orientation,
        inverted = inverted,
        majorTickSpacing = majorTickSpacing,
        minorTickSpacing = minorTickSpacing,
        paintTicks = paintTicks,
        paintLabels = paintLabels,
        labels = labels,
        snapToTicks = snapToTicks,
    )
}

/**
 * The `JSlider` node both [Slider] overloads render. [value] is settled against the slider through
 * [applied] rather than applied on change: the user can drag the slider out from under the declaration, and
 * a declaration equal to the last one still has to stand. [onSettled] is handed the value where the slider
 * answers a declaration with one of its own - outside its range, or off the grid it snaps to.
 */
@Composable
private fun SliderNode(
    value: Int,
    applied: AppliedValue<Int>,
    modifier: SwingModifier,
    onSettled: JSlider.(Int) -> Unit,
    min: Int,
    max: Int,
    @Orientation orientation: Int,
    inverted: Boolean,
    majorTickSpacing: Int,
    minorTickSpacing: Int,
    paintTicks: Boolean,
    paintLabels: Boolean,
    labels: Map<Int, String>?,
    snapToTicks: Boolean,
) {
    SwingNode(
        factory = { JSlider(min, max, value) },
        update = {
            // Everything that bounds or snaps the value goes in before the value itself, so a
            // recomposition that moves both lands the new value on the new grid rather than the old.
            set(min) { this.minimum = it }
            set(max) { this.maximum = it }
            set(majorTickSpacing) { this.majorTickSpacing = it }
            set(minorTickSpacing) { this.minorTickSpacing = it }
            set(snapToTicks) { this.snapToTicks = it }
            declare(value, applied, JSlider::getValue, JSlider::setValue, onSettled)
            set(orientation) { this.orientation = it }
            set(inverted) { this.inverted = it }
            set(paintTicks) { this.paintTicks = it }
            // A declared table goes in before the painting flag, because JSlider fills an unset table
            // in with its own standard labels as soon as that flag is written. Those standard labels
            // listen to the slider so they can regenerate themselves when the range moves, and the
            // slider drops that registration only while they are still the table it holds - so the
            // outgoing table's registration leaves with it here, or a table nothing renders would keep
            // rewriting a declared map, and would fail outright on a range change once there is no
            // table left for it to regenerate. What the slider paints is derived here instead, from
            // the declared map or from the spacing and the range Swing's own labels sit on.
            set(LabelDeclaration(labels, majorTickSpacing, min, max)) { declaration ->
                (labelTable as? PropertyChangeListener)?.let { removePropertyChangeListener(it) }
                this.labelTable = declaration.labels?.toLabelTable() ?: standardLabels()
            }
            set(paintLabels) { this.paintLabels = it }
            applyModifier(modifier)
        },
    )
}

/**
 * What the labels a slider paints are derived from: the declared map, or - where none is declared - the
 * major tick spacing and the range Swing's own labels are generated over.
 */
private data class LabelDeclaration(
    val labels: Map<Int, String>?,
    val majorTickSpacing: Int,
    val min: Int,
    val max: Int,
)

private fun Map<Int, String>.toLabelTable(): Hashtable<Int, JComponent> {
    val table = Hashtable<Int, JComponent>()
    forEach { (value, text) -> table[value] = JLabel(text) }
    return table
}

/**
 * The standard labels a `JSlider` puts at its major tick marks, or `null` when there is no major tick
 * spacing to place them at.
 */
private fun JSlider.standardLabels(): Hashtable<Int, JComponent>? =
    if (majorTickSpacing > 0) createStandardLabels(majorTickSpacing) else null
