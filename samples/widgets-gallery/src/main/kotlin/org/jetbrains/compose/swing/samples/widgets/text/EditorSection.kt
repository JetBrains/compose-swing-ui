package org.jetbrains.compose.swing.samples.widgets.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.highlights
import org.jetbrains.compose.swing.modifier.interaction.caretUpdatePolicy
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.caretListener
import org.jetbrains.compose.swing.modifier.listener.documentListener
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.text.TextRange
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.event.CaretListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.DefaultCaret
import javax.swing.text.DefaultHighlighter
import javax.swing.text.PlainDocument

// The document the editor section edits and the File menu loads into. The menu bar and the shell are
// separate compositions, so they cannot share a remembered DocumentState; they share this one plain
// document instead - constructed outside any composition, both sides reference it by name. File > Open
// replaces its content; the section's DocumentState wraps it and re-renders the loaded text.
internal val galleryEditorDocument =
    PlainDocument().apply {
        insertString(0, "Type here, then use File > Open to load a file into this editor.", null)
    }

// Replaces the whole content of the shared editor document. The Editor section's DocumentState observes
// this document, so the new text appears in the area - and its live counts update - without any wiring
// between the two compositions.
internal fun setEditorText(text: String) {
    galleryEditorDocument.replace(0, galleryEditorDocument.length, text, null)
}

// A multiline editor built on the state-based text model: DocumentState is the whole source of truth,
// carrying an editor's scripted edits, its selection, and the search highlights painted over it.
@Composable
internal fun EditorSection() {
    SectionColumn {
        SectionHeading("Editor")
        DocumentEditorCard()
        ScriptedEditCard()
        SelectionCard()
        HighlightCard()
        CaretCard()
    }
}

@Composable
private fun ColumnScope.DocumentEditorCard() {
    ExampleCard("TextArea (DocumentState) with undo/redo") {
        // Drives the editor with the gallery's shared document, so text loaded by File > Open appears
        // here and every reader below goes through this single state.
        val state = rememberDocumentState(document = galleryEditorDocument)

        // Derived from the observable text: reading state.text here subscribes to edits, so the counts
        // refresh on every keystroke - including edits made by undo, redo, and File > Open - with no
        // manual wiring.
        val text = state.text
        val characters = text.length
        val lines = text.count { it == '\n' } + 1
        val words = text.split(WHITESPACE).count { it.isNotBlank() }

        // canUndo / canRedo are snapshot-observable, so the buttons enable and disable themselves as
        // history changes - no listener, no state mirror to keep in sync.
        FlowPanel(alignment = FlowLayout.LEADING) {
            Button(text = "Undo", onClick = state::undo, modifier = SwingModifier.enabled(state.canUndo))
            Button(text = "Redo", onClick = state::redo, modifier = SwingModifier.enabled(state.canRedo))
        }
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(440, 200))) {
            content {
                TextArea(state = state, rows = 12, columns = 60)
            }
        }
        Label("$lines lines · $words words · $characters characters")
    }
}

@Composable
private fun ColumnScope.ScriptedEditCard() {
    ExampleCard("DocumentState.edit (scripted edits)") {
        val seed = "The quick brown fox jumps over the lazy dog."
        val state = rememberDocumentState(seed)

        // Every button drives one DocumentEditScope member. Each call inside edit { } lands as part of
        // one compound change, and the trailing selectAll/placeCaretAtEnd calls place the caret without
        // editing the text.
        FlowPanel(alignment = FlowLayout.LEADING) {
            Button("insert", onClick = { state.edit { insert(0, "NEW ") } })
            Button("append", onClick = { state.edit { append(" MORE") } })
            Button("replace", onClick = { state.edit { replace(0, minOf(3, length), "XXX") } })
            Button("delete", onClick = { state.edit { delete(0, minOf(4, length)) } })
            Button("setText", onClick = { state.edit { setText(seed) } })
            Button("selectAll", onClick = { state.edit { selectAll() } })
            Button("placeCaretAtEnd", onClick = { state.edit { placeCaretAtEnd() } })
        }
        TextArea(state = state, rows = 3, columns = 50)
        Label("Selection: ${state.selection.start} – ${state.selection.end}")
    }
}

