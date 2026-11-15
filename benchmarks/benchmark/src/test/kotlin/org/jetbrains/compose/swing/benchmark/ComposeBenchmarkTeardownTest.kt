// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.Container
import java.awt.Graphics
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Tests that the teardown of a first frame invocation reports the failures of the phases the method left undone. */
class ComposeBenchmarkTeardownTest {
    private val failure = IllegalArgumentException("Phase failed")

    @Test
    fun `teardown after firstCompose reports a layout failure`() {
        val panel =
            object : JPanel() {
                override fun doLayout() = throw failure
            }
        assertTeardownFails(panel) { benchmark, state -> benchmark.firstCompose(state) }
    }

    @Test
    fun `teardown after firstLayout reports a paint failure`() {
        val panel =
            object : JPanel() {
                override fun paintComponent(graphics: Graphics) = throw failure
            }
        assertTeardownFails(panel) { benchmark, state -> benchmark.firstLayout(state) }
    }

    private fun assertTeardownFails(
        panel: JPanel,
        firstFrame: (ComposeBenchmark, FirstFrameState) -> Unit,
    ) {
        val subject =
            object : FixtureSubject {
                @Composable
                override fun Content() {
                    SwingNode<JPanel>(factory = { panel })
                }

                override fun toggle(alternative: Int) = Unit

                override fun mountRawSwing(parent: Container): Boolean = false

                override fun toggleRawSwing(alternative: Int) = Unit
            }
        val state =
            FirstFrameState().apply {
                fixtureChange = "Label/text/compose"
                subjectOf = { subject }
            }
        state.prepareEnvironment()
        state.createSubjects()
        firstFrame(ComposeBenchmark(), state)
        assertEquals(failure.message, assertFailsWith<IllegalArgumentException> { state.closeSubjects() }.message)
    }
}
