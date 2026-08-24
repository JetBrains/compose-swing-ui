@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.ItemSelectable
import java.awt.event.ItemListener

/**
 * Attaches an [ItemListener] (`addItemListener`/`removeItemListener`) to a component that fires item
 * events: `AbstractButton` (`JCheckBox`, `JRadioButton`, `JToggleButton`, `JCheckBoxMenuItem`,
 * `JRadioButtonMenuItem`), `JComboBox`, and the AWT `Checkbox`, `Choice`, and `List`.
 *
 * The item channel reports the state a component ends up in, however it got there. A button that a
 * [group][org.jetbrains.compose.swing.modifier.interaction.buttonGroup] deselects because another
 * member was picked never fires an action event, so the item listener is the only channel that
 * deselection reaches.
 *
 * @see java.awt.ItemSelectable.addItemListener
 */
public fun SwingModifier.itemListener(listener: ItemListener): SwingModifier =
    listener<Component, ItemListener>(
        listener,
        { component, instance -> component.asItemSelectable().addItemListener(instance) },
        { component, instance -> component.asItemSelectable().removeItemListener(instance) },
    )

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
