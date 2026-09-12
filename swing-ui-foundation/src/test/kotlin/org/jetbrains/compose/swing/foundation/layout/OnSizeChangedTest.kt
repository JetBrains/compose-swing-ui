/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from androidx.compose.ui.layout.OnSizeChangedTest in AndroidX's compose.ui:ui; see
 * this module's META-INF/NOTICE for the synced version.
 */

package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Rectangle
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `onSizeChanged` reports the extent a component occupies whenever a layout pass changes it, starting
 * with the first extent a pass gives it. A pass that lays the component out at the extent it already
 * had, or that moves it without resizing it, reports nothing.
 *
 * A case androidx `compose-ui`'s own `OnSizeChangedTest` makes keeps that test's name and its place in
 * that test's order, so the two files read side by side and a case dropped in translation shows up as a
 * gap. Room reserved around a child is a `padding` in a container's scope here rather than a modifier
 * any chain can carry, so a case whose point is padding inside or outside the reported extent states it
 * by where the padding is declared rather than by where it sits in one chain.
 *
 * [updatedModifierLambda] and [addedModifier] keep their names and invert their expectations: androidx
 * re-reports when a recomposition hands the slot a new lambda, and reports again when the modifier is
 * added to a component already laid out. This library does neither, and each case says why where it
 * asserts it.
 *
 * Cases that test makes and this library has no counterpart for are the ones resting on a raw
 * `Modifier.Node`, which this library's modifier chain does not have: `addedModifierNode`,
 * `removedModifierNode` and `updatedModifierNode` install one directly, and
 * `lazilyDelegatedModifierNode`, `delegatedSizeChanged`, `multipleDelegatedSizeChanged` and
 * `multipleDelegatedOnPlaced` reach one through a `DelegatingNode`.
 *
 * [layoutButNoSizeChange] keeps its name over a pass this library can drive: a container realigning its
 * child moves that child without resizing it, which is the same silence androidx's case asks for.
 */
class OnSizeChangedTest {
    @Test
    fun initialZeroSizeIsReported() =
        runComposeSwingTest {
            val reported = mutableListOf<Dimension>()
            lateinit var panel: InitialZeroExtentPanel

            setContent {
                SwingNode(
                    factory = { InitialZeroExtentPanel().also { panel = it } },
                    modifier = SwingModifier.preferredSize(0, 0).onSizeChanged { reported += it },
                )
            }
            awaitIdle()

            assertEquals(Dimension(), reported.first(), "the first settled zero extent is reported")
            reported.clear()

            panel.repeatInitialResize()
            assertTrue(reported.isEmpty(), "repeating the settled extent is deduplicated")
        }

    @Test
    fun callbackRunsOnTheEventDispatchThread() =
        runComposeSwingTest {
            val callbackThreads = mutableListOf<Boolean>()
            var extent by mutableStateOf(10)

            setContent {
                Label(
                    text = "child",
                    modifier =
                        SwingModifier
                            .preferredSize(extent, extent)
                            .onSizeChanged { callbackThreads += EventQueue.isDispatchThread() },
                )
            }
            awaitIdle()
            callbackThreads.clear()

            extent = 20
            awaitIdle()

            assertTrue(callbackThreads.isNotEmpty(), "the deliberate resize must reach the callback")
            assertTrue(
                callbackThreads.all { it },
                "onSizeChanged delivers every callback on Swing's event dispatch thread",
            )
        }

    @Test
    fun normalSizeChange() =
        runComposeSwingTest {
            val reported = mutableListOf<Dimension>()
            var sizePx by mutableStateOf(10)

            setContent {
                Box(modifier = SwingModifier.onSizeChanged { reported += it }) {
                    Label(text = "child", modifier = SwingModifier.preferredSize(sizePx, sizePx))
                }
            }
            awaitIdle()

            assertEquals(Dimension(10, 10), reported.last(), "the extent a container takes from its contents")

            sizePx = 20
            awaitIdle()

            assertEquals(Dimension(20, 20), reported.last(), "contents of a new extent are reported as one")
        }

    @Test
    fun internalSizeChange() =
        runComposeSwingTest {
            val reported = mutableListOf<Dimension>()
            var sizePx by mutableStateOf(10)

            setContent {
                Box(modifier = SwingModifier.onSizeChanged { reported += it }) {
                    // The room reserved around the child is reserved inside the container reporting, so it
                    // counts toward what that container occupies.
                    Label(text = "child", modifier = SwingModifier.padding(sizePx).preferredSize(10, 10))
                }
            }
            awaitIdle()

            assertEquals(Dimension(30, 30), reported.last(), "room reserved inside the report counts toward it")

            sizePx = 20
            awaitIdle()

            assertEquals(Dimension(50, 50), reported.last(), "more room reserved inside is a new extent to report")
        }

