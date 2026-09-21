// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.layout

/**
 * Marks the receiver of a container's content, so the scopes of the containers around it are unavailable
 * there: a child cannot declare a placement meant for a parent that does not place it.
 *
 * Apply this to a custom container scope that declares modifier extensions for its children.
 */
@DslMarker
public annotation class LayoutScopeMarker
