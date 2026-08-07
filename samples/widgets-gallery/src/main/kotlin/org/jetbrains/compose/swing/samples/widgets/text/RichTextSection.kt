package org.jetbrains.compose.swing.samples.widgets.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextPane
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import java.awt.Dimension
import java.net.URI
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

// The rich-text editors. Markup and the rendering of it are two different things, so they get a card
// each: a source area above the pane that renders what it holds, then a pane over an HTML document
// state, where the rendered content itself is what the user types into. TextPane closes on a styled
// document bound to a remember { mutableStateOf(...) } value.
@Composable
internal fun RichTextSection() {
    SectionColumn {
        SectionHeading("Rich text")
        MarkupPreviewCard()
        RichTextEditorCard()
        BaseUrlCard()
        TextPaneCard()
        TextPaneStateCard()
    }
}

@Composable
private fun ColumnScope.MarkupPreviewCard() {
    ExampleCard("EditorPane (source and rendered preview)") {
        var markup by remember {
            mutableStateOf(
                "<h2>Hello</h2><p>Edit the source and watch it render. " +
                    "Activate the <a href=\"https://example.org/docs\">link</a> - nothing opens, the " +
                    "href is simply reported.</p>",
            )
        }
        var activated by remember { mutableStateOf("nothing yet") }

        Label("Source")
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(360, 90))) {
            TextArea(
                value = markup,
                onValueChange = { markup = it },
                modifier = SwingModifier.viewport(),
                lineWrap = true,
            )
        }
        Label("Rendered")
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(360, 120))) {
            EditorPane(
                markup = markup,
                modifier = SwingModifier.viewport(),
                contentType = "text/html",
                onLinkActivate = { activated = it },
            )
        }
        Label("Link activated: $activated")
    }
}

@Composable
private fun ColumnScope.RichTextEditorCard() {
    ExampleCard("EditorPane (rich text the user authors)") {
        val notes =
            rememberDocumentState(
                "<h2>Notes</h2><p>Type into the <b>rendered</b> content.</p>",
                contentType = "text/html",
            )
        var editable by remember { mutableStateOf(true) }

        CheckBox(text = "Editable", checked = editable, onCheckedChange = { editable = it })
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(360, 120))) {
            EditorPane(state = notes, modifier = SwingModifier.viewport(), editable = editable)
        }
        Label("Length: ${notes.text.length}   Undo available: ${if (notes.canUndo) "yes" else "no"}")
    }
}

@Composable
private fun ColumnScope.BaseUrlCard() {
    ExampleCard("EditorPane (baseUrl + raw hyperlinkListener)") {
        var lastEvent by remember { mutableStateOf("nothing yet") }
        val baseUrl = remember { URI("https://example.org/docs/").toURL() }
        // The raw listener reaches every link event a pane publishes and the resolved URL, where
        // onLinkActivate reports only the activated one's raw href.
        val listener =
            remember {
                HyperlinkListener { event ->
                    val kind =
                        when (event.eventType) {
                            HyperlinkEvent.EventType.ACTIVATED -> "activated"
                            HyperlinkEvent.EventType.ENTERED -> "entered"
                            else -> "exited"
                        }
                    lastEvent = "$kind ${event.url?.toString() ?: event.description}"
                }
            }
        EditorPane(
            markup =
                "<p>The relative link below resolves against the base \"$baseUrl\": " +
                    "<a href=\"guide.html\">guide.html</a></p>",
            hyperlinkListener = listener,
            contentType = "text/html",
            baseUrl = baseUrl,
        )
        Label("Last link event: $lastEvent")
    }
}

@Composable
private fun ColumnScope.TextPaneCard() {
    ExampleCard("TextPane") {
        var notes by remember { mutableStateOf("A styled-document editor.\nType here.") }
        var editable by remember { mutableStateOf(true) }

        CheckBox(text = "Editable", checked = editable, onCheckedChange = { editable = it })
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(360, 100))) {
            TextPane(
                value = notes,
                onValueChange = { notes = it },
                modifier = SwingModifier.viewport(),
                editable = editable,
            )
        }
        Label("Length: ${notes.length}")
    }
}

@Composable
private fun ColumnScope.TextPaneStateCard() {
    ExampleCard("TextPane (DocumentState)") {
        val state =
            rememberDocumentState(
                "{\\rtf1\\ansi\\deff0 {\\b A styled document} driven by state.\\par " +
                    "Edits here go through the undo manager.}",
                contentType = "text/rtf",
            )
        Button("Undo", modifier = SwingModifier.enabled(state.canUndo), onClick = state::undo)
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(360, 100))) {
            TextPane(state = state, modifier = SwingModifier.viewport())
        }
        Label("Length: ${state.text.length}")
    }
}
