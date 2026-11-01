package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTextArea
import javax.swing.text.Document
import javax.swing.text.PlainDocument
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * Behavioral coverage for [TextArea]'s tab size, the number of characters a tab expands to. The size is
 * held by the document the area renders rather than by the area itself, so a state-driven area has to be
 * left holding it on the document its state brought - including a document a later state brings.
 */
class TextAreaTabSizeTest {
    @Test
    fun anUndeclaredTabSizeIsTheAreasOwn() = runComposeSwingTest {
        setContent { TextArea(value = "a\tb", onValueChange = {}) }

        onNodeOfType<JTextArea>().assertProperty(
            JTextArea().tabSize,
            "an undeclared tab size is the one a JTextArea expands to by itself",
        ) { tabSize }
    }

    @Test
    fun aDeclaredTabSizeReachesTheAreaAndFollowsRecomposition() = runComposeSwingTest {
        var tabSize by mutableStateOf(4)
        setContent { TextArea(value = "a\tb", onValueChange = {}, tabSize = tabSize) }

        val area = onNodeOfType<JTextArea>()
        area.assertProperty(4, "the declared tab size is the one the area expands a tab to") { this.tabSize }
        val areaBefore = onNodeOfType<JTextArea>().fetch()

        tabSize = 2
        awaitIdle()
        assertSame(areaBefore, onNodeOfType<JTextArea>().fetch(), "the component is updated in place, not rebuilt")
        area.assertProperty(2, "a tab size declared later is the one it expands a tab to from then on") {
            this.tabSize
        }
    }

    @Test
    fun aDeclaredTabSizeReachesTheDocumentTheStateBrought() = runComposeSwingTest {
        setContent { TextArea(state = rememberDocumentState("a\tb"), tabSize = 4) }

        onNodeOfType<JTextArea>().assertProperty(
            4,
            "the size lands on the state's document rather than on the one it replaced",
        ) { tabSize }
    }

    @Test
    fun aDeclaredTabSizeFollowsOntoTheDocumentALaterStateBrings() = runComposeSwingTest {
        val first = PlainDocument().apply { insertString(0, "a\tb", null) }
        val second = PlainDocument().apply { insertString(0, "c\td", null) }
        var document: Document by mutableStateOf(first)
        setContent { TextArea(state = rememberDocumentState(document = document), tabSize = 4) }

        val area = onNodeOfType<JTextArea>()
        area.assertProperty(4, "the declared tab size reaches the document the first state brought") { tabSize }
        val areaBefore = onNodeOfType<JTextArea>().fetch()

        document = second
        awaitIdle()
        assertSame(areaBefore, onNodeOfType<JTextArea>().fetch(), "the component is updated in place, not rebuilt")

        area.assertTextEquals("c\td")
        area.assertProperty(4, "a new document is declared onto, not left with the area's own size") { tabSize }
    }
}
