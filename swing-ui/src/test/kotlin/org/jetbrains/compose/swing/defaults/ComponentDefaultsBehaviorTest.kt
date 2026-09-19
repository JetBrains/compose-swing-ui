package org.jetbrains.compose.swing.defaults

import androidx.compose.runtime.CompositionLocalMap
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.applyDeclaredModifier
import org.jetbrains.compose.swing.modifier.keyboard.onKeyStroke
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.TestCompositionOwner
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Cursor
import java.awt.Font
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.plaf.ColorUIResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val CustomLabelProperty =
    ComponentPropertyDescriptor<JLabel, String>(
        name = "customLabelProperty",
        read = { it.text },
        write = { label, v -> label.text = v },
    )

private fun SwingModifier.customLabelProperty(value: String): SwingModifier =
    property(CustomLabelProperty, value, inheritable = true)

class ComponentDefaultsBehaviorTest {
    @Test
    fun outerInheritanceNestedReplacementRemovalAndRestoration() = runComposeSwingTest {
        val outerColor = Color.RED
        var innerColor by mutableStateOf<Color?>(Color.BLUE)
        var showInner by mutableStateOf(true)

        setContent {
            ProvideComponentDefaults(DefaultBackground provides outerColor) {
                Label("outer")
                if (showInner) {
                    ProvideComponentDefaults(DefaultBackground provides innerColor) {
                        Label("inner")
                    }
                }
                Label("outerAfter")
            }
        }

        val labels = onAllNodesOfType<JLabel>()
        assertEquals(Color.RED, labels.fetchAll()[0].background, "outer node should receive outer default")

        // In inner provider, it receives innerColor (BLUE)
        // Check labels
        onAllNodesOfType<JLabel>().assertCountEquals(3)
        assertEquals(
            Color.BLUE,
            onAllNodesOfType<JLabel>().fetchAll()[1].background,
            "inner node should receive inner default",
        )

        // Remove inner default by providing null
        innerColor = null
        awaitIdle()
        val baseline = JLabel().background
        assertEquals(
            baseline,
            onAllNodesOfType<JLabel>().fetchAll()[1].background,
            "inner node should revert to baseline when inner default is null",
        )

        // Remove inner provider entirely
        showInner = false
        awaitIdle()
        onAllNodesOfType<JLabel>().assertCountEquals(2)
        assertEquals(Color.RED, onAllNodesOfType<JLabel>().fetchAll()[0].background)
        assertEquals(Color.RED, onAllNodesOfType<JLabel>().fetchAll()[1].background)
    }

    @Test
    fun duplicateKeysInOneProviderLastArgumentWins() = runComposeSwingTest {
        setContent {
            ProvideComponentDefaults(
                DefaultBackground provides Color.RED,
                DefaultBackground provides Color.BLUE,
            ) {
                Label("duplicate")
            }
        }
        assertEquals(
            Color.BLUE,
            onNodeOfType<JLabel>().fetch().background,
            "last argument for duplicate key should win",
        )
    }

    @Test
    fun duplicateKeysInOneProviderLastRemovalWins() = runComposeSwingTest {
        setContent {
            ProvideComponentDefaults(
                DefaultBackground provides Color.RED,
                DefaultBackground provides null,
            ) {
                Label("duplicateRemoved")
            }
        }
        assertEquals(JLabel().background, onNodeOfType<JLabel>().fetch().background, "last removal should win")
    }

    @Test
    fun competingKeysForSamePropertyLaterKeyWins() = runComposeSwingTest {
        val key1 = componentDefaultKeyOf<Color>("key1") { background(it) }
        val key2 = componentDefaultKeyOf<Color>("key2") { background(it) }

        setContent {
            ProvideComponentDefaults(
                key1 provides Color.RED,
                key2 provides Color.GREEN,
            ) {
                Label("competing")
            }
        }
        assertEquals(Color.GREEN, onNodeOfType<JLabel>().fetch().background, "later key in provision order should win")
    }

