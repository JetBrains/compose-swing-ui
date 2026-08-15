@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.constants.Orientation
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.changeListener
import org.jetbrains.compose.swing.modifier.listener.liveCallbackListener
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberAppliedValue
import java.beans.PropertyChangeListener
import java.util.Hashtable
import javax.swing.BoundedRangeModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSlider
import javax.swing.SwingConstants
import javax.swing.event.ChangeListener

/**
 * A composable wrapper for JSlider.
 *
 * A drag is published a value at a time: the slider passes through every value between the one it was
 * grabbed at and the one it is let go on, and each of them reaches [onValueChange] as the user reaches it,
 * while [onValueSettled] hears the value the drag was released on and none of the ones it passed through.
 * A caller that follows the drag adopts [onValueChange]; a caller that acts on the released value alone -
 * one that starts work too expensive to run per step - takes [onValueSettled] and leaves [value] where it
 * is until then, and the slider follows the mouse in the meantime.
 *
 * @param value the current value
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param onValueChange callback invoked with every value the user moves the slider to, the ones a drag
 *   passes through included, and with the value the slider is left on where it cannot hold [value] - one
 *   outside the range, or one off the grid [snapToTicks] resolves to; applying a [value] the slider can
 *   hold is not itself reported
 * @param onValueSettled callback invoked with the value the slider settles on: the one a drag is released
 *   on, one the user reaches outside a drag, and the value the slider is left on where it cannot hold
 *   [value]
 * @param min the minimum value
 * @param max the maximum value
 * @param orientation the orientation of the slider (an [Orientation] `SwingConstants` value)
 * @param inverted whether the value axis runs backwards, with the maximum at the left or bottom end
 * @param majorTickSpacing the value distance between major tick marks, `0` for none
 * @param minorTickSpacing the value distance between minor tick marks, `0` for none
 * @param paintTicks whether the tick marks are painted
 * @param paintLabels whether the value labels are painted
 * @param labels the text to draw at each value, keyed by the value the label sits at. `null` leaves the
 *   labels to Swing, which draws one at every major tick mark when [paintLabels] is `true` and
 *   [majorTickSpacing] is positive
 * @param snapToTicks whether a value the user picks resolves to the closest tick mark
 * @see javax.swing.JSlider
 */
@Composable
public fun Slider(
    value: Int,
    modifier: SwingModifier = SwingModifier,
    onValueChange: (Int) -> Unit = {},
    onValueSettled: (Int) -> Unit = {},
    min: Int = 0,
    max: Int = 100,
    @Orientation orientation: Int = SwingConstants.HORIZONTAL,
    inverted: Boolean = false,
    majorTickSpacing: Int = 0,
    minorTickSpacing: Int = 0,
    paintTicks: Boolean = false,
    paintLabels: Boolean = false,
    labels: Map<Int, @Nls String>? = null,
    snapToTicks: Boolean = false,
) {
    val applied = rememberAppliedValue(value)
    val channel = rememberSliderValueChannel(applied, applied, value)
    SliderNode(
        modifier = modifier.onSliderValue { slider -> channel.publish(slider, onValueChange, onValueSettled) },
        labelRange = min..max,
        orientation = orientation,
        inverted = inverted,
        majorTickSpacing = majorTickSpacing,
        minorTickSpacing = minorTickSpacing,
        paintTicks = paintTicks,
        paintLabels = paintLabels,
        labels = labels,
        snapToTicks = snapToTicks,
    ) {
        // Narrowing the range can force JSlider to clamp the value on the spot, which the change listener
        // would otherwise see as an unannounced move; the write guard is what tells the channel that the
        // clamp is this declaration settling, not the user's.
        set(min) { applied.write { this.minimum = it } }
        set(max) { applied.write { this.maximum = it } }
        // A slider left on a value of its own is where the composition's declaration ended up, and the
        // callbacks are the only way the caller learns of it.
        declare(value, applied, JSlider::getValue, JSlider::setValue) { settled ->
            channel.settledOn(settled, onValueChange, onValueSettled)
        }
    }
}

/** Runs [report] with the slider each time it publishes a value. */
private fun SwingModifier.onSliderValue(report: (JSlider) -> Unit): SwingModifier =
    liveCallbackListener<JSlider, (JSlider) -> Unit, ChangeListener>(
        report,
        { current -> ChangeListener { event -> current()(event.source as JSlider) } },
        { slider, listener -> slider.addChangeListener(listener) },
        { slider, listener -> slider.removeChangeListener(listener) },
    )

