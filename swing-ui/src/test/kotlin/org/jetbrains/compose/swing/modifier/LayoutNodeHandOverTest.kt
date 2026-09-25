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
    fun layoutNodesWrittenInPlaceHandTheNodesOverOnceToAListenerThatTakesOne() = runComposeSwingTest {
        var value by mutableStateOf("a")
        lateinit var taking: ListeningPanel
        lateinit var declining: ListeningPanel
        setContent {
            SwingNode(factory = { JPanel(measuringLayout()) }) {
                val modifier = SwingModifier.then(Layout("${value}1")).then(Layout("${value}2"))
                SwingNode(factory = { ListeningPanel().also { taking = it } }, modifier = modifier)
                SwingNode(
                    factory = { ListeningPanel(needsAfterWrite = { it !is LabelLayoutNode }).also { declining = it } },
                    modifier = modifier,
                )
            }
        }

        value = "b"
        awaitIdle()

        assertEquals(listOf(listOf("a1", "a2"), listOf("b1", "b2")), taking.received, "one hand-over for the pass")
        assertEquals(listOf(listOf("a1", "a2")), declining.received, "a declined layout node hands nothing over")
    }

    @Test
    fun layoutNodesWrittenByADiffHandTheNodesOverOnceToAListenerThatTakesOne() = runComposeSwingTest {
        var value by mutableStateOf("a")
        lateinit var taking: ListeningPanel
        lateinit var declining: ListeningPanel
        setContent {
            SwingNode(factory = { JPanel(measuringLayout()) }) {
                // The property declared first changes with the layout elements, so the pass diffs the modifier.
                val modifier = SwingModifier.opaque(value == "a").then(Layout("${value}1")).then(Layout("${value}2"))
                SwingNode(factory = { ListeningPanel().also { taking = it } }, modifier = modifier)
                SwingNode(
                    factory = { ListeningPanel(needsAfterWrite = { it !is LabelLayoutNode }).also { declining = it } },
                    modifier = modifier,
                )
            }
        }

        value = "b"
        awaitIdle()

        assertEquals(listOf(listOf("a1", "a2"), listOf("b1", "b2")), taking.received, "one hand-over for the pass")
        assertEquals(listOf(listOf("a1", "a2")), declining.received, "a declined layout node hands nothing over")
    }

    @Test
    fun aListenerTakingAWrittenLayoutNodeAheadOfOneItDeclinesIsHandedTheNodesOnce() = runComposeSwingTest {
        var value by mutableStateOf("a")
        val panel = ListeningPanel(needsAfterWrite = { (it as LabelLayoutNode).label == "b1" })
        setContent {
            SwingNode(factory = { JPanel(measuringLayout()) }) {
                // The property declared first changes with the layout elements, so the pass diffs the modifier.
                val modifier = SwingModifier.opaque(value == "a").then(Layout("${value}1")).then(Layout("${value}2"))
                SwingNode(factory = { panel }, modifier = modifier)
            }
        }

        value = "b"
        awaitIdle()

        assertEquals(listOf(listOf("a1", "a2"), listOf("b1", "b2")), panel.received, "one hand-over for the pass")
    }

    @Test
    fun aLayoutNodeThatDoesNotAutoInvalidateIsOfferedToTheListenerWhenWritten() = runComposeSwingTest {
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
        assertEquals(listOf(listOf("a"), listOf("b")), taking.received, "written in place: one hand-over")

        value = "c"
        awaitIdle()
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c")), taking.received, "written by a diff: one hand-over")
    }

    @Test
    fun aLayoutNodeJoiningBesideAWrittenOneHandsTheNodesOver() = runComposeSwingTest {
        var value by mutableStateOf("a")
        val declining = ListeningPanel(needsAfterWrite = { false })
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
    fun aPassThatWritesNoNodeOfTheChainAsksTheListenerNothingAndHandsNothingOver() = runComposeSwingTest {
        var pass by mutableIntStateOf(0)
        var asked = 0
        lateinit var panel: ListeningPanel
        setContent {
            SwingNode(factory = { JPanel(measuringLayout()) }) {
                SwingNode(
                    factory = {
                        ListeningPanel(
                            needsAfterWrite = {
                                asked++
                                true
                            },
                        ).also { panel = it }
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
        assertEquals(0, asked, "no node of the chain is written")
        assertEquals(listOf(listOf("l", "a")), panel.received, "only the attaching pass hands the nodes over")
    }
}

/** A layout manager that takes parent-layout declarations, so a child under it may declare a [Layout]. */
private fun measuringLayout(): MeasurementLayoutManager = mockk<MeasurementLayoutManager>(relaxed = true) {
    every { preferredLayoutSize(any()) } returns Dimension()
    every { minimumLayoutSize(any()) } returns Dimension()
    every { maximumLayoutSize(any()) } returns Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
}
