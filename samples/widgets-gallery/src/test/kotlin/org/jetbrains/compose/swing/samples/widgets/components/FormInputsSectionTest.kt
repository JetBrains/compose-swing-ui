package org.jetbrains.compose.swing.samples.widgets.components

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test

class FormInputsSectionTest {
    @Test
    fun switchingToTheFormInputsSectionDrivesASpinnerEcho() =
        runComposeSwingTest {
            openSection("Form inputs")

            onNodeWithText("Count is 3", substring = true).assertExists()

            onNodeWithText("Bold is off", substring = true).assertExists()
            onNodeWithText("Bold").performClick()
            onNodeWithText("Bold is on", substring = true).assertExists()
        }
}
