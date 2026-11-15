// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.core

import com.intellij.psi.PsiElement

/** A finding collected by a rule before it reports the result. */
internal class RuleFinding(
    val entity: PsiElement,
    val message: String,
)
