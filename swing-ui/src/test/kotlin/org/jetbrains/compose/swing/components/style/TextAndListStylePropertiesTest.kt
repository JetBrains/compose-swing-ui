package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.selection.ComposingListCellRenderer
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.text.TextRange
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JTextArea
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.Caret
import javax.swing.text.DefaultCaret
import javax.swing.text.DefaultHighlighter
import javax.swing.text.DocumentFilter
import javax.swing.text.Highlighter
import javax.swing.text.NavigationFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class TextAndListStylePropertiesTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("setterCaseNames")
    fun settersUpdateAndRestore(index: Int) = runComposeSwingTest {
        val case = setterCases()[index]
        var value: Any? by mutableStateOf(null)
        setContent { case.content(value) }
        val node = case.node(this)
        var original: Any? = null
        node.assertProperty(true) {
            original = case.read(this)
            true
        }

        value = case.first
        awaitIdle()
        node.assertProperty(case.first, "${case.name}: the style writes its value") { case.read(this) }

        value = case.second
        awaitIdle()
        node.assertProperty(case.second, "${case.name}: a changed value is written") { case.read(this) }

        value = null
        awaitIdle()
        node.assertProperty(original, "${case.name}: withdrawal restores the original value") { case.read(this) }
    }

    @Test
    fun caretRemovalRestoresCaretAndSelection() = runComposeSwingTest {
        val styled = DefaultCaret()
        var setsCaret by mutableStateOf(false)
        setContent { TextArea("hello world", {}, style = { if (setsCaret) caret(styled) }) }
        val area = onNodeOfType<JTextArea>()
        var original: Caret? = null
        area.assertProperty(true) {
            original = caret
            caret !== styled
        }

        setsCaret = true
        awaitIdle()
        area.assertProperty(styled) { caret }
        area.fetch().select(2, 5)

        setsCaret = false
        awaitIdle()
        area.assertProperty(2 to 5, "the selection is carried across") { selectionStart to selectionEnd }
        area.assertProperty(original, "the original caret returns") { caret }
    }

    @Test
    fun documentFilterBlocksEditsUntilRemoved() = runComposeSwingTest {
        val rejectAll =
            object : DocumentFilter() {
                override fun insertString(
                    bypass: FilterBypass,
                    offset: Int,
                    string: String?,
                    attributes: AttributeSet?,
                ) = Unit
            }
        var filters by mutableStateOf(true)
        setContent { TextArea("text", {}, style = { if (filters) documentFilter(rejectAll) }) }
        val area = onNodeOfType<JTextArea>()

        area.fetch().document.insertString(0, "x", null)
        area.assertProperty("text", "the filter rejects the insert") { text }

        filters = false
        awaitIdle()
        area.assertProperty(null) { (document as AbstractDocument).documentFilter }
        area.fetch().document.insertString(0, "x", null)
        area.assertProperty("xtext", "the insert passes once the filter is gone") { text }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rendererCases")
    fun itemContentOverridesNestedRendererButYieldsToOwnStyle(listBox: Boolean) = runComposeSwingTest {
        val nested = DefaultListCellRenderer()
        val own = DefaultListCellRenderer()
        var ownStyled by mutableStateOf(false)
        setContent {
            if (listBox) {
                Panel(style = { provideNestedStyle(ListBoxes) { listItemRenderer(nested) } }) {
                    ListBox(ITEMS, style = { if (ownStyled) listItemRenderer(own) }) { item -> Label(item) }
                    ListBox(ITEMS)
                }
            } else {
                Panel(style = { provideNestedStyle(ComboBoxes) { listItemRenderer(nested) } }) {
                    ComboBox(ITEMS, null, {}, style = { if (ownStyled) listItemRenderer(own) }) { item -> Label(item) }
                    ComboBox(ITEMS, null, {})
                }
            }
        }
        val nodes = if (listBox) onAllNodesOfType<JList<*>>() else onAllNodesOfType<JComboBox<*>>()
        val readRenderer: (Component) -> Any? =
            if (listBox) {
                { (it as JList<*>).cellRenderer }
            } else {
                { (it as JComboBox<*>).renderer }
            }
        nodes.assertCountEquals(2)
        val withContent = nodes[0]
        val withoutContent = nodes[1]
        var cells: Any? = null
        withContent.assertProperty(true, "itemContent beats the nested style") {
            cells = readRenderer(this)
            cells is ComposingListCellRenderer<*>
        }
        withoutContent.assertProperty(true, "without itemContent the nested style applies") {
            readRenderer(this) === nested
        }

        ownStyled = true
        awaitIdle()
        withContent.assertProperty(true, "the component's own style beats itemContent") { readRenderer(this) === own }

        ownStyled = false
        awaitIdle()
        withContent.assertProperty(true, "the itemContent renderer returns") { readRenderer(this) === cells }
    }

    @Test
    fun highlightsUpdateAndRemoveOnlyOwnedMarks() = runComposeSwingTest {
        val ranges = mutableStateListOf(TextRange(0, 2))
        var declared by mutableStateOf(false)
        var painter by mutableStateOf<Highlighter.HighlightPainter>(HighlightPainter)
        setContent {
            TextArea(TEXT, {}, style = { if (declared) highlights(ranges, painter) })
        }
        val area = onNodeOfType<JTextArea>()
        area.assertProperty(emptyList()) { marksOf(HighlightPainter) }
        declared = true
        awaitIdle()
        area.assertProperty(listOf(TextRange(0, 2))) { marksOf(HighlightPainter) }
        ranges[0] = TextRange(1, 3)
        awaitIdle()
        area.assertProperty(listOf(TextRange(1, 3))) { marksOf(HighlightPainter) }
        declared = false
        awaitIdle()
        area.assertProperty(emptyList()) { marksOf(HighlightPainter) }
        ranges[0] = TextRange(0, 5)
        declared = true
        awaitIdle()
        area.assertProperty(listOf(TextRange(0, 5))) { marksOf(HighlightPainter) }
        val foreignPainter = DefaultHighlighter.DefaultHighlightPainter(Color.PINK)
        area.fetch().highlighter.apply {
            addHighlight(2, 4, foreignPainter)
            addHighlight(0, 1, foreignPainter)
        }

        ranges.add(TextRange(6, 11))
        awaitIdle()
        area.assertProperty(listOf(TextRange(0, 5), TextRange(6, 11))) { marksOf(HighlightPainter) }

        ranges.removeAt(0)
        awaitIdle()
        area.assertProperty(listOf(TextRange(6, 11))) { marksOf(HighlightPainter) }
        area.assertProperty(listOf(TextRange(2, 4), TextRange(0, 1))) { marksOf(foreignPainter) }

        ranges.clear()
        awaitIdle()
        area.assertProperty(emptyList()) { marksOf(HighlightPainter) }
        ranges.addAll(listOf(TextRange(0, 5), TextRange(6, 11)))
        awaitIdle()
        area.assertProperty(listOf(TextRange(0, 5), TextRange(6, 11))) { marksOf(HighlightPainter) }

        painter = foreignPainter
        awaitIdle()
        area.assertProperty(listOf(TextRange(2, 4), TextRange(0, 1), TextRange(0, 5), TextRange(6, 11))) {
            marksOf(foreignPainter)
        }
        area.assertProperty(emptyList()) { marksOf(HighlightPainter) }

        declared = false
        awaitIdle()
        area.assertProperty(emptyList()) { marksOf(HighlightPainter) }
        area.assertProperty(listOf(TextRange(2, 4), TextRange(0, 1))) { marksOf(foreignPainter) }
    }

    @ParameterizedTest
    @CsvSource("11, 6, 6, 11", "6, 400, 6, 11")
    fun rangesNormalizeAndClamp(
        start: Int,
        end: Int,
        expectedStart: Int,
        expectedEnd: Int,
    ) = runComposeSwingTest {
        setContent { TextArea(TEXT, {}, style = { highlights(listOf(TextRange(start, end)), HighlightPainter) }) }
        onNodeOfType<JTextArea>().assertProperty(listOf(TextRange(expectedStart, expectedEnd))) {
            marksOf(HighlightPainter)
        }
    }

    @Test
    fun highlightsFollowDocumentRebinding() = runComposeSwingTest {
        var rebound by mutableStateOf(false)
        setContent {
            val first = rememberDocumentState(TEXT)
            val second = rememberDocumentState(TEXT)
            TextArea(
                state = if (rebound) second else first,
                style = { highlights(listOf(TextRange(0, 5)), HighlightPainter) },
            )
        }
        val area = onNodeOfType<JTextArea>().fetch()
        val bound = area.document
        assertEquals(listOf(TextRange(0, 5)), area.marksOf(HighlightPainter))
        rebound = true
        awaitIdle()
        assertNotSame(bound, area.document)
        assertEquals(listOf(TextRange(0, 5)), area.marksOf(HighlightPainter))
    }

    companion object {
        @JvmStatic
        fun setterCaseNames(): List<Named<Int>> = setterCases().mapIndexed { index, case -> Named.of(case.name, index) }

        @JvmStatic
        fun rendererCases(): List<Named<Boolean>> = listOf(Named.of("ListBox", true), Named.of("ComboBox", false))
    }
}

