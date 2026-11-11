// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.samples.widgets.menu

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
