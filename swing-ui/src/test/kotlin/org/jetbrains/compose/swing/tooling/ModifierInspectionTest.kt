package org.jetbrains.compose.swing.tooling

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.carriedChainAppearancesOf
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Alignment
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.components.layout.HorizontalAxisAlignment
import org.jetbrains.compose.swing.components.layout.ScrollBehavior
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.components.layout.WeightPlacement
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import org.jetbrains.compose.swing.modifier.appearance.highlights
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.datatransfer.clipboard
import org.jetbrains.compose.swing.modifier.datatransfer.draggable
import org.jetbrains.compose.swing.modifier.datatransfer.dropTarget
import org.jetbrains.compose.swing.modifier.datatransfer.onExportDone
import org.jetbrains.compose.swing.modifier.interaction.caret
import org.jetbrains.compose.swing.modifier.interaction.defaultButton
import org.jetbrains.compose.swing.modifier.interaction.documentFilter
import org.jetbrains.compose.swing.modifier.interaction.initialFocus
import org.jetbrains.compose.swing.modifier.interaction.inputVerifier
import org.jetbrains.compose.swing.modifier.interaction.orderedFocusTraversal
import org.jetbrains.compose.swing.modifier.keyboard.onKeyStroke
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.text.TextRange
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities
import javax.swing.TransferHandler
import javax.swing.border.LineBorder
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.DefaultCaret
import javax.swing.text.DefaultHighlighter
import javax.swing.text.DocumentFilter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

private const val CHAIN_LABEL_TAG = "chain-label"
private const val PLACED_TAG = "chain-placed"
private const val LOOK_AND_FEEL_KEY = "org.jetbrains.compose.swing.test.styling"

/**
 * Behavioral tests for the chain a component carries: what [SwingComponentNode.modifier] answers, and
 * what each element of it reports itself as.
 *
 * The chain is reached through the declaring group, so these turn inspection on the way a tool does and
 * leave it off again - it is process-wide state that would otherwise reach every later test.
 */
class ModifierInspectionTest {
    @AfterTest
    fun turnInspectionOff() {
        SwingUtilities.invokeAndWait { isDebugInspectorInfoEnabled = false }
    }

    @Test
    fun eachElementOfAChainReportsWhatItIsCalledAndWhatItDeclares() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent {
            Label(
                text = "hello",
                modifier = SwingModifier.testTag(CHAIN_LABEL_TAG).background(Color.RED),
            )
        }

