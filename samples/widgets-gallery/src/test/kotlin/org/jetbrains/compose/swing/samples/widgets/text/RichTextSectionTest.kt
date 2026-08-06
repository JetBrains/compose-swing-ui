package org.jetbrains.compose.swing.samples.widgets.text

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JEditorPane
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RichTextSectionTest {
    @Test
    fun theRenderedPreviewStartsWithNoLinkActivated() =
        runComposeSwingTest {
            openSection("Rich text")

            onNodeWithText("Link activated: nothing yet", substring = true).assertExists()
        }

    @Test
    fun theAuthoredEditorEchoesTheLengthOfItsRenderedText() =
        runComposeSwingTest {
            openSection("Rich text")

            // The card renders markup, so what it counts is the text the kit parsed out of it rather
            // than the characters that spell the tags. How much whitespace a kit lays between blocks is
            // its own to decide, so the count comes from the document the card is showing.
            val authored =
                onAllNodesOfType<JEditorPane>()
                    .fetchAll()
                    .single { it.contentType == "text/html" && it.isEditable }
            val rendered = authored.document.getText(0, authored.document.length)
            assertTrue(rendered.contains("Notes"), "the card renders its markup")
            assertFalse(rendered.contains("<h2>"), "the markup was parsed, not held as characters")

            onNodeWithText("Length: ${rendered.length}", substring = true).assertExists()
            onNodeWithText("Undo available: no", substring = true).assertExists()
        }

    @Test
    fun theTextPaneEchoesItsInitialLength() =
        runComposeSwingTest {
            openSection("Rich text")

            val seeded = "A styled-document editor.\nType here."
            onNodeWithText("Length: ${seeded.length}", substring = true).assertExists()
        }
}
