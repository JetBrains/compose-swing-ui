package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.MenuItem
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JLabel
import javax.swing.JPopupMenu
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A context menu the toolkit has realized on screen must come down when its declaration leaves the
 * chain - the transition [JPopupMenu.isVisible] shows only for a popup shown the production way, over
 * an invoker in a real window.
 *
 * An open popup is closed by the toolkit as soon as its window is deactivated, which is why this class
 * needs the window system's undivided attention.
 */
@ExclusiveWindowSystem
class ContextMenuOpenCloseTest {
    @Test
    fun droppingTheModifierClosesAnOpenMenu() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var withMenu by mutableStateOf(true)
        setWindowContent {
            val modifier =
                if (withMenu) {
                    SwingModifier.contextMenu(
                        display = { popup, invoker, x, y ->
                            captured = popup
                            popup.show(invoker, x, y)
                        },
                    ) {
                        MenuItem("Cut", onClick = { })
                    }
                } else {
                    SwingModifier
                }
            Label("target", modifier = modifier)
        }
        val target = onWindowWithTitle(WINDOW_TITLE).onNodeWithText("target").fetch<JLabel>()

        target.dispatchEvent(popupTrigger(target))
        val popup = captured ?: error("popup-trigger event did not build a popup")
        assertTrue(popup.isVisible, "the menu the trigger built must be on screen")

        withMenu = false
        awaitIdle()

        assertFalse(popup.isVisible, "a menu on screen must come down when its declaration leaves the chain")
    }
}

private const val WINDOW_TITLE = "context-menu-open-close-test"

/**
 * Composes [content] as the content of a window that is on screen by the time this returns, and settles
 * the composition.
 *
 * Realizing that window needs a display, so a case built on this one reports SKIPPED where there is
 * none.
 */
private suspend fun ComposeSwingTest.setWindowContent(content: @Composable () -> Unit) {
    assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
    setContent {
        Window(onCloseRequest = {}, title = WINDOW_TITLE) { content() }
    }
    awaitIdle()
}
