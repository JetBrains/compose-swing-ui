// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import org.jetbrains.compose.swing.runSwingTest
import java.awt.image.BufferedImage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Tests the benchmark parameters and that every benchmark method runs on every arm. */
class BenchmarkParametersTest {
    private val arms = listOf(BenchmarkParameters.COMPOSE, BenchmarkParameters.RAW_SWING)

    @Test
    fun `parameters are unique and resolve to fixtures and alternatives`() =
        runSwingTest {
            val parameters = BenchmarkParameters.parameters
            assertTrue(parameters.isNotEmpty())
            assertEquals(parameters.size, parameters.distinct().size)
            parameters.forEach { parameter ->
                BenchmarkParameters.fixtureOf(parameter)
                assertTrue(BenchmarkParameters.alternativeOf(parameter) >= 0)
                BenchmarkParameters.armOf(parameter)
            }
        }

    @Test
    fun `a fixture with a raw Swing tree lists both arms`() =
        runSwingTest {
            val parameters = BenchmarkParameters.parameters
            assertTrue("Label/text/compose" in parameters)
            assertTrue("Label/text/rawSwing" in parameters)
        }

    @Test
    fun `the parameter file lists every parameter one per line`() {
        val file = Files.createTempFile("benchmark-ids", ".txt")
        try {
            main(arrayOf(file.toString()))
            var expected = emptyList<String>()
            runSwingTest { expected = BenchmarkParameters.parameters }
            assertEquals(expected, Files.readAllLines(file))
        } finally {
            Files.delete(file)
        }
    }

    @Test
    fun `every toggle method runs on every arm and puts the tree where a change alone puts it`() =
        arms.forEach { arm ->
            val parameter = "Label/text/$arm"
            val (initial, changed) =
                referenceOf(parameter) { control, subjects ->
                    control.settle()
                    val initial = control.host.digest()
                    BenchmarkParameters.armOf(parameter).change(
                        subjects.first(),
                        BenchmarkParameters.alternativeOf(parameter),
                    )
                    control.settle()
                    initial to control.host.digest()
                }
            assertNotEquals(initial, changed, arm)
            val toggles: List<Triple<(ComposeBenchmark, MountedSubjectsState) -> Unit, Boolean, Boolean>> =
                listOf(
                    Triple({ benchmark, state -> benchmark.toggleRecompose(state) }, true, false),
                    Triple({ benchmark, state -> benchmark.toggleLayout(state) }, false, false),
                    Triple({ benchmark, state -> benchmark.toggleDraw(state) }, false, true),
                )
            toggles.forEach { (toggle, pendingLayout, paints) ->
                assertToggles(parameter, toggle, pendingLayout, paints, listOf(changed, initial, changed))
            }
        }

    @Test
    fun `every first frame method runs on every arm and stops at its phase`() =
        arms.forEach { arm ->
            val parameter = "Label/text/$arm"
            val settled =
                referenceOf(parameter) { control, _ ->
                    control.settle()
                    control.host.digest()
                }
            val firstFrames: List<Triple<(ComposeBenchmark, FirstFrameState) -> Unit, Boolean, Boolean>> =
                listOf(
                    Triple({ benchmark, state -> benchmark.firstCompose(state) }, true, false),
                    Triple({ benchmark, state -> benchmark.firstLayout(state) }, false, false),
                    Triple({ benchmark, state -> benchmark.firstDraw(state) }, false, true),
                )
            firstFrames.forEach { (firstFrame, pendingLayout, paints) ->
                val state =
                    FirstFrameState().apply {
                        fixtureChange = parameter
                    }
                state.prepareEnvironment()
                state.createSubjects()
                try {
                    firstFrame(ComposeBenchmark(), state)
                    var hasPending = false
                    var isPainted = false
                    var digest = 0L
                    runSwingTest {
                        hasPending = state.control.host.hasPendingLayout
                        isPainted = painted(state.control.host.image)
                        digest = state.control.host.digest()
                    }
                    assertEquals(pendingLayout, hasPending, arm)
                    assertEquals(paints, isPainted, arm)
                    if (pendingLayout) assertNotEquals(settled, digest, arm) else assertEquals(settled, digest, arm)
                } finally {
                    state.closeSubjects()
                }
            }
        }

    @Test
    fun `settling a phase leaves the later phases pending on every arm`() =
        arms.forEach { arm ->
            val parameter = "Label/text/$arm"
            runSwingTest {
                val subjects = listOf(FixtureSubject.of(BenchmarkParameters.fixtureOf(parameter)))
                SwingExecutionControl(subjects, coroutineContext).use { control ->
                    assertTrue(BenchmarkParameters.armOf(parameter).mount(control, subjects))
                    control.recompose()
                    assertTrue(control.host.hasPendingLayout, arm)
                    val beforeLayout = control.host.digest()
                    do {
                        control.recompose(control.host::layout)
                        control.host.layout()
                    } while (control.hasPendingChanges() || control.host.hasPendingLayout)
                    assertNotEquals(beforeLayout, control.host.digest(), arm)
                    assertFalse(painted(control.host.image), arm)
                    control.host.drawToBitmap()
                    assertTrue(painted(control.host.image), arm)
                }
            }
        }

    private fun assertToggles(
        parameter: String,
        toggle: (ComposeBenchmark, MountedSubjectsState) -> Unit,
        pendingLayout: Boolean,
        paints: Boolean,
        expectedDigests: List<Long>,
    ) {
        val state =
            MountedSubjectsState().apply {
                fixtureChange = parameter
            }
        state.prepareEnvironment()
        try {
            expectedDigests.forEach { expected ->
                var before = 0L
                runSwingTest {
                    before = state.control.host.digest()
                    clear(state.control.host.image)
                }
                toggle(ComposeBenchmark(), state)
                var hasPending = false
                var isPainted = false
                var digest = 0L
                runSwingTest {
                    hasPending = state.control.host.hasPendingLayout
                    isPainted = painted(state.control.host.image)
                    digest = state.control.host.digest()
                }
                assertEquals(pendingLayout, hasPending, parameter)
                assertEquals(paints, isPainted, parameter)
                if (pendingLayout) {
                    assertNotEquals(before, digest, parameter)
                } else {
                    assertEquals(expected, digest, parameter)
                }
                state.settleChange()
                var settledDigest = 0L
                runSwingTest { settledDigest = state.control.host.digest() }
                assertEquals(expected, settledDigest, parameter)
            }
        } finally {
            state.closeControl()
        }
    }

    private fun <T> referenceOf(
        parameter: String,
        steps: (SwingExecutionControl, List<FixtureSubject>) -> T,
    ): T {
        val results = mutableListOf<T>()
        runSwingTest {
            val subjects = listOf(FixtureSubject.of(BenchmarkParameters.fixtureOf(parameter)))
            SwingExecutionControl(subjects, coroutineContext).use { control ->
                assertTrue(BenchmarkParameters.armOf(parameter).mount(control, subjects))
                results += steps(control, subjects)
            }
        }
        return results.single()
    }

    private fun clear(image: BufferedImage) {
        (0 until image.height).forEach { y -> (0 until image.width).forEach { x -> image.setRGB(x, y, 0) } }
    }

    private fun painted(image: BufferedImage): Boolean =
        (0 until image.height).any { y -> (0 until image.width).any { x -> image.getRGB(x, y) != 0 } }
}
