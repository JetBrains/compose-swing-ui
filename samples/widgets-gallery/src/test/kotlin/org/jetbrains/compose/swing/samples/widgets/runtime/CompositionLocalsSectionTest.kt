package org.jetbrains.compose.swing.samples.widgets.runtime

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test

class CompositionLocalsSectionTest {
    @Test
    fun theCompositionLocalSectionMountsItsAccentedLabels() =
        runComposeSwingTest {
            openSection("Composition locals")

            onNodeWithText("Middle-level accented label", substring = true).assertExists()
            onNodeWithText("Inner-level accented label", substring = true).assertExists()
        }
}
