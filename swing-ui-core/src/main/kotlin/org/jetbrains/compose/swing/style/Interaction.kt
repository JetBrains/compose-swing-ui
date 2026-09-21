// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import java.awt.Component

/**
 * Sets `isFocusable`, declaring whether this component can receive keyboard focus.
 *
 * @param focusable `false` takes the component out of the Tab cycle as well as refusing it a click's focus.
 * @see java.awt.Component.setFocusable
 */
public fun ComponentStyleScope.focusable(focusable: Boolean): Unit = FocusableProperty.provide(focusable)

/**
 * Sets `isEnabled` on this component only, controlling whether it responds to user input and paints in
 * its enabled state. To disable descendants, use `provideNestedStyle(AllComponents) { enabled(false) }`
 * on a container. The provided style does not disable the container itself, and a descendant's own
 * `enabled(true)` overrides it.
 *
 * @param enabled `false` keeps the component out of the Tab cycle and stops its key bindings firing. A
 *   lightweight component still receives mouse events while disabled, so the listeners declared on it go
 *   on reporting; it is the widget's own listeners that read the enabled state before acting on one.
 * @see java.awt.Component.setEnabled
 */
public fun ComponentStyleScope.enabled(enabled: Boolean): Unit = EnabledProperty.provide(enabled)

internal val FocusableDescriptor =
    ComponentPropertyDescriptor<Component, Boolean>(
        name = "focusable",
        read = { it.isFocusable },
        write = { component, value -> component.isFocusable = value },
    )

internal val EnabledDescriptor =
    ComponentPropertyDescriptor<Component, Boolean>(
        name = "enabled",
        read = { it.isEnabled },
        write = { component, value -> component.isEnabled = value },
    )

private val FocusableProperty = stylePropertyOf(FocusableDescriptor)

private val EnabledProperty = stylePropertyOf(EnabledDescriptor)
