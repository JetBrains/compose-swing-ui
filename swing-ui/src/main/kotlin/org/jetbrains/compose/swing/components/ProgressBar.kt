@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.constants.Orientation
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import javax.swing.BoundedRangeModel
import javax.swing.JProgressBar
import javax.swing.SwingConstants

/**
 * A composable wrapper for JProgressBar.
 *
 * @param value the current value
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param min the minimum value
 * @param max the maximum value
 * @param indeterminate whether the progress bar is indeterminate
 * @param orientation the orientation of the progress bar (an [Orientation] `SwingConstants` value)
 * @param stringPainted whether the bar paints text over itself; `false` by default
 * @param string the text painted over the bar; `null` by default, which leaves the bar to paint the
 *   completion percentage, so [stringPainted] on its own gives a percentage readout
 * @see javax.swing.JProgressBar
 */
@Composable
public fun ProgressBar(
    value: Int,
    modifier: SwingModifier = SwingModifier,
    min: Int = 0,
    max: Int = 100,
    indeterminate: Boolean = false,
    @Orientation orientation: Int = SwingConstants.HORIZONTAL,
    stringPainted: Boolean = false,
    string: @Nls String? = null,
) {
    ProgressBarNode(
        modifier = modifier,
        indeterminate = indeterminate,
        orientation = orientation,
        stringPainted = stringPainted,
        string = string,
    ) {
        // The range bounds the value, so it goes in first: a recomposition that moves both lands the
        // declared value rather than the one the old range admits.
        set(min) { this.minimum = it }
        set(max) { this.maximum = it }
        set(value) { this.value = it }
    }
}

/**
 * A composable wrapper for JProgressBar driven by a caller-owned [BoundedRangeModel]. The model owns the
 * value and the range, so nothing is declared over it: the bar renders whatever the model holds, and a
 * model the caller mutates repaints the bar without a recomposition. Supplying a new [model] instance
 * installs it on recomposition.
 *
 * This is what lets one range drive several widgets - a [Slider] the user moves and the bar reading it out,
 * or two views of the same progress - since each of them renders the model as-is:
 *
 * ```
 * val range = remember { DefaultBoundedRangeModel(30, 0, 0, 100) }
 * Slider(model = range)
 * ProgressBar(model = range)
 * ```
 *
 * @param model the range the bar renders; owned by the caller
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param indeterminate whether the progress bar is indeterminate
 * @param orientation the orientation of the progress bar (an [Orientation] `SwingConstants` value)
 * @param stringPainted whether the bar paints text over itself; `false` by default
 * @param string the text painted over the bar; `null` by default, which leaves the bar to paint the
 *   completion percentage, so [stringPainted] on its own gives a percentage readout
 * @see javax.swing.JProgressBar
 */
@Composable
public fun ProgressBar(
    model: BoundedRangeModel,
    modifier: SwingModifier = SwingModifier,
    indeterminate: Boolean = false,
    @Orientation orientation: Int = SwingConstants.HORIZONTAL,
    stringPainted: Boolean = false,
    string: @Nls String? = null,
) {
    ProgressBarNode(
        modifier = modifier,
        indeterminate = indeterminate,
        orientation = orientation,
        stringPainted = stringPainted,
        string = string,
    ) {
        set(model) { this.model = it }
    }
}

/**
 * The `JProgressBar` node both [ProgressBar] overloads render: all of it but the range, which
 * [installRange] declares - a value between a minimum and a maximum in one overload, the caller's own model
 * in the other.
 */
@Composable
private fun ProgressBarNode(
    modifier: SwingModifier,
    indeterminate: Boolean,
    @Orientation orientation: Int,
    stringPainted: Boolean,
    string: @Nls String?,
    installRange: SwingNodeUpdater<JProgressBar>.() -> Unit,
) {
    SwingNode(
        factory = { JProgressBar() },
        update = {
            installRange()
            set(indeterminate) { this.isIndeterminate = it }
            set(orientation) { this.orientation = it }
            set(string) { this.string = it }
            set(stringPainted) { this.isStringPainted = it }
            applyModifier(modifier)
        },
    )
}
