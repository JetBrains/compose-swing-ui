// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.testutils.ComposeTestCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import org.jetbrains.compose.swing.reportedUncaught
import org.jetbrains.compose.swing.runSwingTest
import org.junit.jupiter.api.Test
import java.awt.EventQueue
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

/** Tests how [SwingExecutionControl] reports a subject that fails, never settles, or keeps posting events. */
class SwingExecutionControlFailureTest {
    @Test
    fun `failed initial composition makes frame and settle report the stopped recomposer`() =
        runSwingTest(timeout = 5.seconds) {
            val failure = IllegalArgumentException("Subject composition failed")
            val subject =
                object : ComposeTestCase {
                    @Composable
                    override fun Content(): Unit = throw failure
                }
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                assertSame(failure, assertFailsWith<IllegalArgumentException> { control.compose() })
                val frameFailure = assertFailsWith<IllegalStateException> { control.doFrame() }
                assertSame(failure, frameFailure.cause)
                val settleFailure = assertFailsWith<IllegalStateException> { control.settle() }
                assertSame(failure, settleFailure.cause)
            }
        }

    @Test
    fun `composition failing during a frame exits the probe loop`() =
        runSwingTest(timeout = 5.seconds) {
            var fail by mutableStateOf(false)
            val failure =
                IllegalArgumentException("Recomposition failed", IllegalArgumentException("Recomposition failed"))
            val subject =
                object : ComposeTestCase {
                    @Composable
                    override fun Content() {
                        if (fail) throw failure
                    }
                }
            SwingExecutionControl(listOf(subject), coroutineContext).use { control ->
                control.compose()
                control.settle()
                val reported =
                    reportedUncaught {
                        fail = true
                        assertFailsWith<IllegalStateException> { control.doFrame() }
                    }
                assertIs<IllegalArgumentException>(reported.single())
            }
        }

    @Test
    fun `timeout stops a subject that awaits frames forever and names it`() {
        var message: String? = null
        assertFailsWith<AssertionError> {
            runSwingTest(timeout = 1.seconds) {
                val calling = coroutineContext[Job]
                val subject =
                    object : ComposeTestCase {
                        @Composable
                        override fun Content() {
                            LaunchedEffect(Unit) {
                                while (isActive) {
                                    withFrameNanos {
                                        if (calling?.isCancelled == true) {
                                            throw AssertionError("settle kept driving frames after cancellation")
                                        }
                                    }
                                }
                            }
                        }
                    }
                SwingExecutionControl(listOf(subject), coroutineContext, "EndlessFrames").use { control ->
                    control.compose()
                    try {
                        control.settle()
                    } catch (failure: IllegalStateException) {
                        message = failure.message
                        throw failure
                    }
                }
            }
        }
        assertContains(checkNotNull(message), "EndlessFrames")
    }

    @Test
    fun `timeout stops settle on a subject whose event reposts itself at normal priority`() {
        var posting = true
        assertFailsWith<AssertionError> {
            runSwingTest(timeout = 2.seconds) {
                val chain =
                    object : Runnable {
                        override fun run() {
                            if (posting) EventQueue.invokeLater(this)
                        }
                    }
                SwingExecutionControl(listOf(TreeCase()), coroutineContext, "Reposting").use { control ->
                    control.compose()
                    control.settle()
                    try {
                        control.measure { EventQueue.invokeLater(chain) }
                    } finally {
                        posting = false
                    }
                }
            }
        }
    }

    @Test
    fun `closing a control stops draining when the calling coroutine is cancelled`() {
        var posting = false
        assertFailsWith<AssertionError> {
            runSwingTest(timeout = 2.seconds) {
                val chain =
                    object : Runnable {
                        override fun run() {
                            if (posting) EventQueue.invokeLater(this)
                        }
                    }
                val subject =
                    object : ComposeTestCase {
                        @Composable
                        override fun Content() {
                            DisposableEffect(Unit) {
                                onDispose {
                                    posting = true
                                    EventQueue.invokeLater(chain)
                                }
                            }
                        }
                    }
                val control = SwingExecutionControl(listOf(subject), coroutineContext, "EndlessEvents")
                try {
                    control.compose()
                    control.settle()
                    control.close()
                } finally {
                    posting = false
                }
            }
        }
    }

    @Test
    fun `closing a control drains a finite chain of events deeper than a thousand`() =
        runSwingTest(timeout = 20.seconds) {
            var remaining = 1500
            val chain =
                object : Runnable {
                    override fun run() {
                        if (--remaining > 0) EventQueue.invokeLater(this)
                    }
                }
            val subject =
                object : ComposeTestCase {
                    @Composable
                    override fun Content() {
                        DisposableEffect(Unit) {
                            onDispose { EventQueue.invokeLater(chain) }
                        }
                    }
                }
            val control = SwingExecutionControl(listOf(subject), coroutineContext, "DeepChain")
            control.compose()
            control.settle()
            control.close()
            assertEquals(0, remaining)
        }
}
