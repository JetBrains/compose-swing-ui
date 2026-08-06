package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.CheckBoxMenuItem
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Menu
import org.jetbrains.compose.swing.components.MenuItem
import org.jetbrains.compose.swing.components.MenuSeparator
import org.jetbrains.compose.swing.components.RadioButtonMenuGroup
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.samples.widgets.text.setEditorText
import org.jetbrains.compose.swing.window.LocalWindow
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.JMenuItem
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

// The composable menu bar: File, Edit, a View menu exercising CheckBoxMenuItem and a
// RadioButtonMenuGroup, and a Help menu folding one item straight from MenuNode, the primitive
// every menu component above is itself built on. File > New and File > Open drive the gallery's
// shared editor document, which the Editor section renders. The menu bar is its own composition,
// mounted on the frame's own menu bar, and reads that frame as its LocalWindow.
@Composable
internal fun ShowcaseMenuBar(onExit: () -> Unit) {
    val owner = LocalWindow.current
    var wrapText by remember { mutableStateOf(true) }
    var density by remember { mutableIntStateOf(0) }
    var pings by remember { mutableIntStateOf(0) }
    val shortcut = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
    Menu("File") {
        MenuItem(
            "New",
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcut),
            onClick = { setEditorText("") },
        )
        MenuItem("Open", accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcut)) {
            // Loads a canned document into the shared editor and surfaces the window that renders it,
            // showing one composition (the menu bar) drive another (the Editor section) through the
            // document they share.
            setEditorText(SAMPLE_DOCUMENT)
            owner?.toFront()
        }
        MenuSeparator()
        MenuItem("Exit", onClick = onExit)
    }
    Menu("Edit") {
        MenuItem("Cut", accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_X, shortcut))
        MenuItem("Copy", accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcut))
        MenuItem("Paste", accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcut))
    }
    Menu("View") {
        CheckBoxMenuItem(
            "Wrap text",
            checked = wrapText,
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcut),
            onCheckedChange = { wrapText = it },
        )
        MenuSeparator()
        RadioButtonMenuGroup(selectedIndex = density, onSelectionChange = { density = it }) {
            option("Comfortable", accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_1, shortcut))
            option("Compact", accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_2, shortcut))
        }
    }
    Menu("Help") {
        // MenuNode is the primitive every menu composable above is folded from; this item is built
        // straight from a plain JMenuItem, with no library wrapper of its own between it and the item
        // MenuNode installs.
        val callback = rememberUpdatedState<() -> Unit> { pings++ }
        val listener = remember { ActionListener { callback.value() } }
        MenuNode(
            factory = { JMenuItem() },
            update = {
                set("Ping (raw MenuNode) - $pings") { this.text = it }
                applyModifier(SwingModifier.actionListener(listener))
            },
        )
    }
}

// The sidebar + detail shell: the sidebar selection chooses which section body fills the center.
@Composable
internal fun ShowcaseShell() {
    var selected by remember { mutableIntStateOf(0) }
    BorderPanel(
        modifier = SwingModifier.emptyBorder(12),
    ) {
        west {
            ScrollPane(modifier = SwingModifier.preferredSize(Dimension(180, 0))) {
                content {
                    ListBox(
                        items = showcaseSections.map { it.title },
                        selectedIndices = setOf(selected),
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
