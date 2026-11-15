// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import javax.swing.JComponent

/**
 * The value a raw component carried for [property] when it was created, kept with `putClientProperty` so that a
 * declaration that withdraws a value later restores it. The property must have been stored with a [T].
 */
internal inline fun <reified T> JComponent.defaultOf(property: String): T = getClientProperty(property) as T
