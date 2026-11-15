// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.fixtures.swingUiFixtures
import org.jetbrains.compose.swing.foundation.fixtures.swingUiFoundationFixtures
import org.jetbrains.compose.swing.runSwingTest
import java.awt.HeadlessException
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JPanel
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The fixture changes and arms, which the build passes to JMH as the values of its `fixtureChange` parameter.
 *
 * A parameter reads `fixture/alternative/arm`, such as `Label/text/rawSwing`, where the arm is [COMPOSE] or
 * [RAW_SWING]. JMH can only enumerate a parameter from literal values, so the build runs [main] to read them from the
 * fixture lists of all modules instead of keeping a second list. A fixture whose raw Swing side is a window has no
 * raw Swing parameters, because a window cannot be added to the host the timed changes settle in. Neither does a
 * fixture whose raw Swing side needs a toolkit the headless run lacks. A fixture whose composed side needs that
 * toolkit, as every one that declares a window does, has no parameters at all. Any other failure to build a composed
 * side is not caught, so it fails the enumeration.
 */
internal object BenchmarkParameters {
    const val COMPOSE = "compose"
    const val RAW_SWING = "rawSwing"

    val fixtures: List<ComposableFixture<*, *>> =
        buildList {
            addAll(swingUiFixtures)
            addAll(swingUiFoundationFixtures)
        }

    /** Every parameter, in fixture order. Run on the event dispatch thread, which window fixtures need. */
    val parameters: List<String>
        get() =
            fixtures.flatMap { fixture ->
                val arms =
                    when {
                        !buildsComposed(fixture) -> emptyList()
                        hostsRawSwing(fixture) -> listOf(COMPOSE, RAW_SWING)
                        else -> listOf(COMPOSE)
                    }
                alternativeNames(fixture).flatMap { alternative -> arms.map { "${fixture.name}/$alternative/$it" } }
            }

    private fun hostsRawSwing(fixture: ComposableFixture<*, *>): Boolean =
        try {
            FixtureSubject.of(fixture).mountRawSwing(JPanel())
        } catch (_: HeadlessException) {
            false
        }

    private fun buildsComposed(fixture: ComposableFixture<*, *>): Boolean =
        try {
            val subjects = listOf(FixtureSubject.of(fixture))
            SwingExecutionControl(subjects, EmptyCoroutineContext, fixture.name).use {
                FixtureTree.Composed.mount(it, subjects)
                it.settle()
            }
            true
        } catch (_: HeadlessException) {
            false
        }

    /** The fixture a parameter names. */
    fun fixtureOf(parameter: String): ComposableFixture<*, *> {
        val name = parameter.substringBefore('/')
        return fixtures.singleOrNull { it.name == name }
            ?: error("No fixture is named $name in $parameter. Pass one that the benchmarkParameters task lists.")
    }

    /** The index of the alternative a parameter names. */
    fun alternativeOf(parameter: String): Int {
        val name = parameter.substringAfter('/').substringBeforeLast('/')
        val index = alternativeNames(fixtureOf(parameter)).indexOf(name)
        check(
            index >= 0,
        ) { "No alternative is named $name in $parameter. Pass one that the benchmarkParameters task lists." }
        return index
    }

    /** The arm a parameter names. */
    fun armOf(parameter: String): FixtureTree =
        when (val name = parameter.substringAfterLast('/')) {
            COMPOSE -> FixtureTree.Composed
            RAW_SWING -> FixtureTree.RawSwing
            else -> error("No arm is named $name in $parameter. Use $COMPOSE or $RAW_SWING.")
        }
}

/** Writes the parameters, one per line, to the file named by the only argument. */
fun main(arguments: Array<String>) {
    require(arguments.size == 1) { "Pass the path of the file to write the parameters to." }
    var parameters = emptyList<String>()
    runSwingTest { parameters = BenchmarkParameters.parameters }
    Files.newBufferedWriter(Path.of(arguments.single())).use { writer ->
        parameters.forEach { writer.appendLine(it) }
    }
}
