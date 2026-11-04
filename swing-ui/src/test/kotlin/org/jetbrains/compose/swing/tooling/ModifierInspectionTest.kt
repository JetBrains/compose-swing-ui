package org.jetbrains.compose.swing.tooling

import androidx.compose.runtime.Composable
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
import org.jetbrains.compose.swing.core.SwingCompositionDiagnostics
import org.jetbrains.compose.swing.core.SwingRecomposer
import org.jetbrains.compose.swing.core.awaitUntil
import org.jetbrains.compose.swing.defaults.DefaultBackground
import org.jetbrains.compose.swing.defaults.ProvideComponentDefaults
import org.jetbrains.compose.swing.defaults.provides
import org.jetbrains.compose.swing.modifier.ModifierInfo
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import org.jetbrains.compose.swing.modifier.appearance.highlights
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.appearance.toolTip
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
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
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
import javax.swing.JPanel
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
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
    fun aComponentDeclaredWithNoModifierUnderNoPublisherCarriesNoStyleSlot() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent { Label(text = "hello") }

        val label = onNodeOfType<JLabel>()
        label.assertTextEquals("hello")
        label.assertProperty(
            emptyList<String>(),
            "a modifier-free label under no publisher carries no style slot in its inspected chain",
        ) { declaredChain().map { it.first } }
        label.assertProperty(
            false,
            "and installs no style node, whatever the inspected chain filters out",
        ) { holdsStyleNode() }
    }

    @Test
    fun aProviderAddedAndRemovedInstallsAndUninstallsTheStyleNodeOfEachLabelBelowIt() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        var providing by mutableStateOf(false)
        setContent {
            Panel(style = { if (providing) provideNestedStyle(Labels) { background(Color.RED) } }) {
                Label("first")
                Label("second")
            }
        }
        val texts = listOf("first", "second")
        texts.forEach { text ->
            onNodeWithText(text).assertProperty(false, "no provider installs no style node") { holdsStyleNode() }
        }

        providing = true
        awaitIdle()
        texts.forEach { text ->
            onNodeWithText(text).assertProperty(true, "a provider installs one") { holdsStyleNode() }
        }

        providing = false
        awaitIdle()
        texts.forEach { text ->
            onNodeWithText(text).assertProperty(false, "removing the provider uninstalls it") { holdsStyleNode() }
        }

        providing = true
        awaitIdle()
        texts.forEach { text ->
            onNodeWithText(text).assertProperty(true, "and adding it again installs it again") { holdsStyleNode() }
        }
    }

    @Test
    fun unstyledComponentsOfOneKindShareTheirStyleSlot() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent {
            Panel(style = { provideNestedStyle(Labels) { background(Color.RED) } }) {
                Label(text = "first")
                Label(text = "second")
            }
        }

        val (first, second) =
            listOf("first", "second").map { text ->
                onNodeWithText(text)
                    .fetch()
                    .declaringNode()
                    .modifier
                    .elements()
                    .last()
            }
        assertSame(first, second, "labels declaring no style stand under one style declaration")
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
            listOf("name", "actionListener"),
            item.declaredChain().map { it.first },
            "a menu node holds its chain the way a component node does, and what the widget's own " +
                "composable declares, its listener, stands in it after what the caller passed",
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

    // Tests the deprecated modifier forwarding and its declared elements.
    @Suppress("DEPRECATION")
    @Test
    fun anAppearanceElementNamesThePropertyItWritesAndDeclaresItsArgument() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        val painter = DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW)
        val ranges = listOf(TextRange(0, 2))
        setContent {
            Panel {
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

    // Tests the deprecated modifier forwarding and its declared elements.
    @Suppress("DEPRECATION")
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
        assertEquals(
            listOf("testTag" to mapOf("testTag" to CHAIN_LABEL_TAG), "name" to mapOf("name" to "x")),
            node.modifier.foldIn(listOf<Pair<String, Map<String, Any?>>>()) { acc, element ->
                val inspectable = assertIs<SwingModifier.InspectableElement>(element, "a chain entry: $element")
                acc + (inspectable.name to inspectable.declaredValues)
            },
            "the chain should hold the factory's entries in place of the composed one",
        )
    }

    // Tests the deprecated defaults forwarding and its effective elements.
    @Suppress("DEPRECATION")
    @Test
    fun inheritedDefaultsAreExposedAsEffectivePropertyElementsWithoutSyntheticKeys() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent {
            ProvideComponentDefaults(DefaultBackground provides Color.RED) {
                Label("hello", modifier = SwingModifier.testTag(CHAIN_LABEL_TAG).toolTip("tip"))
            }
        }

        val chain = onNodeWithTag(CHAIN_LABEL_TAG).fetch().declaredChain()
        assertEquals(
            listOf("background", "testTag", "toolTip"),
            chain.map { it.first },
            "inherited defaults should precede explicit modifiers, without synthetic key elements",
        )
        assertEquals(
            mapOf("background" to Color.RED),
            chain.first { it.first == "background" }.second,
            "inherited element should report its declared value",
        )
    }

    @Test
    fun aProvidedStyleAddsNoElementToTheChain() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent {
            Panel(style = { provideNestedStyle(Labels) { background(Color.RED) } }) {
                Label("hello", modifier = SwingModifier.testTag(CHAIN_LABEL_TAG).name("greeting"))
            }
        }

        onNodeWithTag(CHAIN_LABEL_TAG).apply {
            assertProperty(Color.RED, "the provided style reaches the label") { background }
            assertProperty(
                listOf("testTag", "name", "styleable"),
                "the chain is the caller's modifier and the style slot; a provided style adds nothing to it",
            ) { declaredChain().map { it.first } }
        }
    }

    @Test
    fun aStyledComponentReportsItsStyleAsTheLastElementOfItsChain() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        val style = SwingStyle<LabelStyleScope> { background(Color.RED) }
        setContent { Label("hello", modifier = SwingModifier.testTag(CHAIN_LABEL_TAG), style = style) }

        onNodeWithTag(CHAIN_LABEL_TAG).fetch().declaredChain().let { chain ->
            assertEquals(
                listOf("testTag", "styleable"),
                chain.map { it.first },
                "the style stands after the modifier as one element; the properties it sets are not elements",
            )
            assertSame(style, chain.last().second["style"], "the style element reports the style the caller passed")
        }
        onNodeWithTag(CHAIN_LABEL_TAG).assertProperty(Color.RED) { background }
    }

    @Test
    fun oneInstalledDeclarationIsReportedUnderOneIdentityAcrossItsWritesAndItsRestore() = runSwingTest {
        val diagnostics = RecordingDiagnostics()
        var propertyValue by mutableStateOf<String?>("first")
        mounted(diagnostics, { Label("target", modifier = testProperty(propertyValue)) }) {
            propertyValue = "second"
            awaitUntil("the rebound value is written") { diagnostics.declared.size == 2 }
            propertyValue = "third"
            awaitUntil("the third value is written") { diagnostics.declared.size == 3 }
            propertyValue = null
            awaitUntil("the declaration is restored") { diagnostics.restored.isNotEmpty() }

            val identity = diagnostics.declared.first()
            diagnostics.declared.forEach { assertSame(identity, it, "every write reports the installed declaration") }
            assertSame(identity, diagnostics.restored.single(), "its restore reports the same declaration")
        }
    }

    @Test
    fun aReboundModifierElementKeepsTheIdentityAndReportsTheElementCurrentAtEachWrite() = runSwingTest {
        val diagnostics = RecordingDiagnostics()
        var propertyValue by mutableStateOf<String?>("first")
        mounted(diagnostics, { Label("target", modifier = testProperty(propertyValue)) }) {
            propertyValue = "second"
            awaitUntil("the rebound value is written") { diagnostics.declared.size == 2 }

            assertSame(diagnostics.declared[0], diagnostics.declared[1])
            assertNotSame(
                diagnostics.modifiers[0],
                diagnostics.modifiers[1],
                "the rebound declaration reports its replacement element, not the first one",
            )
        }
    }

    @Test
    fun eachDeclarationOfAComponentIsReportedUnderAnIdentityOfItsOwn() = runSwingTest {
        val diagnostics = RecordingDiagnostics()
        var declared by mutableStateOf(true)
        val both = SwingModifier.clientProperty("testKey", "first").name("declared")
        mounted(diagnostics, { Label("target", modifier = if (declared) both else SwingModifier) }) {
            declared = false
            awaitUntil("both declarations are restored") { diagnostics.restored.size == 2 }

            assertEquals(2, diagnostics.declared.size)
            assertNotSame(diagnostics.declared[0], diagnostics.declared[1])
            assertEquals(
                diagnostics.declared.toSet(),
                diagnostics.restored.toSet(),
                "each restore reports the identity its declaration was written under",
            )
        }
    }

    @Test
    fun aStyleBearingChainAttachedWholeReportsEachDeclarationUnderAnIdentityOfItsOwn() = runSwingTest {
        val diagnostics = RecordingDiagnostics()
        var declared by mutableStateOf(true)
        mounted(
            diagnostics,
            {
                Label(
                    "target",
                    modifier = if (declared) SwingModifier.name("declared") else SwingModifier,
                    style = { if (declared) background(Color.RED) },
                )
            },
        ) {
            assertEquals(2, diagnostics.declared.size, "the modifier and the style property are each declared once")
            assertNotSame(diagnostics.declared[0], diagnostics.declared[1])
            val components = diagnostics.declared.mapTo(HashSet()) { it.component }
            assertEquals(1, components.size)
            assertContains(diagnostics.modifiers.map { it.name }, "name")

            declared = false
            awaitUntil("both declarations are restored") { diagnostics.restored.size == 2 }
            assertEquals(diagnostics.declared.toSet(), diagnostics.restored.toSet())
        }
    }
}

