@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.style.NestedStyleKey
import org.jetbrains.compose.swing.style.SwingComponentStyleScope

/** Label: the properties of a `JLabel`. */
public interface LabelStyleScope : SwingComponentStyleScope

/** Styles provided for every Label. */
public val Labels: NestedStyleKey<LabelStyleScope> = NestedStyleKey("Labels")
