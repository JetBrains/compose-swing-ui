package org.jetbrains.compose.swing.samples.todo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.SwingConstants

// Shared layout primitives - consistent spacing, typography, and the card shape - so the sample reads
// as one design rather than a raw widget grid.

internal const val ROW_GAP: Int = 8

// The whole screen wrapped in a uniform body inset. main composes exactly this; so does the test.
@Composable
internal fun ReactiveTaskListScreen() {
    BorderPanel(
        modifier = SwingModifier.emptyBorder(16),
    ) {
        ReactiveTaskList(SwingModifier.center())
    }
}

@Composable
internal fun SampleTitle(text: String) {
    Label(
        text = text,
        modifier =
            SwingModifier
                .font(Font(Font.SANS_SERIF, Font.BOLD, 20))
                .foreground(Color(0x2D, 0x4B, 0x73))
                .horizontalAlignment(SwingConstants.LEADING),
    )
}

// Secondary explanatory text. Wrapping it in an HTML body of bounded width lets a long caption flow
// onto multiple lines instead of forcing the layout wide and triggering a horizontal scrollbar.
@Composable
internal fun Caption(text: String) {
    Label(
        text = "<html><body style='width:520px'>$text</body></html>",
        modifier =
            SwingModifier
                .font(Font(Font.SANS_SERIF, Font.PLAIN, 14))
                .foreground(Color(0x5A, 0x5A, 0x5A))
                .horizontalAlignment(SwingConstants.LEADING),
    )
}

// A titled box that spans the width of the column it sits in, and whose content stacks top to bottom,
// each item at the size it asks for, so the card is exactly as tall as what it holds.
@Composable
internal fun ColumnScope.Card(
    title: String,
    content: @Composable () -> Unit,
) {
    // A border is compared by identity, so one built inline would be a new border on every recomposition
    // and would be written to the panel each time.
    val cardBorder =
        remember(title) {
            BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(ROW_GAP, ROW_GAP, ROW_GAP, ROW_GAP),
            )
        }
    Column(modifier = SwingModifier.border(cardBorder).fillWidth()) {
        content()
    }
}

// A label that echoes live state ("3 of 5 done"). Asking for a fixed width stops it from re-measuring
// and shifting its neighbors every time the value grows or shrinks - the most visible source of jitter
// in a reactive Swing UI.
@Composable
internal fun ValueLabel(
    text: String,
    width: Int,
) {
    Label(
        text = text,
        modifier =
            SwingModifier
                .preferredSize(Dimension(width, 22))
                .horizontalAlignment(SwingConstants.LEADING),
    )
}
