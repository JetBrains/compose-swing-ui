@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.style.ContainerStyleScope
import org.jetbrains.compose.swing.style.NestedStyleKey
import org.jetbrains.compose.swing.style.SwingComponentStyleScope

/** ListBox: the properties of a `JList`. */
public interface ListBoxStyleScope : SwingComponentStyleScope

/** ComboBox: the properties of a `JComboBox`. */
public interface ComboBoxStyleScope : SwingComponentStyleScope

/** Styles provided for every ListBox. */
public val ListBoxes: NestedStyleKey<ListBoxStyleScope> = NestedStyleKey("ListBoxes")

/** Styles provided for every ComboBox. */
public val ComboBoxes: NestedStyleKey<ComboBoxStyleScope> = NestedStyleKey("ComboBoxes")

/** Styles provided for every RadioGroup: the panel that holds its options. Its options apply [RadioButtons]. */
public val RadioGroups: NestedStyleKey<ContainerStyleScope> = NestedStyleKey("RadioGroups")

/** Styles provided for every Table. */
public val Tables: NestedStyleKey<SwingComponentStyleScope> = NestedStyleKey("Tables")

/** Styles provided for every table header that a Table's `Header` declares. */
public val TableHeaders: NestedStyleKey<SwingComponentStyleScope> = NestedStyleKey("TableHeaders")

/** Styles provided for every Tree. */
public val Trees: NestedStyleKey<SwingComponentStyleScope> = NestedStyleKey("Trees")
