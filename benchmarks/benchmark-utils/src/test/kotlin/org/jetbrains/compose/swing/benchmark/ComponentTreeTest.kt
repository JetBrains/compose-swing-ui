// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.testutils.ComposeTestCase
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Test
import java.awt.Rectangle
import java.awt.event.FocusAdapter
import java.awt.event.KeyAdapter
import java.awt.event.MouseAdapter
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests the counts a toggle measurement takes from a component tree and its repaint manager. */
class ComponentTreeTest {
    @Test
    fun `component and listener counts follow the tree`() =
        runSwingTest {
            val panel = JPanel()
            val label = JLabel("Child")
            panel.add(label)

            fun listeners() = componentTree(panel).sumOf { componentListeners(it).size }
            val before = listeners()
            label.addMouseListener(object : MouseAdapter() {})
            label.addKeyListener(object : KeyAdapter() {})
            label.addFocusListener(object : FocusAdapter() {})
            assertEquals(before + 3, listeners())
            val ancestorListener =
                object : MouseAdapter(), AncestorListener {
                    override fun ancestorAdded(event: AncestorEvent): Unit = Unit

                    override fun ancestorRemoved(event: AncestorEvent): Unit = Unit

                    override fun ancestorMoved(event: AncestorEvent): Unit = Unit
                }
            label.addAncestorListener(ancestorListener)
            // AncestorNotifier registers on both the label and its parent, counted once per component.
            assertEquals(before + 6, listeners())
            label.addMouseListener(ancestorListener)
            assertEquals(before + 6, listeners())
            label.removeMouseListener(ancestorListener)
            label.removeAncestorListener(ancestorListener)
            assertEquals(before + 3, listeners())
        }

    @Test
    fun `listener identities are counted once per component`() =
        runSwingTest {
            val panel = JPanel()
            val label = JLabel("Child")
            panel.add(label)
            val before = componentTree(panel).sumOf { componentListeners(it).size }
            val listener = object : MouseAdapter() {}
            label.addMouseListener(listener)
            label.addMouseMotionListener(listener)
            panel.addMouseListener(listener)
            assertEquals(before + 2, componentTree(panel).sumOf { componentListeners(it).size })
        }

    @Test
    fun `repaint requests coalesce in host coordinates`() =
        runSwingTest {
            val host = JPanel(null).apply { setSize(100, 100) }
            val child = JLabel("Child").apply { setBounds(10, 20, 40, 30) }
            host.add(child)
            host.addNotify()
            try {
                val metrics = ToggleMetrics()
                val manager = CountingRepaintManager(host) { metrics }
                manager.addInvalidComponent(child)
                manager.addDirtyRegion(child, 0, 0, 10, 10)
                manager.addDirtyRegion(child, 5, 5, 10, 10)
                assertEquals(1L, metrics.snapshot().getValue("revalidateRequests"))
                assertEquals(2L, metrics.snapshot().getValue("repaintRequests"))
                assertEquals(Rectangle(10, 20, 15, 15), manager.dirtyRectangle())
                assertEquals(2L, manager.componentsInDirtyRegion(checkNotNull(manager.dirtyRectangle())))
                manager.clear()
                assertNull(manager.dirtyRectangle())
            } finally {
                host.removeNotify()
            }
        }

    @Test
    fun `dirty rectangle is clipped and excludes detached components`() =
        runSwingTest {
            val host = JPanel(null).apply { setSize(100, 100) }
            val manager = CountingRepaintManager(host) { null }
            manager.addDirtyRegion(JLabel("Detached"), 0, 0, 1000, 1000)
            assertNull(manager.dirtyRectangle())
            host.addNotify()
            try {
                manager.addDirtyRegion(host, -10, -10, 200, 200)
                assertEquals(Rectangle(0, 0, 100, 100), manager.dirtyRectangle())
            } finally {
                host.removeNotify()
            }
        }

    @Test
    fun `hidden requests and ancestor clipped request areas are excluded`() =
        runSwingTest {
            val host = JPanel(null).apply { setSize(100, 100) }
            val parent = JPanel(null).apply { setBounds(20, 30, 10, 15) }
            val child = JLabel("Clipped").apply { setBounds(5, 5, 40, 40) }
            val hidden =
                JLabel("Hidden").apply {
                    setBounds(0, 0, 100, 100)
                    isVisible = false
                }
            parent.add(child)
            host.add(parent)
            host.add(hidden)
            host.addNotify()
            try {
                val manager = CountingRepaintManager(host) { null }
                manager.addDirtyRegion(hidden, 0, 0, 100, 100)
                assertNull(manager.dirtyRectangle())
                manager.addDirtyRegion(child, 0, 0, 40, 40)
                assertEquals(Rectangle(25, 35, 5, 10), manager.dirtyRectangle())
            } finally {
                host.removeNotify()
            }
        }

