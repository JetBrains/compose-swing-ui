package org.jetbrains.compose.swing.defaults

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JButton
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ComponentDefaultsCostTest {
    private fun SwingModifier.writeTracking(
        value: String,
        writeCounter: () -> Unit,
    ): SwingModifier = property(
        ComponentPropertyDescriptor<JLabel, String>(
            name = "writeTracking",
            read = { it.text },
            write = { comp, v ->
                writeCounter()
                comp.text = v
            },
        ),
        value,
        inheritable = true,
    )

    /**
     * Diagnostic property targeting [JButton] alone, so a component default built from it is filtered
     * out of the chain a [JLabel] inherits.
     */
    private fun SwingModifier.buttonWriteTracking(
        value: String,
        writeCounter: () -> Unit,
    ): SwingModifier = property(
        ComponentPropertyDescriptor<JButton, String>(
            name = "buttonWriteTracking",
            read = { it.text },
            write = { comp, v ->
                writeCounter()
                comp.text = v
            },
        ),
        value,
        inheritable = true,
    )

    @Test
    fun noProviderVersusEmptyDefaultPathAllocatesNoEffectiveChainAndPerformsNoExtraWrites() = runComposeSwingTest {
        var writesWithNoProvider = 0
        var writesWithEmptyProvider = 0

        val mod1 = SwingModifier.writeTracking("direct") { writesWithNoProvider++ }
        val mod2 = SwingModifier.writeTracking("direct") { writesWithEmptyProvider++ }

        setContent {
            // Node without provider
            SwingNode(
                factory = { JLabel() },
                modifier = mod1,
            )

            // Node with empty defaults provider
            ProvideComponentDefaults {
                SwingNode(
                    factory = { JLabel() },
                    modifier = mod2,
                )
            }
        }

        // Initial mount should perform exactly one write for each
        assertEquals(1, writesWithNoProvider, "initial composition without provider writes property once")
        assertEquals(1, writesWithEmptyProvider, "initial composition with empty provider writes property once")

        // In empty defaults, modifierFor must return SwingModifier
        val emptyDefaults = ComponentDefaults.Empty
        val returnedModifier = emptyDefaults.modifierFor(JLabel::class.java)
        assertSame(
            SwingModifier,
            returnedModifier,
            "empty defaults must return SwingModifier without allocating a wrapper",
        )
    }

    @Test
    fun componentsOfOneClassDeclaringNoModifierShareOneEffectiveChain() = runComposeSwingTest {
        val defaults = ComponentDefaults.Empty.withProvision(DefaultBackground provides Color.YELLOW)

        val undeclared = defaults.effectiveModifier(JLabel::class.java, SwingModifier)

        assertSame(
            undeclared,
            defaults.effectiveModifier(JLabel::class.java, SwingModifier),
            "a label declaring no modifier must take the effective chain another such label holds",
        )
    }

    @Test
    fun defaultsAndModifierChangedTogetherDiffTheNodeOnce() = runComposeSwingTest {
        var dark by mutableStateOf(false)
        var declaredWrites = 0
        val light = SwingModifier.writeTracking("light") { declaredWrites++ }
        val darkModifier = SwingModifier.writeTracking("dark") { declaredWrites++ }

        setContent {
            ProvideComponentDefaults(DefaultBackground provides if (dark) Color.BLACK else Color.WHITE) {
                SwingNode(factory = { JLabel() }, modifier = if (dark) darkModifier else light)
            }
        }
        assertEquals(1, declaredWrites, "initial mount writes the declared property once")

        dark = true
        awaitIdle()
        assertEquals(
            2,
            declaredWrites,
            "a pass changing the defaults and the modifier writes the declared property once",
        )
    }

    @Test
    fun unchangedRecompositionRetainsWholeChainAdoptionFastPath() = runComposeSwingTest {
        var tick by mutableStateOf(0)
        var writes = 0

        val customMod = SwingModifier.writeTracking("stable") { writes++ }

        setContent {
            // Recomposes when tick changes, creating fresh provision tokens
            tick.let {
                ProvideComponentDefaults(
                    DefaultBackground provides Color(10, 20, 30),
                    DefaultForeground provides Color(40, 50, 60),
                ) {
                    SwingNode(
                        factory = { JLabel() },
                        modifier = customMod,
                    )
                }
            }
        }

        assertEquals(1, writes, "initial mount writes property once")

        // Recompose with structurally equal provisions
        tick++
        awaitIdle()
        assertEquals(
            1,
            writes,
            "unchanged recomposition under structurally equal provisions must not re-write properties",
        )

        // Recompose again
        tick++
        awaitIdle()
        assertEquals(
            1,
            writes,
            "subsequent unchanged recomposition must maintain adoption fast path without re-writing properties",
        )
    }

    @Test
    fun anUnrelatedDefaultChangePreservesInheritedColors() = runComposeSwingTest {
        val unrelatedKey = componentDefaultKeyOf<String>("buttonOnly") { buttonWriteTracking(it) {} }
        val background = Color(10, 20, 30)
        val foreground = Color(40, 50, 60)
        var unrelated by mutableStateOf("first")
        var updates = 0
        val trackingElement =
            object : SwingModifier.NodeElement<JLabel, SwingModifier.ComponentNode<JLabel>>() {
                override val targetType: Class<JLabel> = JLabel::class.java

                override fun create(): SwingModifier.ComponentNode<JLabel> =
                    object : SwingModifier.ComponentNode<JLabel>() {}

                override fun update(node: SwingModifier.ComponentNode<JLabel>) {
                    updates++
                }

                // Every visit must update the probe, even when it is handed the same element.
                override fun equals(other: Any?): Boolean = false

                override fun hashCode(): Int = System.identityHashCode(this)
            }

        setContent {
            ProvideComponentDefaults(
                DefaultBackground provides background,
                DefaultForeground provides foreground,
                unrelatedKey provides unrelated,
            ) {
                Label("inheriting", modifier = trackingElement)
                SwingNode(factory = { JButton() })
            }
        }

        val label = onNodeOfType<JLabel>().fetch()
        val button = onNodeOfType<JButton>().fetch()
        assertEquals(2, updates, "mounting initializes and updates the always-updating probe")
        assertEquals("first", button.text, "the button receives its own default")
        assertEquals(background, label.background, "the label inherits its background")
        assertEquals(foreground, label.foreground, "the label inherits its foreground")

        unrelated = "second"
        awaitIdle()

        assertEquals(3, updates, "recomposition updates the probe once without an extra defaults refresh")
        assertEquals("second", button.text, "the changed default reaches the button")
        assertSame(label, onNodeOfType<JLabel>().fetch(), "changing an unrelated default keeps the label")
        assertEquals(background, label.background, "an unrelated default preserves the background")
        assertEquals(foreground, label.foreground, "an unrelated default preserves the foreground")
    }

    @Test
    fun togglingOneKeyOverSubtreeUpdatesOnlyAffectedNodes() = runComposeSwingTest {
        var defaultColor by mutableStateOf(Color.RED)
        var unprovokedWrites = 0

        val explicitMod =
            SwingModifier
                .background(Color.BLUE)
                .writeTracking("explicit") { unprovokedWrites++ }

        setContent {
            ProvideComponentDefaults(DefaultBackground provides defaultColor) {
                // Node inheriting the default
                Label("inheriting")

                // Node with explicit background override
                SwingNode(
                    factory = { JLabel() },
                    modifier = explicitMod,
                )
            }
        }

        val labels = onAllNodesOfType<JLabel>().fetchAll()
        assertEquals(Color.RED, labels[0].background)
        assertEquals(Color.BLUE, labels[1].background)
        assertEquals(1, unprovokedWrites)

        // Toggle the default key
        defaultColor = Color.GREEN
        awaitIdle()

        // Inheriting node updates
        assertEquals(Color.GREEN, labels[0].background)
        // Node with explicit override is unaffected and its modifier wasn't re-written
        assertEquals(Color.BLUE, labels[1].background)
        assertEquals(
            1,
            unprovokedWrites,
            "node with explicit override must not re-write properties when unrelated default changes",
        )
    }

    @Test
    fun heterogeneousNodesAndClassFilterCache() = runComposeSwingTest {
        val labelOnlyKey =
            componentDefaultKeyOf<String>("labelOnly") {
                writeTracking(it) {}
            }
        val bgKey =
            componentDefaultKeyOf<Color>("bg") {
                background(it)
            }

        setContent {
            ProvideComponentDefaults(
                labelOnlyKey provides "labelDefault",
                bgKey provides Color.YELLOW,
            ) {
                Label("firstLabel")
                Button("firstButton", onClick = {})
                Label("secondLabel")
                Button("secondButton", onClick = {})
            }
        }

        val labels = onAllNodesOfType<JLabel>().fetchAll()
        val buttons = onAllNodesOfType<JButton>().fetchAll()

        assertEquals(2, labels.size)
        assertEquals(2, buttons.size)

        // Both labels got yellow background and labelDefault text
        labels.forEach {
            assertEquals(Color.YELLOW, it.background)
        }
        assertEquals(listOf("labelDefault", "labelDefault"), labels.map { it.text })

        // Both buttons got yellow background; label-only key was skipped without failure
        buttons.forEach {
            assertEquals(Color.YELLOW, it.background)
        }
        assertEquals(listOf("firstButton", "secondButton"), buttons.map { it.text })

        // Verify cache hit behavior directly:
        val defaults =
            ComponentDefaults.Empty.withProvisions(
                arrayOf(
                    labelOnlyKey provides "labelDefault",
                    bgKey provides Color.YELLOW,
                ),
            )

        val firstLabelMod = defaults.modifierFor(JLabel::class.java)
        val secondLabelMod = defaults.modifierFor(JLabel::class.java)
        assertSame(
            firstLabelMod,
            secondLabelMod,
            "warm per-class filter cache must return identical modifier instance",
        )

        val firstButtonMod = defaults.modifierFor(JButton::class.java)
        val secondButtonMod = defaults.modifierFor(JButton::class.java)
        assertSame(
            firstButtonMod,
            secondButtonMod,
            "warm per-class filter cache must return identical modifier instance for buttons",
        )
    }
}
