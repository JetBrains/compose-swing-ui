@file:JvmMultifileClass
@file:JvmName("MenuComponentsKt")

package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.style.RadioButtonMenuItems
import org.jetbrains.compose.swing.components.style.SelectableStyleScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberMirrorState
import org.jetbrains.compose.swing.node.report
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import java.awt.event.ActionListener
import javax.swing.JRadioButtonMenuItem
import javax.swing.KeyStroke

/**
 * A menu item carrying a radio mark: a `JRadioButtonMenuItem` that shows [selected] and reports the
 * user's activation through [onSelectedChange]. The item stands on its own, so activating it toggles the
 * mark; for a set of items where at most one is marked, use [RadioButtonMenuGroup].
 *
 * A change the caller does not answer with a matching [selected] is settled back onto the declared
 * value, so the mark on screen is the one the composition holds.
 *
 * @param text the text of the menu item
 * @param selected whether the menu item is selected
 * @param onSelectedChange callback invoked with the selected state when the item is activated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 * @see javax.swing.JRadioButtonMenuItem
 */
@Composable
public fun RadioButtonMenuItem(
    text: @Nls String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<SelectableStyleScope> = SwingStyle,
    accelerator: KeyStroke? = null,
) {
    val mirror = rememberMirrorState(selected)
    RadioButtonMenuItemNode(
        text = text,
        modifier =
            modifier.actionListener<JRadioButtonMenuItem> {
                mirror.report { onSelectedChange(isSelected) }
            },
        style = style,
        selected = selected,
        accelerator = accelerator,
        mirror = mirror,
    )
}

/**
 * A JRadioButtonMenuItem driven by a raw [ActionListener] instead of an `onSelectedChange` lambda. The
 * listener is attached as-is and removed on the same instance; pass a stable instance (e.g.
 * `remember {}`) to avoid churn.
 *
 * @param text the text of the menu item
 * @param selected whether the menu item is selected
 * @param actionListener the listener notified when the item is activated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @param accelerator the key combination that activates the item without navigating the menu
 *   hierarchy, displayed next to its text; `null` (the default) leaves the item without one
 * @see javax.swing.JRadioButtonMenuItem
 */
@Composable
public fun RadioButtonMenuItem(
    text: @Nls String,
    selected: Boolean,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<SelectableStyleScope> = SwingStyle,
    accelerator: KeyStroke? = null,
) {
    val mirror = rememberMirrorState(selected)
    RadioButtonMenuItemNode(
        text = text,
        modifier =
            modifier.actionListener(actionListener),
        style = style,
        selected = selected,
        accelerator = accelerator,
        mirror = mirror,
    )
}

/** [SelectableStyleScope] for RadioButtonMenuItem styles. The declared selected state supplies `isSelected`. */
private val RadioButtonMenuItemKind =
    nestingStyleKind<SelectableStyleScope, Boolean>({ base, selected ->
        object : SelectableStyleScope, ComponentStyleScope by base {
            override val isSelected: Boolean get() = selected
        }
    }) {
        applyNestedStyle(RadioButtonMenuItems)
    }

/**
 * The `JRadioButtonMenuItem` node both [RadioButtonMenuItem] overloads render. [selected] is settled
 * against the item through [mirror] rather than applied on change: the user can take the item out from
 * under the declaration, and a declaration equal to the last one still has to stand.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun RadioButtonMenuItemNode(
    text: @Nls String,
    modifier: SwingModifier,
    style: SwingStyle<SelectableStyleScope>,
    selected: Boolean,
    accelerator: KeyStroke?,
    mirror: MirrorState<Boolean>,
) {
    MenuNode(
        factory = { JRadioButtonMenuItem() },
        modifier = modifier.styleable(RadioButtonMenuItemKind, selected, style),
        update = {
            set(text) { this.text = it }
            init { addItemListener { mirror.observed(isSelected) } }
            declare(selected, mirror, JRadioButtonMenuItem::isSelected, JRadioButtonMenuItem::setSelected)
            set(accelerator) { this.accelerator = it }
        },
    )
}
