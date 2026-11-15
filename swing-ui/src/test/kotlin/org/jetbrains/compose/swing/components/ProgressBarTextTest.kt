// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.countTests
import org.jetbrains.compose.swing.fixtures.components.ProgressBarFixture
import org.jetbrains.compose.swing.fixtures.components.ProgressBarModelFixture
import org.jetbrains.compose.swing.rawSwingTests
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.TestFactory
import java.text.NumberFormat
import javax.swing.JProgressBar
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests for the orientation and the painted text of [ProgressBar]. They assert what an
 * observer of the live `JProgressBar` sees across the three text states the two parameters express:
 * no text, the bar's own completion percentage, and a declared string.
 */
class ProgressBarTextTest {
    // The bar's own text for a quarter-complete range. A percentage is written differently from one
    // locale to the next, so the expectation is formatted the same way rather than spelled "25%".
    private val quarterComplete: String get() = NumberFormat.getPercentInstance().format(0.25)

    @TestFactory
    fun rawSwing() = ProgressBarFixture.rawSwingTests()

    @Test
    fun aPaintedNullStringShowsTheCompletionPercentage() = runComposeSwingTest {
        setContent { ProgressBar(value = 50, min = 0, max = 200, stringPainted = true) }

        val bar = onNodeOfType<JProgressBar>().fetch()
        assertEquals(quarterComplete, bar.string, "with no declared string the bar shows its completion")
    }

    @Test
    fun aDeclaredStringOverridesThePercentage() = runComposeSwingTest {
        setContent { ProgressBar(value = 50, min = 0, max = 200, stringPainted = true, string = "Copying") }

        val bar = onNodeOfType<JProgressBar>().fetch()
        assertEquals("Copying", bar.string, "a declared string replaces the completion percentage")
    }

    @Test
    fun clearingTheStringFallsBackToThePercentage() = runComposeSwingTest {
        var string by mutableStateOf<String?>("Copying")
        setContent { ProgressBar(value = 50, min = 0, max = 200, stringPainted = true, string = string) }

        val bar = onNodeOfType<JProgressBar>().fetch()
        assertEquals("Copying", bar.string, "the bar should start with the declared string")

        string = null
        awaitIdle()
        assertEquals(quarterComplete, bar.string, "clearing the string returns the bar to its completion")
    }

    @TestFactory
    fun progressBarModelRawSwing() = ProgressBarModelFixture.rawSwingTests()

    @TestFactory
    fun progressBarModelCounts() = ProgressBarModelFixture.countTests()

    @TestFactory
    fun counts() = ProgressBarFixture.countTests()
}
