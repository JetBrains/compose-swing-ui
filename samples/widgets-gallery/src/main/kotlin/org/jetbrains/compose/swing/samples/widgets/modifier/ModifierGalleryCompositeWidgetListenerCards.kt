package org.jetbrains.compose.swing.samples.widgets.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.desktop.DesktopPane
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.listener.hyperlinkListener
import org.jetbrains.compose.swing.modifier.listener.internalFrameListener
import org.jetbrains.compose.swing.modifier.listener.listSelectionListener
import org.jetbrains.compose.swing.modifier.listener.treeExpansionListener
import org.jetbrains.compose.swing.modifier.listener.treeSelectionListener
import org.jetbrains.compose.swing.modifier.listener.treeWillExpandListener
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.event.HyperlinkListener
import javax.swing.event.InternalFrameAdapter
import javax.swing.event.InternalFrameEvent
import javax.swing.event.ListSelectionListener
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionListener
import javax.swing.event.TreeWillExpandListener
import javax.swing.tree.ExpandVetoException

// Raw java.awt/javax.swing listener cards for the gallery's composite widgets (ListBox, Tree,
// EditorPane, DesktopPane), each additive on top of the wrapper's own typed callbacks.

@Composable
internal fun ColumnScope.ListSelectionListenerCard() {
    ExampleCard("listSelectionListener (raw ListSelectionListener, additive on ListBox)") {
        val fruits = listOf("Apple", "Banana", "Cherry", "Date")
        var selected by remember { mutableStateOf(setOf(0)) }
        var events by remember { mutableIntStateOf(0) }
        val listener = remember { ListSelectionListener { event -> if (!event.valueIsAdjusting) events++ } }
        ListBox(
            items = fruits,
            modifier = SwingModifier.listSelectionListener(listener).preferredSize(Dimension(160, 90)),
            selectedIndices = selected,
            onSelectionChange = { selected = it },
            visibleRowCount = 4,
        )
        Label("Selection events: $events")
    }
}

@Composable
internal fun ColumnScope.TreeListenersCard() {
    ExampleCard("treeSelectionListener / treeExpansionListener / treeWillExpandListener") {
        var selectionEvents by remember { mutableIntStateOf(0) }
        var expansionEvents by remember { mutableIntStateOf(0) }
        var vetoCollapse by remember { mutableStateOf(false) }
        val selectionListener = remember { TreeSelectionListener { selectionEvents++ } }
        val expansionListener =
            remember {
                object : TreeExpansionListener {
                    override fun treeExpanded(event: TreeExpansionEvent) {
                        expansionEvents++
                    }

                    override fun treeCollapsed(event: TreeExpansionEvent) {
                        expansionEvents++
                    }
                }
            }
        val willExpandListener =
            remember(vetoCollapse) {
                object : TreeWillExpandListener {
                    override fun treeWillExpand(event: TreeExpansionEvent) = Unit

                    override fun treeWillCollapse(event: TreeExpansionEvent) {
                        if (vetoCollapse) throw ExpandVetoException(event)
                    }
                }
            }
        val root = remember { FolderNode("root", listOf(FolderNode("A", listOf(FolderNode("A1"))), FolderNode("B"))) }
        CheckBox(text = "Veto collapsing nodes", checked = vetoCollapse, onCheckedChange = { vetoCollapse = it })
        Tree(
            root = root,
            children = { it.children },
            modifier =
                SwingModifier
                    .treeSelectionListener(selectionListener)
                    .treeExpansionListener(expansionListener)
                    .treeWillExpandListener(willExpandListener)
                    .preferredSize(Dimension(200, 120)),
            label = { it.name },
            onSelectionChange = {},
            onExpansionChange = {},
        )
        Label("Selection events: $selectionEvents, expansion events: $expansionEvents")
    }
}

/** A small folder tree, used by [TreeListenersCard] to exercise the tree's raw listener modifiers. */
private data class FolderNode(
    val name: String,
    val children: List<FolderNode> = emptyList(),
)

@Composable
internal fun ColumnScope.HyperlinkListenerCard() {
    ExampleCard("hyperlinkListener (raw HyperlinkListener, additive on EditorPane)") {
        var lastEvent by remember { mutableStateOf("none") }
        var activated by remember { mutableStateOf("none") }
        val listener =
            remember { HyperlinkListener { event -> lastEvent = "${event.eventType}: ${event.description}" } }
        EditorPane(
            markup = "<a href=\"https://example.com\">Hover or click this link</a>",
            onLinkActivate = { activated = it },
            modifier = SwingModifier.hyperlinkListener(listener).preferredSize(Dimension(260, 40)),
            contentType = "text/html",
        )
        Label("Last hyperlink event: $lastEvent")
        Label("Activated: $activated")
    }
}

@Composable
internal fun ColumnScope.InternalFrameListenerCard() {
    ExampleCard("internalFrameListener (raw InternalFrameListener, additive on DesktopPane)") {
        var events by remember { mutableIntStateOf(0) }
        val listener =
            remember {
                object : InternalFrameAdapter() {
                    override fun internalFrameActivated(event: InternalFrameEvent) {
                        events++
                    }

                    override fun internalFrameDeactivated(event: InternalFrameEvent) {
                        events++
                    }
                }
            }
        WrappedCaption("Click inside then outside the frame to activate and deactivate it.")
        DesktopPane(modifier = SwingModifier.preferredSize(Dimension(260, 140))) {
            InternalFrame(
                title = "Notes",
                bounds = Rectangle(10, 10, 200, 100),
                onClose = { },
                modifier = SwingModifier.internalFrameListener(listener),
            ) {
                Label("Click me")
            }
        }
        Label("Activation events: $events")
    }
}
