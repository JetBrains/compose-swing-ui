package org.jetbrains.compose.swing.samples.widgets

import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test

class RichTextSectionTest {
    @Test
    fun theEditorPaneTogglesItsRenderedContentType() =
        runComposeSwingTest {
            openSection("Rich text")

            onNodeWithText("Rendered as plain text", substring = true).assertExists()
            onNodeWithText("Content type: PlainText", substring = true).assertExists()

            onNodeWithText("Content type: PlainText", substring = true).performClick()
            onNodeWithText("Rendered as HTML", substring = true).assertExists()
            onNodeWithText("Content type: Html", substring = true).assertExists()
        }

    @Test
    fun theTextPaneEchoesItsInitialLength() =
        runComposeSwingTest {
            openSection("Rich text")

            val seeded = "A styled-document editor.\nType here."
            onNodeWithText("Length: ${seeded.length}", substring = true).assertExists()
        }
}
