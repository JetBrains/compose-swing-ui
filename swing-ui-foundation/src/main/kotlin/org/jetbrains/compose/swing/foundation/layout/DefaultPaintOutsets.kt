// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.defaults.componentDefaultKeyOf

/**
 * Key for the default [PaintOutsets] value applied to descendants via [paintOutsets].
 *
 * It comes first in a descendant's modifier, and a `paintOutsets` the descendant declares replaces it.
 *
 * Use `provideNestedStyle(AllComponents) { paintOutsets(outsets) }` instead. Where `DefaultPaintOutsets provides null`
 * withdrew an outer value for a subtree, provide [PaintOutsets.Decoration] there.
 */
@Deprecated("Provide a nested style: style = { provideNestedStyle(AllComponents) { paintOutsets(outsets) } }")
@Suppress("DEPRECATION", "PreferImportsOverFqn")
public val DefaultPaintOutsets: org.jetbrains.compose.swing.defaults.ComponentDefaultKey<PaintOutsets> =
    componentDefaultKeyOf("paintOutsets") { paintOutsets(it) }
