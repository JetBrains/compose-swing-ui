// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.location
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.size
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Rectangle
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import javax.swing.JLayeredPane
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `onSizeChanged` cases with no androidx `OnSizeChangedTest` counterpart: the first zero extent, the
 * dispatch thread, a component moved between containers or reused, state reads in the callback, and
 * throwing callbacks.
 */
class OnSizeChangedSwingTest {
    @Test
    fun initialZeroSizeIsReported() =
        runComposeSwingTest {
            val reported = mutableListOf<Dimension>()
            lateinit var panel: InitialZeroExtentPanel

            setContent {
                SwingNode(
                    factory = { InitialZeroExtentPanel().also { panel = it } },
                    modifier =
                        SwingModifier.onSizeChanged { reported += it }.styleable { preferredSize(0, 0) },
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
                            .onSizeChanged { callbackThreads += EventQueue.isDispatchThread() },
                    style = { preferredSize(extent, extent) },
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
                            modifier = SwingModifier.styleable { preferredSize(120, 120) },
                        ) { moving(SwingModifier.matchParentSize()) }
                    } else {
                        Box(
                            modifier = SwingModifier.styleable { preferredSize(50, 50) },
                        ) { moving(SwingModifier.matchParentSize()) }
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
                                    .onSizeChanged(report),
                            style = { preferredSize(extent, extent) },
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

    /**
     * androidx calls `onRemeasured` under `Snapshot.withoutReadObservation`, and this reports the same way: a state
     * read only inside the callback of a child placed again is not recorded as a read behind its container's
     * placement.
     */
    @Test
    fun aStateReadOnlyInOnSizeChangedRunsNoPlacementBlock() =
        runComposeSwingTest {
            var placements = 0
            var shown by mutableStateOf(true)
            var sizePx by mutableStateOf(10)
            var tracked by mutableStateOf(0)

            setContent {
                Layout(
                    content = {
                        Layout(
                            content = {
                                Label(
                                    text = "child",
                                    modifier = SwingModifier.onSizeChanged { tracked },
                                    style = { preferredSize(sizePx, sizePx) },
                                )
                            },
                            measurePolicy = { measurables, constraints ->
                                val placeable = measurables.single().measure(constraints.copyMaxDimensions())
                                layout(placeable.width, placeable.height) {
                                    placements++
                                    placeable.place(0, 0)
                                }
                            },
                        )
                    },
                    measurePolicy = { measurables, constraints ->
                        val placeable = measurables.single().measure(constraints.copyMaxDimensions())
                        layout(40, 40) { if (shown) placeable.place(0, 0) }
                    },
                )
            }
            awaitIdle()
            // Placed again with its container at a new size, the child is reported by its container's placement.
            shown = false
            awaitIdle()
            sizePx = 20
            awaitIdle()
            shown = true
            awaitIdle()
            placements = 0

            tracked = 1
            awaitIdle()

            assertEquals(0, placements, "a state read only inside onSizeChanged must not rerun the placement block")
        }

    @Test
    fun throwingCallbacksAreReportedAndNeitherKeepsTheOtherFromReporting() =
        runComposeSwingTest {
            val sizes = mutableListOf<Dimension>()
            val placements = mutableListOf<Rectangle>()
            var extent by mutableStateOf(10)
            setContent {
                // Resized in place: a move would give the report after the throwing one a second event to
                // catch up on.
                LayeredPane {
                    Label(
                        text = "child",
                        modifier =
                            SwingModifier
                                .layer(JLayeredPane.DEFAULT_LAYER)
                                .onSizeChanged {
                                    sizes += it
                                    error("sized")
                                }.onPlaced {
                                    placements += it
                                    error("placed")
                                },
                        style = {
                            location(0, 0)
                            size(extent, extent)
                        },
                    )
                }
            }
            awaitIdle()
            extent = 20
            val atResize = assertFailsWith<IllegalStateException> { awaitIdle() }
            val reported = listOf(atResize) + atResize.suppressed

            assertEquals(sizes.size + placements.size, reported.size, "each throw is reported and none escapes")
            assertEquals(Dimension(20, 20), sizes.last(), "onSizeChanged reports after onPlaced threw")
            assertEquals(Dimension(20, 20), placements.last().size, "onPlaced reports after onSizeChanged threw")
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
