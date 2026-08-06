@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.constants.Orientation
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
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
    string: String? = null,
) {
    SwingNode(
        factory = { JProgressBar(min, max) },
        update = {
            set(min) { this.minimum = it }
            set(max) { this.maximum = it }
            set(value) { this.value = it }
            set(indeterminate) { this.isIndeterminate = it }
            set(orientation) { this.orientation = it }
            set(string) { this.string = it }
            set(stringPainted) { this.isStringPainted = it }
            applyModifier(modifier)
        },
    )
}