        assertEquals(
            listOf(
                "testTag" to mapOf("testTag" to CHAIN_LABEL_TAG),
                "background" to mapOf("background" to Color.RED),
            ),
            onNodeWithTag(CHAIN_LABEL_TAG).fetch().declaredChain(),
            "the chain should report every element it declares, in declaration order",
        )
    }

    @Test
    fun aChainAnswersWithWhatTheCompositionDeclaredLast() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        var color by mutableStateOf(Color.RED)
        setContent {
            Label(text = "hello", modifier = SwingModifier.testTag(CHAIN_LABEL_TAG).background(color))
        }
        assertEquals(
            mapOf("background" to Color.RED),
            onNodeWithTag(CHAIN_LABEL_TAG).fetch().valuesOf("background"),
            "the chain starts on what the first pass declared",
        )

        color = Color.BLUE
        awaitIdle()

        assertEquals(
            mapOf("background" to Color.BLUE),
            onNodeWithTag(CHAIN_LABEL_TAG).fetch().valuesOf("background"),
            "and follows the composition onto what a later pass declares",
        )
    }

    @Test
    fun aComponentDeclaredWithNoModifierCarriesTheEmptyChain() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent { Label(text = "hello", modifier = SwingModifier.testTag(CHAIN_LABEL_TAG)) }

        val node =
            assertNotNull(onNodeWithTag(CHAIN_LABEL_TAG).fetch().findDeclaringGroup()?.node as? SwingComponentNode)
        assertEquals(
            listOf("testTag"),
            node.modifier.elements().map { it.name },
            "a component whose chain declares one element carries that one and nothing else",
        )
    }

    @Test
    fun aListenerIsNamedAfterTheEventSourceItRegistersOn() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent {
            Label(
                text = "hello",
                modifier = SwingModifier.testTag(CHAIN_LABEL_TAG).mouseListener { },
            )
        }

        assertEquals(
            listOf("testTag", "mouseListener"),
            onNodeWithTag(CHAIN_LABEL_TAG).fetch().declaredChain().map { it.first },
            "a listener element names the event source it registers on, not the class backing it",
        )
    }

    @Test
    fun aPlacementStandsInTheChainAlongsideWhatItLooksLike() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent {
            BorderPanel {
                Label(
                    text = "hello",
                    modifier =
                        SwingModifier
                            .testTag(PLACED_TAG)
                            .layoutConstraint(BorderLayout.NORTH),
                )
            }
        }

        assertEquals(
            listOf(
                "testTag" to mapOf("testTag" to PLACED_TAG),
                "layoutConstraint" to mapOf("constraint" to BorderLayout.NORTH),
            ),
            onNodeWithTag(PLACED_TAG).fetch().declaredChain(),
            "where a component sits is declared by the same chain as what it looks like",
        )
    }

    @Test
    fun aMenuItemAnswersForItsChainAsAComponentDoes() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        val popup = composeMenu { MenuItem("Cut", onClick = { }, modifier = SwingModifier.background(Color.RED)) }

        val item = popup.getComponent(0)
        assertEquals(
            listOf("background", "actionListener"),
            item.declaredChain().map { it.first },
            "a menu node holds its chain the way a component node does, and what the widget's own " +
                "composable declares stands in it after what the caller passed",
        )
        assertEquals(
            mapOf("background" to Color.RED),
            item.valuesOf("background"),
            "and reports what the caller declared",
        )
    }

    @Test
    fun everyWidgetCarriesTheChainItWasGivenOnce() = runComposeSwingTest {
        // A widget assembles its own declarations - a mirror, an editor, a row height - onto the chain it
        // was handed, through builders private to its own file. Each widget here is called through the
        // overload that reaches one of those, which is where they are held to carrying the caller's chain
        // once, the way every builder a test can call is held to it directly.
        isDebugInspectorInfoEnabled = true
        setContent {
            Column {
                ComboBox(
                    items = listOf("a"),
                    selectedItem = "a",
                    onSelectionChange = {},
                    modifier = SwingModifier.testTag("combo"),
                )
                Slider(value = 0, changeListener = ChangeListener { }, modifier = SwingModifier.testTag("slider"))
                Spinner(
                    model = SpinnerNumberModel(),
                    changeListener = ChangeListener { },
                    modifier = SwingModifier.testTag("spinner"),
                    editor = { Label("value") },
                )
                Table(rows = listOf("a"), modifier = SwingModifier.testTag("table"), rowHeight = 20) {
                    column("c", cellContent = { Label("cell") }) { it }
                }
                Tree(root = "root", children = { emptyList() }, modifier = SwingModifier.testTag("tree"))
                FormattedTextField(
                    value = "v",
                    onValueChange = {},
                    modifier = SwingModifier.testTag("formatted"),
                )
                FormattedTextField(
                    value = "v",
                    valuePropertyChangeListener = PropertyChangeListener { },
                    modifier = SwingModifier.testTag("formattedRaw"),
                )
                PasswordField(
                    value = charArrayOf('a'),
                    documentListener = NoDocumentChange,
                    modifier = SwingModifier.testTag("password"),
                )
                TextArea(state = rememberDocumentState("v"), modifier = SwingModifier.testTag("area"), tabSize = 4)
            }
        }

        val tags =
            listOf("combo", "slider", "spinner", "table", "tree", "formatted", "formattedRaw", "password", "area")
        assertEquals(
            tags.associateWith { 1 },
            tags.associateWith { onNodeWithTag(it).fetch().carriedChainAppearancesOf(it) },
            "each widget should carry the chain it was given once",
        )
    }

    @Test
    fun anAppearanceElementNamesThePropertyItWritesAndDeclaresItsArgument() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        val painter = DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW)
        val ranges = listOf(TextRange(0, 2))
        setContent {
            Column {
                Label("hello", modifier = SwingModifier.testTag("hinted").toolTip("What this does"))
                TextArea(
                    state = rememberDocumentState("hello"),
                    modifier = SwingModifier.testTag("marked").highlights(ranges, painter),
                )
            }
        }

        assertEquals(
            mapOf("text" to "What this does"),
            declaredBy("hinted", "toolTip"),
            "a tooltip declares the text it was given",
        )
        assertEquals(
            mapOf("ranges" to ranges, "painter" to painter),
            declaredBy("marked", "highlights"),
            "a highlight declares both the spans it marks and the painter that draws them",
        )
    }

    @Test
    fun aTextEditingElementNamesWhatItInstallsOnTheField() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        val caret = DefaultCaret()
        val filter = DocumentFilter()
        setContent {
            TextField(
                value = "text",
                onValueChange = {},
                modifier =
                    SwingModifier
                        .testTag("field")
                        .caret(caret)
                        .documentFilter(filter)
                        .inputVerifier { true },
            )
        }

        assertEquals(
            mapOf("caret" to caret),
            declaredBy("field", "caret"),
            "a caret declares the caret instance it installs",
        )
        assertEquals(
            mapOf("documentFilter" to filter),
            declaredBy("field", "documentFilter"),
            "a document filter declares the filter it installs",
        )
        assertEquals(
            emptyMap(),
            declaredBy("field", "inputVerifier"),
            "an input verifier is the behavior itself and carries no values",
        )
    }

    @Test
    fun aFocusOrKeyboardElementNamesTheBehaviorItInstalls() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent {
            Column(modifier = SwingModifier.testTag("form").orderedFocusTraversal()) {
                Button(
                    text = "OK",
                    onClick = {},
                    modifier =
                        SwingModifier
                            .testTag("ok")
                            .defaultButton()
                            .initialFocus()
                            .onKeyStroke("ctrl S") {}
                            .clientProperty(LOOK_AND_FEEL_KEY, "flat"),
                )
            }
        }

        assertEquals(
            mapOf("defaultButton" to true),
            declaredBy("ok", "defaultButton"),
            "a default button declares whether it claims the association",
        )
        assertEquals(
            mapOf("keyStroke" to KeyStroke.getKeyStroke("ctrl S"), "condition" to JComponent.WHEN_FOCUSED),
            declaredBy("ok", "onKeyStroke"),
            "a key-stroke binding declares the stroke and the focus scope it is live in",
        )
        assertEquals(
            mapOf("key" to LOOK_AND_FEEL_KEY, "clientProperty" to "flat"),
            declaredBy("ok", "clientProperty"),
            "a client property declares the key it writes under beside the value written there",
        )
        assertEquals(
            listOf(emptyMap(), emptyMap()),
            listOf(declaredBy("ok", "initialFocus"), declaredBy("form", "orderedFocusTraversal")),
            "an element that only decides where focus goes carries no values",
        )
    }

    @Test
    fun aDataTransferElementNamesTheDirectionItDeclares() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent {
            Column {
                Label(
                    "drag",
                    modifier =
                        SwingModifier
                            .testTag("source")
                            .draggable(TransferHandler.COPY) { null }
                            .onExportDone { _, _ -> },
                )
                Label(
                    "drop",
                    modifier =
                        SwingModifier
                            .testTag("target")
                            .dropTarget(TransferHandler.COPY_OR_MOVE, onDrop = { false }),
                )
                Label(
                    "paste",
                    modifier =
                        SwingModifier
                            .testTag("pasted")
                            .clipboard(transferable = { null }, onPaste = { false }, bindKeys = false),
                )
            }
        }

        assertEquals(
            mapOf("exportedActions" to TransferHandler.COPY),
            declaredBy("source", "draggable"),
            "a drag source declares the operations it offers",
        )
        assertEquals(
            emptyMap(),
            declaredBy("source", "onExportDone"),
            "an export-completion callback stands in the chain carrying no values",
        )
        assertEquals(
            mapOf("acceptedActions" to TransferHandler.COPY_OR_MOVE),
            declaredBy("target", "dropTarget"),
            "a drop target declares the operations it accepts",
        )
        assertEquals(
            mapOf("bindKeys" to false),
            declaredBy("pasted", "clipboard"),
            "a clipboard declares whether it binds the platform keystrokes",
        )
    }

    @Test
    fun aChildDeclaresItsPlacementToTheContainerItSitsIn() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent {
            Column {
                Label("a", modifier = SwingModifier.testTag("weighted").weight(2f))
                Label("b", modifier = SwingModifier.testTag("aligned").align(Alignment.CenterHorizontally))
                Label("c", modifier = SwingModifier.testTag("filled").fillWidth())
                ScrollPane {
                    Label("d", modifier = SwingModifier.testTag("scrolled").viewport(unitIncrement = 8))
                }
            }
        }

        assertEquals(
            mapOf("weight" to WeightPlacement(2f, fill = true)),
            declaredBy("weighted", "weight"),
            "a weight declares the share of the leftover space the child claims",
        )
        assertEquals(
            mapOf("alignment" to HorizontalAxisAlignment(Alignment.CenterHorizontally)),
            declaredBy("aligned", "align"),
            "an alignment declares where across the axis the child sits",
        )
        assertEquals(
            emptyMap(),
            declaredBy("filled", "fill"),
            "a cross-axis fill is the whole declaration and carries no values",
        )
        assertEquals(
            mapOf("region" to "SwingModifier.viewport()"),
            declaredBy("scrolled", "slot"),
            "a slot declares the region of its host by the call that fills it",
        )
        val scrolling = declaredBy("scrolled", "scrollBehavior")
        assertEquals(
            setOf("region", "behavior"),
            scrolling.keys,
            "scroll behavior is declared to one pane's viewport, so it names both",
        )
        assertEquals(
            ScrollBehavior(
                unitIncrement = 8,
                blockIncrement = null,
                tracksViewportWidth = null,
                tracksViewportHeight = null,
            ),
            scrolling["behavior"],
            "the content declares the answers it gave and leaves the rest to the pane",
        )
    }

    @Test
    fun aWidgetsOwnSeamsStandInTheChainItBuilds() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent {
            Column {
                Table(rows = listOf("a"), modifier = SwingModifier.testTag("table"), rowHeight = 20) {
                    column("c", cellContent = { Label("cell") }) { it }
                }
                Spinner(
                    model = SpinnerNumberModel(),
                    changeListener = ChangeListener { },
                    modifier = SwingModifier.testTag("spinner"),
                    editor = { Label("value") },
                )
            }
        }

        assertEquals(
            listOf(emptyMap(), emptyMap(), emptyMap()),
            listOf(
                declaredBy("table", "columnCells"),
                declaredBy("table", "userSelectionListener"),
                declaredBy("spinner", "spinnerEditor"),
            ),
            "a widget's own seams are named in the chain it builds and carry nothing a tool can show",
        )
    }

    @Test
    fun aDeclarationCarriedByALibraryTypeStillTellsOneArgumentFromAnother() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        val border = LineBorder(Color.RED)
        val otherBorder = LineBorder(Color.BLUE)
        setContent {
            Column {
                Label("a", modifier = SwingModifier.testTag("border-one").border(border))
                Label("b", modifier = SwingModifier.testTag("border-two").border(border))
                Label("c", modifier = SwingModifier.testTag("border-other").border(otherBorder))
                TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                    Label("p", modifier = SwingModifier.testTag("tab-one").tab("One"))
                    Label("q", modifier = SwingModifier.testTag("tab-two").tab("One"))
                    Label("r", modifier = SwingModifier.testTag("tab-other").tab("Two"))
                }
                ListBox(items = listOf("a"), modifier = SwingModifier.testTag("cells-one"), fixedCellHeight = 10)
                ListBox(items = listOf("a"), modifier = SwingModifier.testTag("cells-two"), fixedCellHeight = 10)
                ListBox(items = listOf("a"), modifier = SwingModifier.testTag("cells-other"), fixedCellHeight = 20)
            }
        }

        fun declarations(suffix: String) = listOf(
            declaredBy("border-$suffix", "border"),
            declaredBy("tab-$suffix", "tab"),
            declaredBy("cells-$suffix", "listCellSizing"),
        )

        assertEquals(
            declarations("one"),
            declarations("two"),
            "two chains built from one argument declare the same thing",
        )
        assertNotEquals(
            declarations("one"),
            declarations("other"),
            "and a chain built from another argument declares something else",
        )
    }

    @Test
    fun anElementNamingNothingOfItsOwnIsReportedByItsClassName() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent { Label("hello", modifier = SwingModifier.testTag(CHAIN_LABEL_TAG) then UnnamedElement()) }

        assertEquals(
            listOf("testTag", "UnnamedElement"),
            onNodeWithTag(CHAIN_LABEL_TAG).fetch().declaredChain().map { it.first },
            "an element that names no property of its own is reported by the class it is declared as",
        )
    }
}

