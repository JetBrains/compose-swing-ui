@file:JvmMultifileClass
@file:JvmName("MenuComponentsKt")

package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.components.style.MenuStyleScope
import org.jetbrains.compose.swing.components.style.Menus
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.style.NestedStyleProviderScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import javax.swing.JMenu

/**
 * An item that opens onto other items instead of acting itself: a `JMenu`, a pull-down in a menu bar or
 * a submenu of another menu.
 *
 * @param text the text of the menu
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @param content the composable content of the menu (menu items); empty by default, which leaves a menu
 *   that opens onto nothing
 * @see javax.swing.JMenu
 */
@Composable
public fun Menu(
    text: @Nls String,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<MenuStyleScope> = SwingStyle,
    content:
        @Composable @SwingMenuComposable
        () -> Unit = {},
) {
    MenuNode(
        factory = { JMenu() },
        modifier = modifier.styleable(MenuKind, style),
        update = {
            set(text) { this.text = it }
        },
        content = content,
    )
}

/** [MenuStyleScope] for Menu styles. No component state is supplied. */
private val MenuKind =
    nestingStyleKind<MenuStyleScope, Unit>(
        { base, _ -> object : MenuStyleScope, NestedStyleProviderScope by base {} },
    ) {
        applyNestedStyle(Menus)
    }
