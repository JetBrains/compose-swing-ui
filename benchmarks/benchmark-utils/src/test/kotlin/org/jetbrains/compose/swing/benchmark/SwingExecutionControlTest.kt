// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.testutils.ComposeTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.runSwingTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Tests the counters [SwingExecutionControl] records for composition, state changes, effects and disposal. */
class SwingExecutionControlTest {
    @Test
    fun `a long tween reaches its target and measures the same twice`() =
        runSwingTest {
            val subject = TweenCase()
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                control.compose()
                control.settle()
                val first = control.measure { subject.toggleState() }
                assertEquals(1f, subject.value)
                val second = control.measure { subject.toggleState() }
                assertEquals(0f, subject.value)
                // The machine's frame cadence decides how many events a tween posts, so that count is left out.
                assertEquals(first - "eventsPosted", second - "eventsPosted")
            }
        }

    @Test
    fun `first composition records composition tree paint and effect counters`() =
        runSwingTest {
            val subject = TreeCase()
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                val metrics = control.measure { control.compose() }
                listOf(
                    "compositions",
                    "recomposedScopes",
                    "stateReads",
                    "componentsAdded",
                    "compositionGroups",
                    "compositionSlots",
                    "effectStarts",
                    "listenersCreated",
                    "layouts",
                    "revalidateRequests",
                    "repaintRequests",
                    "dirtyArea",
                    "componentsInDirtyRegion",
                    "eventsPosted",
                    "span.apply",
                    "span.insert",
                    "span.attach",
                ).forEach { name -> assertTrue(metrics.getValue(name) > 0, "$name: $metrics") }
            }
        }

    @Test
    fun `state change invalidates recomposes and reads the changed scope`() =
        runSwingTest {
            val subject = TreeCase()
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                control.compose()
                control.settle()
                val before = control.host.digest()
                val metrics = control.measure { subject.toggle() }
                listOf(
                    "compositions",
                    "invalidatedScopes",
                    "recomposedScopes",
                    "stateReads",
                    "span.frame",
                ).forEach { name ->
                    assertTrue(metrics.getValue(name) > 0, "$name: $metrics")
                }
                assertEquals(0L, metrics.getValue("componentsAdded"))
                assertEquals(0L, metrics.getValue("listenersCreated"))
                assertNotEquals(before, control.host.digest())
                control.measure { subject.toggle() }
                assertEquals(before, control.host.digest())
            }
        }

    @Test
    fun `idle step has zero counts`() =
        runSwingTest {
            SwingExecutionControl(listOf(TreeCase()), coroutineContext).use { control ->
                control.compose()
                control.settle()
                val metrics = control.measure {}
                assertEquals(metrics.mapValues { 0L }, metrics)
            }
        }

    @Test
    fun `dispose removes the measured tree`() =
        runSwingTest {
            SwingExecutionControl(listOf(TreeCase()), coroutineContext).use { control ->
                control.compose()
                control.settle()
                val before = control.host.digest()
                val metrics = control.measure { control.disposeContent() }
                assertTrue(metrics.getValue("compositionGroups") < 0)
                assertTrue(metrics.getValue("compositionSlots") < 0)
                assertTrue(metrics.getValue("span.remove") > 0)
                val restored = control.measure { control.compose() }
                assertTrue(restored.getValue("span.insert") > 0)
                assertTrue(restored.getValue("componentsAdded") > 0, restored.toString())
                assertEquals(before, control.host.digest())
            }
        }

    @Test
    fun `frame drains work dispatched by an effect`() =
        runSwingTest {
            var completed = false
            val subject =
                object : ComposeTestCase {
                    @Composable
                    override fun Content() {
                        LaunchedEffect(Unit) {
                            withContext(Dispatchers.Swing) {
                                yield()
                                yield()
                                completed = true
                            }
                        }
                    }
                }
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                val metrics = control.measure { control.compose() }
                assertTrue(completed)
                assertEquals(1L, metrics.getValue("effectStarts"))
                assertTrue(metrics.getValue("eventsPosted") > 0, metrics.toString())
            }
        }

    @Test
    fun `a restarted effect counts as another start`() =
        runSwingTest {
            val subject = EffectCase()
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                val first = control.measure { control.compose() }
                assertEquals(1L, first.getValue("effectStarts"))
                val changed = control.measure { subject.toggle() }
                assertEquals(1L, changed.getValue("effectStarts"))
            }
        }
}