    @Test
    fun customUpdateInheritedDefaultAndExplicitModifierPrecedence() = runComposeSwingTest {
        var defaultColor by mutableStateOf(Color.GREEN)
        var explicitColor by mutableStateOf<Color?>(null)

        setContent {
            ProvideComponentDefaults(DefaultBackground provides defaultColor) {
                SwingNode(
                    factory = { JLabel("precedence") },
                    modifier = if (explicitColor != null) SwingModifier.background(explicitColor) else SwingModifier,
                    update = {
                        set(Color.YELLOW) { background = it }
                    },
                )
            }
        }

        val label = onNodeOfType<JLabel>().fetch()
        assertEquals(
            Color.GREEN,
            label.background,
            "inherited default should overwrite custom update block baseline",
        )

        // Changing default while explicit modifier remains equal
        defaultColor = Color.CYAN
        awaitIdle()
        assertEquals(
            Color.CYAN,
            label.background,
            "changing inherited default should update node when explicit modifier is unchanged",
        )

        // Explicit modifier wins over inherited default
        explicitColor = Color.MAGENTA
        awaitIdle()
        assertEquals(
            Color.MAGENTA,
            label.background,
            "explicit modifier should win over inherited default",
        )

        // Removing explicit modifier reveals inherited default
        explicitColor = null
        awaitIdle()
        assertEquals(
            Color.CYAN,
            label.background,
            "removing explicit modifier should reveal standing inherited default",
        )
    }

    @Test
    fun currentReportsInheritedDeclaration() = runComposeSwingTest {
        val keyA = componentDefaultKeyOf<String>("keyA") { this }
        val keyB = componentDefaultKeyOf<String>("keyB") { this }

        var readA: String? = "uninitialized"
        var readB: String? = "uninitialized"
        var keyAValue by mutableStateOf<String?>("initialA")

        setContent {
            readB = keyB.current
            ProvideComponentDefaults(keyA provides keyAValue) {
                readA = keyA.current
            }
        }

        assertNull(readB, "unprovided key.current should be null")
        assertEquals("initialA", readA, "provided key.current should report value")

        keyAValue = "updatedA"
        awaitIdle()
        assertEquals("updatedA", readA, "updated key.current should report updated value")

        keyAValue = null
        awaitIdle()
        assertNull(readA, "removed key.current should be null")
    }

    @Test
    fun currentReportsDeclarationEvenWhenPropertyLosesToAnotherKey() = runComposeSwingTest {
        val key1 = componentDefaultKeyOf<Color>("key1") { background(it) }
        val key2 = componentDefaultKeyOf<Color>("key2") { background(it) }

        var readKey1: Color? = null
        var readKey2: Color? = null

        setContent {
            ProvideComponentDefaults(
                key1 provides Color.RED,
                key2 provides Color.BLUE,
            ) {
                readKey1 = key1.current
                readKey2 = key2.current
                Label("competing")
            }
        }

        assertEquals(Color.RED, readKey1, "key1.current should report its provided value")
        assertEquals(Color.BLUE, readKey2, "key2.current should report its provided value")
        assertEquals(Color.BLUE, onNodeOfType<JLabel>().fetch().background, "component background is won by key2")
    }

    @Test
    fun coreKeysApplyExactlyTheirNamedPropertyAndSupplyNoImplicitValues() = runComposeSwingTest {
        val customFont = Font(Font.MONOSPACED, Font.ITALIC, 18)
        val customCursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
        val customOrientation = ComponentOrientation.RIGHT_TO_LEFT

        var emptyBg: Color? = Color.RED
        var emptyFg: Color? = Color.RED
        var emptyFont: Font? = customFont
        var emptyEnabled: Boolean? = true
        var emptyCursor: Cursor? = customCursor
        var emptyOrientation: ComponentOrientation? = customOrientation
        var emptyOpaque: Boolean? = true

        setContent {
            emptyBg = DefaultBackground.current
            emptyFg = DefaultForeground.current
            emptyFont = DefaultFont.current
            emptyEnabled = DefaultEnabled.current
            emptyCursor = DefaultCursor.current
            emptyOrientation = DefaultComponentOrientation.current
            emptyOpaque = DefaultOpaque.current

            ProvideComponentDefaults(
                DefaultBackground provides Color.DARK_GRAY,
                DefaultForeground provides Color.LIGHT_GRAY,
                DefaultFont provides customFont,
                DefaultEnabled provides false,
                DefaultCursor provides customCursor,
                DefaultComponentOrientation provides customOrientation,
                DefaultOpaque provides false,
            ) {
                Label("allCore")
                Button("childButton", onClick = {})
            }
        }

        val label = onNodeOfType<JLabel>().fetch()
        val button = onNodeOfType<JButton>().fetch()

        assertEquals(Color.DARK_GRAY, label.background)
        assertEquals(Color.LIGHT_GRAY, label.foreground)
        assertEquals(customFont, label.font)
        assertFalse(label.isEnabled)
        assertFalse(button.isEnabled, "DefaultEnabled provides false cascades to descendant buttons")
        assertEquals(customCursor, label.cursor)
        assertEquals(customOrientation, label.componentOrientation)
        assertFalse(label.isOpaque)

        // Verify without provider, defaults start empty with no implicit values
        assertNull(emptyBg)
        assertNull(emptyFg)
        assertNull(emptyFont)
        assertNull(emptyEnabled)
        assertNull(emptyCursor)
        assertNull(emptyOrientation)
        assertNull(emptyOpaque)
    }

