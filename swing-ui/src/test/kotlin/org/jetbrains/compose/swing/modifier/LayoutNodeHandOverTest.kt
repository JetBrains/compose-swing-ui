package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.mockk.every
import io.mockk.mockk
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Which passes over the layout nodes of a modifier hand a component under a measuring parent its nodes. */
class LayoutNodeHandOverTest {
    @Test
    fun layoutNodePolicyControlsHandOverAfterInPlaceWrites() = runComposeSwingTest {
        var value by mutableStateOf("a")
        lateinit var taking: ListeningPanel
        lateinit var declining: ListeningPanel
        setContent {
            SwingNode(factory = { JPanel(measuringLayout()) }) {
                val modifier = SwingModifier.then(Layout("${value}1")).then(Layout("${value}2"))
                SwingNode(factory = { ListeningPanel().also { taking = it } }, modifier = modifier)
                SwingNode(
                    factory = { ListeningPanel().also { declining = it } },
                    modifier =
                        SwingModifier
                            .then(Layout("${value}1", autoInvalidates = false))
                            .then(Layout("${value}2", autoInvalidates = false)),
                )
            }
        }

        value = "b"
        awaitIdle()

        assertEquals(listOf(listOf("a1", "a2"), listOf("b1", "b2")), taking.received, "one hand-over for the pass")
        assertEquals(listOf(listOf("a1", "a2")), declining.received, "false opts out of update handovers")
    }

    @Test
    fun layoutNodePolicyControlsHandOverAfterDiffWrites() = runComposeSwingTest {
        var value by mutableStateOf("a")
        lateinit var taking: ListeningPanel
        lateinit var declining: ListeningPanel
        setContent {
            SwingNode(factory = { JPanel(measuringLayout()) }) {
                // The property declared first changes with the layout elements, so the pass diffs the modifier.
                val modifier = SwingModifier.opaque(value == "a").then(Layout("${value}1")).then(Layout("${value}2"))
                SwingNode(factory = { ListeningPanel().also { taking = it } }, modifier = modifier)
                SwingNode(
                    factory = { ListeningPanel().also { declining = it } },
                    modifier =
                        SwingModifier
                            .opaque(value == "a")
                            .then(Layout("${value}1", autoInvalidates = false))
                            .then(Layout("${value}2", autoInvalidates = false)),
                )
            }
        }

        value = "b"
        awaitIdle()

        assertEquals(listOf(listOf("a1", "a2"), listOf("b1", "b2")), taking.received, "one hand-over for the pass")
        assertEquals(listOf(listOf("a1", "a2")), declining.received, "false opts out of update handovers")
    }

    @Test
    fun oneAutoInvalidatingLayoutNodeHandsTheNodesOverOnce() = runComposeSwingTest {
        var value by mutableStateOf("a")
        val panel = ListeningPanel()
        setContent {
            SwingNode(factory = { JPanel(measuringLayout()) }) {
                // The property declared first changes with the layout elements, so the pass diffs the modifier.
                val modifier =
                    SwingModifier
                        .opaque(value == "a")
                        .then(Layout("${value}1", autoInvalidates = value == "b"))
                        .then(Layout("${value}2", autoInvalidates = false))
                SwingNode(factory = { panel }, modifier = modifier)
            }
        }

        value = "b"
        awaitIdle()

        assertEquals(listOf(listOf("a1", "a2"), listOf("b1", "b2")), panel.received, "one hand-over for the pass")
    }

    @Test
    fun aLayoutNodeThatDoesNotAutoInvalidateDoesNotHandOverOnWrites() = runComposeSwingTest {
        var value by mutableStateOf("a")
        val taking = ListeningPanel()
        setContent {
            SwingNode(factory = { JPanel(measuringLayout()) }) {
                // The property declared first changes on the last pass only, which therefore diffs the modifier.
                val modifier = SwingModifier.opaque(value != "c").then(Layout(value, autoInvalidates = false))
                SwingNode(factory = { taking }, modifier = modifier)
            }
        }

        value = "b"
        awaitIdle()
        assertEquals(listOf(listOf("a")), taking.received, "false suppresses the update handover")

        value = "c"
        awaitIdle()
        assertEquals(listOf(listOf("a")), taking.received, "false suppresses the update handover")
    }

    @Test
    fun aLayoutNodeJoiningBesideAWrittenOneHandsTheNodesOver() = runComposeSwingTest {
        var value by mutableStateOf("a")
        val declining = ListeningPanel()
        setContent {
            SwingNode(factory = { JPanel(measuringLayout()) }) {
                val joining = if (value == "a") SwingModifier else Layout("joined")
                SwingNode(factory = { declining }, modifier = SwingModifier.then(Layout(value)).then(joining))
            }
        }

        value = "b"
        awaitIdle()

        assertEquals(listOf(listOf("a"), listOf("b", "joined")), declining.received, "the joining node hands over")
    }

    @Test
    fun aPropertyOnlyWriteDoesNotHandOverTheNodeList() = runComposeSwingTest {
        var pass by mutableIntStateOf(0)
        lateinit var panel: ListeningPanel
        setContent {
            SwingNode(factory = { JPanel(measuringLayout()) }) {
                SwingNode(
                    factory = {
                        ListeningPanel().also { panel = it }
                    },
                    modifier = SwingModifier.opaque(pass > 1).then(Layout("l")).then(Additive("a")),
                )
            }
        }

        pass = 1
        awaitIdle()
        pass = 2
        awaitIdle()

        assertTrue(panel.isOpaque, "the second pass writes the property declared with another value")
        assertEquals(listOf(listOf("l", "a")), panel.received, "only the attaching pass hands the nodes over")
    }
}

/** A layout manager that takes parent-layout declarations, so a child under it may declare a [Layout]. */
private fun measuringLayout(): MeasurementLayoutManager = mockk<MeasurementLayoutManager>(relaxed = true) {
    every { preferredLayoutSize(any()) } returns Dimension()
    every { minimumLayoutSize(any()) } returns Dimension()
    every { maximumLayoutSize(any()) } returns Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
}
