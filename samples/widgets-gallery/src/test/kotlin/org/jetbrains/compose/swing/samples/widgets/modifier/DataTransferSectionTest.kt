package org.jetbrains.compose.swing.samples.widgets.modifier

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test

class DataTransferSectionTest {
    @Test
    fun theDataTransferSectionMountsWithItsInitialEchoes() =
        runComposeSwingTest {
            openSection("Data transfer")

            onNodeWithText("Drop here: Nothing dropped yet", substring = true).assertExists()
            onNodeWithText("Copy me to the system clipboard", substring = true).assertExists()
        }
}
