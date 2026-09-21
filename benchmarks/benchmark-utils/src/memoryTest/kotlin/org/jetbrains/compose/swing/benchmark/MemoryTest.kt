// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import org.jetbrains.compose.swing.animation.core.fixtures.swingUiAnimationCoreFixtures
import org.jetbrains.compose.swing.animation.fixtures.swingUiAnimationFixtures
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.fixtures.components.swingUiComponentsFixtures
import org.jetbrains.compose.swing.fixtures.swingUiCoreFixtures
import org.jetbrains.compose.swing.foundation.fixtures.swingUiFoundationFixtures
import org.jetbrains.compose.swing.runSwingTest
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestReporter
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import java.awt.HeadlessException
import java.lang.management.ManagementFactory
import java.util.TimeZone
import javax.swing.UIManager

/**
 * Measures retained heap and EDT allocations for every fixture of the library modules in one JVM.
 *
 * Every fixture yields one retained-heap test, and one allocation test for doing nothing, its first composition, each
 * of its alternatives and its disposal at each size. The allocation tests run the composed tree and the raw Swing tree
 * of the fixture, so their values compare directly. The `memoryTest` suite supplies JDK 21, headless Metal, UI scale 1
 * and time zone UTC.
 *
 * The `benchmark.fixtures` system property, a comma-separated list of fixture names such as `Label,Padding`, limits
 * the run to those fixtures.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("Swing benchmark instrumentation")
class MemoryTest {
    /** Publishes the pinned environment. */
    @BeforeAll
    fun publishEnvironment(reporter: TestReporter) {
        runSwingTest {
            reporter.publishEntry(
                mapOf(
                    "environment.jdk" to System.getProperty("java.runtime.version"),
                    "environment.jdkVendor" to System.getProperty("java.vendor"),
                    "environment.os" to System.getProperty("os.name"),
                    "environment.osVersion" to System.getProperty("os.version"),
                    "environment.architecture" to System.getProperty("os.arch"),
                    "environment.lookAndFeel" to UIManager.getLookAndFeel().javaClass.name,
                    "environment.uiScale" to System.getProperty("sun.java2d.uiScale", "default"),
                    "environment.jvmFlags" to ManagementFactory.getRuntimeMXBean().inputArguments.toString(),
                    "environment.timeZone" to TimeZone.getDefault().id,
                ),
            )
        }
    }

    /** Creates a retained-heap test per fixture and an allocation test per fixture step and size. */
    @TestFactory
    fun memoryTests(reporter: TestReporter): List<DynamicTest> =
        selectedFixtures().flatMap { fixture ->
            val retained =
                DynamicTest.dynamicTest("${fixture.name}:retained") {
                    needingDisplay { MemoryMeasurement(fixture, reporter).retained() }
                }
            val steps =
                buildList {
                    add(FixtureStep.NoChange)
                    add(FixtureStep.FirstComposition)
                    alternativeNames(fixture).forEachIndexed { index, name ->
                        add(FixtureStep.AlternativeChange(name, index))
                    }
                    add(FixtureStep.Dispose)
                }
            buildList {
                add(retained)
                steps.forEach { step ->
                    SIZES.forEach { size ->
                        add(
                            DynamicTest.dynamicTest("${fixture.name}:${step.name}:$size:allocated") {
                                needingDisplay { MemoryMeasurement(fixture, reporter).allocated(step, size) }
                            },
                        )
                    }
                }
            }
        }

    /** Aborts, rather than fails, a measurement that needs a display when the JVM has none. */
    private fun needingDisplay(measurement: () -> Unit) {
        try {
            measurement()
        } catch (failure: HeadlessException) {
            Assumptions.abort("Needs a display, and this JVM is headless: ${failure.message}")
        }
    }

    private fun selectedFixtures(): List<ComposableFixture<*, *>> {
        val property = "benchmark.fixtures"
        val all =
            buildList {
                addAll(swingUiCoreFixtures)
                addAll(swingUiComponentsFixtures)
                addAll(swingUiFoundationFixtures)
                addAll(swingUiAnimationCoreFixtures)
                addAll(swingUiAnimationFixtures)
            }
        val counts = all.groupingBy { it.name }.eachCount()
        val duplicates = counts.filterValues { it > 1 }.keys
        check(duplicates.isEmpty()) { "Fixture names must be unique, but ${duplicates.joinToString()} repeat." }
        val wanted =
            System
                .getProperty(property, "")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        if (wanted.isEmpty()) return all
        val unknown = wanted - all.map { it.name }.toSet()
        check(unknown.isEmpty()) {
            "$property names no fixture called ${unknown.joinToString()}. Use the name without `Fixture`."
        }
        return all.filter { it.name in wanted }
    }
}
