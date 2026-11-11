// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.foundation.layout.stockLayoutBoundsInParent
import java.awt.Component
import java.awt.Rectangle

/** This component's layout bounds in its parent's layout coordinates, as `onPlaced` reports them. */
internal val Component.layoutBounds: Rectangle
    get() = (this as? Decoratable)?.decoration?.layoutBoundsInParent(this) ?: stockLayoutBoundsInParent()
