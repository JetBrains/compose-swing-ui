// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.style.accessibleDescription
import org.jetbrains.compose.swing.style.accessibleName
import org.jetbrains.compose.swing.style.toolTip
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JButton
import javax.swing.JTextField
import kotlin.test.Test

/** The accessible properties a style drops while the widget answers with a value of its own. */
class SwingComponentStylePropertiesTest {
    @Test
    fun droppingTheAccessibleNameLetsTheButtonAnswerWithItsOwnText() = runComposeSwingTest {
        var named by mutableStateOf(true)
        var text by mutableStateOf("Save")
        setContent {
            Button(
                text,
                onClick = { },
                style = { if (named) accessibleName("Persist the document") },
            )
        }
        onNodeOfType<JButton>().assertProperty(
            "Persist the document",
            "the accessible name should apply while the modifier is present",
        ) { accessibleContext.accessibleName }

        // A button answers with its text while it holds no name of its own, so a name pinned on removal
        // would go on being announced after the text moved on.
        named = false
        text = "Store"
        awaitIdle()
        onNodeOfType<JButton>().assertProperty(
            "Store",
            "removing the modifier should hand the name back to the button's own text",
        ) { accessibleContext.accessibleName }
    }

    @Test
    fun droppingTheAccessibleDescriptionLetsTheFieldAnswerWithItsTooltip() = runComposeSwingTest {
        var described by mutableStateOf(true)
        var tip by mutableStateOf("Where you live")
        setContent {
            TextField(
                "",
                onValueChange = {},
                style = {
                    toolTip(tip)
                    if (described) accessibleDescription("The address to deliver to")
                },
            )
        }
        onNodeOfType<JTextField>().assertProperty(
            "The address to deliver to",
            "the accessible description should apply while the modifier is present",
        ) { accessibleContext.accessibleDescription }

        described = false
        tip = "Where you work"
        awaitIdle()
        onNodeOfType<JTextField>().assertProperty(
            "Where you work",
            "removing the modifier should hand the description back to the field's own tooltip",
        ) { accessibleContext.accessibleDescription }
    }
}