    @Test
    fun mixedTargetTypesSkipNarrowerElementsOnIncompatibleComponents() = runComposeSwingTest {
        val mixedKey =
            componentDefaultKeyOf<String>("textAndColor") { text ->
                background(Color.PINK) then
                    customLabelProperty(text)
            }

        setContent {
            ProvideComponentDefaults(mixedKey provides "inherited") {
                // Button is not a JLabel: customLabelProperty must be skipped, while background still applies!
                Button("btn", onClick = {})
                Label("sample")
            }
        }

        val button = onNodeOfType<JButton>().fetch()
        val label = onNodeOfType<JLabel>().fetch()

        assertEquals(Color.PINK, button.background, "matching background element applies to JButton")
        assertEquals(Color.PINK, label.background, "matching background element applies to JLabel")
        assertEquals("inherited", label.text, "label-only element applies to JLabel")
    }

    @Test
    fun multiTargetDefaultsSkipComponentsNoCaseServes() = runComposeSwingTest {
        val alignment = componentDefaultKeyOf<Int>("alignment") { horizontalAlignment(it) }

        setContent {
            ProvideComponentDefaults(alignment provides SwingConstants.RIGHT) {
                SwingNode(factory = { JPanel() })
                Label("sample")
            }
        }

        assertEquals(SwingConstants.RIGHT, onNodeOfType<JLabel>().fetch().horizontalAlignment)
    }

    @Test
    fun defaultsBecomingApplicableKeepEveryDeclaredElement() = runComposeSwingTest {
        val labelOnly = componentDefaultKeyOf<String>("labelOnly") { customLabelProperty(it) }
        var provided by mutableStateOf<ProvidedComponentDefault<*>>(labelOnly provides "unused")

        setContent {
            ProvideComponentDefaults(provided) {
                Button("btn", onClick = {}, modifier = SwingModifier.foreground(Color.RED).toolTip("tip"))
            }
        }
        provided = DefaultBackground provides Color.BLUE
        awaitIdle()

        val button = onNodeOfType<JButton>().fetch()
        assertEquals(Color.BLUE, button.background)
        assertEquals(Color.RED, button.foreground)
        assertEquals("tip", button.toolTipText)
    }

    @Test
    fun explicitTargetMismatchThrowsCheckedTargetError() = runComposeSwingTest {
        // Passing a JLabel-only modifier explicitly to a button should still throw checkedTarget error
        assertFailsWith<IllegalStateException> {
            setContent {
                Button("failing", onClick = {}, modifier = SwingModifier.customLabelProperty("fail"))
            }
        }
    }

    @Test
    fun providerRejectsANonInheritableProperty() {
        val propertyKey = componentDefaultKeyOf<String>("invalidProperty") { toolTip(it) }
        val failure =
            assertFailsWith<IllegalArgumentException> {
                runComposeSwingTest {
                    setContent {
                        ProvideComponentDefaults(propertyKey provides "test") {
                            Label("error")
                        }
                    }
                }
            }
        assertTrue(
            failure.message?.contains("non-inheritable") == true,
            "A non-inheritable property must be rejected as such",
        )
    }

    @Test
    fun providerRejectsInvalidElementsNamingKeyAndElement() {
        val listenerKey =
            componentDefaultKeyOf<String>("invalidListener") {
                actionListener { }
            }
        val failure =
            assertFailsWith<IllegalArgumentException> {
                runComposeSwingTest {
                    setContent {
                        ProvideComponentDefaults(listenerKey provides "test") {
                            Label("error")
                        }
                    }
                }
            }
        assertTrue(
            failure.message?.contains("invalidListener") == true,
            "Exception message must mention key name 'invalidListener'",
        )

        val additiveKey =
            componentDefaultKeyOf<String>("invalidAdditive") {
                onKeyStroke(KeyStroke.getKeyStroke("control X")) {}
            }
        val failure2 =
            assertFailsWith<IllegalArgumentException> {
                runComposeSwingTest {
                    setContent {
                        ProvideComponentDefaults(additiveKey provides "test") {
                            Label("error")
                        }
                    }
                }
            }
        assertTrue(
            failure2.message?.contains("invalidAdditive") == true,
            "Exception message must mention key name 'invalidAdditive'",
        )

        val placementKey =
            componentDefaultKeyOf<String>("invalidPlacement") {
                layoutConstraint("Center")
            }
        val failure3 =
            assertFailsWith<IllegalArgumentException> {
                runComposeSwingTest {
                    setContent {
                        ProvideComponentDefaults(placementKey provides "test") {
                            Label("error")
                        }
                    }
                }
            }
        assertTrue(
            failure3.message?.contains("invalidPlacement") == true,
            "Exception message must mention key name 'invalidPlacement'",
        )
    }

