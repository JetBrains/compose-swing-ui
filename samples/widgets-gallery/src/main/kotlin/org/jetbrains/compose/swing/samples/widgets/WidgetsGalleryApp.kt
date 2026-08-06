package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.CheckBoxMenuItem
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Menu
import org.jetbrains.compose.swing.components.MenuItem
import org.jetbrains.compose.swing.components.MenuSeparator
import org.jetbrains.compose.swing.components.RadioButtonMenuItem
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Dimension
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

// The composable menu bar: File, Edit, and a View menu exercising CheckBoxMenuItem and a
// RadioButtonMenuItem group, all driven by hoisted state. File > New and File > Open drive the
// gallery's shared editor document, which the Editor section renders. The owner window is passed in
// because the menu bar is its own composition and does not receive the shell's LocalWindow; File > Open
// brings it to the front so the loaded document is in view.
@Composable
internal fun ShowcaseMenuBar(
    owner: Window,
    onExit: () -> Unit,
) {
    var wrapText by remember { mutableStateOf(true) }
    var density by remember { mutableIntStateOf(0) }
    Menu("File") {
        MenuItem("New", onClick = { setEditorText("") })
        MenuItem("Open") {
            // Loads a canned document into the shared editor and surfaces the window that renders it,
            // showing one composition (the menu bar) drive another (the Editor section) through the
            // document they share.
            setEditorText(SAMPLE_DOCUMENT)
            owner.toFront()
        }
        MenuSeparator()
        MenuItem("Exit", onClick = onExit)
    }
    Menu("Edit") {
        MenuItem("Cut")
        MenuItem("Copy")
        MenuItem("Paste")
    }
    Menu("View") {
        CheckBoxMenuItem("Wrap text", checked = wrapText, onCheckedChange = { wrapText = it })
        MenuSeparator()
        RadioButtonMenuItem("Comfortable", selected = density == 0, onSelectedChange = { density = 0 })
        RadioButtonMenuItem("Compact", selected = density == 1, onSelectedChange = { density = 1 })
    }
}

// The sidebar + detail shell: the sidebar selection chooses which section body fills the center.
@Composable
internal fun ShowcaseShell() {
    var selected by remember { mutableIntStateOf(0) }
    BorderPanel(
        modifier = SwingModifier.border(BorderFactory.createEmptyBorder(12, 12, 12, 12)),
    ) {
        west {
            ScrollPane(modifier = SwingModifier.preferredSize(Dimension(180, 0))) {
                content {
                    ListBox(
                        items = showcaseSections.map { it.title },
                        selectedIndices = listOf(selected),
                        onSelectionChange = { indices -> indices.firstOrNull()?.let { selected = it } },
                        selectionMode = ListSelectionModel.SINGLE_SELECTION,
                        visibleRowCount = showcaseSections.size,
                        modifier = SwingModifier.accessibleName("Sections"),
                    )
                }
            }
        }
        center {
            showcaseSections.getOrNull(selected)?.body?.invoke()
        }
        south {
            Label(
                "Section: ${showcaseSections.getOrNull(selected)?.title ?: "-"}",
                modifier = SwingModifier.horizontalAlignment(SwingConstants.LEADING),
            )
        }
    }
}

// The canned text File > Open loads into the shared editor document - a stand-in for a real file's
// contents, so the sample stays self-contained and needs no file on disk.
private const val SAMPLE_DOCUMENT =
    "Opened from the File menu.\n\n" +
        "This text was loaded into the editor's shared document by File > Open, which lives in a " +
        "separate composition from the Editor section that renders it."
