// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.androidx

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression

/**
 * The size an expression has at compile time: the length of a constant string, the element count of an array
 * or list built from a literal element list, and the distinct count of a set built from constants.
 * Any other expression has no known size.
 */
internal object KnownSize {
    private fun factories(
        packageName: String,
        vararg names: String,
    ): Set<CallableId> = names.map { CallableId(FqName(packageName), Name.identifier(it)) }.toSet()

    fun KaSession.of(
        expression: KtExpression,
        constant: KaConstantValue?,
    ): Long? =
        (constant as? KaConstantValue.StringValue)?.value?.length?.toLong()
            ?: (expression as? KtCallExpression)?.let { factorySize(it) }

    private fun KaSession.factorySize(call: KtCallExpression): Long? {
        val factory =
            call
                .resolveToCall()
                ?.successfulCallOrNull<KaFunctionCall<*>>()
                ?.symbol
                ?.callableId
        val elementCounted =
            factories(
                "kotlin",
                "arrayOf",
                "emptyArray",
                "booleanArrayOf",
                "byteArrayOf",
                "charArrayOf",
                "shortArrayOf",
                "intArrayOf",
                "longArrayOf",
                "floatArrayOf",
                "doubleArrayOf",
            ) + factories("kotlin.collections", "listOf", "mutableListOf", "arrayListOf", "emptyList")
        val setFactories = factories("kotlin.collections", "setOf", "mutableSetOf", "emptySet")
        val arguments = call.valueArguments
        val literalElements = arguments.none { it.isSpread || it.isNamed() } && call.lambdaArguments.isEmpty()
        return when {
            !literalElements -> {
                null
            }

            factory in elementCounted -> {
                arguments.size.toLong()
            }

            factory in setFactories -> {
                distinctSize(arguments.map { it.getArgumentExpression() })
            }

            else -> {
                null
            }
        }
    }

    private fun KaSession.distinctSize(elements: List<KtExpression?>): Long? {
        val constants = elements.map { it?.evaluate()?.value }
        return when {
            elements.size <= 1 -> elements.size.toLong()
            null in constants -> null
            else -> constants.toSet().size.toLong()
        }
    }
}