    @Test
    fun onlyInnerSizeChange() =
        runComposeSwingTest {
            val reported = mutableListOf<Dimension>()
            // The placement report alongside says the pass under test ran at all, so the silence the extent
            // report is held to is silence over a real layout rather than over nothing happening.
            val placements = mutableListOf<Rectangle>()
            var sizePx by mutableStateOf(10)

            setContent {
                Box {
                    // The room is reserved around the reporting container rather than within it, so less of
                    // it moves that container's edges inward without changing the extent it occupies.
                    Box(
                        modifier =
                            SwingModifier
                                .padding(sizePx)
                                .onSizeChanged { reported += it }
                                .onPlaced { placements += it },
                    ) {
                        Label(text = "child", modifier = SwingModifier.preferredSize(10, 10))
                    }
                }
            }
            awaitIdle()

            assertEquals(Dimension(10, 10), reported.last(), "room reserved outside the report is left out of it")
            reported.clear()
            placements.clear()

            sizePx = 5
            awaitIdle()

            assertEquals(
                listOf(Rectangle(5, 5, 10, 10)),
                placements,
                "the room around it shrank, so the layout moved it",
            )
            assertTrue(reported.isEmpty(), "less room around a component is not a change in what it occupies")
        }

    @Test
    fun layoutButNoSizeChange() =
        runComposeSwingTest {
            val reported = mutableListOf<Dimension>()
            // As in onlyInnerSizeChange: the placement report states that the pass happened.
            val placements = mutableListOf<Rectangle>()
            var alignment by mutableStateOf(Alignment.TopStart)

            setContent {
                Box(modifier = SwingModifier.preferredSize(200, 100), contentAlignment = alignment) {
                    Label(
                        text = "child",
                        modifier =
                            SwingModifier
                                .preferredSize(50, 40)
                                .onSizeChanged { reported += it }
                                .onPlaced { placements += it },
                    )
                }
            }
            awaitIdle()

            assertEquals(Dimension(50, 40), reported.last(), "the extent a container grants its child")
            reported.clear()
            placements.clear()

            // The container places the same child somewhere else at the same extent.
            alignment = Alignment.Center
            awaitIdle()

            assertEquals(listOf(Rectangle(75, 30, 50, 40)), placements, "the container placed the child somewhere else")
            assertTrue(reported.isEmpty(), "a pass that only moves a component reports no new extent")
        }

    @Test
    fun addedModifier() =
        runComposeSwingTest {
            val reported1 = mutableListOf<Dimension>()
            val reported2 = mutableListOf<Dimension>()
            val report1: (Dimension) -> Unit = { reported1 += it }
            val report2: (Dimension) -> Unit = { reported2 += it }
            var addModifier by mutableStateOf(false)
            var sizePx by mutableStateOf(10)

            setContent {
                val added = if (addModifier) SwingModifier.onSizeChanged(report2) else SwingModifier
                Box(modifier = SwingModifier.onSizeChanged(report1) then added) {
                    Label(text = "child", modifier = SwingModifier.preferredSize(sizePx, sizePx))
                }
            }
            awaitIdle()

            assertEquals(listOf(Dimension(10, 10)), reported1, "the extent a container takes from its contents")
            reported1.clear()

            addModifier = true
            awaitIdle()

            // androidx reports here: adding the modifier re-runs the measure, and the fresh node's sentinel
            // makes that measure its first. A report here rides the notification a component sends when its
            // extent changes, and a pass that changes no extent sends none.
            assertEquals(
                emptyList(),
                reported2,
                "a report declared onto a component already laid out reports nothing yet",
            )
            assertTrue(reported1.isEmpty(), "the report already declared hears nothing either: no extent changed")

            sizePx = 20
            awaitIdle()

            assertEquals(listOf(Dimension(20, 20)), reported2, "the late report hears the first extent that changes")
            assertEquals(listOf(Dimension(20, 20)), reported1, "and so does the one declared with the component")
        }

    @Test
    fun removedModifier() =
        runComposeSwingTest {
            val reported1 = mutableListOf<Dimension>()
            val reported2 = mutableListOf<Dimension>()
            val report1: (Dimension) -> Unit = { reported1 += it }
            val report2: (Dimension) -> Unit = { reported2 += it }
            var addModifier by mutableStateOf(true)
            var sizePx by mutableStateOf(10)

            setContent {
                val added = if (addModifier) SwingModifier.onSizeChanged(report2) else SwingModifier
                Box(modifier = SwingModifier.onSizeChanged(report1) then added) {
                    Label(text = "child", modifier = SwingModifier.preferredSize(sizePx, sizePx))
                }
            }
            awaitIdle()

            assertEquals(listOf(Dimension(10, 10)), reported1, "each of the two declarations is its own slot")
            assertEquals(listOf(Dimension(10, 10)), reported2, "each of the two declarations is its own slot")
            reported1.clear()
            reported2.clear()

            addModifier = false
            awaitIdle()

            assertTrue(reported1.isEmpty(), "the report left standing hears nothing: no extent changed")

            sizePx = 20
            awaitIdle()

            // Without this the case cannot tell a slot that heard nothing from a slot that was torn down
            // with its sibling: a detach taking every report off the component passes the assertion above.
            assertEquals(listOf(Dimension(20, 20)), reported1, "the report left standing is still attached")
            assertTrue(reported2.isEmpty(), "and the one taken off the chain hears nothing again")
        }

