// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
