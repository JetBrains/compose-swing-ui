package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What a style costs on a pass that changes nothing. Resolves are counted by the runs of the styles, and writes
 * by the writes a [StyleProbe] logs.
 */
class StyleCostTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun unchangedStylesDoNotResolve(keyedWrite: Boolean) = runComposeSwingTest {
        var pass by mutableIntStateOf(0)
        val container = StyleProbe()
        val leaf = StyleProbe("leaf")
        var containerRuns = 0
        var leafRuns = 0
        var nestedRuns = 0
        val containerStyle =
            SwingStyle<ProbeContainerScope> {
                containerRuns++
                InheritedFirst.provide("inherited")
                provideNestedStyle(Probes) {
                    nestedRuns++
                    ProbeSecond.provide("nested")
                }
            }
        val leafStyle =
            SwingStyle<ProbeScope> {
                leafRuns++
                if (keyedWrite) ProbeFirst.provide("styled")
                ProbeThird.provide("caller")
            }
        setContent {
            readPass(pass)
            ProbeContainer(container, containerStyle) {
                SwingNode(
                    factory = { leaf },
                    modifier =
                        (if (keyedWrite) SwingModifier.property(FirstDescriptor, "chain $pass") else SwingModifier)
                            .styleable(NestedProbe, leafStyle),
                )
            }
        }
        onProbe("leaf")
            .assertProperty(if (keyedWrite) "styled" else "inherited") { first }
            .assertProperty("nested") { second }
            .assertProperty("caller") { third }
        val attachedRuns = listOf(containerRuns, leafRuns, nestedRuns)
        val attachedWrites = container.writes + leaf.writes
        repeat(3) {
            pass++
            awaitIdle()
        }
        assertEquals(attachedRuns, listOf(containerRuns, leafRuns, nestedRuns))
        if (!keyedWrite) assertEquals(attachedWrites, container.writes + leaf.writes)
        onProbe("leaf")
            .assertProperty(if (keyedWrite) "styled" else "inherited") { first }
            .assertProperty("nested") { second }
    }

    @Test
    fun movedChildResolvesWithoutItsSibling() = runComposeSwingTest {
        var first by mutableStateOf(true)
        var containerRuns = 0
        var leafRuns = 0
        var nestedRuns = 0
        val containerStyle =
            SwingStyle<ProbeContainerScope> {
                containerRuns++
                InheritedFirst.provide("inherited")
                provideNestedStyle(Probes) {
                    nestedRuns++
                    ProbeSecond.provide("nested")
                }
            }
        val leafStyle = SwingStyle<ProbeScope> { leafRuns++ }
        val moved = StyleProbe("moved")
        setContent {
            val content = remember { movableContentOf { ProbeLeaf(moved, leafStyle, NestedProbe) } }
            ProbeContainer(StyleProbe(), containerStyle) {
                if (first) content()
                ProbeLeaf(StyleProbe("fixed"), leafStyle, NestedProbe)
                if (!first) content()
            }
        }
        val attachedContainerRuns = containerRuns
        val attachedLeafRuns = leafRuns
        val attachedNestedRuns = nestedRuns
        val panel = assertIs<JPanel>(moved.parent)

        first = false
        awaitIdle()
        assertSame(moved, panel.getComponent(1), "the child moved to the other place under the same parent")
        assertEquals(attachedLeafRuns + 1, leafRuns, "the moved child resolves once more, and its sibling does not")
        assertEquals(attachedContainerRuns, containerRuns, "the moved child does not replay its provider")
        assertEquals(attachedNestedRuns + 1, nestedRuns, "the moved child's resolve applies the nested style once")
        onProbe("moved")
            .assertProperty("inherited") { this.first }
            .assertProperty("nested") { second }
    }

    @Test
    fun restyleReadsDoNotGrowWithDepth() {
        assertEquals(
            statesReadByARestyleBelowAPublisher(plainContainers = 1),
            statesReadByARestyleBelowAPublisher(plainContainers = 32),
            "the states a leaf's restyle reads do not grow with the containers between it and the publisher",
        )
    }

    private fun statesReadByARestyleBelowAPublisher(plainContainers: Int): Int {
        var read = 0
        runComposeSwingTest {
            val shade = mutableStateOf("a")
            val provider =
                SwingStyle<ProbeContainerScope> {
                    provideNestedStyle(Probes) { ProbeSecond.provide("nested") }
                }
            setContent {
                ProbeContainer(StyleProbe(), provider) {
                    Plain(plainContainers) {
                        ProbeLeaf(StyleProbe("leaf"), { ProbeThird.provide(shade.value) }, NestedProbe)
                    }
                }
            }
            awaitIdle()
            val states = HashSet<Any>()
            Snapshot.observe(readObserver = { states += it }) {
                Snapshot.withMutableSnapshot { shade.value = "b" }
            }
            awaitIdle()
            assertTrue(shade in states, "the restyle reads the leaf's own state at $plainContainers containers")
            read = states.size
        }
        return read
    }

    @Composable
    private fun Plain(
        depth: Int,
        content: @Composable () -> Unit,
    ) {
        if (depth == 0) content() else ProbeContainer(StyleProbe(), SwingStyle) { Plain(depth - 1, content) }
    }
}
