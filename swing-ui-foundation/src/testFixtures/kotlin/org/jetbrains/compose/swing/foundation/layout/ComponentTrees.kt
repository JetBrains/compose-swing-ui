package org.jetbrains.compose.swing.foundation.layout

import java.awt.Component
import java.awt.Container

/** This component and the components it holds, at any depth. */
public fun Component.withDescendants(): List<Component> =
    listOf(this) + ((this as? Container)?.components.orEmpty().flatMap { it.withDescendants() })