/**
 * A composable wrapper for JSlider driven by a raw [ChangeListener] instead of the `onValueChange` and
 * `onValueSettled` lambdas. The [changeListener] is attached as-is and removed on the same instance; pass a
 * stable instance (e.g. `remember {}`) to avoid a detach/re-attach on every recomposition. Being attached
 * as-is, it is notified of every change to the slider's value - the values a drag passes through as well as
 * the one it settles on, and the change that applies [value] included - and reads `getValueIsAdjusting` off
 * the slider to tell them apart.
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
 * @param labels the text to draw at each value, keyed by the value the label sits at. `null` leaves the
 *   labels to Swing, which draws one at every major tick mark when [paintLabels] is `true` and
 *   [majorTickSpacing] is positive
 * @param snapToTicks whether a value the user picks resolves to the closest tick mark
 * @see javax.swing.JSlider
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
    labels: Map<Int, @Nls String>? = null,
    snapToTicks: Boolean = false,
) {
    val applied = rememberAppliedValue(value)
    SliderNode(
        modifier = modifier.changeListener(changeListener).sliderValueMirror(applied),
        labelRange = min..max,
        orientation = orientation,
        inverted = inverted,
        majorTickSpacing = majorTickSpacing,
        minorTickSpacing = minorTickSpacing,
        paintTicks = paintTicks,
        paintLabels = paintLabels,
        labels = labels,
        snapToTicks = snapToTicks,
    ) {
        set(min) { applied.write { this.minimum = it } }
        set(max) { applied.write { this.maximum = it } }
        // The listener is attached as-is, so the slider has already told it where it settled.
        declare(value, applied, JSlider::getValue, JSlider::setValue)
    }
}

/**
 * Feeds [applied]'s mirror the value the slider settles on. It rides the slider's `BoundedRangeModel`
 * rather than the slider itself, so the slider's own listener list stays the caller's alone.
 *
 * A value a drag passes through is not mirrored: it would invalidate the composition, and re-assert the
 * declaration, before the user has let go.
 */
private fun SwingModifier.sliderValueMirror(applied: AppliedValue<Int>): SwingModifier =
    liveCallbackListener<JSlider, AppliedValue<Int>, ChangeListener>(
        applied,
        { current ->
            ChangeListener { event ->
                val model = event.source as BoundedRangeModel
                if (!model.valueIsAdjusting) current().observed(model.value)
            }
        },
        { slider, listener -> slider.model.addChangeListener(listener) },
        { slider, listener -> slider.model.removeChangeListener(listener) },
    )

/**
 * A composable wrapper for JSlider driven by a caller-owned [BoundedRangeModel]. The model owns the value
 * and the range, so nothing is declared over it: the slider renders whatever the model holds, the library
 * never writes to it, and a model the caller mutates repaints the slider without a recomposition. Supplying
 * a new [model] instance installs it on recomposition.
 *
 * This is what lets one range drive several widgets - a slider and the [ProgressBar] reading it out, or two
 * views of the same position - since each of them renders the model as-is:
 *
 * ```
 * val range = remember { DefaultBoundedRangeModel(30, 0, 0, 100) }
 * Slider(model = range)
 * ProgressBar(model = range)
 * ```
 *
 * A drag reaches [onValueChange] a value at a time and [onValueSettled] once, on the value it is released
 * on; see the declared-value [Slider] for what each channel carries.
 *
 * @param model the range the slider renders and the user moves; owned by the caller and never written to
 *   by the library
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param onValueChange callback invoked with every value the slider publishes, the ones a drag passes
 *   through included
 * @param onValueSettled callback invoked with the value the slider settles on: the one a drag is released
 *   on, and one the value reaches outside a drag
 * @param orientation the orientation of the slider (an [Orientation] `SwingConstants` value)
 * @param inverted whether the value axis runs backwards, with the maximum at the left or bottom end
 * @param majorTickSpacing the value distance between major tick marks, `0` for none
 * @param minorTickSpacing the value distance between minor tick marks, `0` for none
 * @param paintTicks whether the tick marks are painted
 * @param paintLabels whether the value labels are painted
 * @param labels the text to draw at each value, keyed by the value the label sits at. `null` leaves the
 *   labels to Swing, which draws one at every major tick mark when [paintLabels] is `true` and
 *   [majorTickSpacing] is positive
 * @param snapToTicks whether a value the user picks resolves to the closest tick mark
 * @see javax.swing.JSlider
 */
