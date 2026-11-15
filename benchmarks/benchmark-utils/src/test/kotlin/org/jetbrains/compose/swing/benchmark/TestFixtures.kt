// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import javax.swing.JLabel
import javax.swing.JPanel

/** The parameters of the fixtures that exist to test the harness. */
internal data class StepParameters(
    val step: Int,
)

/** A fixture that composes nothing, so that the harness's own allocation is all a measurement sees. */
internal object EmptyFixture : ComposableFixture<StepParameters, JPanel>(StepParameters(0)) {
    override val alternatives: List<StepParameters> = listOf(initial.copy(step = 1))

    @Composable
    override fun Content(parameters: StepParameters): Unit = Unit

    override fun createRawSwing(): JPanel = JPanel()

    override fun updateRawSwing(
        component: JPanel,
        parameters: StepParameters,
    ): Unit = Unit
}

/** A fixture that allocates a block of memory on its first composition and keeps it. */
internal object LazyInitFixture : ComposableFixture<StepParameters, JPanel>(StepParameters(0)) {
    const val LAZY_BYTES = 1 shl 16

    override val alternatives: List<StepParameters> = listOf(initial.copy(step = 1))

    @Composable
    override fun Content(parameters: StepParameters) {
        remember { ByteArray(LAZY_BYTES) }
    }

    override fun createRawSwing(): JPanel = JPanel()

    override fun updateRawSwing(
        component: JPanel,
        parameters: StepParameters,
    ): Unit = Unit
}

/** A fixture whose alternative reports an uncaught failure on the event thread. */
internal object UncaughtFailureFixture : ComposableFixture<StepParameters, JPanel>(StepParameters(0)) {
    override val alternatives: List<StepParameters> = listOf(initial.copy(step = 1))

    @Composable
    override fun Content(parameters: StepParameters) {
        SideEffect { if (parameters.step != 0) reportUncaught() }
    }

    override fun createRawSwing(): JPanel = JPanel()

    override fun updateRawSwing(
        component: JPanel,
        parameters: StepParameters,
    ) {
        if (parameters.step != 0) reportUncaught()
    }

    /** Reports an uncaught failure the way a failing event handler does. */
    fun reportUncaught() {
        val thread = Thread.currentThread()
        thread.uncaughtExceptionHandler.uncaughtException(thread, IllegalArgumentException("EDT failure"))
    }
}

/** A fixture that reports an uncaught failure on the event thread while it composes. */
internal object UncaughtCompositionFixture : ComposableFixture<StepParameters, JPanel>(StepParameters(0)) {
    /** How many more compositions report a failure, which one is enough for a measurement to fail. */
    var failuresLeft = 1

    override val alternatives: List<StepParameters> = listOf(initial.copy(step = 1))

    @Composable
    override fun Content(parameters: StepParameters) {
        SideEffect {
            if (failuresLeft > 0) {
                failuresLeft--
                UncaughtFailureFixture.reportUncaught()
            }
        }
    }

    override fun createRawSwing(): JPanel = JPanel()

    override fun updateRawSwing(
        component: JPanel,
        parameters: StepParameters,
    ): Unit = Unit
}

/** A fixture named `Tree`, whose raw Swing is a panel holding one label. */
internal object TreeFixture : ComposableFixture<StepParameters, JPanel>(StepParameters(0)) {
    override val alternatives: List<StepParameters> = listOf(initial.copy(step = 1))

    @Composable
    override fun Content(parameters: StepParameters): Unit = Unit

    override fun createRawSwing(): JPanel = JPanel().apply { add(JLabel()) }

    override fun updateRawSwing(
        component: JPanel,
        parameters: StepParameters,
    ) {
        component.components.filterIsInstance<JLabel>().forEach { it.text = parameters.step.toString() }
    }
}

/** A fixture whose raw Swing panels are a class of their own, so that a census can find them by name. */
internal class SwingCensusFixture :
    ComposableFixture<StepParameters, SwingCensusFixture.CensusPanel>(StepParameters(0)) {
    override val alternatives: List<StepParameters> = listOf(initial.copy(step = 1))

    @Composable
    override fun Content(parameters: StepParameters): Unit = Unit

    override fun createRawSwing(): CensusPanel = CensusPanel()

    override fun updateRawSwing(
        component: CensusPanel,
        parameters: StepParameters,
    ): Unit = Unit

    /** The panel the census counts. */
    class CensusPanel : JPanel()
}