/** What the element named [element] declares in the chain the component tagged [tag] carries. */
private fun ComposeSwingTest.declaredBy(
    tag: String,
    element: String,
): Map<String, Any?> = onNodeWithTag(tag).fetch().valuesOf(element)

/** The chain this component carries, as the name and values each element reports. */
private fun Component.declaredChain(): List<Pair<String, Map<String, Any?>>> =
    declaringNode().modifier.elements().map { it.name to it.declaredValues }

private fun Component.holdsStyleNode(): Boolean = (declaringNode() as SwingNodeHolder<*>).styleNode != null

private fun Component.declaringNode(): SwingComponentNode<*> =
    assertNotNull(findDeclaringGroup()?.node as? SwingComponentNode<*>, "no composition declared $this")

/** The chain this component carries ahead of the widget's own style slot, which stands last where it has one. */
private fun Component.callerChain(): List<Pair<String, Map<String, Any?>>> =
    declaredChain().let { chain -> if (chain.lastOrNull()?.first == "styleable") chain.dropLast(1) else chain }

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

/** Mounts [content] over a recomposer that reports to [diagnostics], runs [body], and disposes both. */
private suspend fun mounted(
    diagnostics: SwingCompositionDiagnostics,
    content: @Composable () -> Unit,
    body: suspend () -> Unit,
) {
    val recomposer = SwingRecomposer.create(JPanel(), effectContext = diagnostics)
    val handle = JPanel().setContent(parent = recomposer.compositionContext, content = content)
    try {
        body()
    } finally {
        handle.dispose()
        recomposer.dispose()
    }
}

private fun testProperty(value: String?): SwingModifier =
    if (value == null) SwingModifier else SwingModifier.clientProperty("testKey", value)

/** Records every declaration the composition reports, with the element each write was made for. */
private class RecordingDiagnostics : SwingCompositionDiagnostics {
    val declared = mutableListOf<ModifierInfo>()
    val modifiers = mutableListOf<SwingModifier.NodeElement<*, *>>()
    val restored = mutableListOf<ModifierInfo>()

    override fun declaring(
        info: ModifierInfo,
        write: () -> Unit,
    ) {
        declared += info
        modifiers += info.modifier
        write()
    }

    override fun restoring(
        info: ModifierInfo,
        restore: () -> Unit,
    ) {
        restored += info
        restore()
    }
}