    @Test
    fun sharedSlotTransitionsInBothDirections() = runComposeSwingTest {
        var hasExplicit by mutableStateOf(true)
        var inheritedColor by mutableStateOf<Color?>(Color.RED)

        setContent {
            ProvideComponentDefaults(DefaultBackground provides inheritedColor) {
                Label(
                    "slot",
                    modifier = if (hasExplicit) SwingModifier.background(Color.BLUE) else SwingModifier,
                )
            }
        }

        val label = onNodeOfType<JLabel>().fetch()
        assertEquals(Color.BLUE, label.background, "explicit modifier wins initially")

        // 1. Explicit removed while inherited stands -> gets inherited
        hasExplicit = false
        awaitIdle()
        assertEquals(Color.RED, label.background, "removing explicit modifier reveals inherited default")

        // 2. Explicit added back -> explicit wins
        hasExplicit = true
        awaitIdle()
        assertEquals(Color.BLUE, label.background, "explicit wins again")

        // 3. Inherited removed while explicit stands -> stays explicit
        inheritedColor = null
        awaitIdle()
        assertEquals(Color.BLUE, label.background, "removing inherited default leaves explicit modifier standing")

        // 4. Finally explicit removed -> restores baseline
        hasExplicit = false
        awaitIdle()
        assertEquals(JLabel().background, label.background, "removing all restores baseline")
    }

    @Test
    fun restorationOfUIResourceBaseline() = runComposeSwingTest {
        var hasDefault by mutableStateOf(true)
        val uiResource = ColorUIResource(Color.ORANGE)

        setContent {
            if (hasDefault) {
                ProvideComponentDefaults(DefaultBackground provides Color.RED) {
                    SwingNode(
                        factory = { JLabel("uiResource").apply { background = uiResource } },
                    )
                }
            } else {
                SwingNode(
                    factory = { JLabel("uiResource").apply { background = uiResource } },
                )
            }
        }

        val label = onNodeOfType<JLabel>().fetch()
        assertEquals(Color.RED, label.background, "default background applies")

        hasDefault = false
        awaitIdle()
        assertEquals(uiResource, label.background, "removing default background restores UIResource baseline")
    }

    @Test
    fun dynamicInsertionKeyedReorderAndLifecycle() = runComposeSwingTest {
        var showSecond by mutableStateOf(false)
        var items by mutableStateOf(listOf("A", "B"))

        setContent {
            ProvideComponentDefaults(DefaultBackground provides Color.MAGENTA) {
                Label("first")
                if (showSecond) {
                    Label("second")
                }
                for (item in items) {
                    androidx.compose.runtime.key(item) {
                        Label(item)
                    }
                }
            }
        }

        // Initially 1 + 2 = 3 labels
        onAllNodesOfType<JLabel>().assertCountEquals(3)
        onAllNodesOfType<JLabel>().fetchAll().forEach {
            assertEquals(Color.MAGENTA, it.background)
        }

        // Dynamically inserted node inherits default
        showSecond = true
        awaitIdle()
        onAllNodesOfType<JLabel>().assertCountEquals(4)
        assertEquals(Color.MAGENTA, onAllNodesOfType<JLabel>().fetchAll()[1].background)

        // Keyed reorder preserves default
        items = listOf("B", "A")
        awaitIdle()
        onAllNodesOfType<JLabel>().fetchAll().forEach {
            assertEquals(Color.MAGENTA, it.background)
        }
    }

    @Test
    fun callerOwnedCompositionRootIsExcludedFromDefaults() = runComposeSwingTest {
        setContent {
            // Obtain the root container or observe that root is not touched
            ProvideComponentDefaults(DefaultBackground provides Color.RED) {
                Label("child")
            }
        }

        val label = onNodeOfType<JLabel>().fetch()
        assertEquals(Color.RED, label.background)

        // The parent of label is the root JPanel owned by the harness
        val parent = label.parent as JPanel
        assertEquals(
            JPanel().background,
            parent.background,
            "caller-owned composition root container must not be touched by inherited defaults",
        )
    }

