package org.jetbrains.compose.swing.defaults

import androidx.compose.runtime.CompositionLocalMap
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.applyDeclaredModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.node.TestCompositionOwner
import org.jetbrains.compose.swing.test.onAllNodesOfType
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
    fun refreshingUnderTwoOrMoreInheritedDefaultsSkipsReDiffingWhenAnUnrelatedKeyChanges() = runComposeSwingTest {
        // Targets JButton alone, so it never contributes to what a JLabel inherits - but changing its
        // value is still a real change to the defaults, so a label under it is still scheduled for a
        // refresh.
        val unrelatedKey = componentDefaultKeyOf<String>("buttonOnly") { buttonWriteTracking(it) {} }

        var firstMap: CompositionLocalMap? = null
        var secondMap: CompositionLocalMap? = null

        setContent {
            ProvideComponentDefaults(
                DefaultBackground provides Color(10, 20, 30),
                DefaultForeground provides Color(40, 50, 60),
                unrelatedKey provides "first",
            ) {
                firstMap = currentComposer.currentCompositionLocalMap
            }
            ProvideComponentDefaults(
                DefaultBackground provides Color(10, 20, 30),
                DefaultForeground provides Color(40, 50, 60),
                unrelatedKey provides "second",
            ) {
                secondMap = currentComposer.currentCompositionLocalMap
            }
        }

        val holder = SwingNodeHolder(JLabel()).attachedTo(TestCompositionOwner())
        holder.applyDeclaredModifier(SwingModifier)
        holder.compositionLocalMap = checkNotNull(firstMap)
        // Two or more defaults (DefaultBackground and DefaultForeground) apply to this label; this first
        // refresh bakes their inherited chain into what the holder has applied.
        holder.refreshInheritedDefaults()
        val appliedAfterFirst = holder.modifierState?.applied

        // The second defaults are a genuinely different instance - unrelatedKey's own value is a real
        // change - but background and foreground, the only two that apply to a JLabel, are unchanged.
        holder.compositionLocalMap = checkNotNull(secondMap)
        holder.refreshInheritedDefaults()

        assertSame(
            appliedAfterFirst,
            holder.modifierState?.applied,
            "refreshing a label under two or more inherited defaults must not re-diff its applied chain " +
                "when only a default that does not apply to it changed",
        )
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
