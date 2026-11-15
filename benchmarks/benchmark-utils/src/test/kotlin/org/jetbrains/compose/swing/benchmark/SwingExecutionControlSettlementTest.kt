// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.testutils.ComposeTestCase
import kotlinx.coroutines.Job
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.runSwingTest
import org.junit.jupiter.api.Test
import java.awt.AWTEvent
import java.awt.ActiveEvent
import java.awt.Graphics
import java.awt.Toolkit
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.PaintEvent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Tests that a [SwingExecutionControl] measurement includes all the work its toggle causes. */
class SwingExecutionControlSettlementTest {
    @Test
    fun `measurement includes recomposition scheduled by layout`() =
        runSwingTest {
            var laidOut by mutableStateOf(false)
            val label = JLabel()
            val subject =
                object : ComposeTestCase {
                    @Composable
                    override fun Content() {
                        val text = if (laidOut) "After layout" else "Before layout"
                        SwingNode(
                            factory = {
                                object : JPanel() {
                                    override fun doLayout() {
                                        super.doLayout()
                                        laidOut = true
                                    }
                                }.apply { add(label) }
                            },
                            update = { set(text) { label.text = it } },
                        )
                    }
                }
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                val metrics = control.measure { control.compose() }
                assertEquals("After layout", label.text)
                assertTrue(metrics.getValue("compositions") > 1, metrics.toString())
                val idle = control.measure {}
                assertEquals(idle.mapValues { 0L }, idle)
            }
        }

    @Test
    fun `settle stops when the coroutine attached after setup is cancelled`() =
        runSwingTest {
            val subject =
                object : ComposeTestCase {
                    @Composable
                    override fun Content() {
                        SwingNode(factory = { JLabel() })
                    }
                }
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                control.compose()
                control.settle()
                val invocation = Job()
                control.coroutineContext = invocation
                invocation.cancel()
                assertFailsWith<IllegalStateException> { control.settle() }
            }
        }

    @Test
    fun `finite frame animation paints every frame inside the measurement`() =
        runSwingTest {
            var animate by mutableStateOf(false)
            var value by mutableStateOf(0)
            val painted = mutableListOf<String>()
            val label =
                object : JLabel() {
                    override fun paintComponent(graphics: Graphics) {
                        painted += text
                        super.paintComponent(graphics)
                    }
                }
            val subject =
                object : ComposeTestCase {
                    @Composable
                    override fun Content() {
                        LaunchedEffect(animate) {
                            if (animate) {
                                repeat(4) { frame ->
                                    withFrameNanos { value = frame + 1 }
                                }
                            }
                        }
                        SwingNode(factory = { label }, update = { set(value.toString()) { text = it } })
                    }
                }
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                control.compose()
                control.settle()
                painted.clear()
                val metrics = control.measure { animate = true }
                assertEquals("4", label.text)
                assertEquals(listOf("1", "2", "3", "4"), painted)
                assertTrue(metrics.getValue("repaintRequests") > 0, metrics.toString())
                assertTrue(metrics.getValue("dirtyArea") > 0, metrics.toString())
                val idle = control.measure {}
                assertEquals(idle.mapValues { 0L }, idle)
            }
        }

    @Test
    fun `measurement includes recomposition scheduled by painting`() =
        runSwingTest {
            var painted by mutableStateOf(false)
            val label =
                object : JLabel() {
                    override fun paintComponent(graphics: Graphics) {
                        super.paintComponent(graphics)
                        painted = true
                    }
                }
            val subject =
                object : ComposeTestCase {
                    @Composable
                    override fun Content() {
                        val text = if (painted) "After paint" else "Before paint"
                        SwingNode(factory = { label }, update = { set(text) { this.text = it } })
                    }
                }
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                val metrics = control.measure { control.compose() }
                assertEquals("After paint", label.text)
                assertTrue(metrics.getValue("compositions") > 1, metrics.toString())
                val idle = control.measure {}
                assertEquals(idle.mapValues { 0L }, idle)
            }
        }

    @Test
    fun `measurement paints a repaint requested during painting`() =
        runSwingTest {
            var paintCount = 0
            val label =
                object : JLabel("Paint") {
                    override fun paintComponent(graphics: Graphics) {
                        super.paintComponent(graphics)
                        paintCount++
                        if (paintCount == 1) {
                            repaint()
                        }
                    }
                }
            val subject =
                object : ComposeTestCase {
                    @Composable
                    override fun Content() {
                        SwingNode(factory = { label })
                    }
                }
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                val metrics = control.measure { control.compose() }
                assertEquals(2, paintCount)
                assertTrue(metrics.getValue("repaintRequests") > 1, metrics.toString())
                assertTrue(metrics.getValue("dirtyArea") > 0, metrics.toString())
                val idle = control.measure {}
                assertEquals(idle.mapValues { 0L }, idle)
            }
        }

    @Test
    fun `measurement dispatches a component moved event the toggle posted`() =
        runSwingTest {
            val label = JLabel("Before")
            label.addComponentListener(
                object : ComponentListener {
                    override fun componentMoved(event: ComponentEvent) {
                        label.text = "After move"
                    }

                    override fun componentResized(event: ComponentEvent) = Unit

                    override fun componentShown(event: ComponentEvent) = Unit

                    override fun componentHidden(event: ComponentEvent) = Unit
                },
            )
            val subject =
                object : ComposeTestCase {
                    @Composable
                    override fun Content() {
                        SwingNode(factory = { label })
                    }
                }
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                control.compose()
                control.settle()
                control.measure {
                    Toolkit.getDefaultToolkit().systemEventQueue.postEvent(
                        ComponentEvent(label, ComponentEvent.COMPONENT_MOVED),
                    )
                }
                assertEquals("After move", label.text)
            }
        }

    @Test
    fun `measurement dispatches a low priority event the toggle posted`() =
        runSwingTest {
            val label = JLabel("Before")
            var dispatched = false
            val subject =
                object : ComposeTestCase {
                    @Composable
                    override fun Content() {
                        SwingNode(factory = { label })
                    }
                }
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                control.compose()
                control.settle()
                val digest = control.host.digest()
                control.measure {
                    Toolkit.getDefaultToolkit().systemEventQueue.postEvent(
                        object : AWTEvent(label, PaintEvent.UPDATE), ActiveEvent {
                            override fun dispatch() {
                                dispatched = true
                                label.text = "After update"
                            }
                        },
                    )
                }
                assertTrue(dispatched)
                assertEquals("After update", label.text)
                assertNotEquals(digest, control.host.digest())
            }
        }
}
