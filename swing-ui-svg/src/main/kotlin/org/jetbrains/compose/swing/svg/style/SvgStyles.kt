// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.svg.style

import org.jetbrains.compose.swing.style.NestedStyleKey
import org.jetbrains.compose.swing.style.SwingComponentStyleScope
import org.jetbrains.compose.swing.svg.SvgImage

/** Styles an ancestor provides to its descendant [SvgImage]s. */
public val SvgImages: NestedStyleKey<SwingComponentStyleScope> = NestedStyleKey("SvgImages")