/** What the element named [element] declares in the chain the component tagged [tag] carries. */
private fun ComposeSwingTest.declaredBy(
    tag: String,
    element: String,
): Map<String, Any?> = onNodeWithTag(tag).fetch().valuesOf(element)

/** The chain this component carries, as the name and values each element reports. */
private fun Component.declaredChain(): List<Pair<String, Map<String, Any?>>> {
    val node = assertNotNull(findDeclaringGroup()?.node as? SwingComponentNode, "no composition declared $this")
    return node.modifier.elements().map { it.name to it.declaredValues }
}

/** What the element named [name] declares in this component's chain. */
private fun Component.valuesOf(name: String): Map<String, Any?> =
    assertNotNull(declaredChain().firstOrNull { it.first == name }, "no $name element in the chain").second

/** The chain's elements, in declaration order. */
private fun SwingModifier.elements(): List<SwingModifier.NodeElement<*, *>> =
    foldIn(mutableListOf<SwingModifier.NodeElement<*, *>>()) { acc, element -> acc.apply { add(element) } }

/** A chain element declaring nothing, standing for one that names no property of its own. */
private class UnnamedElement : SwingModifier.NodeElement<Component, SwingModifier.Node<Component>>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): SwingModifier.Node<Component> = SwingModifier.Node()

    override fun update(node: SwingModifier.Node<Component>): Unit = Unit

    override fun equals(other: Any?): Boolean = other is UnnamedElement

    override fun hashCode(): Int = javaClass.hashCode()
}

/** A [DocumentListener] that answers nothing, for a declaration whose only subject is the chain. */
private val NoDocumentChange =
    object : DocumentListener {
        override fun insertUpdate(event: DocumentEvent) = Unit

        override fun removeUpdate(event: DocumentEvent) = Unit

        override fun changedUpdate(event: DocumentEvent) = Unit
    }