    @Test
    fun `layouts count invalid containers`() =
        runSwingTest {
            val host = JPanel().apply { add(JPanel().apply { add(JLabel("Child")) }) }
            assertEquals(3L, invalidContainers(host))
            host.addNotify()
            try {
                host.validate()
                assertEquals(0L, invalidContainers(host))
                host.getComponent(0).invalidate()
                assertTrue(invalidContainers(host) > 0)
            } finally {
                host.removeNotify()
            }
        }

    @Test
    fun `subject effects post events while idle measurements exclude harness probes`() =
        runSwingTest {
            val subject = LaunchedEffectCase()
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                control.compose()
                control.settle()
                val metrics = control.measure { subject.toggle() }
                assertTrue(subject.completed)
                assertTrue(metrics.getValue("eventsPosted") > 0)
                assertEquals(0L, control.measure {}.getValue("eventsPosted"))
            }
        }

    @Test
    fun `dirty region excludes hidden children and children clipped by an ancestor`() =
        runSwingTest {
            val hidden = JLabel("Hidden").apply { setBounds(0, 0, 10, 10) }
            val clipped = JLabel("Clipped").apply { setBounds(0, 0, 10, 10) }
            val clip =
                JPanel(null).apply {
                    setBounds(20, 0, 10, 10)
                    add(clipped)
                }
            val panel =
                JPanel(null).apply {
                    add(hidden)
                    add(clip)
                }
            val subject = VisibilityCase(panel, hidden, clipped)
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                control.compose()
                control.settle()
                val visible = control.measure { panel.repaint(0, 0, 100, 100) }
                val excluded = control.measure { subject.toggle() }
                assertEquals(
                    visible.getValue("componentsInDirtyRegion") - 2,
                    excluded.getValue("componentsInDirtyRegion"),
                )
            }
        }

    @Test
    fun `replacing a child counts additions only in the measured host`() =
        runSwingTest {
            val panel = JPanel().apply { add(JLabel("Before")) }
            val unrelated = JPanel().apply { add(JLabel("Unrelated")) }
            val subject = ReplaceCase(panel, unrelated)
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                control.compose()
                control.settle()
                val metrics = control.measure { subject.toggle() }
                assertEquals(1L, metrics.getValue("componentsAdded"))
                panel.add(JLabel("Outside measurement"))
                assertEquals(0L, control.measure {}.getValue("componentsAdded"))
            }
        }

    @Test
    fun `nested composition groups and slots contribute to size totals`() =
        runSwingTest {
            var grow by mutableStateOf(false)
            val subject =
                object : ComposeTestCase {
                    @Composable
                    override fun Content() {
                        val panel = remember { JPanel() }
                        val parent = rememberCompositionContext()
                        SwingNode(factory = { panel })
                        DisposableEffect(parent) {
                            val handle =
                                panel.setContent(parent) {
                                    if (grow) {
                                        key("nested") {
                                            val text = remember { "Nested remembered content" }
                                            SwingNode(factory = { JLabel(text) })
                                        }
                                    }
                                }
                            onDispose { handle.dispose() }
                        }
                    }
                }
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                control.compose()
                control.settle()
                val added = control.measure { grow = true }
                assertTrue(added.getValue("compositionGroups") > 0, added.toString())
                assertTrue(added.getValue("compositionSlots") > 0, added.toString())
                val removed = control.measure { grow = false }
                assertEquals(-added.getValue("compositionGroups"), removed.getValue("compositionGroups"))
                assertEquals(-added.getValue("compositionSlots"), removed.getValue("compositionSlots"))
                val idle = control.measure {}
                assertEquals(idle.mapValues { 0L }, idle)
            }
        }

    private class LaunchedEffectCase : ComposeTestCase {
        private var changed by mutableStateOf(false)

        fun toggle() {
            changed = !changed
        }

        var completed = false

        @Composable
        override fun Content() {
            LaunchedEffect(changed) {
                yield()
                yield()
                completed = changed
            }
        }
    }

    private class VisibilityCase(
        private val panel: JPanel,
        private val hidden: JLabel,
        private val clipped: JLabel,
    ) : ComposeTestCase {
        fun toggle() {
            hidden.isVisible = !hidden.isVisible
            clipped.setLocation(if (hidden.isVisible) 0 else 20, 0)
            panel.repaint(0, 0, 100, 100)
        }

        @Composable
        override fun Content() {
            SwingNode(factory = { panel })
        }
    }

    private class ReplaceCase(
        private val panel: JPanel,
        private val unrelated: JPanel,
    ) : ComposeTestCase {
        fun toggle() {
            panel.remove(0)
            panel.add(JLabel("After"))
            unrelated.remove(0)
            unrelated.add(JLabel("Other"))
        }

        @Composable
        override fun Content() {
            SwingNode(factory = { panel })
        }
    }
}
