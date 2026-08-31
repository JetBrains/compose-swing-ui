package org.jetbrains.compose.swing.node

import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Parameter-level coverage for the two [SwingNode] overloads - the primitive every wrapper is built
 * on. The component is built once from `factory` and then driven by `update` and by the `modifier`
 * chain applied after it, `onRelease` runs the block that was declared last, and the container
 * overload's `content` tracks the declaration that produced it.
 *
 * Every assertion reads the live Swing component the node holds.
 */
class SwingNodeTest {
    @Test
    fun theUpdateBlockAppliesEveryStateChangeToTheComponent() = runComposeSwingTest {
        var text by mutableStateOf("first")
        setContent {
            SwingNode(
                factory = { JLabel() },
                update = { set(text) { this.text = it } },
            )
        }

        val label = onNodeOfType<JLabel>()
        label.assertTextEquals("first")

        text = "second"
        awaitIdle()
        label.assertTextEquals("second")

        text = "first"
        awaitIdle()
        label.assertTextEquals("first")
    }

    @Test
    fun theModifierIsAppliedAfterTheUpdateBlockOnBothOverloads() = runComposeSwingTest {
        var color by mutableStateOf(Color.BLUE)
        setContent {
            SwingNode(
                factory = { JPanel() },
                modifier = SwingModifier.background(color),
                update = { set(Unit) { background = Color.RED } },
            ) {
                SwingNode(
                    factory = { JLabel() },
                    modifier = SwingModifier.background(color),
                    update = { set(Unit) { background = Color.RED } },
                )
            }
        }

        val label = onNodeOfType<JLabel>().fetch<JLabel>()
        val panel = label.parent as JPanel
        assertEquals(Color.BLUE, panel.background, "the container's chain overrides what its update block wrote")
        assertEquals(Color.BLUE, label.background, "the leaf's chain overrides what its update block wrote")

        color = Color.GREEN
        awaitIdle()
        assertEquals(Color.GREEN, panel.background, "a changed chain reaches the container")
        assertEquals(Color.GREEN, label.background, "a changed chain reaches the leaf")
    }

    @Test
    fun theComponentIsBuiltOnceAndKeptAcrossRecompositions() = runComposeSwingTest {
        var seed by mutableStateOf("built-from-first-seed")
        var tip by mutableStateOf("first")
        var builds = 0
        setContent {
            SwingNode(
                factory = {
                    builds++
                    JLabel(seed)
                },
                update = { set(tip) { this.toolTipText = it } },
            )
        }

        val label = onNodeOfType<JLabel>()
        val original = label.fetch()
        label.assertTextEquals("built-from-first-seed")

        // The factory is a one-shot builder, not a reactive parameter: a later seed neither rebuilds the
        // component nor edits it. A component whose properties must track state declares them in
        // `update`, which the tooltip here proves did run again.
        seed = "later-seed"
        tip = "second"
        awaitIdle()

        val recomposed = label.fetch()
        assertEquals("second", recomposed.toolTipText, "the node recomposed and applied its update block")
        assertEquals(1, builds, "the factory runs once for the node")
        assertSame(original, recomposed, "the node keeps the component the factory built")
        label.assertTextEquals("built-from-first-seed")
    }

    @Test
    fun theReleaseBlockThatRunsIsTheOneDeclaredLast() = runComposeSwingTest {
        var present by mutableStateOf(true)
        var second by mutableStateOf(false)
        val released = mutableListOf<String>()
        setContent {
            if (present) {
                SwingNode(
                    factory = { JLabel() },
                    onRelease = if (second) ({ released += "second" }) else ({ released += "first" }),
                )
            }
        }

        second = true
        awaitIdle()
        assertEquals(emptyList(), released, "a node still in the composition has not been released")

        present = false
        awaitIdle()
        assertEquals(listOf("second"), released, "the block that runs is the one declared last")
    }

    @Test
    fun droppingTheReleaseBlockLeavesNothingToRunAtDisposal() = runComposeSwingTest {
        var present by mutableStateOf(true)
        var releasing by mutableStateOf(true)
        var releases = 0
        setContent {
            if (present) {
                SwingNode(
                    factory = { JLabel() },
                    onRelease = if (releasing) ({ releases++ }) else null,
                )
            }
        }

        releasing = false
        awaitIdle()
        present = false
        awaitIdle()

        assertEquals(0, releases, "a release block replaced by null must not run when the node leaves")
    }

    @Test
    fun theContentFollowsTheDeclarationDrivingIt() = runComposeSwingTest {
        var second by mutableStateOf(false)
        setContent {
            SwingNode(factory = { JPanel() }) {
                Label(text = "first")
                if (second) Label(text = "second")
            }
        }

        // The host is the container the declared child ended up in.
        val host = onNodeWithText("first").onParent()
        val secondLabel = onNodeWithText("second")
        host.onChildren().assertCountEquals(1)
        secondLabel.assertDoesNotExist()

        second = true
        awaitIdle()
        host.onChildren().assertCountEquals(2)
        secondLabel.assertExists()

        second = false
        awaitIdle()
        host.onChildren().assertCountEquals(1)
        secondLabel.assertDoesNotExist()
    }