private val ITEMS = listOf("alpha", "beta", "gamma")

private class ComposedSetterCase(
    val name: String,
    val first: Any,
    val second: Any,
    val content: @Composable (Any?) -> Unit,
    val node: ComposeSwingTest.() -> SwingNodeInteraction<Component>,
    val read: (Component) -> Any?,
)

private fun setterCases(): List<ComposedSetterCase> = listOf(
    textCase("caretColor", Color.RED, Color.BLUE, { it.caretColor }) { caretColor(it) },
    textCase("selectionColor", Color.RED, Color.BLUE, { it.selectionColor }) { selectionColor(it) },
    textCase("selectedTextColor", Color.RED, Color.BLUE, { it.selectedTextColor }) { selectedTextColor(it) },
    textCase("disabledTextColor", Color.RED, Color.BLUE, { it.disabledTextColor }) { disabledTextColor(it) },
    textCase("caret", DefaultCaret(), DefaultCaret(), { it.caret }) { caret(it) },
    textCase("caretBlinkRate", 250, 750, { it.caret.blinkRate }) { caretBlinkRate(it) },
    textCase("caretUpdatePolicy", DefaultCaret.ALWAYS_UPDATE, DefaultCaret.NEVER_UPDATE, {
        (it.caret as DefaultCaret).updatePolicy
    }) { caretUpdatePolicy(it) },
    textCase("documentFilter", DocumentFilter(), DocumentFilter(), {
        (it.document as AbstractDocument).documentFilter
    }) { documentFilter(it) },
    textCase("navigationFilter", NavigationFilter(), NavigationFilter(), { it.navigationFilter }) {
        navigationFilter(it)
    },
    textCase("focusAccelerator", 'A', 'B', { it.focusAccelerator }) { focusAccelerator(it) },
    ComposedSetterCase(
        "listItemRenderer on ListBox",
        DefaultListCellRenderer(),
        DefaultListCellRenderer(),
        { value ->
            ListBox(ITEMS, style = { (value as DefaultListCellRenderer?)?.let { listItemRenderer(it) } })
        },
        { onNodeOfType<JList<*>>() },
        { (it as JList<*>).cellRenderer },
    ),
    ComposedSetterCase(
        "listItemRenderer on ComboBox",
        DefaultListCellRenderer(),
        DefaultListCellRenderer(),
        { value ->
            ComboBox(ITEMS, null, {}, style = {
                (value as DefaultListCellRenderer?)?.let { listItemRenderer(it) }
            })
        },
        { onNodeOfType<JComboBox<*>>() },
        { (it as JComboBox<*>).renderer },
    ),
)

private inline fun <reified V : Any> textCase(
    name: String,
    first: V,
    second: V,
    noinline read: (JTextArea) -> Any?,
    noinline set: TextStyleScope.(V) -> Unit,
): ComposedSetterCase = ComposedSetterCase(
    name,
    first,
    second,
    { value -> TextArea("hello world", {}, style = { (value as V?)?.let { set(it) } }) },
    { onNodeOfType<JTextArea>() },
    { read(it as JTextArea) },
)

private fun JTextArea.marksOf(painter: Highlighter.HighlightPainter): List<TextRange> =
    highlighter.highlights.filter { it.painter === painter }.map { TextRange(it.startOffset, it.endOffset) }

private const val TEXT = "hello world"
private val HighlightPainter = DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW)
