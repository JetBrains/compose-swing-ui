// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.compose.swing.modifier.inheritableProperty
import org.jetbrains.compose.swing.modifier.keyboard.onKeyStroke
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.CreatedNodeHolder
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.TestCompositionOwner
import org.jetbrains.compose.swing.node.attachedTo
import org.jetbrains.compose.swing.test.interaction.assertProperty
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val CustomLabelProperty =
    ComponentPropertyDescriptor<JLabel, String>(
        name = "customLabelProperty",
        read = { it.text },
        write = { label, v -> label.text = v },
    )

private fun SwingModifier.customLabelProperty(value: String): SwingModifier =
    inheritableProperty(CustomLabelProperty, value)

@Suppress("DEPRECATION")
@Deprecated("Exercises the deprecated component-defaults API.")
class ComponentDefaultsBehaviorTest {
    @Test
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
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
        labels[0].assertProperty(Color.RED, "outer node should receive outer default") { background }

        // In inner provider, it receives innerColor (BLUE)
        // Check labels
        onAllNodesOfType<JLabel>().assertCountEquals(3)
        onAllNodesOfType<JLabel>()[1].assertProperty(
            Color.BLUE,
            "inner node should receive inner default",
        ) { background }

        // Remove inner default by providing null
        innerColor = null
        awaitIdle()
        val baseline = JLabel().background
        onAllNodesOfType<JLabel>()[1].assertProperty(
            baseline,
            "inner node should revert to baseline when inner default is null",
        ) { background }

