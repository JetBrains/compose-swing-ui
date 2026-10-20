@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.style.ContainerStyleScope
import org.jetbrains.compose.swing.style.NestedStyleKey

/** ToolBar: a container with the properties of a `JToolBar`. */
public interface ToolBarStyleScope : ContainerStyleScope

/** Styles provided for every ToolBar. */
public val ToolBars: NestedStyleKey<ToolBarStyleScope> = NestedStyleKey("ToolBars")
