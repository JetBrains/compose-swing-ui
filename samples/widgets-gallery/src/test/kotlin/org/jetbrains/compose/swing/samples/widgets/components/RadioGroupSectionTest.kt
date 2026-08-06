package org.jetbrains.compose.swing.samples.widgets.components

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JRadioButton
import kotlin.test.Test

class RadioGroupSectionTest {
    @Test
    fun theRadioGroupSelectsExactlyOneOption() =
        runComposeSwingTest {
            openSection("RadioGroup")

            onNodeWithText("Selected plan: Free", substring = true).assertExists()
            onNodeWithText("Selected size: Medium", substring = true).assertExists()

            onNodeWithText("Team").performClick()
            onNodeWithText("Selected plan: Team", substring = true).assertExists()
            onNodeWithText("Selected plan: Free", substring = true).assertDoesNotExist()
        }

    @Test
    fun theRadioGroupRendersOneButtonPerOption() =
        runComposeSwingTest {
            openSection("RadioGroup")

            onAllNodesOfType<JRadioButton>().assertCountEquals(VERTICAL_PLANS + HORIZONTAL_SIZES)
        }

    private companion object {
        const val VERTICAL_PLANS = 4
        const val HORIZONTAL_SIZES = 3
    }
}
