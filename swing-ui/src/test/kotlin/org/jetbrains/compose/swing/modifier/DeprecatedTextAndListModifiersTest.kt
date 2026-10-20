package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.listItemRenderer
import org.jetbrains.compose.swing.components.style.ComboBoxStyleScope
import org.jetbrains.compose.swing.components.style.ListBoxStyleScope
import org.jetbrains.compose.swing.components.style.TextStyleScope
import org.jetbrains.compose.swing.components.style.caret
import org.jetbrains.compose.swing.components.style.caretBlinkRate
import org.jetbrains.compose.swing.components.style.caretColor
import org.jetbrains.compose.swing.components.style.caretUpdatePolicy
import org.jetbrains.compose.swing.components.style.disabledTextColor
import org.jetbrains.compose.swing.components.style.documentFilter
import org.jetbrains.compose.swing.components.style.focusAccelerator
import org.jetbrains.compose.swing.components.style.highlights
import org.jetbrains.compose.swing.components.style.listItemRenderer
import org.jetbrains.compose.swing.components.style.navigationFilter
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
import org.jetbrains.compose.swing.modifier.interaction.documentFilter
import org.jetbrains.compose.swing.modifier.interaction.focusAccelerator
import org.jetbrains.compose.swing.modifier.interaction.navigationFilter
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.text.TextRange
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import javax.swing.DefaultListCellRenderer
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JTextArea
import javax.swing.text.AbstractDocument
import javax.swing.text.DefaultCaret
import javax.swing.text.DefaultHighlighter
import javax.swing.text.DocumentFilter
import javax.swing.text.NavigationFilter

// The test calls the deprecated modifiers on purpose to pin their forwarding.

/**
 * A deprecated modifier leaves the component with what the style setter of the same name writes, and removing it
 * restores the value the component held before.
 */
@Suppress("DEPRECATION")
class DeprecatedTextAndListModifiersTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun modifierAndStyleApplyAndRestore(case: DeprecatedModifierCase<*>) = runDeprecatedModifierCase(case)

    companion object {
        @JvmStatic
        fun cases(): List<Named<*>> = textColorCases() + textCaretCases() + textEditingCases() + rendererCases()

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
            val navigation = object : NavigationFilter() {}
            val document = object : DocumentFilter() {}
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
                    "documentFilter",
                    document,
                    { (it.document as AbstractDocument).documentFilter },
                    SwingModifier.documentFilter(document),
                    SwingStyle { documentFilter(document) },
                ),
                textCase(
                    "navigationFilter",
                    navigation,
                    { it.navigationFilter },
                    SwingModifier.navigationFilter(navigation),
                    SwingStyle { navigationFilter(navigation) },
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

        private fun rendererCases(): List<Named<*>> {
            val items = listOf("alpha", "beta", "gamma")
            val listRenderer = DefaultListCellRenderer()
            val comboRenderer = DefaultListCellRenderer()
            return listOf(
                named(
                    DeprecatedModifierCase<ListBoxStyleScope>(
                        "listItemRenderer on ListBox",
                        listRenderer,
                        { m, s -> ListBox(items, modifier = m, style = s) },
                        { (it as JList<*>).cellRenderer },
                        SwingModifier.listItemRenderer(listRenderer),
                        SwingStyle { listItemRenderer(listRenderer) },
                    ),
                ),
                named(
                    DeprecatedModifierCase<ComboBoxStyleScope>(
                        "listItemRenderer on ComboBox",
                        comboRenderer,
                        { m, s -> ComboBox(items, null, {}, modifier = m, style = s) },
                        { (it as JComboBox<*>).renderer },
                        SwingModifier.listItemRenderer(comboRenderer),
                        SwingStyle { listItemRenderer(comboRenderer) },
                    ),
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
                { m, s -> TextArea("hello world", {}, modifier = m, style = s) },
                { read(it as JTextArea) },
                modifier,
                style,
            ),
        )

        private fun named(case: DeprecatedModifierCase<*>): Named<*> = Named.of(case.name, case)
    }
}