    @Test
    fun updatedModifierLambda() =
        runComposeSwingTest {
            val reported1 = mutableListOf<Dimension>()
            val reported2 = mutableListOf<Dimension>()
            var sizePx by mutableStateOf(10)
            var lambda1: (Dimension) -> Unit by mutableStateOf({ reported1 += it })
            // Stable, so that the slot beside the one being changed is left alone.
            val lambda2: (Dimension) -> Unit = { reported2 += it }

            setContent {
                Box(modifier = SwingModifier.onSizeChanged(lambda1).onSizeChanged(lambda2)) {
                    Label(text = "child", modifier = SwingModifier.preferredSize(sizePx, sizePx))
                }
            }
            awaitIdle()

            assertEquals(Dimension(10, 10), reported1.last(), "each of the two declarations is its own slot")
            assertEquals(Dimension(10, 10), reported2.last(), "each of the two declarations is its own slot")

            val fresh = mutableListOf<Dimension>()
            lambda1 = { fresh += it }
            awaitIdle()

            // Androidx reports again here, its node treating a new lambda as a reason to hand it the extent
            // over. This library reads the callback live and a fresh lambda on every pass is the style it is
            // built for, so a report here would name a change the layout never made.
            assertTrue(fresh.isEmpty(), "a recomposition handing the slot a new callback reports nothing by itself")

            sizePx = 20
            awaitIdle()

            assertEquals(listOf(Dimension(20, 20)), fresh, "the next extent goes to the callback declared last")
            assertEquals(
                Dimension(10, 10),
                reported1.last(),
                "the replaced callback hears nothing after it is replaced",
            )
        }

    @Test
    fun modifierIsReturningEqualObjectForTheSameLambda() {
        val lambda: (Dimension) -> Unit = {}

        assertEquals(
            SwingModifier.onSizeChanged(lambda),
            SwingModifier.onSizeChanged(lambda),
            "one callback declared twice is one declaration, so a recomposition holding it changes nothing",
        )
    }

    @Test
    fun modifierIsReturningNotEqualObjectForDifferentLambdas() {
        val lambda1: (Dimension) -> Unit = { it.height }
        val lambda2: (Dimension) -> Unit = { it.width }

        assertNotEquals(
            SwingModifier.onSizeChanged(lambda1),
            SwingModifier.onSizeChanged(lambda2),
            "two callbacks are two declarations, so the slot is told which one the latest pass wants",
        )
    }

    @Test
    fun sizeChangedWhenMovedBetweenLayouts() =
        runComposeSwingTest {
            val reported = mutableListOf<Dimension>()
            val report: (Dimension) -> Unit = { reported += it }
            var moveContent by mutableStateOf(false)

            setContent {
                val moving =
                    remember {
                        movableContentOf<SwingModifier> { modifier ->
                            Label(text = "child", modifier = modifier.onSizeChanged(report))
                        }
                    }
                Box {
                    if (moveContent) {
                        Box(
                            modifier = SwingModifier.preferredSize(120, 120),
                        ) { moving(SwingModifier.matchParentSize()) }
                    } else {
                        Box(modifier = SwingModifier.preferredSize(50, 50)) { moving(SwingModifier.matchParentSize()) }
                    }
                }
            }
            awaitIdle()

            assertEquals(Dimension(50, 50), reported.last(), "the extent the container it started in grants it")

            moveContent = true
            awaitIdle()

            assertEquals(Dimension(120, 120), reported.last(), "a component moved to another container reports anew")
        }

    @Test
    fun sizeChangedWhenReused() =
        runComposeSwingTest {
            val reported = mutableListOf<Dimension>()
            val report: (Dimension) -> Unit = { reported += it }
            var key by mutableStateOf(true)

            setContent {
                Box {
                    ReusableContent(key) {
                        val extent = if (key) 50 else 100
                        Label(
                            text = "child",
                            modifier =
                                SwingModifier
                                    .preferredSize(extent, extent)
                                    .onSizeChanged(report),
                        )
                    }
                }
            }
            awaitIdle()

            assertEquals(
                Dimension(50, 50),
                reported.last(),
                "the extent the content is laid out at before it is reused",
            )

            key = false
            awaitIdle()

            assertEquals(Dimension(100, 100), reported.last(), "content reused under a new key reports its new extent")
        }
}

private class InitialZeroExtentPanel : JPanel() {
    private var listener: ComponentListener? = null

    override fun addComponentListener(listener: ComponentListener) {
        super.addComponentListener(listener)
        this.listener = listener
        listener.componentResized(ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED))
    }

    fun repeatInitialResize() {
        listener?.componentResized(ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED))
    }
}