    @Test
    fun theContainerOverloadDrivesItsOwnComponentAndReleaseBlockToo() = runComposeSwingTest {
        var present by mutableStateOf(true)
        var tip by mutableStateOf<String?>("host")
        var builds = 0
        var releases = 0
        setContent {
            if (present) {
                SwingNode(
                    factory = {
                        builds++
                        JPanel()
                    },
                    update = { set(tip) { this.toolTipText = it } },
                    onRelease = { releases++ },
                ) {
                    Label(text = "child")
                }
            }
        }

        // The host is the container the declared child ended up in.
        val host = onNodeWithText("child").onParent()
        assertEquals("host", host.fetch<JPanel>().toolTipText, "the update block applies the initial value")

        tip = null
        awaitIdle()
        assertEquals(1, builds, "the factory runs once for the node")
        assertEquals(
            null,
            host.fetch<JPanel>().toolTipText,
            "the update block applies the value on the way back to null",
        )
        assertEquals(0, releases, "a node still in the composition has not been released")

        present = false
        awaitIdle()
        assertEquals(1, releases, "the release block runs when the node leaves the composition")
    }

    @Test
    fun theInitBlockRunsOnceAtCreationAndNotAgainOnRecomposition() = runComposeSwingTest {
        var text by mutableStateOf("first")
        var inits = 0
        setContent {
            SwingNode(
                factory = { JLabel() },
                update = {
                    set(text) { this.text = it }
                    init { inits++ }
                },
            )
        }

        onNodeOfType<JLabel>().assertTextEquals("first")
        assertEquals(1, inits, "the init block runs once when the node is built")

        text = "second"
        awaitIdle()
        onNodeOfType<JLabel>().assertTextEquals("second")
        assertEquals(1, inits, "the init block does not run again when the node recomposes")
    }

    @Test
    fun theInitBlockSeesWhatTheSetBlockAboveItAlreadyApplied() = runComposeSwingTest {
        var seenAtInit: String? = null
        setContent {
            SwingNode(
                factory = { JLabel() },
                update = {
                    set("built-by-set") { this.text = it }
                    init { seenAtInit = text }
                },
            )
        }

        onNodeOfType<JLabel>().assertTextEquals("built-by-set")
        assertEquals(
            "built-by-set",
            seenAtInit,
            "init declared after a set block runs after it, so it sees the value that set applied",
        )
    }

    @Test
    fun theInitBlockDeclaredFirstRunsBeforeTheSetBlockBelowIt() = runComposeSwingTest {
        var seenAtInit: String? = null
        setContent {
            SwingNode(
                factory = { JLabel() },
                update = {
                    init { seenAtInit = text }
                    set("built-by-set") { this.text = it }
                },
            )
        }

        onNodeOfType<JLabel>().assertTextEquals("built-by-set")
        assertEquals(
            "",
            seenAtInit,
            "the blocks run in the order the update lambda declares them, so an init declared first " +
                "sees the component as the factory built it",
        )
    }

    @Test
    fun aFreshComponentBuiltAfterAKeyChangeRunsInitAgain() = runComposeSwingTest {
        var reuseKey by mutableStateOf(0)
        var inits = 0
        setContent {
            ReusableContent(reuseKey) {
                SwingNode(
                    factory = { JLabel() },
                    update = { init { inits++ } },
                )
            }
        }

        assertEquals(1, inits, "the init block runs once for the component the first key builds")

        reuseKey = 1
        awaitIdle()

        assertEquals(2, inits, "a key change builds a fresh component and runs init again for it")
    }

    @Test
    fun theUpdateBlockSkipsTheCompositionThatBuiltTheComponent() = runComposeSwingTest {
        var text by mutableStateOf("first")
        val applied = mutableListOf<String>()
        setContent {
            SwingNode(
                factory = { JLabel("first") },
                update = {
                    update(text) {
                        applied += it
                        this.text = it
                    }
                },
            )
        }

        val label = onNodeOfType<JLabel>()
        assertEquals(emptyList(), applied, "the factory already carried the first value, so nothing is applied")
        label.assertTextEquals("first")

        text = "second"
        awaitIdle()

        assertEquals(listOf("second"), applied, "a value that changed is applied")
        label.assertTextEquals("second")
    }

    @Test
    fun theReconcileBlockRunsOnEveryCompositionWhileASettledSetBlockDoesNot() = runComposeSwingTest {
        var tick by mutableStateOf(0)
        var reconciles = 0
        var settledApplications = 0
        var reconciledAgainst: JLabel? = null
        setContent {
            val declared = "tick $tick"
            SwingNode(
                factory = { JLabel() },
                update = {
                    set(declared) { this.toolTipText = it }
                    set(SETTLED) { settledApplications++ }
                    reconcile {
                        reconciles++
                        reconciledAgainst = this
                    }
                },
            )
        }

        val label = onNodeOfType<JLabel>().fetch()
        val reconciledAtMount = reconciles
        assertTrue(reconciledAtMount > 0, "the block runs on the composition that built the component")
        assertSame(label, reconciledAgainst, "the block runs against the typed component")
        assertEquals(1, settledApplications, "a set block applies its value once")

        tick = 1
        awaitIdle()

        assertEquals("tick 1", label.toolTipText, "the node recomposed")
        assertEquals(1, settledApplications, "a set block whose value did not change is skipped")
        assertTrue(reconciles > reconciledAtMount, "reconcile runs again, though nothing it reads changed")
    }
}

/** A value a `set` block is declared on that never changes, so it settles after the first composition. */
private const val SETTLED: String = "settled"
