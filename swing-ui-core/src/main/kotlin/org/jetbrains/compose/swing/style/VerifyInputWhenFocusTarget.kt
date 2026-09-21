// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import javax.swing.JComponent

/**
 * Whether the `inputVerifier` of the component that currently holds the keyboard is consulted before focus moves
 * to *this* component. Swing's own value is `true`; set `false` on a control that must act regardless of what the
 * focused field holds, such as a Cancel button or a scrollbar.
 *
 * @param verify `false` skips the focused component's verifier for every focus transfer into this component; the
 *   verifier stays where it was declared and goes on gating transfers to other targets.
 * @see javax.swing.JComponent.setVerifyInputWhenFocusTarget
 */
public fun SwingComponentStyleScope.verifyInputWhenFocusTarget(verify: Boolean): Unit =
    VerifyInputWhenFocusTargetProperty.provide(verify)

internal val VerifyInputWhenFocusTargetDescriptor =
    ComponentPropertyDescriptor<JComponent, Boolean>(
        name = "verifyInputWhenFocusTarget",
        read = { it.verifyInputWhenFocusTarget },
        write = { component, value -> component.verifyInputWhenFocusTarget = value },
    )

private val VerifyInputWhenFocusTargetProperty = stylePropertyOf(VerifyInputWhenFocusTargetDescriptor)