@Composable
private fun ColumnScope.SelectionCard() {
    ExampleCard("DocumentState.selection") {
        val state = rememberDocumentState("Drag across this text to select it, or set the range below.")
        var start by remember { mutableIntStateOf(0) }
        var end by remember { mutableIntStateOf(4) }

        // Reading state.selection here subscribes to caret changes, so the label follows a selection made
        // with the mouse or the keyboard as well as one applied through the spinners below.
        TextArea(state = state, rows = 3, columns = 50)
        Label("Selection: ${state.selection.start} – ${state.selection.end}")
        FlowPanel {
            Label("Start:")
            Spinner(start, onValueChange = { start = it.toInt() }, min = 0, max = 200, step = 1)
            Label("End:")
            Spinner(end, onValueChange = { end = it.toInt() }, min = 0, max = 200, step = 1)
            Button(
                "Apply",
                onClick = { state.selection = TextRange(start, end) },
            )
        }
    }
}

@Composable
private fun ColumnScope.HighlightCard() {
    ExampleCard("highlights (search and highlight)") {
        var source by remember {
            mutableStateOf("The quick brown fox jumps over the lazy dog. The dog barks at the fox.")
        }
        var query by remember { mutableStateOf("dog") }
        // The painter is compared by identity, so it is hoisted into a remember to repaint only when the
        // declared ranges actually change rather than on every recomposition.
        val painter = remember { DefaultHighlighter.DefaultHighlightPainter(Color(0xFF, 0xF1, 0x8A)) }
        val matches = remember(source, query) { findMatches(source, query) }

        FlowPanel(alignment = FlowLayout.LEADING) {
            Label("Search:")
            TextField(value = query, onValueChange = { query = it }, columns = 16)
        }
        TextArea(
            value = source,
            onValueChange = { source = it },
            rows = 4,
            columns = 50,
            lineWrap = true,
            wrapStyleWord = true,
            modifier = SwingModifier.highlights(matches, painter),
        )
        Label("${matches.size} match(es)")
    }
}

@Composable
private fun ColumnScope.CaretCard() {
    ExampleCard("caretListener, documentListener & caretUpdatePolicy") {
        var text by remember {
            mutableStateOf("Scroll down, then click Append and watch where the caret ends up.\n".repeat(6))
        }
        var caretInfo by remember { mutableStateOf("dot 0, mark 0") }
        var edits by remember { mutableIntStateOf(0) }
        var policyIndex by remember { mutableIntStateOf(0) }
        // remember each listener instance: it is attached as-is, so a fresh one each recomposition would
        // detach the old and attach the new instead of simply reporting through the same instance.
        val caretMoveListener =
            remember { CaretListener { event -> caretInfo = "dot ${event.dot}, mark ${event.mark}" } }
        val documentEditListener =
            remember {
                object : DocumentListener {
                    override fun insertUpdate(e: DocumentEvent) {
                        edits++
                    }

                    override fun removeUpdate(e: DocumentEvent) {
                        edits++
                    }

                    override fun changedUpdate(e: DocumentEvent) {
                        edits++
                    }
                }
            }

        RadioGroup(
            selectedIndex = policyIndex,
            onSelectionChange = { policyIndex = it },
            axis = BoxLayout.X_AXIS,
        ) {
            CARET_UPDATE_POLICIES.forEach { (name, _) -> option(name) }
        }
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(360, 90))) {
            content {
                TextArea(
                    value = text,
                    onValueChange = { text = it },
                    rows = 6,
                    columns = 40,
                    modifier =
                        SwingModifier
                            .caretListener(caretMoveListener)
                            .documentListener(documentEditListener)
                            .caretUpdatePolicy(CARET_UPDATE_POLICIES[policyIndex].second),
                )
            }
        }
        Button("Append a line at the end", onClick = { text += "Appended line.\n" })
        Label("Caret: $caretInfo")
        Label("Document edits observed: $edits")
    }
}

// The DefaultCaret.*_UPDATE policies caretUpdatePolicy accepts, named for the radio group.
private val CARET_UPDATE_POLICIES =
    listOf(
        "Always update" to DefaultCaret.ALWAYS_UPDATE,
        "Never update" to DefaultCaret.NEVER_UPDATE,
        "Update on EDT" to DefaultCaret.UPDATE_WHEN_ON_EDT,
    )

// Every case-insensitive occurrence of a non-empty query in source, as the ranges highlights paints.
private fun findMatches(
    source: String,
    query: String,
): List<TextRange> {
    if (query.isEmpty()) return emptyList()
    val ranges = mutableListOf<TextRange>()
    var index = source.indexOf(query, ignoreCase = true)
    while (index >= 0) {
        ranges += TextRange(index, index + query.length)
        index = source.indexOf(query, index + query.length, ignoreCase = true)
    }
    return ranges
}

private val WHITESPACE = "\\s+".toRegex()
