package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JSpinner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral coverage for clearing the value of a [Spinner] over a non-empty [items]: a spinner over
 * items always shows one of them, so an undeclared selection settles on the head rather than on whatever
 * the widget was last left holding.
 */
class SpinnerItemsClearedValueTest {
    @Test
    fun clearingTheDeclaredValueSettlesOnTheHeadAndReportsIt() = runComposeSwingTest {
        // Adopting the reported value into state is what a real controlled Spinner does; a declaration
        // left behind would reassert itself over the very settle this test is checking for.
        var value by mutableStateOf<String?>("Alan")
        val changes = mutableListOf<String>()
        setContent {
            Spinner(
                items = listOf("Ada", "Alan"),
                value = value,
                onValueChange = {
                    changes += it
                    value = it
                },
            )
        }
        awaitIdle()

        value = null
        awaitIdle()

        assertEquals("Ada", onNodeOfType<JSpinner>().fetch().value, "the widget should settle on the head item")
        assertEquals(listOf("Ada"), changes, "the head item, not the value the widget was left holding, is reported")
    }
}
