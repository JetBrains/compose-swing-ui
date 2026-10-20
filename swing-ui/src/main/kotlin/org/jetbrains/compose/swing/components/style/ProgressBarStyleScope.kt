@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.style.NestedStyleKey
import org.jetbrains.compose.swing.style.SwingComponentStyleScope

/** ProgressBar: the properties of a `JProgressBar`. */
public interface ProgressBarStyleScope : SwingComponentStyleScope

/** Styles provided for every ProgressBar. */
public val ProgressBars: NestedStyleKey<ProgressBarStyleScope> = NestedStyleKey("ProgressBars")
