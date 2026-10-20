package org.jetbrains.compose.swing.samples.agent.transcript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.samples.agent.components.Card
import org.jetbrains.compose.swing.samples.agent.components.LeftToRight
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.state.DiffLine
import org.jetbrains.compose.swing.samples.agent.state.DiffLineKind
import org.jetbrains.compose.swing.samples.agent.state.diffLines
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.StyleClass
import javax.swing.BorderFactory
import javax.swing.JScrollPane

/** A unified [diff], line by line: added lines tinted green, removed ones red, with the old and new line numbers. */
@Composable
internal fun ConstrainedScope.DiffView(
    diff: String,
    modifier: SwingModifier = SwingModifier,
) {
    // The card above already names the file, so the file headers are left out.
    val lines = remember(diff) { diffLines(diff).filter { it.kind != DiffLineKind.FileHeader } }
    var showAll by remember(diff) { mutableStateOf(false) }
    val preview = if (showAll) lines else lines.take(200)
    val gutterDigits = (lines.maxOfOrNull { maxOf(it.oldNumber ?: 0, it.newNumber ?: 0) } ?: 0).toString().length
    // The block's border insets keep the full-width line tints clear of its rounded corners.
    LeftToRight {
        Card(modifier, style = StyleClass.CodeBlock) {
            ScrollPane(
                SwingModifier.fillMaxWidth().opaque(false).border(BorderFactory.createEmptyBorder()),
                verticalScrollbar = JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS,
            ) {
                Viewport(SwingModifier.opaque(false)) {
                    Panel(PanelLayout.Border(), SwingModifier.opaque(false)) {
                        Column(SwingModifier.center()) {
                            for (line in preview) DiffLineRow(line, gutterDigits)
                        }
                    }
                }
            }
            if (lines.size > 200) {
                DisclosureButton(
                    expanded = showAll,
                    onExpandedChange = { showAll = it },
                    text = if (showAll) message("toolCall.showLess") else message("toolCall.showAll", lines.size),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.DiffLineRow(
    line: DiffLine,
    gutterDigits: Int,
) {
    val theme = LocalAgentTheme.current
    val colors = theme.colors
    val mono = SwingModifier.font(theme.fonts.mono)
    val (tint, markerColor) =
        when (line.kind) {
            DiffLineKind.Added -> colors.diffAdded to colors.success
            DiffLineKind.Removed -> colors.diffRemoved to colors.danger
            DiffLineKind.Hunk -> colors.diffHunk to colors.mutedText
            else -> colors.codeBackground to colors.mutedText
        }
    Row(SwingModifier.fillMaxWidth().background(tint, RectangleShape).padding(horizontal = 4, vertical = 1)) {
        if (line.kind == DiffLineKind.Hunk) {
            Label(line.text, mono.foreground(colors.mutedText))
        } else {
            Label(line.oldNumber.gutter(gutterDigits), mono.foreground(colors.mutedText))
            Label(line.newNumber.gutter(gutterDigits), mono.foreground(colors.mutedText).padding(start = 8))
            Label(line.kind.marker, mono.foreground(markerColor).padding(horizontal = 8))
            Label(line.text, mono.foreground(colors.text))
        }
    }
}

private val DiffLineKind.marker: String
    get() =
        when (this) {
            DiffLineKind.Added -> "+"
            DiffLineKind.Removed -> "−"
            else -> " "
        }

/** A line number right-aligned in a monospaced gutter [digits] wide; blank where the line has none. */
private fun Int?.gutter(digits: Int) = this?.toString().orEmpty().padStart(digits)

internal fun hasDiffHunk(output: String): Boolean = output.lineSequence().any { it.startsWith("@@") }
