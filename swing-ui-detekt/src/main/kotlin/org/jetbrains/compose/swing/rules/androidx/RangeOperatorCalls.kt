// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.androidx

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCompoundArrayAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCompoundVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression

/**
 * Every function an expression calls, including the operator functions an index, a binary operator or a compound
 * assignment resolves to. `a[i] += x` calls `get`, an operator function and `set`.
 */
internal fun KaSession.functionCallsOf(expression: KtExpression): List<ArgumentedCall> =
    when (val call = expression.resolveToCall()?.successfulCallOrNull<KaCall>()) {
        is KaFunctionCall<*> -> {
            listOf(call.argumented())
        }

        is KaCompoundArrayAccessCall -> {
            listOf(call.getterCall, call.setterCall, call.compoundOperation.operationCall).map { it.argumented() }
        }

        is KaCompoundVariableAccessCall -> {
            listOfNotNull(call.operatorCallOf(expression))
        }

        else -> {
            emptyList()
        }
    }

private fun KaFunctionCall<*>.argumented() = ArgumentedCall(symbol, valueArgumentMapping)

/**
 * The analysis keys the operand of `a += x` on `a` instead of `x`, so the operand is taken from the expression.
 */
private fun KaCompoundVariableAccessCall.operatorCallOf(expression: KtExpression): ArgumentedCall? {
    val operand = (expression as? KtBinaryExpression)?.right ?: return null
    val operation = compoundOperation.operationCall
    return ArgumentedCall(operation.symbol, operation.valueArgumentMapping.values.associateBy { operand })
}

/**
 * The array access that `a[i] = x` or `a[i] += x` writes through is resolved with its assignment, not on its own.
 */
internal fun KtArrayAccessExpression.isAssignmentTarget(): Boolean {
    val assignment = parent as? KtBinaryExpression
    return assignment?.left == this && assignment.operationToken in KtTokens.ALL_ASSIGNMENTS
}
