// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0

package org.jetbrains.compose.swing.rules.runtime

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression

internal fun KtDotQualifiedExpression.isCompositionLocalCurrentRead(): Boolean = runCatching {
    val selector = selectorExpression as? KtNameReferenceExpression ?: return@runCatching false
    if (selector.getReferencedName() != "current") return@runCatching false

    analyze(receiverExpression) {
        val type = receiverExpression.expressionType ?: return@analyze false
        type.symbol?.classId?.asSingleFqName() == ComposeFqNames.CompositionLocal ||
            type.allSupertypes.any { supertype ->
                supertype.symbol?.classId?.asSingleFqName() == ComposeFqNames.CompositionLocal
            }
    }
}.getOrDefault(false)

internal fun KtQualifiedExpression.isComposeStateValueRead(): Boolean = runCatching {
    val selector = selectorExpression as? KtNameReferenceExpression ?: return@runCatching false
    val stateNames = ComposeStateValueNames[selector.getReferencedName()] ?: return@runCatching false

    analyze(receiverExpression) {
        val type = receiverExpression.expressionType ?: return@analyze false
        type.symbol?.classId?.isComposeState(stateNames) == true ||
            type.allSupertypes.any { supertype ->
                supertype.symbol?.classId?.isComposeState(stateNames) == true
            }
    }
}.getOrDefault(false)

private val ComposeStateValueNames = mapOf(
    "value" to setOf("State", "MutableState"),
    "intValue" to setOf("IntState", "MutableIntState"),
    "longValue" to setOf("LongState", "MutableLongState"),
    "floatValue" to setOf("FloatState", "MutableFloatState"),
    "doubleValue" to setOf("DoubleState", "MutableDoubleState"),
)