    @Test
    fun nestedReprovisionOfSameValueMovesKeyToEndAndWinsPrecedence() = runComposeSwingTest {
        val keyA = componentDefaultKeyOf<Color>("keyA") { background(it) }
        val keyB = componentDefaultKeyOf<Color>("keyB") { background(it) }

        setContent {
            ProvideComponentDefaults(keyA provides Color.RED, keyB provides Color.BLUE) {
                Label("outer")
                ProvideComponentDefaults(keyA provides Color.RED) {
                    Label("inner")
                }
            }
        }

        val labels = onAllNodesOfType<JLabel>().fetchAll()
        assertEquals(Color.BLUE, labels[0].background, "in outer scope, keyB comes last so BLUE wins")
        assertEquals(Color.RED, labels[1].background, "in inner scope, re-provided keyA moves to end so RED wins")
    }

    @Test
    fun parkReactivateUnderInheritedDefaults() = runComposeSwingTest {
        var active by mutableStateOf(true)

        setContent {
            ProvideComponentDefaults(DefaultBackground provides Color.MAGENTA) {
                ReusableContentHost(active = active) {
                    Label("parkable")
                }
            }
        }

        onNodeOfType<JLabel>().assertExists()
        assertEquals(Color.MAGENTA, onNodeOfType<JLabel>().fetch().background)

        // Park component (active = false)
        active = false
        awaitIdle()

        // Reactivate component (active = true)
        active = true
        awaitIdle()
        onNodeOfType<JLabel>().assertExists()
        assertEquals(
            Color.MAGENTA,
            onNodeOfType<JLabel>().fetch().background,
            "reactivated node must inherit active defaults",
        )
    }

    @Test
    fun conditionalNodeRemovalAndReleaseUnderDefaults() = runComposeSwingTest {
        var show by mutableStateOf(true)
        var released = false

        setContent {
            ProvideComponentDefaults(DefaultBackground provides Color.GREEN) {
                if (show) {
                    SwingNode(
                        factory = { JLabel("removable") },
                        onRelease = { released = true },
                    )
                }
            }
        }

        onNodeOfType<JLabel>().assertExists()
        assertEquals(Color.GREEN, onNodeOfType<JLabel>().fetch().background)
        assertFalse(released)

        show = false
        awaitIdle()
        assertTrue(released, "conditional removal should run onRelease teardown")
    }

    @Test
    fun rewriteOnReassertsDefaultWhenExternallyOverwritten() = runComposeSwingTest {
        setContent {
            ProvideComponentDefaults(DefaultOpaque provides true) {
                Label("opaqueLabel")
            }
        }

        val label = onNodeOfType<JLabel>().fetch()
        assertTrue(label.isOpaque)

        // Simulate external overwrite of property
        label.isOpaque = false
        awaitIdle()

        assertTrue(
            label.isOpaque,
            "inherited default with a rewriteOn property must reassert when overwritten",
        )
    }

    @Test
    fun defaultsWithdrawnAfterTheirApplicationThrewComeApart() = runComposeSwingTest {
        val events = ArrayList<String>()
        val failing = componentDefaultKeyOf<String>("failing") { this then FailingDefaultElement(events) }
        var withoutDefaults: CompositionLocalMap? = null
        var withDefaults: CompositionLocalMap? = null
        setContent {
            withoutDefaults = currentComposer.currentCompositionLocalMap
            ProvideComponentDefaults(failing provides "value") {
                withDefaults = currentComposer.currentCompositionLocalMap
            }
        }
        val holder = SwingNodeHolder(JLabel()).attachedTo(TestCompositionOwner())
        holder.applyDeclaredModifier(SwingModifier)
        holder.compositionLocalMap = checkNotNull(withDefaults)
        assertFailsWith<IllegalStateException> { holder.refreshInheritedDefaults() }
        events.clear()

        holder.compositionLocalMap = checkNotNull(withoutDefaults)
        holder.refreshInheritedDefaults()

        assertEquals(listOf("onDetach"), events, "the node the defaults declared comes apart once they are withdrawn")
    }

    /** An inheritable element whose node attaches and fails to be updated. */
    private class FailingDefaultElement(
        private val events: MutableList<String>,
    ) : SwingModifier.NodeElement<JLabel, FailingDefaultElement.Node>() {
        override val targetType: Class<JLabel> get() = JLabel::class.java

        override val inheritable: Boolean get() = true

        override fun create(): Node = Node(events)

        override fun update(node: Node): Unit = error("update fails")

        override fun equals(other: Any?): Boolean = other is FailingDefaultElement && events === other.events

        override fun hashCode(): Int = System.identityHashCode(events)

        class Node(
            private val events: MutableList<String>,
        ) : SwingModifier.ComponentNode<JLabel>() {
            override fun onDetach() {
                events += "onDetach"
            }
        }
    }
}
