// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.style.NestedStyleKey
import org.jetbrains.compose.swing.style.SwingComponentStyleScope

/** Label: the properties of a `JLabel`. */
public interface LabelStyleScope : SwingComponentStyleScope

/** Styles provided for every Label. */
public val Labels: NestedStyleKey<LabelStyleScope> = NestedStyleKey("Labels")
