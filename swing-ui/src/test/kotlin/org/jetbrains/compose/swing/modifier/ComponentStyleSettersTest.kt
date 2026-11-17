// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.layout.RawParentProtocol
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JLabel
import kotlin.test.Test

internal class ComponentStyleSettersTest {
    @Test
    fun multipleStyleSettingsAllApply() = runComposeSwingTest {
        setContent {
            Label(
                "X",
                style = {
                    foreground(Color.RED)
                    background(Color.BLUE)
                    opaque(true)
                },
            )
        }
        onNodeOfType<JLabel>().apply {
            assertProperty(Color.RED, "the foreground setter should apply") { foreground }
            assertProperty(Color.BLUE, "the background setter should apply") { background }
            assertProperty(true, "the opaque setter should apply") { isOpaque }
        }
    }

    @Test
    fun repeatedStyleSettingStillLastWins() = runComposeSwingTest {
        setContent {
            Label(
                "X",
                style = {
                    background(Color.RED)
                    background(Color.BLUE)
                },
            )
        }
        // Repeated setters keep last-wins semantics: the later background wins.
        onNodeOfType<JLabel>().assertProperty(Color.BLUE) { background }
    }

    @Test
    fun everyLayoutBuilderAppendsToTheChainWithoutRepeatingIt() {
        assertDeclaredChainCarriedOnce { layoutConstraint("region") }
        assertDeclaredChainCarriedOnce {
            slot(RawParentProtocol, "region", { _, _, _ -> {} })
        }
    }
}
