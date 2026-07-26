package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.text.NumberFormat
import javax.swing.JProgressBar
import javax.swing.SwingConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral tests for the orientation and the painted text of [ProgressBar]. They assert what an
 * observer of the live `JProgressBar` sees across the three text states the two parameters express:
 * no text, the bar's own completion percentage, and a declared string.
 */
class ProgressBarTextTest {
    // The bar's own text for a quarter-complete range. A percentage is written differently from one
    // locale to the next, so the expectation is formatted the same way rather than spelled "25%".
    private val quarterComplete: String get() = NumberFormat.getPercentInstance().format(0.25)

    @Test
    fun orientationMapsThrough() = runComposeSwingTest {
        var orientation by mutableIntStateOf(SwingConstants.HORIZONTAL)
        setContent { ProgressBar(value = 0, orientation = orientation) }

        val bar = onNodeOfType<JProgressBar>().fetch()
        assertEquals(SwingConstants.HORIZONTAL, bar.orientation, "the bar should start horizontal")

        orientation = SwingConstants.VERTICAL
        awaitIdle()
        assertEquals(SwingConstants.VERTICAL, bar.orientation, "the orientation should map through to vertical")
    }

    @Test
    fun aPaintedNullStringShowsTheCompletionPercentage() = runComposeSwingTest {
        setContent { ProgressBar(min = 0, max = 200, value = 50, stringPainted = true) }

        val bar = onNodeOfType<JProgressBar>().fetch()
        assertTrue(bar.isStringPainted, "the bar should paint text over itself")
        assertEquals(quarterComplete, bar.string, "with no declared string the bar shows its completion")
    }

    @Test
    fun aDeclaredStringOverridesThePercentage() = runComposeSwingTest {
        setContent { ProgressBar(min = 0, max = 200, value = 50, stringPainted = true, string = "Copying") }

        val bar = onNodeOfType<JProgressBar>().fetch()
        assertTrue(bar.isStringPainted, "the bar should paint text over itself")
        assertEquals("Copying", bar.string, "a declared string replaces the completion percentage")
    }

    @Test
    fun clearingTheStringFallsBackToThePercentage() = runComposeSwingTest {
        var string by mutableStateOf<String?>("Copying")
        setContent { ProgressBar(min = 0, max = 200, value = 50, stringPainted = true, string = string) }

        val bar = onNodeOfType<JProgressBar>().fetch()
        assertEquals("Copying", bar.string, "the bar should start with the declared string")

        string = null
        awaitIdle()
        assertEquals(quarterComplete, bar.string, "clearing the string returns the bar to its completion")
    }
}
