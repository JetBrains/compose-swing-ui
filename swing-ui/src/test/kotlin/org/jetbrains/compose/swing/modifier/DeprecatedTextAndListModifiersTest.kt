package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.components.style.TextStyleScope
import org.jetbrains.compose.swing.components.style.caret
import org.jetbrains.compose.swing.components.style.caretBlinkRate
import org.jetbrains.compose.swing.components.style.caretColor
import org.jetbrains.compose.swing.components.style.caretUpdatePolicy
import org.jetbrains.compose.swing.components.style.disabledTextColor
import org.jetbrains.compose.swing.components.style.focusAccelerator
import org.jetbrains.compose.swing.components.style.highlights
import org.jetbrains.compose.swing.components.style.selectedTextColor
import org.jetbrains.compose.swing.components.style.selectionColor
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.modifier.appearance.caretColor
import org.jetbrains.compose.swing.modifier.appearance.disabledTextColor
import org.jetbrains.compose.swing.modifier.appearance.highlights
import org.jetbrains.compose.swing.modifier.appearance.selectedTextColor
import org.jetbrains.compose.swing.modifier.appearance.selectionColor
import org.jetbrains.compose.swing.modifier.interaction.caret
import org.jetbrains.compose.swing.modifier.interaction.caretBlinkRate
import org.jetbrains.compose.swing.modifier.interaction.caretUpdatePolicy
import org.jetbrains.compose.swing.modifier.interaction.focusAccelerator
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.text.TextRange
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import javax.swing.JTextArea
import javax.swing.text.DefaultCaret
import javax.swing.text.DefaultHighlighter

// The test calls the deprecated modifiers on purpose to pin their forwarding.

/**
 * A deprecated modifier leaves the component with what the style setter of the same name writes, and removing it
 * restores the value the component held before.
 */
@Suppress("DEPRECATION")
internal class DeprecatedTextAndListModifiersTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun modifierAndStyleApplyAndRestore(case: DeprecatedModifierCase<*>) = runDeprecatedModifierCase(case)

    companion object {
        @JvmStatic
        fun cases(): List<Named<*>> = textColorCases() + textCaretCases() + textEditingCases()

        private fun textColorCases(): List<Named<*>> = listOf(
            textCase("caretColor", Color.RED, { it.caretColor }, SwingModifier.caretColor(Color.RED)) {
                caretColor(Color.RED)
            },
            textCase("selectionColor", Color.RED, { it.selectionColor }, SwingModifier.selectionColor(Color.RED)) {
                selectionColor(Color.RED)
            },
            textCase(
                "selectedTextColor",
                Color.RED,
                { it.selectedTextColor },
                SwingModifier.selectedTextColor(Color.RED),
            ) { selectedTextColor(Color.RED) },
            textCase(
                "disabledTextColor",
                Color.RED,
                { it.disabledTextColor },
                SwingModifier.disabledTextColor(Color.RED),
            ) { disabledTextColor(Color.RED) },
        )

        private fun textCaretCases(): List<Named<*>> {
            val installedCaret = DefaultCaret()
            return listOf(
                textCase(
                    "caret",
                    installedCaret,
                    { it.caret },
                    SwingModifier.caret(installedCaret),
                    SwingStyle { caret(installedCaret) },
                ),
                textCase(
                    "caretBlinkRate",
                    250,
                    { it.caret.blinkRate },
                    SwingModifier.caretBlinkRate(250),
                    SwingStyle { caretBlinkRate(250) },
                ),
                textCase(
                    "caretUpdatePolicy",
                    DefaultCaret.NEVER_UPDATE,
                    { (it.caret as DefaultCaret).updatePolicy },
                    SwingModifier.caretUpdatePolicy(DefaultCaret.NEVER_UPDATE),
                    SwingStyle { caretUpdatePolicy(DefaultCaret.NEVER_UPDATE) },
                ),
            )
        }

        private fun textEditingCases(): List<Named<*>> {
            val painter = DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW)
            val ranges = listOf(TextRange(0, 2), TextRange(3, 5))
            return listOf(
                textCase(
                    "highlights",
                    ranges,
                    { area ->
                        area.highlighter.highlights
                            .filter { it.painter === painter }
                            .map { TextRange(it.startOffset, it.endOffset) }
                    },
                    SwingModifier.highlights(ranges, painter),
                    SwingStyle { highlights(ranges, painter) },
                ),
                textCase(
                    "focusAccelerator",
                    'A',
                    { it.focusAccelerator },
                    SwingModifier.focusAccelerator('A'),
                    SwingStyle { focusAccelerator('A') },
                ),
            )
        }

        private fun textCase(
            name: String,
            expected: Any?,
            read: (JTextArea) -> Any?,
            modifier: SwingModifier,
            style: SwingStyle<TextStyleScope>,
        ): Named<*> = named(
            DeprecatedModifierCase<TextStyleScope>(
                name,
                expected,
                { read(it as JTextArea) },
                modifier,
                style,
            ) { hostModifier, hostStyle ->
                TextArea("hello world", {}, modifier = hostModifier, style = hostStyle)
            },
        )

        private fun named(case: DeprecatedModifierCase<*>): Named<*> = Named.of(case.name, case)
    }
}
