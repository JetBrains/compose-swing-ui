package org.jetbrains.compose.swing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Parameter-level coverage for the two [SwingNode] overloads - the primitive every wrapper is built
 * on. The component is built once from `factory` and then driven entirely by `update`, `onRelease`
 * runs the block that was declared last, and the container overload's `content` tracks the
 * declaration that produced it.
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
}
