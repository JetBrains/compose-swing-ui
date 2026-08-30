@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.constants.Orientation
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.CallbackRegistration
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.ModelSwapAware
import org.jetbrains.compose.swing.modifier.listener.SwappableModel
import org.jetbrains.compose.swing.modifier.listener.changeListener
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberMirrorState
import javax.swing.BoundedRangeModel
import javax.swing.JSlider
import javax.swing.SwingConstants
import javax.swing.event.ChangeEvent
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
 * @param onValueChange callback invoked with every value the user moves the slider to, the ones a drag
 *   passes through included, and with the value the slider is left on where it cannot hold [value] - one
 *   outside the range, or one off the grid [snapToTicks] resolves to; applying a [value] the slider can
 *   hold is not itself reported
 * @param modifier the [SwingModifier] applied to the underlying component
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
    onValueChange: (Int) -> Unit,
    modifier: SwingModifier = SwingModifier,
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
    val mirror = rememberMirrorState(value)
    val channel = rememberSliderValueChannel(mirror, mirror, value)
    SliderNode(
        modifier = modifier.changeListener<JSlider> { channel.publish(this, onValueChange, onValueSettled) },
        labelMin = min,
        labelMax = max,
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
        // would otherwise see as an unannounced change; the write guard is what tells the channel that the
        // clamp is this declaration settling, not the user's.
        set(min) { mirror.write { this.minimum = it } }
        set(max) { mirror.write { this.maximum = it } }
        // A slider left on a value of its own is where the composition's declaration ended up, and the
        // callbacks are the only way the caller learns of it.
        declare(value, mirror, JSlider::getValue, JSlider::setValue) { settled ->
            channel.settledOn(settled, onValueChange, onValueSettled)
        }
    }
}

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
    val mirror = rememberMirrorState(value)
    SliderNode(
        modifier = modifier.changeListener(changeListener).sliderValueMirror(mirror),
        labelMin = min,
        labelMax = max,
        orientation = orientation,
        inverted = inverted,
        majorTickSpacing = majorTickSpacing,
        minorTickSpacing = minorTickSpacing,
        paintTicks = paintTicks,
        paintLabels = paintLabels,
        labels = labels,
        snapToTicks = snapToTicks,
    ) {
        set(min) { mirror.write { this.minimum = it } }
        set(max) { mirror.write { this.maximum = it } }
        // The listener is attached as-is, so the slider has already told it where it settled.
        declare(value, mirror, JSlider::getValue, JSlider::setValue)
    }
}

/**
 * Feeds [mirror] the value the slider settles on. It rides the slider's `BoundedRangeModel`
 * rather than the slider itself, so the slider's own listener list stays the caller's alone.
 *
 * A value a drag passes through is not mirrored: it would invalidate the composition, and re-assert the
 * declaration, before the user has let go.
 */
private fun SwingModifier.sliderValueMirror(mirror: MirrorState<Int>): SwingModifier = listener(mirror, SLIDER_VALUES)

// A slider publishes its value through the range model it holds, which a caller can replace.
private val SLIDER_RANGE =
    SwappableModel<JSlider, BoundedRangeModel, ChangeListener>(
        property = "model",
        modelType = BoundedRangeModel::class.java,
        model = JSlider::getModel,
        add = BoundedRangeModel::addChangeListener,
        remove = BoundedRangeModel::removeChangeListener,
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
        modifier = modifier.changeListener<JSlider> { channel.publish(this, onValueChange, onValueSettled) },
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
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun ModelSliderNode(
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
        labelMin = model.minimum,
        labelMax = model.maximum,
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
 * the other. [labelMin] and [labelMax] bound the range Swing's own labels are generated over, and
 * [modifier] already carries every listener the slider needs.
 *
 * A declared value is settled against the slider through a [MirrorState] rather than applied on change:
 * the user can drag the slider out from under the declaration, and a declaration equal to the last one
 * still has to stand.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Composable
private inline fun SliderNode(
    modifier: SwingModifier,
    labelMin: Int,
    labelMax: Int,
    @Orientation orientation: Int,
    inverted: Boolean,
    majorTickSpacing: Int,
    minorTickSpacing: Int,
    paintTicks: Boolean,
    paintLabels: Boolean,
    labels: Map<Int, @Nls String>?,
    snapToTicks: Boolean,
    crossinline installRange: SwingNodeUpdater<JSlider>.() -> Unit,
) {
    val declaredLabels = rememberDeclaredLabels(labels)
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
            declareLabels(declaredLabels, majorTickSpacing, labelMin, labelMax)
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
 * [mirror] - mirroring one it passes through would invalidate the composition, and re-assert the
 * declaration, before the user has let go. Every step still reaches `onValueChange`, since following the
 * drag is what that channel is for, while `onValueSettled` hears the value the drag ends on - which is news
 * even where the caller already adopted it, because the release is what it reports.
 *
 * A `null` [mirror] is a caller-owned model: nothing is declared over it, so nothing tells the wrapper's
 * own writes from the user's and every value the slider publishes is news.
 */
private class SliderValueChannel(
    private val mirror: MirrorState<Int>?,
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
        if (!isAdjusting) mirror?.observed(value)
        val isNews = value != agreed && mirror?.isWriting != true
        agreed = value
        if (isNews) onValueChange(value)
        if (!isAdjusting && (isNews || released)) onValueSettled(value)
    }
}

/**
 * Remembers the [SliderValueChannel] the lambda-driven overloads report through, seeded with [value] so
 * the first value the slider publishes is measured against what the composition declares.
 *
 * [range] is what the channel measures values against - the [MirrorState] a declared value settles
 * through, or a caller's own model. A slider given a different model is measuring against a different
 * range, so the channel is rebuilt around the value that model arrived holding rather than left seeded
 * with a value the slider no longer shows.
 */
@Composable
private fun rememberSliderValueChannel(
    mirror: MirrorState<Int>?,
    range: Any?,
    value: Int,
): SliderValueChannel = remember(range) { SliderValueChannel(mirror, value) }

/** A slider change listener whose own state describes the range model it is registered on. */
private interface SliderValueMirror :
    ChangeListener,
    ModelSwapAware<BoundedRangeModel>

private val SLIDER_VALUE_MIRRORS =
    ListenerRegistration<JSlider, SliderValueMirror>(
        { slider, mirror -> SLIDER_RANGE.attachSettling(slider, mirror, mirror::adoptModelSwap) },
        SLIDER_RANGE::detach,
    )

private val SLIDER_VALUES =
    CallbackRegistration<JSlider, MirrorState<Int>, SliderValueMirror>(
        adapter = { current ->
            object : SliderValueMirror {
                override fun stateChanged(event: ChangeEvent) {
                    val model = event.source as BoundedRangeModel
                    if (!model.valueIsAdjusting) current().observed(model.value)
                }

                override fun adoptModelSwap(model: BoundedRangeModel) {
                    current().observed(model.value)
                }
            }
        },
        registration = SLIDER_VALUE_MIRRORS,
    )
