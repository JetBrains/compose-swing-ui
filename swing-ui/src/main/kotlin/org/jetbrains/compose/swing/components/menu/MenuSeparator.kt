@file:JvmMultifileClass
@file:JvmName("MenuComponentsKt")

package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.style.Separators
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.style.AllComponents
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import javax.swing.JPopupMenu

/**
 * The rule a menu draws between groups of items: a `JPopupMenu.Separator`, which shows no text and is
 * not one of the items the menu's navigation moves through.
 *
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @see javax.swing.JPopupMenu.Separator
 */
@Composable
public fun MenuSeparator(
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<SwingComponentStyleScope> = SwingStyle,
) {
    MenuNode(
        factory = { JPopupMenu.Separator() },
        modifier = modifier.styleable(style, MenuSeparatorKind),
    )
}

private val MenuSeparatorKind =
    nestingStyleKind<SwingComponentStyleScope, Unit>(
        { base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} },
    ) {
        applyNestedStyle(AllComponents)
        applyNestedStyle(Separators)
    }
