package org.jetbrains.compose.swing.test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.appearance.testTag
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral coverage for the `SwingModifier.testTag` modifier and the harness finders that read it:
 * [ComposeSwingTest.onNodeWithTag] and [ComposeSwingTest.onAllNodesWithTag]. A tag locates exactly the tagged
 * node, is independent of the component's name, leaves untagged nodes unmatched, and is removed when
 * the modifier leaves the chain.
 */
class TestTagFinderTest {
    @Test
    fun onNodeWithTagResolvesTheTaggedNode() = runComposeSwingTest {
        setContent {
            BoxPanel {
                Label(text = "first", modifier = SwingModifier.testTag("target"))
                Label(text = "second")
            }
        }

        val tagged = onNodeWithTag("target").fetch<JLabel>()
        assertEquals("first", tagged.text)
    }

    @Test
    fun testTagIsIndependentOfComponentName() = runComposeSwingTest {
        setContent {
            Label(text = "lbl", modifier = SwingModifier.name("the-name").testTag("the-tag"))
        }

        // The tag and the name are separate keys: finding by one does not match the other's value.
        assertEquals("lbl", onNodeWithTag("the-tag").fetch<JLabel>().text, "the tag key should resolve the label")
        assertEquals("lbl", onNodeWithName("the-name").fetch<JLabel>().text, "the name key should resolve the label")
        onNodeWithTag("the-name").assertDoesNotExist()
        onNodeWithName("the-tag").assertDoesNotExist()
    }

    @Test
    fun onAllNodesWithTagMatchesEveryTaggedNode() = runComposeSwingTest {
        setContent {
            BoxPanel {
                Label(text = "a", modifier = SwingModifier.testTag("row"))
                Label(text = "b", modifier = SwingModifier.testTag("row"))
                Label(text = "c", modifier = SwingModifier.testTag("other"))
                Label(text = "d")
            }
        }

        onAllNodesWithTag("row").assertCountEquals(2)
        assertEquals(1, onAllNodesWithTag("other").fetchSize(), "the single \"other\"-tagged node should match once")
        assertEquals(0, onAllNodesWithTag("absent").fetchSize(), "an absent tag should match nothing")
    }

    @Test
    fun removingTheTestTagModifierUntagsTheNode() = runComposeSwingTest {
        var tagged by mutableStateOf(true)
        setContent {
            Label(
                text = "lbl",
                modifier = SwingModifier.name("lbl").let { if (tagged) it.testTag("tag") else it },
            )
        }
        onNodeWithTag("tag").assertExists()

        tagged = false
        awaitIdle()

        // The element left the chain, so the tag no longer resolves the node.
        onNodeWithTag("tag").assertDoesNotExist()
    }
}
