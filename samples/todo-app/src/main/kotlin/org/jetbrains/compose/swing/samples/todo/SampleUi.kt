package org.jetbrains.compose.swing.samples.todo

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.SwingConstants

// Shared layout primitives - consistent spacing, typography, and the card shape - so the sample reads
// as one design rather than a raw widget grid.

internal const val ROW_GAP: Int = 8

// The whole screen wrapped in a uniform body inset. main composes exactly this; so does the test.
@Composable
internal fun ReactiveTaskListScreen() {
    BorderPanel(
        modifier = SwingModifier.border(BorderFactory.createEmptyBorder(16, 16, 16, 16)),
    ) {
        center { ReactiveTaskList() }
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

@Composable
internal fun Card(
    title: String,
    content: @Composable () -> Unit,
) {
    BorderPanel(
        modifier =
            SwingModifier.border(
                BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(title),
                    BorderFactory.createEmptyBorder(ROW_GAP, ROW_GAP, ROW_GAP, ROW_GAP),
                ),
            ),
    ) {
        north {
            BoxPanel(axis = BoxLayout.Y_AXIS) {
                content()
            }
        }
    }
}

// A label that echoes live state ("3 of 5 done"). Pinning preferred/minimum/maximum to a fixed width
// stops it from re-measuring and shifting its neighbours every time the value grows or shrinks - the
// most visible source of jitter in a reactive Swing UI. Height stays flexible.
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
                .minimumSize(Dimension(width, 22))
                .maximumSize(Dimension(width, Int.MAX_VALUE))
                .horizontalAlignment(SwingConstants.LEADING),
    )
}
