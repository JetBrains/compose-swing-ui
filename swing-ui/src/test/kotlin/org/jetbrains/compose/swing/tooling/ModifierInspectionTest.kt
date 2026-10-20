package org.jetbrains.compose.swing.tooling

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.mockk.mockk
import org.jetbrains.compose.swing.carriedChainAppearancesOf
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.components.style.LabelStyleScope
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import org.jetbrains.compose.swing.modifier.appearance.highlights
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.composed
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
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.text.TextRange
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.KeyStroke
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities
import javax.swing.TransferHandler
import javax.swing.border.LineBorder
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentListener
import javax.swing.text.DefaultCaret
import javax.swing.text.DefaultHighlighter
import javax.swing.text.DocumentFilter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

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
                modifier = SwingModifier.testTag(CHAIN_LABEL_TAG).name("greeting"),
            )
        }

        onNodeWithTag(CHAIN_LABEL_TAG).assertProperty(
            listOf(
                "testTag" to mapOf("testTag" to CHAIN_LABEL_TAG),
                "name" to mapOf("name" to "greeting"),
            ),
            "the chain should report every element it declares, in declaration order",
        ) { callerChain() }
    }

    @Test
    fun aChainAnswersWithWhatTheCompositionDeclaredLast() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        var declared by mutableStateOf("first")
        setContent {
            Label(text = "hello", modifier = SwingModifier.testTag(CHAIN_LABEL_TAG).name(declared))
        }
        onNodeWithTag(CHAIN_LABEL_TAG).assertProperty(
            mapOf("name" to "first"),
            "the chain starts on what the first pass declared",
        ) { valuesOf("name") }

        declared = "second"
        awaitIdle()

        onNodeWithTag(CHAIN_LABEL_TAG).assertProperty(
            mapOf("name" to "second"),
            "and follows the composition onto what a later pass declares",
        ) { valuesOf("name") }
    }

    @Test
    fun aComponentDeclaredWithNoModifierCarriesOnlyItsStyleSlot() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent { Label(text = "hello") }

        val label = onNodeOfType<JLabel>()
        label.assertTextEquals("hello")
        label.assertProperty(
            listOf("styleable"),
            "a modifier-free label carries only the widget's style slot in its inspected chain",
        ) { declaredChain().map { it.first } }
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

        onNodeWithTag(CHAIN_LABEL_TAG).assertProperty(
            listOf("testTag", "mouseListener"),
            "a listener element names the event source it registers on, not the class backing it",
        ) { callerChain().map { it.first } }
    }

    @Test
    fun aPlacementStandsInTheChainAlongsideWhatItLooksLike() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent {
            Panel(PanelLayout.Border()) {
                Label(
                    text = "hello",
                    modifier =
                        SwingModifier
                            .testTag(PLACED_TAG)
                            .layoutConstraint(BorderLayout.NORTH),
                )
            }
        }

        onNodeWithTag(PLACED_TAG).assertProperty(
            listOf(
                "testTag" to mapOf("testTag" to PLACED_TAG),
                "layoutConstraint" to mapOf("constraint" to BorderLayout.NORTH),
            ),
            "where a component sits is declared by the same chain as what it looks like",
        ) { callerChain() }
    }

    @Test
    fun aMenuItemAnswersForItsChainAsAComponentDoes() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        val popup = composeMenu { MenuItem("Cut", onClick = { }, modifier = SwingModifier.name("cut")) }

        val item = popup.getComponent(0)
        assertEquals(
            listOf("name", "actionListener", "styleable"),
            item.declaredChain().map { it.first },
            "a menu node holds its chain the way a component node does, and what the widget's own " +
                "composable declares, its listener and then its style slot, stands in it after what the caller passed",
        )
        assertEquals(
            mapOf("name" to "cut"),
            item.valuesOf("name"),
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
            Panel {
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
                Table(
                    rows = listOf("a"),
                    columns = { column("c", cellContent = { Label("cell") }) { it } },
                    modifier = SwingModifier.testTag("table"),
                    rowHeight = 20,
                )
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
                    documentListener = mockk<DocumentListener>(relaxed = true),
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
            Panel {
                TextArea(
                    state = rememberDocumentState("hello"),
                    modifier = SwingModifier.testTag("marked").highlights(ranges, painter),
                )
            }
        }

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
            Panel(modifier = SwingModifier.testTag("form").orderedFocusTraversal()) {
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
            Panel {
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
    fun aWidgetsOwnSeamsStandInTheChainItBuilds() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent {
            Panel {
                Table(
                    rows = listOf("a"),
                    columns = { column("c", cellContent = { Label("cell") }) { it } },
                    modifier = SwingModifier.testTag("table"),
                    rowHeight = 20,
                )
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
        val borderKey = "border"
        setContent {
            Panel {
                Label("a", modifier = SwingModifier.testTag("border-one").clientProperty(borderKey, border))
                Label("b", modifier = SwingModifier.testTag("border-two").clientProperty(borderKey, border))
                Label("c", modifier = SwingModifier.testTag("border-other").clientProperty(borderKey, otherBorder))
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

        listOf(
            "border" to "clientProperty",
            "tab" to "tab",
            "cells" to "listCellSizing",
        ).forEach { (tag, element) ->
            val first = declaredBy("$tag-one", element)
            assertEquals(first, declaredBy("$tag-two", element), "$element retains equal arguments")
            assertNotEquals(first, declaredBy("$tag-other", element), "$element distinguishes changed arguments")
        }
    }

    @Test
    fun anElementNamingNothingOfItsOwnIsReportedByItsClassName() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent { Label("hello", modifier = SwingModifier.testTag(CHAIN_LABEL_TAG) then UnnamedElement()) }

        onNodeWithTag(CHAIN_LABEL_TAG).assertProperty(
            listOf("testTag", "UnnamedElement"),
            "an element that names no property of its own is reported by the class it is declared as",
        ) { callerChain().map { it.first } }
    }

    @Test
    fun aComposedEntryIsReportedAsTheEntriesItsFactoryReturned() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent { Label("hello", modifier = SwingModifier.testTag(CHAIN_LABEL_TAG).composed { name("x") }) }

        val node =
            assertNotNull(
                onNodeWithTag(CHAIN_LABEL_TAG).fetch().findDeclaringGroup()?.node as? SwingComponentNode<*>,
                "no composition declared the label",
            )
        val chain =
            node.modifier.foldIn(listOf<Pair<String, Map<String, Any?>>>()) { acc, element ->
                val inspectable = assertIs<SwingModifier.InspectableElement>(element, "a chain entry: $element")
                acc + (inspectable.name to inspectable.declaredValues)
            }
        assertEquals("styleable", chain.last().first, "the widget's style slot stands last")
        assertEquals(
            listOf("testTag" to mapOf("testTag" to CHAIN_LABEL_TAG), "name" to mapOf("name" to "x")),
            chain.dropLast(1),
            "the chain should hold the factory's entries in place of the composed one",
        )
    }

    @Test
    fun aWidgetsStyleSlotStandsLastAndReportsTheStyleItWasGiven() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        val style = SwingStyle<LabelStyleScope> { background(Color.BLUE) }
        setContent { Label("hello", modifier = SwingModifier.testTag(CHAIN_LABEL_TAG), style = style) }

        val chain = onNodeWithTag(CHAIN_LABEL_TAG).fetch().declaredChain()
        assertEquals(
            listOf("testTag", "styleable"),
            chain.map { it.first },
            "the style slot follows the caller's chain",
        )
        assertSame(style, chain.last().second["style"], "the slot reports the style the caller passed")
    }

    @Test
    fun aProvidedStyleAddsNoElementToTheChain() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent {
            Panel(style = { provideNestedStyle(Labels) { background(Color.RED) } }) {
                Label("hello", modifier = SwingModifier.testTag(CHAIN_LABEL_TAG).name("greeting"))
            }
        }

        val label = onNodeWithTag(CHAIN_LABEL_TAG)
        label.assertProperty(Color.RED, "the provided style reaches the label") { background }
        label.assertProperty(
            listOf("testTag", "name", "styleable"),
            "the chain is the caller's modifier and the style slot; a provided style adds nothing to it",
        ) { declaredChain().map { it.first } }
    }

    @Test
    fun aStyledComponentReportsItsStyleAsTheLastElementOfItsChain() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        val style = SwingStyle<LabelStyleScope> { background(Color.RED) }
        setContent { Label("hello", modifier = SwingModifier.testTag(CHAIN_LABEL_TAG), style = style) }

        val chain = onNodeWithTag(CHAIN_LABEL_TAG).fetch().declaredChain()
        assertEquals(
            listOf("testTag", "styleable"),
            chain.map { it.first },
            "the style stands after the modifier as one element; the properties it sets are not elements",
        )
        assertSame(style, chain.last().second["style"], "the style element reports the style the caller passed")
        onNodeWithTag(CHAIN_LABEL_TAG).assertProperty(Color.RED) { background }
    }
}

