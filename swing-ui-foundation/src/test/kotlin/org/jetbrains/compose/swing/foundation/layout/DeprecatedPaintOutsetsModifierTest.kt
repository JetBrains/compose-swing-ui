package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.style.TextFieldStyleScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Test

// The test calls the deprecated modifiers on purpose to pin their forwarding.

/**
 * A deprecated modifier leaves the component with what the style setter of the same name writes, and removing it
 * restores the value the component held before, which the harness's restore check covers.
 */
@Suppress("DEPRECATION")
class DeprecatedPaintOutsetsModifierTest {
    private enum class Declared { None, Modifier, Style }

    @Test
    fun paintOutsetsLeavesWhatTheStyleSetterWritesAndRemovingItRestoresTheOriginal() =
        runComposeSwingTest {
            val expected = -LINE
            var declared by mutableStateOf(Declared.None)
            setContent {
                val modifier = SwingModifier.paintOutsets(PaintOutsets.FullInsets)
                val style = SwingStyle<TextFieldStyleScope> { paintOutsets(PaintOutsets.FullInsets) }
                Column {
                    LinedField(
                        "target",
                        if (declared == Declared.Modifier) modifier else SwingModifier,
                        if (declared == Declared.Style) style else SwingStyle,
                    )
                }
            }
            val node = onNodeWithTag("target")

            declared = Declared.Modifier
            awaitIdle()
            node.assertProperty(expected, "paintOutsets: the modifier writes the literal value") { x }

            declared = Declared.None
            awaitIdle()

            declared = Declared.Style
            awaitIdle()
            node.assertProperty(expected, "paintOutsets: the style setter writes the literal value") { x }

            declared = Declared.None
            awaitIdle()
        }
}
