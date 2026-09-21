// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.ReusableComposeNode
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.NestedContentHost
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.onChildAt
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** What a [SwingComponentNodeListener] is told when it throws from `onDetached` as its node moves or is reused. */
class SwingComponentNodeDetachFailureTest {
    /**
     * The [component] a case attached, and how its node moves: [move] triggers it, and [end] then ends the composition
     * the node is in.
     */
    private class Scenario(
        val component: ListeningPanel,
        val move: () -> Unit,
        val end: suspend () -> Unit,
    )

    @TestFactory
    fun aListenerThatThrowsOnDetachIsReportedAndItsNodeIsToldItAttachedAgain(): List<DynamicTest> = listOf(
        detachFails("moves to another composition") { movesToAnotherComposition() },
        detachFails("moves into a new composition") { movesIntoANewComposition() },
        detachFails("is reused") { isReused() },
    )

    private suspend fun ComposeSwingTest.movesToAnotherComposition(): Scenario {
        var inInner by mutableStateOf(true)
        var hosted by mutableStateOf(true)
        var shown by mutableStateOf(true)
        var text by mutableStateOf("before")
        setContent {
            val content =
                remember {
                    movableContentOf {
                        SwingNode(factory = { ListeningPanel() }, update = { set(text) { name = it } })
                    }
                }
            if (hosted) NestedContentHost { if (inInner) content() }
            Panel { if (shown && !inInner) content() }
        }
        awaitIdle()
        val component = onNodeOfType<ListeningPanel>().fetch()
        return Scenario(component, move = { inInner = false }) {
            assertSame(
                onRoot().onChildAt(1).assertIsOfType<JPanel>().fetch(),
                component.parent,
                "the destination contains the moved component",
            )
            hosted = false
            awaitIdle()
            text = "after"
            awaitIdle()
            assertEquals("after", component.name, "the moved content still recomposes")
            shown = false
            awaitIdle()
        }
    }

    private suspend fun ComposeSwingTest.movesIntoANewComposition(): Scenario {
        var inInner by mutableStateOf(false)
        var hosted by mutableStateOf(true)
        setContent {
            val content = remember { movableContentOf { SwingNode(factory = { ListeningPanel() }) } }
            if (hosted) NestedContentHost { if (inInner) content() }
            if (!inInner) content()
        }
        awaitIdle()
        val component = onNodeOfType<ListeningPanel>().fetch()
        return Scenario(component, move = { inInner = true }) {
            hosted = false
            awaitIdle()
        }
    }

    private suspend fun ComposeSwingTest.isReused(): Scenario {
        var key by mutableIntStateOf(0)
        var hosted by mutableStateOf(true)
        setContent {
            if (hosted) {
                NestedContentHost {
                    ReusableContent(key) {
                        ReusableComposeNode<SwingNodeHolder<JPanel>, SwingApplier>(
                            factory = { CreatedNodeHolder(ListeningPanel()) },
                            update = {},
                        )
                    }
                }
            }
        }
        awaitIdle()
        val component = onNodeOfType<ListeningPanel>().fetch()
        return Scenario(component, move = { key = 1 }) {
            hosted = false
            awaitIdle()
        }
    }

    /**
     * A case named [case]: [setUp] attaches a [ListeningPanel] and returns how its node moves. The listener throws from
     * `onDetached` as it does, and the node must still attach where it went, observe reads there and detach with it.
     */
    private fun detachFails(
        case: String,
        setUp: suspend ComposeSwingTest.() -> Scenario,
    ): DynamicTest = DynamicTest.dynamicTest(case) {
        runComposeSwingTest {
            val scenario = setUp()
            val component = scenario.component
            val node = component.events.single().node
            val watched = mutableIntStateOf(0)
            val changes = ArrayList<SwingComponentNode<*>>()
            node.observeReads({ changes += it }) { watched.intValue }
            component.failsOnDetach = true

            scenario.move()
            val thrown = assertFailsWith<IllegalStateException> { awaitIdle() }
            awaitIdle()

            assertSame(component.detachFailure, thrown, "the listener's own failure is reported")
            assertEquals(
                listOf(Event("attached", node), Event("detached", node), Event("attached", node)),
                component.events,
                "the move completes and the listener is told the node attached again",
            )
            node.observeReads({ changes += it }) { watched.intValue }
            watched.intValue = 1
            awaitIdle()
            assertEquals(listOf(node), changes, "the node observes in its new composition only")

            component.failsOnDetach = false
            scenario.end()

            assertEquals(
                listOf(
                    Event("attached", node),
                    Event("detached", node),
                    Event("attached", node),
                    Event("detached", node),
                ),
                component.events,
                "ending the composition the node is in detaches it, once",
            )
        }
    }
}
