// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.appearance.testTag
import kotlin.test.Test

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
            Panel(PanelLayout.Box()) {
                Label(text = "first", modifier = SwingModifier.testTag("target"))
                Label(text = "second")
            }
        }

        onNodeWithTag("target").assertTextEquals("first")
    }

    @Test
    fun testTagIsIndependentOfComponentName() = runComposeSwingTest {
        setContent {
            Label(text = "lbl", modifier = SwingModifier.name("the-name").testTag("the-tag"))
        }

        // The tag and the name are separate keys: finding by one does not match the other's value.
        onNodeWithTag("the-tag").assertTextEquals("lbl")
        onNodeWithName("the-name").assertTextEquals("lbl")
        onNodeWithTag("the-name").assertDoesNotExist()
        onNodeWithName("the-tag").assertDoesNotExist()
    }

    @Test
    fun onAllNodesWithTagMatchesEveryTaggedNode() = runComposeSwingTest {
        setContent {
            Panel(PanelLayout.Box()) {
                Label(text = "a", modifier = SwingModifier.testTag("row"))
                Label(text = "b", modifier = SwingModifier.testTag("row"))
                Label(text = "c", modifier = SwingModifier.testTag("other"))
                Label(text = "d")
            }
        }

        onAllNodesWithTag("row").assertCountEquals(2)
        onAllNodesWithTag("other").assertCountEquals(1)
        onAllNodesWithTag("absent").assertCountEquals(0)
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
