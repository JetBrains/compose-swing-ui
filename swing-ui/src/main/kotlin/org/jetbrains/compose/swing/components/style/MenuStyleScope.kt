@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.style.NestedStyleKey
import org.jetbrains.compose.swing.style.NestedStyleProviderScope

/** Menu: a button that holds menu items. Its style may provide nested styles for them. */
public interface MenuStyleScope :
    ButtonStyleScope,
    NestedStyleProviderScope

/** Styles provided for every Menu. */
public val Menus: NestedStyleKey<MenuStyleScope> = NestedStyleKey("Menus")

/** Styles provided for every MenuItem. */
public val MenuItems: NestedStyleKey<ButtonStyleScope> = NestedStyleKey("MenuItems")

/** Styles provided for every CheckBoxMenuItem. */
public val CheckBoxMenuItems: NestedStyleKey<CheckableStyleScope> = NestedStyleKey("CheckBoxMenuItems")

/** Styles provided for every RadioButtonMenuItem and every RadioButtonMenuGroup option. */
public val RadioButtonMenuItems: NestedStyleKey<SelectableStyleScope> = NestedStyleKey("RadioButtonMenuItems")
