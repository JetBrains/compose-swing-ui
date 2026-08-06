package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.components.text.TextPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.alignmentX
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Dimension

// The rich-text components EditorPane and TextPane. The EditorPane renders markup it is given, flipping
// its ContentType between plain text and HTML so the same source is shown raw, then rendered. The
// TextPane is bound to a remember { mutableStateOf(...) } value so edits and the echo label stay in
// lock-step.
@Composable
internal fun RichTextSection() {
    SectionColumn {
        SectionHeading("Rich text")
        EditorPaneCard()
        TextPaneCard()
    }
}

@Composable
private fun EditorPaneCard() {
    ExampleCard("EditorPane (PlainText / Html)") {
        val markup = "<h2>Hello</h2><p>Shown raw, then rendered as <b>HTML</b>.</p>"
        var html by remember { mutableStateOf(false) }

        FlowPanel(modifier = SwingModifier.alignmentX(LEFT_ALIGNED)) {
            ToggleButton(
                text = if (html) "Content type: Html" else "Content type: PlainText",
                selected = html,
                onSelectedChange = { html = it },
            )
        }
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(360, 120)).alignmentX(LEFT_ALIGNED)) {
            content {
                EditorPane(
                    markup,
                    contentType = if (html) "text/html" else "text/plain",
                )
            }
        }
        Label("Rendered as ${if (html) "HTML" else "plain text"}")
    }
}

@Composable
private fun TextPaneCard() {
    ExampleCard("TextPane") {
        var notes by remember { mutableStateOf("A styled-document editor.\nType here.") }
        var editable by remember { mutableStateOf(true) }

        CheckBox(text = "Editable", checked = editable, onCheckedChange = { editable = it })
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(360, 100)).alignmentX(LEFT_ALIGNED)) {
            content {
                TextPane(
                    value = notes,
                    onValueChange = { notes = it },
                    editable = editable,
                )
            }
        }
        Label("Length: ${notes.length}")
    }
}
