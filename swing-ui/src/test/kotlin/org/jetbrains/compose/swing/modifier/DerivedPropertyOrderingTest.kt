// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import org.jetbrains.compose.swing.style.font
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Font
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the modifier's order is worth where a look and feel works a property out from a write of another.
 *
 * The derivation is stood in for by a listener of the test's own, so the case says the same thing under
 * every look and feel rather than only under one that happens to derive this way. A property no
 * declaration names is the other half of this and belongs to [KeyTest].
 */
class DerivedPropertyOrderingTest {
    @Test
    fun aDeclarationStandsOverWhatAnEarlierWriteWasDerivedInto() = runComposeSwingTest {
        val styleKey = "JComponent.sizeVariant"
        var style by mutableStateOf("small")
        setContent {
            Label("x", modifier = SwingModifier.clientProperty(styleKey, style), style = { font(DECLARED) })
        }
        val label = onNodeOfType<JLabel>().fetch()
        assertEquals(DECLARED, label.font, "the declared font reaches the label")

        // A look and feel that works the font out again whenever the styling key it reads is written.
        label.addPropertyChangeListener(styleKey) { label.font = DERIVED }

        style = "large"
        awaitIdle()

        assertEquals(
            DECLARED,
            label.font,
            "the font is declared after the styling key, so it stands over what the derivation worked " +
                "out from that write",
        )
    }

    private companion object {
        val DECLARED = Font("Dialog", Font.PLAIN, 11)
        val DERIVED = Font("Dialog", Font.BOLD, 23)
    }
}