/** What the element named [element] declares in the chain the component tagged [tag] carries. */
private fun ComposeSwingTest.declaredBy(
    tag: String,
    element: String,
): Map<String, Any?> = onNodeWithTag(tag).fetch().valuesOf(element)

/** The chain this component carries, as the name and values each element reports. */
private fun Component.declaredChain(): List<Pair<String, Map<String, Any?>>> {
    val node = assertNotNull(findDeclaringGroup()?.node as? SwingComponentNode<*>, "no composition declared $this")
    return node.modifier.elements().map { it.name to it.declaredValues }
}

/** The chain this component carries ahead of the widget's own style slot, which stands last. */
private fun Component.callerChain(): List<Pair<String, Map<String, Any?>>> {
    val chain = declaredChain()
    assertEquals("styleable", chain.lastOrNull()?.first, "the widget's style slot stands last in its chain")
    return chain.dropLast(1)
}

/** What the element named [name] declares in this component's chain. */
private fun Component.valuesOf(name: String): Map<String, Any?> =
    assertNotNull(declaredChain().firstOrNull { it.first == name }, "no $name element in the chain").second

/** The modifier's entries that describe themselves, in declaration order. */
private fun SwingModifier.elements(): List<SwingModifier.InspectableElement> =
    foldIn(mutableListOf<SwingModifier.InspectableElement>()) { acc, element ->
        acc.apply { if (element is SwingModifier.InspectableElement) add(element) }
    }

/** A chain element declaring nothing, standing for one that names no property of its own. */
private class UnnamedElement : SwingModifier.NodeElement<Component, SwingModifier.ComponentNode<Component>>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): SwingModifier.ComponentNode<Component> = SwingModifier.ComponentNode()

    override fun update(node: SwingModifier.ComponentNode<Component>): Unit = Unit

    override fun equals(other: Any?): Boolean = other is UnnamedElement

    override fun hashCode(): Int = javaClass.hashCode()
}
