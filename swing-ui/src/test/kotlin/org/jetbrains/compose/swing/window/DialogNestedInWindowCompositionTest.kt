package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.setContentAsInteropHost
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Container
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Display-independent coverage that a `Dialog`'s content composes WITHIN a `Window`-owned composition
 * and that state flows from the window scope into the nested dialog content.
 *
 * Realizing a real `Window`/`JDialog` peer needs a display; the display-gated window and dialog tests
 * cover that path. This test does not realize on-screen peers, so it runs with or without a display.
 * It models the wiring `Window`/`Dialog` use: the harness composition plays the
 * window-owned scope, the dialog's content is mounted into a DETACHED host container (standing in for
 * a `JDialog` content pane that is not part of the window's Swing tree) as a CHILD of the captured
 * window-scope [androidx.compose.runtime.CompositionContext] via
 * [setContentAsInteropHost]. Because the host is detached, an upward tree-walk from it finds nothing;
 * only the explicit parent context joins the two compositions.
 *
 * The observable contract: a value provided and a state owned in the window scope reach the detached
 * dialog content and recompose it when the window-scope state changes — proving the nesting, not a
 * coincidental independent root that would see only CompositionLocal defaults.
 */
class DialogNestedInWindowCompositionTest {
    private fun ComposeSwingTest.dialogLabelText(host: Container): String {
        val labels = mutableListOf<JLabel>()

        fun visit(container: Container) {
            for (child in container.components) {
                if (child is JLabel) labels += child
                if (child is Container) visit(child)
            }
        }
        visit(host)
        return labels.single().text
    }

    @Test
    fun dialogContentNestsInTheWindowCompositionAndReceivesWindowState() = runComposeSwingTest {
        // A real container that is deliberately NOT part of the harness (window) Swing tree, the
        // way a JDialog's content pane is detached from its owner window's content pane.
        val dialogHost = JPanel().apply { size = Dimension(200, 200) }
        var title by mutableStateOf("initial")

        setContent {
            // The harness composition is the window-owned scope: it provides a value and owns state.
            CompositionLocalProvider(LocalDialogTitle provides title) {
                DialogIn(dialogHost) {
                    // Resolves the window-scope CompositionLocal only if this dialog content is a
                    // CHILD of the window composition.
                    Label(text = "title=${LocalDialogTitle.current}")
                }
            }
        }

        assertEquals(
            "title=initial",
            dialogLabelText(dialogHost),
            "window-scope value did not reach the nested dialog content",
        )

        // A window-scope state change must recompose the nested dialog content.
        title = "updated"
        awaitIdle()
        assertEquals(
            "title=updated",
            dialogLabelText(dialogHost),
            "nested dialog content did not recompose on window-scope state change",
        )
    }

    /**
     * Mirrors the `Dialog` wiring shape: capture the enclosing (window) context in the composable body,
     * then mount [content] into the detached [host] as a child of that context.
     */
    @Composable
    private fun DialogIn(
        host: Container,
        content: @Composable () -> Unit,
    ) {
        val windowContext = rememberCompositionContext()
        // The dialog content is mounted once and read live through this state, so the effect never has
        // to restart when a fresh content lambda arrives across recompositions.
        val currentContent by rememberUpdatedState(content)
        DisposableEffect(Unit) {
            val handle: DisposableHandle = host.setContentAsInteropHost(windowContext) { currentContent() }
            onDispose { handle.dispose() }
        }
    }
}

/**
 * A window-scope CompositionLocal used to prove propagation into nested dialog content. Declared
 * top-level so it carries the `Local` prefix while remaining file-private to this test.
 */
private val LocalDialogTitle = compositionLocalOf { "default" }
