package org.jetbrains.compose.swing.samples.agent.transcript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.interaction.caretUpdatePolicy
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.samples.agent.i18n.componentOrientationOf
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import java.awt.Desktop
import java.net.URI
import javax.swing.BorderFactory
import javax.swing.JScrollPane
import javax.swing.text.DefaultCaret

/**
 * Selectable assistant [markdown], with prose wrapping and wide code scrolling inside the answer.
 * A link the user clicks goes to [onLinkActivate], which opens it in the browser by default.
 */
@Composable
internal fun MarkdownView(
    markdown: String,
    modifier: SwingModifier = SwingModifier,
    onLinkActivate: (String) -> Unit = ::openInBrowser,
) {
    // A streaming answer changes many times a frame; the view renders the latest text once per frame at most.
    val latest by rememberUpdatedState(markdown)
    var shown by remember { mutableStateOf(markdown) }
    LaunchedEffect(Unit) {
        snapshotFlow { latest }.collectLatest {
            withFrameNanos {}
            shown = it
        }
    }
    val theme = LocalAgentTheme.current
    val direction = remember(shown) { componentOrientationOf(shown) }
    val css = remember(theme, direction) { markdownCss(theme, direction.isLeftToRight) }
    val html = remember(shown, css) { markdownToHtml(shown, css) }
    // The pane is kept across updates and only its text is replaced. With the caret left where it is, streamed
    // text never scrolls the transcript to the caret.
    ScrollPane(
        modifier = modifier.opaque(false).border(BorderFactory.createEmptyBorder()),
        verticalScrollbar = JScrollPane.VERTICAL_SCROLLBAR_NEVER,
        horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED,
    ) {
        Viewport(SwingModifier.opaque(false)) {
            EditorPane(
                markup = html,
                onLinkActivate = { href -> if (isAllowedLink(href)) onLinkActivate(href) },
                modifier =
                    SwingModifier
                        .componentOrientation(direction)
                        .opaque(false)
                        .border(BorderFactory.createEmptyBorder())
                        .caretUpdatePolicy(DefaultCaret.NEVER_UPDATE),
                contentType = "text/html",
            )
        }
    }
}

private fun openInBrowser(href: String) {
    if (!isAllowedLink(href) || !Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    // A relative or malformed href, or a system with no browser, leaves the click without effect.
    if (desktop.isSupported(Desktop.Action.BROWSE)) runCatching { desktop.browse(URI(href)) }
}

private fun isAllowedLink(href: String): Boolean =
    runCatching { URI(href).scheme?.lowercase() in setOf("http", "https", "mailto") }.getOrDefault(false)
