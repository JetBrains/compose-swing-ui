// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0

// Adapted from compose-rules.

package org.jetbrains.compose.swing.rules.runtime

import org.jetbrains.compose.swing.rules.psi.isDeclaredInLibrary
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.psi.KtBinaryExpression

/**
 * Whether the operator is built in, like `&&` or `?:`, or calls a non-composable library function
 * or a generated member, like the `equals` of a data class.
 */
internal fun KtBinaryExpression.isBuiltInOrLibraryOperator(): Boolean = runCatching {
    analyze(this) {
        val symbol = this@isBuiltInOrLibraryOperator.resolveToCall()?.successfulFunctionCallOrNull()?.symbol
            ?: return@analyze true
        !symbol.hasComposableAnnotation() &&
            (isDeclaredInLibrary(symbol) || symbol.origin == KaSymbolOrigin.SOURCE_MEMBER_GENERATED)
    }
}.getOrDefault(false)
