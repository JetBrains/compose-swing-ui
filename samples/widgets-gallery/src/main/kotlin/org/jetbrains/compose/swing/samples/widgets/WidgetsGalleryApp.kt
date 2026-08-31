package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.menu.CheckBoxMenuItem
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.menu.MenuSeparator
import org.jetbrains.compose.swing.components.menu.RadioButtonMenuGroup
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.samples.widgets.text.setEditorText
import org.jetbrains.compose.swing.window.LocalWindow
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.JMenuItem
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel

// The menu bar is its own composition, mounted on the frame's menu bar, and reads that frame as its
// LocalWindow.
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
            onClick = { setEditorText("") },
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_N, shortcut),
        )
        MenuItem("Open", onClick = {
            // Loads a canned document into the shared editor. The Editor section - a separate
            // composition - renders it through that shared document.
            setEditorText(SAMPLE_DOCUMENT)
            owner?.toFront()
        }, accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcut))
        MenuSeparator()
        MenuItem("Exit", onClick = onExit)
    }
    Menu("Edit") {
        MenuItem("Cut", onClick = { }, accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_X, shortcut))
        MenuItem("Copy", onClick = { }, accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcut))
        MenuItem("Paste", onClick = { }, accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_V, shortcut))
    }
    Menu("View") {
        CheckBoxMenuItem(
            "Wrap text",
            checked = wrapText,
            onCheckedChange = { wrapText = it },
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_W, shortcut),
        )
        MenuSeparator()
        RadioButtonMenuGroup(selectedIndex = density, onSelectionChange = { density = it }) {
            option("Comfortable", accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_1, shortcut))
            option("Compact", accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_2, shortcut))
        }
    }
    Menu("Help") {
        // MenuNode is the primitive every menu composable above is built on. This item is built
        // straight from a plain JMenuItem, with no wrapper between it and the item MenuNode installs.
        val callback = rememberUpdatedState<() -> Unit> { pings++ }
        val listener = remember { ActionListener { callback.value() } }
        MenuNode(
            factory = { JMenuItem() },
            modifier = SwingModifier.actionListener(listener),
            update = {
                set("Ping (raw MenuNode) - $pings") { this.text = it }
            },
        )
    }
}

// The sidebar + detail shell, navigated by an androidx.navigation3 back stack.
//
// The back stack is the state and the sidebar is a view of it: the selected row is derived from the top
// key, and picking a row pushes. Only the top entry is composed, which is what lets the same section be
// visited twice - "Components -> Table -> Components" puts one contentKey on the stack twice, and a
// display that composed every entry would fail on it.
@Composable
internal fun ShowcaseShell() {
    val backStack = remember { mutableStateListOf(SectionKey(showcaseSections.first().title)) }
    val entries =
        rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider =
                entryProvider {
                    entry<SectionKey> { key -> showcaseSections.first { it.title == key.title }.body() }
                },
        )
    val current = backStack.last().title
    BorderPanel(
        modifier = SwingModifier.emptyBorder(12),
    ) {
        ScrollPane(modifier = SwingModifier.west().preferredSize(Dimension(180, 0))) {
            ListBox(
                items = showcaseSections.map { it.title },
                selectedIndices = setOf(showcaseSections.indexOfFirst { it.title == current }),
                // Driving the selection from the back stack does not re-enter this callback, so a pop
                // moves the highlight without pushing the row it lands on.
                onSelectionChange = { indices ->
                    indices.firstOrNull()?.let { backStack += SectionKey(showcaseSections[it].title) }
                },
                selectionMode = ListSelectionModel.SINGLE_SELECTION,
                visibleRowCount = showcaseSections.size,
                modifier = SwingModifier.viewport().accessibleName("Sections"),
            )
        }
        // The entry names no region, so BorderLayout gives it the center by default.
        entries.last().Content()
        FlowPanel(modifier = SwingModifier.south(), alignment = FlowLayout.LEADING) {
            Button(
                text = "Back",
                onClick = { backStack.removeLastOrNull() },
                modifier = SwingModifier.enabled(backStack.size > 1),
            )
            Label("Section: $current")
        }
    }
}

// The back stack's key type. A section is identified by its title, so the key is comparable and its
// toString - which is what nav3 derives contentKey from - is stable across visits.
internal data class SectionKey(
    val title: String,
)

// The canned text File > Open loads into the shared editor document - a stand-in for a real file's
// contents, so the sample stays self-contained and needs no file on disk.
private const val SAMPLE_DOCUMENT =
    "Opened from the File menu.\n\n" +
        "This text was loaded into the editor's shared document by File > Open, which lives in a " +
        "separate composition from the Editor section that renders it."
