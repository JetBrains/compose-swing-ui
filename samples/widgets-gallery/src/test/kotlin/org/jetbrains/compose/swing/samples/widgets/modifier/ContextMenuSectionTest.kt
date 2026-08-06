package org.jetbrains.compose.swing.samples.widgets.modifier

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test

class ContextMenuSectionTest {
    @Test
    fun theContextMenuSectionMountsWithItsInitialEchoes() =
        runComposeSwingTest {
            openSection("Context menu")

            onNodeWithText("Last action: none", substring = true).assertExists()
            onNodeWithText("Word wrap: on, line numbers: off", substring = true).assertExists()
        }
}
