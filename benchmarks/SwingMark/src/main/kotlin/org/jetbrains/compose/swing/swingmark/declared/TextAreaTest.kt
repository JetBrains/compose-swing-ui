package org.jetbrains.compose.swing.swingmark.declared

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.text.DocumentState
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.swingmark.fixtures.TEXT_AREA_DISPLAY_STRING
import org.jetbrains.compose.swing.swingmark.harness.change
import javax.swing.JTextArea

/**
 * `TextAreaTest`: text appended to a wrapping area, with a line break every twelfth append.
 *
 * Declared over a document rather than over a string. An append reaches the same `insertString` the
 * original's `JTextArea.append` does, where a field declared by its whole value would hand the library a
 * longer string to diff and rewrite on every pass.
 */
internal class TextAreaTest : DeclaredTest() {
    override val testName: String = "TextArea"

    private lateinit var document: DocumentState

    @Composable
    override fun Content() {
        document = rememberDocumentState()
        FlowPanel {
            ScrollPane {
                TextArea(
                    state = document,
                    modifier = SwingModifier.viewport(),
                    rows = ROWS,
                    columns = COLUMNS,
                    lineWrap = true,
                )
            }
        }
    }

    override fun runTest() {
        val area = widget(JTextArea::class.java)
        var length = 0
        repeat(REPEAT) { pass ->
            val breaks = pass % BREAK_INCREMENT == BREAK_INCREMENT - 1
            val appended = if (breaks) "$TEXT_AREA_DISPLAY_STRING\n" else TEXT_AREA_DISPLAY_STRING
            length += appended.length
            val expected = length
            change(
                apply = { document.edit { append(appended) } },
                reached = { area.document.length == expected },
            )
        }
    }

    private companion object {
        const val REPEAT = 300
        const val BREAK_INCREMENT = 12
        const val ROWS = 10
        const val COLUMNS = 30
    }
}
