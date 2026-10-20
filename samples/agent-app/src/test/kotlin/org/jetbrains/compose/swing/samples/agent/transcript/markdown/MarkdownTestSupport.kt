package org.jetbrains.compose.swing.samples.agent.transcript.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.defaults.DefaultComponentOrientation
import org.jetbrains.compose.swing.defaults.ProvideComponentDefaults
import org.jetbrains.compose.swing.defaults.provides
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.RoundedCornerShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.readAgentTheme
import org.jetbrains.compose.swing.samples.agent.transcript.MarkdownView
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import java.awt.BorderLayout
import java.awt.ComponentOrientation
import javax.swing.JEditorPane
import javax.swing.text.Element
import javax.swing.text.StyleConstants
import javax.swing.text.html.HTML

internal val sampleMarkdown =
    """
    # Sync test fix

    The flaky `SyncTest` waited on a **real clock**. See [the guide](https://example.com/guide).

    ## Steps

    - [x] Replace the sleep with a test clock
    - Await the sync job
      - then assert once

    ```kotlin
    @Test
    fun syncsOnce() = runTest {
        sync(clock = testClock)
    }
    ```

    | File | Added | Removed |
    |------|------:|--------:|
    | SyncTest.kt | 12 | 4 |
    | Sync.kt | 3 | 1 |

    > Tests now pass 200 runs in a row.
    """.trimIndent()

/**
 * Mounts [markdown] in a card [width] wide on the app's background, under the theme read at [themeVersion] and
 * [orientation], as the transcript hosts an answer.
 */
internal fun ComposeSwingTest.showMarkdown(
    markdown: () -> String,
    width: () -> Int = { 560 },
    themeVersion: () -> Int = { 0 },
    orientation: () -> ComponentOrientation = { ComponentOrientation.LEFT_TO_RIGHT },
    onLinkActivate: (String) -> Unit = {},
) {
    root.layout = BorderLayout()
    setContent {
        MarkdownCard(markdown(), width(), themeVersion(), orientation(), onLinkActivate)
    }
}

@Composable
private fun MarkdownCard(
    markdown: String,
    width: Int,
    themeVersion: Int,
    orientation: ComponentOrientation,
    onLinkActivate: (String) -> Unit,
) {
    val theme = remember(themeVersion) { readAgentTheme() }
    val colors = theme.colors
    CompositionLocalProvider(LocalAgentTheme provides theme) {
        ProvideComponentDefaults(DefaultComponentOrientation provides orientation) {
            Box(SwingModifier.background(colors.background, RectangleShape)) {
                val card = SwingModifier.padding(16).width(width).background(colors.surface, RoundedCornerShape(12f))
                Column(card.padding(16)) {
                    MarkdownView(markdown, SwingModifier.fillMaxWidth(), onLinkActivate)
                }
            }
        }
    }
}

internal fun ComposeSwingTest.markdownPane(): JEditorPane = onNodeOfType<JEditorPane>().fetch()

/** The text of every element of [tag] in this pane's document, trimmed. */
internal fun JEditorPane.textsOf(tag: HTML.Tag): List<String> =
    elementsOf(tag).map { document.getText(it.startOffset, it.endOffset - it.startOffset).trim() }

/** The `class` attribute of every element of [tag] in this pane's document, `null` where it has none. */
internal fun JEditorPane.classesOf(tag: HTML.Tag): List<Any?> =
    elementsOf(tag).map { it.attributes.getAttribute(HTML.Attribute.CLASS) }

private fun JEditorPane.elementsOf(tag: HTML.Tag): List<Element> {
    val found = mutableListOf<Element>()

    fun visit(element: Element) {
        if (element.attributes.getAttribute(StyleConstants.NameAttribute) == tag) found += element
        for (index in 0 until element.elementCount) visit(element.getElement(index))
    }
    visit(document.defaultRootElement)
    return found
}
