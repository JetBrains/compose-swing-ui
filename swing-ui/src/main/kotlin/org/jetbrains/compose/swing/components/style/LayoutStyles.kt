@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.style.ContainerStyleScope
import org.jetbrains.compose.swing.style.NestedStyleKey
import org.jetbrains.compose.swing.style.SwingComponentStyleScope

/** Styles provided for every Panel. */
public val Panels: NestedStyleKey<ContainerStyleScope> = NestedStyleKey("Panels")

/** Styles provided for every ScrollPane. */
public val ScrollPanes: NestedStyleKey<ContainerStyleScope> = NestedStyleKey("ScrollPanes")

/** Styles provided for every viewport that a ScrollPane's `Viewport` declares. */
public val Viewports: NestedStyleKey<ContainerStyleScope> = NestedStyleKey("Viewports")

/** Styles provided for every scroll bar that a ScrollPane's `VerticalScrollbar` or `HorizontalScrollbar` declares. */
public val ScrollBars: NestedStyleKey<SwingComponentStyleScope> = NestedStyleKey("ScrollBars")

/** Styles provided for every SplitPane. */
public val SplitPanes: NestedStyleKey<ContainerStyleScope> = NestedStyleKey("SplitPanes")

/** Styles provided for every TabbedPane. */
public val TabbedPanes: NestedStyleKey<ContainerStyleScope> = NestedStyleKey("TabbedPanes")

/** Styles provided for every Spacer, Strut, Glue and RigidArea. */
public val Fillers: NestedStyleKey<SwingComponentStyleScope> = NestedStyleKey("Fillers")

/** Styles provided for every Separator, ToolBarSeparator and MenuSeparator. */
public val Separators: NestedStyleKey<SwingComponentStyleScope> = NestedStyleKey("Separators")
