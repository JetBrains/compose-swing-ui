// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import java.awt.EventQueue
import java.awt.Rectangle
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import javax.swing.JLayeredPane
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `onPlaced` reports the bounds a component occupies in its parent whenever a layout pass changes them,
 * starting with the first bounds a pass gives it. It is `onSizeChanged`'s counterpart for where a parent
 * put a child rather than how large it made it, and a resize reports through it too, since a resize is a
 * placement.
 *
 * The cases here have no androidx counterpart: they pin the first zero bounds a pass reports and that
 * every callback runs on the event dispatch thread. The ported cases are in [PlacementLayoutCoordinatesTest].
 */
class OnPlacedTest {
    @Test
    fun initialZeroBoundsAreReported() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            lateinit var panel: InitialZeroBoundsPanel

            setContent {
                SwingNode(
                    factory = { InitialZeroBoundsPanel().also { panel = it } },
                    modifier = SwingModifier.onPlaced { reported += it }.styleable { preferredSize(0, 0) },
                )
            }
            awaitIdle()

            assertEquals(Rectangle(), reported.first(), "the first settled zero bounds are reported")
            reported.clear()

            panel.repeatInitialMove()
            assertTrue(reported.isEmpty(), "repeating the settled bounds is deduplicated")
        }

    @Test
    fun callbackRunsOnTheEventDispatchThread() =
        runComposeSwingTest {
            val callbackThreads = mutableListOf<Boolean>()
            var offset by mutableStateOf(0)

            setContent {
                LayeredPane {
                    Label(
                        text = "child",
                        modifier =
                            SwingModifier
                                .layer(JLayeredPane.DEFAULT_LAYER)
                                .onPlaced { callbackThreads += EventQueue.isDispatchThread() },
                        style = {
                            size(10, 10)
                            location(offset, 0)
                        },
                    )
                }
            }
            awaitIdle()
            callbackThreads.clear()

            offset = 20
            awaitIdle()

            assertTrue(callbackThreads.isNotEmpty(), "the deliberate placement change must reach the callback")
            assertTrue(callbackThreads.all { it }, "onPlaced delivers every callback on Swing's event dispatch thread")
        }
}

private class InitialZeroBoundsPanel : JPanel() {
    private var listener: ComponentListener? = null

    override fun addComponentListener(listener: ComponentListener) {
        super.addComponentListener(listener)
        this.listener = listener
        listener.componentMoved(ComponentEvent(this, ComponentEvent.COMPONENT_MOVED))
    }

    fun repeatInitialMove() {
        listener?.componentMoved(ComponentEvent(this, ComponentEvent.COMPONENT_MOVED))
    }
}
