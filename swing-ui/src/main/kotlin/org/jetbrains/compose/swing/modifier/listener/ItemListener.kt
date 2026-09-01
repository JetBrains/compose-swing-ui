@file:JvmMultifileClass
@file:JvmName("ListenerModifierKt")

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.ItemSelectable
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import kotlin.reflect.KClass

/**
 * Runs [onItemStateChange] on the item event of a component that fires one - the components
 * [itemListener] lists, over the same event source, which reports the state a component ends up in
 * however it got there.
 *
 * [onItemStateChange] is read when the event fires, so writing a fresh lambda on every recomposition
 * registers nothing again.
 *
 * @param onItemStateChange receives the event, whose `stateChange` is `ItemEvent.SELECTED` or
 *   `ItemEvent.DESELECTED` and whose `item` is the value it happened to.
 * @return this chain with the item callback declared on it.
 * @see java.awt.ItemSelectable.addItemListener
 */
public fun SwingModifier.itemListener(onItemStateChange: (ItemEvent) -> Unit): SwingModifier =
    listener(onItemStateChange, ITEM_CALLBACKS)

/**
 * Runs [onItemStateChange] on the item event of a component of type [T] that fires one, with that
 * component as `this`.
 *
 * @param onItemStateChange read when the event fires. A parameter of the enclosing composable shadows
 *   the receiver, so reach it through `this`.
 *   Read the component's state through the receiver; writing a property the composition declares
 *   through it makes the second manager [listener] describes.
 * @return this chain with the item callback declared on it.
 * @see java.awt.ItemSelectable.addItemListener
 */
public inline fun <reified T : Component> SwingModifier.itemListener(
    noinline onItemStateChange: T.(ItemEvent) -> Unit,
): SwingModifier = itemListener(T::class, onItemStateChange)

/**
 * Runs [onItemStateChange] on the item event of a component of type [targetType] that fires one, with
 * that component as `this`. An event sourced anywhere else is refused; see [listener].
 *
 * @param targetType the component type the node and every event's source are both checked against.
 * @param onItemStateChange read when the event fires.
 * @return this chain with the item callback declared on it.
 * @see java.awt.ItemSelectable.addItemListener
 */
public fun <T : Component> SwingModifier.itemListener(
    targetType: KClass<T>,
    onItemStateChange: T.(ItemEvent) -> Unit,
): SwingModifier = listener(targetType, ITEM_CALLBACKS, onItemStateChange)

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
 * @param listener attached by identity, so a remembered instance stays put while a fresh one on every
 *   pass is detached and re-added.
 * @return this chain with the item listener declared on it.
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
