@file:JvmMultifileClass
@file:JvmName("WindowKt")

package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.setContentAsMenuInteropHost
import javax.swing.JMenuBar
import javax.swing.JRootPane

/**
 * Declares the menu bar of the window whose content this is composed in.
 *
 * This is declared on [WindowScope], the receiver of the content of a [Window] and of a [Dialog], so it
 * is only available where there is a window to carry the bar.
 *
 * [content] is a menu tree - `Menu`, `MenuItem`, `CheckBoxMenuItem`, `RadioButtonMenuItem`,
 * `MenuSeparator` - and the menus it declares run across the top of the window:
 *
 * ```
 * Window(onCloseRequest = ::exitApplication) {
 *     MenuBar {
 *         Menu("File") {
 *             MenuItem("Open", onClick = { open() })
 *             MenuSeparator()
 *             MenuItem("Exit", onClick = ::exitApplication)
 *         }
 *     }
 *     Label("Ready")
 * }
 * ```
 *
 * The menu tree shares the surrounding composition, so a menu item shows the current state and its
 * callback writes that state like any other composable callback. The bar is on the window for as long
 * as this is in the composition; once this leaves, the window carries the bar it carried before, so a
 * window whose menu bar comes and goes is an ordinary `if` around the call.
 *
 * A window carries one menu bar, so one declaration serves a window: put the choice of menus inside the
 * declaration rather than composing a second one for the same window, which fails and leaves the window
 * the bar it carried.
 *
 * @param content the composable menu tree shown as the window's menu bar.
 */
@Composable
@SwingComposable
public fun WindowScope.MenuBar(
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
) {
    // The tree is hosted as a child of this composition, so state around the MenuBar reaches the menu
    // items and their callbacks, and a menu tree that merely changes flows in through the handle the
    // recomposition refreshes.
    val currentContent by rememberUpdatedState(content)
    val parentContext = rememberCompositionContext()
    val scope = this

    DisposableEffect(rootPane) {
        val serving = scope.declaredMenuBar
        if (serving != null) {
            // Answering this declaration ends the composition both of them belong to, which is not
            // itself a withdrawal of the one already serving the window, so it is withdrawn here: the
            // window - which a caller may own and keep - is handed back the bar it carried before, and
            // carries no record to refuse the next declaration reaching it.
            serving.withdraw()
            error(
                "Two MenuBar { } declarations are composed in this window at once, and a window " +
                    "carries one menu bar: the second would take the window from the first, leaving " +
                    "that declaration composed with nothing of it on the window. Declare one " +
                    "MenuBar { } per window and put the choice inside it, or branch so that only one " +
                    "of them is composed at a time.",
            )
        }

        val declaration = DeclaredMenuBar(scope)
        val handle = declaration.bar.setContentAsMenuInteropHost(parentContext) { currentContent() }
        declaration.serve()

        onDispose {
            declaration.withdraw()
            handle.dispose()
        }
    }
}

/**
 * The menu bar one [MenuBar] declaration puts on [WindowScope.rootPane], together with the bar that pane
 * carried before.
 *
 * A window carries one menu bar, so whether one already serves it is read off [scope]'s own
 * [WindowScope.declaredMenuBar] rather than off the Swing side: that field is this declaration's sole
 * bookkeeping, so a bar already in place answers for itself.
 */
internal class DeclaredMenuBar(
    private val scope: WindowScope,
) {
    /** The bar this declaration puts on the window; its menus are the declared menu tree. */
    val bar: JMenuBar = JMenuBar()

    private val displaced: JMenuBar? = scope.rootPane.jMenuBar
    private var serving: Boolean = false

    /** Puts [bar] on the window and records this declaration as the one serving it. */
    fun serve() {
        serving = true
        scope.declaredMenuBar = this
        scope.rootPane.installMenuBar(bar)
    }

    /**
     * Hands the window back the bar it carried before [serve]. Does nothing unless this declaration is
     * the one serving, so withdrawing what has already been withdrawn leaves the window alone.
     */
    fun withdraw() {
        if (!serving) return
        serving = false
        scope.declaredMenuBar = null
        scope.rootPane.installMenuBar(displaced)
    }
}

/**
 * Puts [menuBar] on this root pane, or takes the current one off when it is `null`, and asks for the
 * layout pass the change needs: a root pane lays the menu bar out above the content, so the strip it
 * occupies is only handed over - or reclaimed - once the pane has been laid out again.
 */
private fun JRootPane.installMenuBar(menuBar: JMenuBar?) {
    jMenuBar = menuBar
    revalidate()
}
