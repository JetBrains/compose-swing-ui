// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.swing.runSwingTest
import org.junit.jupiter.api.Test
import java.awt.event.ComponentAdapter
import javax.swing.JPanel
import javax.swing.JSlider
import kotlin.coroutines.resume
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests which events [CountingEventQueue] counts as pending. */
class CountingEventQueueTest {
    @Test
    fun `component events of a removed component are not pending`() =
        runSwingTest {
            val queue = BenchmarkInstrumentation.eventQueue
            val parent = JPanel(null).apply { setSize(100, 100) }
            val slider = JSlider().apply { addComponentListener(object : ComponentAdapter() {}) }
            parent.add(slider)
            parent.addNotify()
            slider.setBounds(0, 0, 40, 20)
            slider.setBounds(5, 5, 60, 30)
            assertTrue(queue.eventPending())
            slider.removeNotify()
            suspendCancellableCoroutine { continuation -> queue.probe { continuation.resume(Unit) } }
            assertFalse(queue.eventPending())
        }
}
