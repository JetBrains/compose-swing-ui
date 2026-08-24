@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.ItemSelectable
import java.awt.event.ItemEvent
import java.awt.event.ItemListener

/**
 * Runs [onItemStateChange] on the item event of a component that fires one - the components
 * [itemListener] lists, over the same event source, which reports the state a component ends up in
 * however it got there.
 *
 * [onItemStateChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @see java.awt.ItemSelectable.addItemListener
 */
public fun SwingModifier.itemListener(onItemStateChange: (ItemEvent) -> Unit): SwingModifier =
    listener(onItemStateChange, ITEM_CALLBACKS)

/**
 * Attaches an [ItemListener] (`addItemListener`/`removeItemListener`) to a component that fires item
 * events: `AbstractButton` (`JCheckBox`, `JRadioButton`, `JToggleButton`, `JCheckBoxMenuItem`,
 * `JRadioButtonMenuItem`), `JComboBox`, and the AWT `Checkbox`, `Choice`, and `List`.
 *
 * An item listener reports the state a component ends up in, however it got there. A button that a
 * [group][org.jetbrains.compose.swing.modifier.interaction.buttonGroup] deselects because another
 * member was picked never fires an action event, so the item listener is the only one deselection
 * reaches.
 *
 * @see java.awt.ItemSelectable.addItemListener
 */
public fun SwingModifier.itemListener(listener: ItemListener): SwingModifier = listener(listener, ITEM)

/**
 * Casts to [ItemSelectable], which every component that fires item events implements. Throws if the
 * component does not, instead of silently attaching nothing.
 */
private fun Component.asItemSelectable(): ItemSelectable =
    this as? ItemSelectable
        ?: error(
            "itemListener requires a component that fires item events " +
                "(AbstractButton - JCheckBox, JRadioButton, JToggleButton, JCheckBoxMenuItem, " +
                "JRadioButtonMenuItem -, JComboBox, java.awt.Checkbox, java.awt.Choice, java.awt.List), " +
                "but the component is a ${javaClass.name}",
        )

private val ITEM =
    ListenerRegistration<Component, ItemListener>(
        { component, listener -> component.asItemSelectable().addItemListener(listener) },
        { component, listener -> component.asItemSelectable().removeItemListener(listener) },
    )

private val ITEM_CALLBACKS =
    CallbackRegistration<Component, (ItemEvent) -> Unit, ItemListener>(
        adapter = { current -> ItemListener { event -> current()(event) } },
        registration = ITEM,
    )
