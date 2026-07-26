package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.layout.alignmentX
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Dimension
import java.awt.FlowLayout
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

// A multiline editor built on the state-based text model over the shared document: one DocumentState is
// the whole source of truth. The area renders the state's own document (no value round-trip per
// keystroke), the Undo/Redo buttons drive and observe that same state, and the status line reads its
// live text - the advantage a single small field cannot show.
@Composable
internal fun EditorSection() {
    SectionColumn {
        SectionHeading("Editor")
        DocumentEditorCard()
    }
}

@Composable
private fun DocumentEditorCard() {
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
        FlowPanel(alignment = FlowLayout.LEADING, modifier = SwingModifier.alignmentX(LEFT_ALIGNED)) {
            Button(text = "Undo", onClick = state::undo, modifier = SwingModifier.enabled(state.canUndo))
            Button(text = "Redo", onClick = state::redo, modifier = SwingModifier.enabled(state.canRedo))
        }
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(440, 200)).alignmentX(LEFT_ALIGNED)) {
            content {
                TextArea(state = state, rows = 12, columns = 60)
            }
        }
        Label("$lines lines · $words words · $characters characters")
    }
}

private val WHITESPACE = "\\s+".toRegex()
