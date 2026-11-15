// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import org.jetbrains.compose.swing.fixtures.components.LabelFixture
import org.jetbrains.compose.swing.runSwingTest
import org.junit.jupiter.api.Test
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Tests how the memory measurement samples steps and reports failures. */
class MemoryProtocolTest {
    @Test
    fun `null allocation floor stays small for one and one thousand empty subjects`() {
        val floors =
            listOf(1, 1000).map { size ->
                val entries = mutableListOf<Map<String, String>>()
                MemoryMeasurement(EmptyFixture) { entries.add(it) }.allocated(FixtureStep.NoChange, size)
                entries.single().getValue("Empty/noChange/$size/allocatedBytes").toDouble()
            }
        assertTrue(floors.all { it < 1024 }, "$floors")
    }

    @Test
    fun `an uncaught change failure fails the allocation measurement`() {
        assertFailsWith<IllegalStateException> {
            MemoryMeasurement(UncaughtFailureFixture) {}
                .allocated(FixtureStep.AlternativeChange("step", 0), 1)
        }
    }

    @Test
    fun `Swing census counts every mounted fixture`() =
        runSwingTest {
            val fixture = SwingCensusFixture()
            val census = mutableMapOf<String, LiveHeap.ClassUsage>()
            assertTrue(
                MemoryMeasurement(fixture) {}.mountSwing(3, mutableMapOf(), census, coroutineContext),
            )
            assertTrue(census.getValue(SwingCensusFixture.CensusPanel::class.java.name).instances >= 3)
        }

    @Test
    fun `uncaught failures during fixture creation fail retained measurement`() {
        UncaughtCompositionFixture.failuresLeft = 1
        assertFailsWith<IllegalStateException> {
            MemoryMeasurement(UncaughtCompositionFixture) {}.retained()
        }
    }

    @Test
    fun `each step starts every sampled run from the state it measures`() {
        val steps =
            listOf(
                FixtureStep.NoChange,
                FixtureStep.FirstComposition,
                FixtureStep.AlternativeChange("text", 0),
                FixtureStep.Dispose,
            )
        FixtureTree.entries.forEach { arm ->
            steps.forEach { step ->
                runSwingTest {
                    val unmounted = referenceDigest(coroutineContext) { _, _ -> }
                    val initial =
                        referenceDigest(coroutineContext) { control, subjects ->
                            arm.mount(control, subjects)
                        }
                    val alternative =
                        referenceDigest(coroutineContext) { control, subjects ->
                            arm.mount(control, subjects)
                            control.settle()
                            arm.change(subjects.first(), 0)
                        }
                    val disposed =
                        referenceDigest(coroutineContext) { control, subjects ->
                            arm.mount(control, subjects)
                            control.settle()
                            arm.dispose(control)
                        }
                    assertNotEquals(initial, alternative, "$arm ${step.name}: the alternative must differ")
                    assertNotEquals(initial, unmounted, "$arm ${step.name}: mounting must change the tree")
                    assertNotEquals(initial, disposed, "$arm ${step.name}: disposing must change the tree")
                    val (starts, ends) = sampledDigests(arm, step, coroutineContext)
                    val expected =
                        when (step) {
                            FixtureStep.NoChange -> {
                                List(3) { initial } to List(3) { initial }
                            }

                            FixtureStep.FirstComposition -> {
                                List(3) { unmounted } to List(3) { initial }
                            }

                            is FixtureStep.AlternativeChange -> {
                                listOf(initial, alternative, initial) to listOf(alternative, initial, alternative)
                            }

                            FixtureStep.Dispose -> {
                                List(3) { initial } to List(3) { disposed }
                            }
                        }
                    assertEquals(expected.first, starts, "$arm ${step.name} starts")
                    assertEquals(expected.second, ends, "$arm ${step.name} ends")
                }
            }
        }
    }

    /** The digests before and after the action of each of three runs that [step] samples on [arm]. */
    private fun sampledDigests(
        arm: FixtureTree,
        step: FixtureStep,
        coroutineContext: CoroutineContext,
    ): Pair<List<Long>, List<Long>> {
        val starts = mutableListOf<Long>()
        val ends = mutableListOf<Long>()
        step.sample(
            arm = arm,
            runs = 3,
            create = {
                val subjects = listOf(FixtureSubject.of(LabelFixture))
                SwingExecutionControl(subjects, coroutineContext) to subjects
            },
        ) { control, _, action ->
            starts += control.host.digest()
            action()
            control.settle()
            ends += control.host.digest()
        }
        return starts to ends
    }

    /** The digest of a fresh one-label host after [prepare] ran and the control settled. */
    private fun referenceDigest(
        coroutineContext: CoroutineContext,
        prepare: (SwingExecutionControl, List<FixtureSubject>) -> Unit,
    ): Long {
        val subjects = listOf(FixtureSubject.of(LabelFixture))
        return SwingExecutionControl(subjects, coroutineContext).use { control ->
            prepare(control, subjects)
            control.settle()
            control.host.digest()
        }
    }

    @Test
    fun `first composition allocation covers the block the fixture keeps`() {
        val entries = mutableListOf<Map<String, String>>()
        MemoryMeasurement(LazyInitFixture) { entries.add(it) }.allocated(FixtureStep.FirstComposition, 1)
        assertTrue(
            entries.single().getValue("LazyInit/firstComposition/1/allocatedBytes").toDouble() >=
                LazyInitFixture.LAZY_BYTES,
        )
    }
}
