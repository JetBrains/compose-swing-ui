// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests that [countTests] ignores requests from components outside both hosts, and counts those of a child that
 * left a host in the change that made them.
 */
class FixtureCountChecksTest {
    @TestFactory
    fun aComponentOutsideBothHostsIsNotCounted() = OutsideRepaintFixture.countTests()

    @Test
    fun aChildRemovedInTheChangeThatRepaintedItIsCounted() =
        assertCounted(RemovedChildFixture { it.repaint() }, "repaints")

    @Test
    fun aChildRemovedInTheChangeThatRevalidatedItIsCounted() =
        assertCounted(RemovedChildFixture { it.revalidate() }, "revalidations")

    @Test
    fun aRequestFromAReplacementWindowIsCounted() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        assertCounted(ReplacedWindowFixture, "repaints")
    }

    @Test
    fun aPinnedRepaintCeilingOverridesRawSwing() =
        assertPinOverridesRaw("repaints", RemovedChildFixture { it.repaint() })

    @Test
    fun aPinnedRevalidationCeilingOverridesRawSwing() =
        assertPinOverridesRaw("revalidations", RemovedChildFixture { it.revalidate() })

    @Test
    fun aPinnedLayoutCeilingOverridesRawSwing() =
        assertPinOverridesRaw("host layout passes", RemovedChildFixture { it.parent.parent.doLayout() })

    /** The fixture fails on [count] unpinned, passes with its ceiling set high, and fails naming it when set to 0. */
    private fun assertPinOverridesRaw(
        count: String,
        fixture: ComposableFixture<*, *>,
    ) {
        fun tests(ceiling: Int?) = fixture.countTests(
            maxRepaints = ceiling.takeIf { count == "repaints" },
            maxRevalidations = ceiling.takeIf { count == "revalidations" },
            maxLayouts = ceiling.takeIf { count == "host layout passes" },
        )
        assertCounted(fixture, count)
        tests(1000).forEach { test -> test.executable.execute() }
        val tests = tests(0)
        assertTrue(tests.isNotEmpty())
        tests.forEach { test ->
            val failure = assertFailsWith<AssertionError> { test.executable.execute() }
            assertContains(checkNotNull(failure.message), "$count; the pinned maximum is 0")
        }
    }

    private fun assertCounted(
        fixture: ComposableFixture<*, *>,
        request: String,
    ) {
        val tests = fixture.countTests()
        assertTrue(tests.isNotEmpty())
        tests.forEach { test ->
            val failure = assertFailsWith<AssertionError> { test.executable.execute() }
            assertContains(checkNotNull(failure.message), "$request; raw Swing asked for")
        }
    }
}

/** Composes a panel, and on every change adds a child to it, makes [request] of the child, and removes it. */
private class RemovedChildFixture(
    private val request: (JLabel) -> Unit,
) : ComposableFixture<StepParameters, JPanel>(StepParameters(0)) {
    private var panel: JPanel? = null

    override val alternatives: List<StepParameters> = listOf(initial.copy(step = 1))

    @Composable
    override fun Content(parameters: StepParameters) {
        SwingNode(factory = { JPanel().also { panel = it } })
        SideEffect {
            if (parameters.step > 0) {
                val child = JLabel()
                checkNotNull(panel).add(child)
                request(child)
                checkNotNull(panel).remove(child)
            }
        }
    }

    override fun createRawSwing(): JPanel = JPanel()

    override fun updateRawSwing(
        component: JPanel,
        parameters: StepParameters,
    ) {
        if (parameters.step > 0) {
            val child = JLabel()
            component.add(child)
            component.remove(child)
        }
    }
}

/** Composes a window that a change replaces and whose root pane asks for a repaint when it is composed. */
private object ReplacedWindowFixture : ComposableFixture<StepParameters, JFrame>(StepParameters(0)) {
    override val alternatives: List<StepParameters> = listOf(initial.copy(step = 1))

    @Composable
    override fun Content(parameters: StepParameters) {
        Window(onCloseRequest = {}, undecorated = parameters.step > 0) {
            SideEffect {
                if (parameters.step > 0) rootPane.repaint()
            }
        }
    }

    override fun createRawSwing(): JFrame = JFrame()

    override fun updateRawSwing(
        component: JFrame,
        parameters: StepParameters,
    ): Unit = Unit
}

/** The parameters of [OutsideRepaintFixture], which differ by a step number. */
private data class StepParameters(
    val step: Int,
)

/** Composes nothing, and asks for a repaint and a revalidation of a component outside both hosts on every change. */
private object OutsideRepaintFixture : ComposableFixture<StepParameters, JPanel>(StepParameters(0)) {
    private val outside = JLabel().also { JPanel().add(it) }

    override val alternatives: List<StepParameters> = listOf(initial.copy(step = 1))

    @Composable
    override fun Content(parameters: StepParameters) {
        val step = parameters.step
        SideEffect {
            outside.putClientProperty("step", step)
            outside.repaint()
            outside.revalidate()
        }
    }

    override fun createRawSwing(): JPanel = JPanel()

    override fun updateRawSwing(
        component: JPanel,
        parameters: StepParameters,
    ): Unit = Unit
}