        // Remove inner provider entirely
        showInner = false
        awaitIdle()
        onAllNodesOfType<JLabel>().assertCountEquals(2)
        onAllNodesOfType<JLabel>()[0].assertProperty(Color.RED) { background }
        onAllNodesOfType<JLabel>()[1].assertProperty(Color.RED) { background }
    }

    @Test
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
    fun duplicateKeysInOneProviderLastArgumentWins() = runComposeSwingTest {
        setContent {
            ProvideComponentDefaults(
                DefaultBackground provides Color.RED,
                DefaultBackground provides Color.BLUE,
            ) {
                Label("duplicate")
            }
        }
        onNodeOfType<JLabel>().assertProperty(Color.BLUE, "last argument for duplicate key should win") { background }
    }

    @Test
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
    fun duplicateKeysInOneProviderLastRemovalWins() = runComposeSwingTest {
        setContent {
            ProvideComponentDefaults(
                DefaultBackground provides Color.RED,
                DefaultBackground provides null,
            ) {
                Label("duplicateRemoved")
            }
        }
        onNodeOfType<JLabel>().assertProperty(JLabel().background, "last removal should win") { background }
    }

    @Test
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
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
        onNodeOfType<JLabel>().assertProperty(Color.GREEN, "later key in provision order should win") { background }
    }

    @Test
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
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
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
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
        onNodeOfType<JLabel>().assertProperty(Color.BLUE, "component background is won by key2") { background }
    }

    @Test
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
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

        onNodeOfType<JLabel>()
            .assertProperty(Color.DARK_GRAY) { background }
            .assertProperty(Color.LIGHT_GRAY) { foreground }
            .assertProperty(customFont) { font }
            .assertIsNotEnabled()
            .assertProperty(customCursor) { cursor }
            .assertProperty(customOrientation) { componentOrientation }
            .assertProperty(false) { isOpaque }
        onNodeOfType<JButton>().assertProperty(
            false,
            "DefaultEnabled provides false cascades to descendant buttons",
        ) { isEnabled }

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
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
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

        onNodeOfType<JButton>().assertProperty(
            Color.PINK,
            "matching background element applies to JButton",
        ) { background }
        onNodeOfType<JLabel>()
            .assertProperty(Color.PINK, "matching background element applies to JLabel") { background }
            .assertProperty("inherited", "label-only element applies to JLabel") { text }
    }

    @Test
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
    fun multiTargetDefaultsSkipComponentsNoCaseServes() = runComposeSwingTest {
        val alignment = componentDefaultKeyOf<Int>("alignment") { horizontalAlignment(it) }

        setContent {
            ProvideComponentDefaults(alignment provides SwingConstants.RIGHT) {
                SwingNode(factory = { JPanel() })
                Label("sample")
            }
        }

        onNodeOfType<JLabel>().assertProperty(SwingConstants.RIGHT) { horizontalAlignment }
    }

    @Test
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
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

        onNodeOfType<JButton>()
            .assertProperty(Color.BLUE) { background }
            .assertProperty(Color.RED) { foreground }
            .assertProperty("tip") { toolTipText }
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
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
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
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
    fun restorationOfUIResourceBaseline() = runComposeSwingTest {
        var hasDefault by mutableStateOf(true)
        val uiResource = ColorUIResource(Color.ORANGE)

        setContent {
            ProvideComponentDefaults(DefaultBackground provides if (hasDefault) Color.RED else null) {
                SwingNode(factory = { JLabel("uiResource").apply { background = uiResource } })
            }
        }

        val label = onNodeOfType<JLabel>().fetch()
        assertEquals(Color.RED, label.background, "default background applies")

        hasDefault = false
        awaitIdle()
        assertSame(uiResource, label.background, "removing default background restores UIResource baseline")
    }

    @Test
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
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
        val labels = onAllNodesOfType<JLabel>()
        for (index in 0 until 3) {
            labels[index].assertProperty(Color.MAGENTA, "label $index inherits the provided default") { background }
        }

        // Dynamically inserted node inherits default
        showSecond = true
        awaitIdle()
        onAllNodesOfType<JLabel>().assertCountEquals(4)
        val beforeMove = onAllNodesOfType<JLabel>().fetchAll()
        assertEquals(listOf("first", "second", "A", "B"), beforeMove.map { it.text })
        assertEquals(Color.MAGENTA, beforeMove[1].background)
        val labelA = beforeMove[2]
        val labelB = beforeMove[3]

        // Keyed reorder preserves default
        items = listOf("B", "A")
        awaitIdle()
        val afterMove = onAllNodesOfType<JLabel>().fetchAll()
        assertEquals(listOf("first", "second", "B", "A"), afterMove.map { it.text })
        assertSame(labelB, afterMove[2])
        assertSame(labelA, afterMove[3])
        afterMove.forEach {
            assertEquals(Color.MAGENTA, it.background)
        }
    }

    @Test
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
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
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
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

        val labels = onAllNodesOfType<JLabel>()
        labels[0].assertProperty(Color.BLUE, "in outer scope, keyB comes last so BLUE wins") { background }
        labels[1].assertProperty(Color.RED, "in inner scope, re-provided keyA moves to end so RED wins") { background }
    }

    @Test
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
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
        val mountedLabel = onNodeOfType<JLabel>().fetch()
        assertEquals(Color.MAGENTA, mountedLabel.background)

        // Park component (active = false)
        active = false
        awaitIdle()
        onAllNodesOfType<JLabel>().assertCountEquals(0)
        assertNull(mountedLabel.parent)

        // Reactivate component (active = true)
        active = true
        awaitIdle()
        onNodeOfType<JLabel>().assertExists()
        onNodeOfType<JLabel>().assertProperty(
            Color.MAGENTA,
            "reactivated node must inherit active defaults",
        ) { background }
    }

    @Test
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
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
        onNodeOfType<JLabel>().assertProperty(Color.GREEN) { background }
        assertFalse(released)

        show = false
        awaitIdle()
        assertTrue(released, "conditional removal should run onRelease teardown")
    }

    @Test
    // Exercises the deprecated component-defaults API.
    @Suppress("DEPRECATION")
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
        val holder = CreatedNodeHolder(JLabel()).attachedTo(TestCompositionOwner())
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

        @Deprecated(
            "Provide a nested style: style = { provideNestedStyle(AllComponents) { ... } }. " +
                "For a parent-layout declaration on each child, wrap the children in VirtualSwingNode(modifier).",
        )
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
