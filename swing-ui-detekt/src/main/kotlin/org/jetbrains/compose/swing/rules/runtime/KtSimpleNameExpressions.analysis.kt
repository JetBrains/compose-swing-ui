// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0

package org.jetbrains.compose.swing.rules.runtime

import org.jetbrains.compose.swing.rules.psi.resolvedPropertyRead
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaVariableAccessCall
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.sourcePsiSafe
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

internal fun KtSimpleNameExpression.isComposablePropertyRead(): Boolean = resolvedPropertyRead(
    symbolPredicate = { property -> property.getter?.hasComposableAnnotation() == true },
    sourcePredicate = { property -> property.getter?.isComposable() == true },
)

internal fun KtSimpleNameExpression.isReadOnlyComposablePropertyRead(): Boolean = resolvedPropertyRead(
    symbolPredicate = { property -> property.getter?.hasReadOnlyComposableAnnotation() == true },
    sourcePredicate = { property -> property.getter?.isReadOnlyComposable() == true },
)

internal fun KtSimpleNameExpression.isDelegatedComposeStateRead(): Boolean = runCatching {
    if (isPlainAssignmentLeftHandSide()) return@runCatching false

    analyze(this) {
        val property = ((resolveToCall() as? KaVariableAccessCall)?.signature?.symbol as? KaPropertySymbol)
            ?.sourcePsiSafe<KtProperty>()
            ?: mainReference.resolveToSymbol()?.sourcePsiSafe<KtProperty>()
            ?: return@analyze false
        val delegateExpression = property.delegateExpression ?: return@analyze false
        val delegateType = delegateExpression.expressionType ?: return@analyze false
        delegateType.symbol?.classId?.isComposeState(ComposeStateNames) == true ||
            delegateType.allSupertypes.any { supertype ->
                supertype.symbol?.classId?.isComposeState(ComposeStateNames) == true
            }
    }
}.getOrDefault(false)

private val ComposeStateNames = setOf("State", "MutableState")

private fun KtSimpleNameExpression.isPlainAssignmentLeftHandSide(): Boolean {
    val assignedExpression = (parent as? KtDotQualifiedExpression)
        ?.takeIf { expression -> expression.selectorExpression == this }
        ?: this
    return (assignedExpression.parent as? KtBinaryExpression)?.let { expression ->
        expression.operationToken == KtTokens.EQ && expression.left == assignedExpression
    } == true
}
