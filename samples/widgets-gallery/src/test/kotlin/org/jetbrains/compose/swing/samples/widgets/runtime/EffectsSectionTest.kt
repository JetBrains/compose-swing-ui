package org.jetbrains.compose.swing.samples.widgets.runtime

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test

class EffectsSectionTest {
    @Test
    fun theEffectsSectionDrivesDisposeAndDerivedState() =
        runComposeSwingTest {
            openSection("Effects")

            onNodeWithText("Child has not left composition yet.", substring = true).assertExists()
            onNodeWithText("Keep child in composition").performClick()
            onNodeWithText("Child left composition.", substring = true).assertExists()

            onNodeWithText("Derived level: Medium", substring = true).assertExists()
        }
}
