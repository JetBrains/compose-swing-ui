// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0

// Adapted from compose-rules.
@file:OptIn(KaExperimentalApi::class)

package org.jetbrains.compose.swing.rules.runtime

import org.jetbrains.compose.swing.rules.psi.isResolvedCallToAnyOf
import org.jetbrains.compose.swing.rules.psi.resolvedCallFqName
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtCallExpression

internal fun KtCallExpression.isComposableCall(): Boolean = runCatching {
    analyze(this) {
        val call = this@isComposableCall.resolveCall() ?: return@analyze false
        call.signature.symbol.hasComposableAnnotation() ||
            calleeExpression?.expressionType?.hasComposableAnnotation() == true
    }
}.getOrDefault(false)

internal fun KtCallExpression.isReadOnlyComposableCall(): Boolean = runCatching {
    analyze(this) {
        val call = this@isReadOnlyComposableCall.resolveCall() ?: return@analyze false
        call.signature.symbol.hasReadOnlyComposableAnnotation()
    }
}.getOrDefault(false)

internal fun KtCallExpression.isRememberUpdatedStateCall(): Boolean =
    isResolvedCallToAnyOf(setOf(ComposeFqNames.RememberUpdatedState))

internal fun KtCallExpression.memoizingComposableName(): String? {
    val name = resolvedCallFqName() ?: return null
    return when (name) {
        ComposeFqNames.Remember, ComposeFqNames.RememberSaveable, ComposeFqNames.Retain -> name.shortName().asString()
        else -> null
    }
}