@Composable
public fun Slider(
    model: BoundedRangeModel,
    modifier: SwingModifier = SwingModifier,
    onValueChange: (Int) -> Unit = {},
    onValueSettled: (Int) -> Unit = {},
    @Orientation orientation: Int = SwingConstants.HORIZONTAL,
    inverted: Boolean = false,
    majorTickSpacing: Int = 0,
    minorTickSpacing: Int = 0,
    paintTicks: Boolean = false,
    paintLabels: Boolean = false,
    labels: Map<Int, @Nls String>? = null,
    snapToTicks: Boolean = false,
) {
    // Nothing is declared over a caller's model, so there is no mirror for the channel to settle against
    // and every value the slider publishes is the model's own.
    val channel = rememberSliderValueChannel(null, model, model.value)
    ModelSliderNode(
        model = model,
        modifier = modifier.onSliderValue { slider -> channel.publish(slider, onValueChange, onValueSettled) },
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
 * A model-driven `Slider` driven by a raw [ChangeListener] instead of the `onValueChange` and
 * `onValueSettled` lambdas. The [model] is rendered as-is and never written to by the library. The
 * [changeListener] is attached as-is and removed on the same instance; pass a stable instance (e.g.
 * `remember {}`) to avoid churn, and read `getValueIsAdjusting` off the slider to tell the values a drag
 * passes through from the one it settles on.
 *
 * @param model the range the slider renders and the user moves; owned by the caller and never written to
 *   by the library
 * @param changeListener the listener notified when the value changes
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param orientation the orientation of the slider (an [Orientation] `SwingConstants` value)
 * @param inverted whether the value axis runs backwards, with the maximum at the left or bottom end
 * @param majorTickSpacing the value distance between major tick marks, `0` for none
 * @param minorTickSpacing the value distance between minor tick marks, `0` for none
 * @param paintTicks whether the tick marks are painted
 * @param paintLabels whether the value labels are painted
 * @param labels the text to draw at each value, keyed by the value the label sits at. `null` leaves the
 *   labels to Swing, which draws one at every major tick mark when [paintLabels] is `true` and
 *   [majorTickSpacing] is positive
 * @param snapToTicks whether a value the user picks resolves to the closest tick mark
 * @see javax.swing.JSlider
 */
@Composable
public fun Slider(
    model: BoundedRangeModel,
    changeListener: ChangeListener,
    modifier: SwingModifier = SwingModifier,
    @Orientation orientation: Int = SwingConstants.HORIZONTAL,
    inverted: Boolean = false,
    majorTickSpacing: Int = 0,
    minorTickSpacing: Int = 0,
    paintTicks: Boolean = false,
    paintLabels: Boolean = false,
    labels: Map<Int, @Nls String>? = null,
    snapToTicks: Boolean = false,
) {
    ModelSliderNode(
        model = model,
        modifier = modifier.changeListener(changeListener),
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
 * The `JSlider` node the model-driven [Slider] overloads render, with [modifier] already carrying every
 * listener the slider needs.
 */
@Composable
private fun ModelSliderNode(
    model: BoundedRangeModel,
    modifier: SwingModifier,
    @Orientation orientation: Int,
    inverted: Boolean,
    majorTickSpacing: Int,
    minorTickSpacing: Int,
    paintTicks: Boolean,
    paintLabels: Boolean,
    labels: Map<Int, @Nls String>?,
    snapToTicks: Boolean,
) {
    SliderNode(
        modifier = modifier,
        // The range Swing's own labels are generated over is the model's, read as it stands: a range the
        // caller moves inside the model reaches the labels through the slider's own regeneration.
        labelRange = model.minimum..model.maximum,
        orientation = orientation,
        inverted = inverted,
        majorTickSpacing = majorTickSpacing,
        minorTickSpacing = minorTickSpacing,
        paintTicks = paintTicks,
        paintLabels = paintLabels,
        labels = labels,
        snapToTicks = snapToTicks,
    ) {
        set(model) { this.model = it }
    }
}

/**
 * The `JSlider` node every [Slider] overload renders: all of it but the range, which [installRange]
 * declares - a value between a minimum and a maximum in one family of overloads, the caller's own model in
 * the other. [labelRange] is the range Swing's own labels are generated over, and [modifier] already
 * carries every listener the slider needs.
 *
 * A declared value is settled against the slider through an [AppliedValue] rather than applied on change:
 * the user can drag the slider out from under the declaration, and a declaration equal to the last one
 * still has to stand.
 */
@Composable
private fun SliderNode(
    modifier: SwingModifier,
    labelRange: IntRange,
    @Orientation orientation: Int,
    inverted: Boolean,
    majorTickSpacing: Int,
    minorTickSpacing: Int,
    paintTicks: Boolean,
    paintLabels: Boolean,
    labels: Map<Int, @Nls String>?,
    snapToTicks: Boolean,
    installRange: SwingNodeUpdater<JSlider>.() -> Unit,
) {
    SwingNode(
        factory = { JSlider() },
        update = {
            // Everything that snaps the value goes in before the range itself, so a recomposition that
            // moves both lands the new value on the new grid rather than the old.
            set(majorTickSpacing) { this.majorTickSpacing = it }
            set(minorTickSpacing) { this.minorTickSpacing = it }
            set(snapToTicks) { this.snapToTicks = it }
            installRange()
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
            set(LabelDeclaration(labels, majorTickSpacing, labelRange)) { declaration ->
                (labelTable as? PropertyChangeListener)?.let { removePropertyChangeListener(it) }
                this.labelTable = declaration.labels?.toLabelTable() ?: standardLabels()
            }
            set(paintLabels) { this.paintLabels = it }
            applyModifier(modifier)
        },
    )
}

/**
 * One slider's value channel: the value the caller and the slider currently agree on, and whether a drag
 * is underway. The callbacks the values reach the caller through are passed in per event, so the channel
 * outlives a recomposition that declares new ones.
 *
 * A drag publishes a value per step before it settles, and only the value it settles on is mirrored into
 * [applied] - mirroring one it passes through would invalidate the composition, and re-assert the
 * declaration, before the user has let go. Every step still reaches `onValueChange`, since following the
 * drag is what that channel is for, while `onValueSettled` hears the value the drag ends on - which is news
 * even where the caller already adopted it, because the release is what it reports.
 *
 * A `null` [applied] is a caller-owned model: nothing is declared over it, so nothing tells the wrapper's
 * own writes from the user's and every value the slider publishes is news.
 */
private class SliderValueChannel(
    private val applied: AppliedValue<Int>?,
    agreed: Int,
) {
    private var agreed: Int = agreed
    private var adjusting: Boolean = false

    /**
     * Reports [value] as the value the slider answered a declaration with, on both channels: it is where
     * the declaration ended up, and the caller hears of it here or not at all.
     */
    fun settledOn(
        value: Int,
        onValueChange: (Int) -> Unit,
        onValueSettled: (Int) -> Unit,
    ) {
        agreed = value
        onValueChange(value)
        onValueSettled(value)
    }

    /** Reports a value the slider published, on the channels it is news on. */
    fun publish(
        slider: JSlider,
        onValueChange: (Int) -> Unit,
        onValueSettled: (Int) -> Unit,
    ) {
        val value = slider.value
        val isAdjusting = slider.valueIsAdjusting
        val released = adjusting && !isAdjusting
        adjusting = isAdjusting
        if (!isAdjusting) applied?.observed(value)
        val isNews = value != agreed && applied?.isWriting != true
        agreed = value
        if (isNews) onValueChange(value)
        if (!isAdjusting && (isNews || released)) onValueSettled(value)
    }
}

/**
 * Remembers the [SliderValueChannel] the lambda-driven overloads report through, seeded with [value] so
 * the first value the slider publishes is measured against what the composition declares.
 *
 * [range] is what the channel measures values against - the [AppliedValue] a declared value settles
 * through, or a caller's own model. A slider given a different model is measuring against a different
 * range, so the channel is rebuilt around the value that model arrived holding rather than left seeded
 * with a value the slider no longer shows.
 */
@Composable
private fun rememberSliderValueChannel(
    applied: AppliedValue<Int>?,
    range: Any?,
    value: Int,
): SliderValueChannel = remember(range) { SliderValueChannel(applied, value) }

/**
 * What the labels a slider paints are derived from: the declared map, or - where none is declared - the
 * major tick spacing and the range Swing's own labels are generated over.
 */
private data class LabelDeclaration(
    val labels: Map<Int, @Nls String>?,
    val majorTickSpacing: Int,
    val range: IntRange,
)

/** The `JSlider` label table this text draws as, one [JLabel] per entry. */
private fun Map<Int, @Nls String>.toLabelTable(): Hashtable<Int, JComponent> {
    val table = Hashtable<Int, JComponent>()
    for ((value, text) in this) table[value] = JLabel(text)
    return table
}

/**
 * The standard labels a `JSlider` puts at its major tick marks, or `null` when there is no major tick
 * spacing to place them at.
 */
private fun JSlider.standardLabels(): Hashtable<Int, JComponent>? =
    if (majorTickSpacing > 0) createStandardLabels(majorTickSpacing) else null
