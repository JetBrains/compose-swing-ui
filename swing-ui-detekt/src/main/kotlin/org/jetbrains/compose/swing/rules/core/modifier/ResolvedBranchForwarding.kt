// Copyright 2026 Nacho Lopez, JetBrains s.r.o., and contributors.
// SPDX-License-Identifier: Apache-2.0
@file:OptIn(KaExperimentalApi::class)

package org.jetbrains.compose.swing.rules.core.modifier

import io.nlopez.compose.core.ComposeKtConfig
import org.jetbrains.compose.swing.rules.psi.unwrapArgumentExpression
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Answers, for the calls in the branches of a composable, whether each passes the composable's modifier
 * on, by resolving the callee instead of finding its declaration in the same file.
 *
 * The composable's modifier is its parameter named `modifier`, or else its first modifier-typed parameter.
 * Only resolved Unit-returning composables with a matching modifier parameter are considered. An argument
 * forwards the modifier by reference or through a modifier chain. An argument of unknown origin leaves the
 * branch undecided.
 */
internal object ResolvedBranchForwarding {
    fun resolve(
        function: KtNamedFunction,
        calls: List<KtCallExpression>,
        config: ComposeKtConfig,
    ): Map<KtCallExpression, Boolean> {
        val modifierTypes = config.getSet("customModifiers", emptySet()) + SWING_MODIFIER_FQN
        return runCatching {
            analyze(function) {
                val modifier =
                    modifierParameter(function.symbol, modifierTypes)
                        ?: return@analyze emptyMap()
                calls
                    .mapNotNull { call -> forwards(call, modifier)?.let { call to it } }
                    .toMap()
            }
        }.getOrDefault(emptyMap())
    }

    private fun KaSession.modifierParameter(
        function: KaFunctionSymbol,
        modifierTypes: Set<String>,
    ): KaValueParameterSymbol? {
        val parameters = function.valueParameters.filter { isModifier(it.returnType, modifierTypes) }
        return parameters.firstOrNull { it.name.asString() == "modifier" } ?: parameters.firstOrNull()
    }

    private fun KaSession.isModifier(
        type: KaType,
        modifierTypes: Set<String>,
    ): Boolean {
        val classId = modifierClassId(type) ?: return false
        return classId.shortClassName.asString() in modifierTypes ||
            classId.asSingleFqName().asString() in modifierTypes
    }

    private fun KaSession.forwards(
        call: KtCallExpression,
        modifier: KaValueParameterSymbol,
    ): Boolean? {
        val resolved = call.resolveCall()?.takeIf { isEmittingUnitComposable(it.signature.symbol) } ?: return null
        val modifierType = modifierClassId(modifier.returnType)
        val candidates = resolved.signature.valueParameters.filter { modifierClassId(it.returnType) == modifierType }
        val parameter = candidates.firstOrNull { it.symbol.name.asString() == "modifier" } ?: candidates.singleOrNull()
        return parameter?.let { target ->
            val argument =
                resolved.valueArgumentMapping.entries
                    .singleOrNull { (_, value) -> value.symbol == target.symbol }
                    ?.key
            if (argument == null) false else forwarding(argument, modifier)
        }
    }

    private fun KaSession.forwarding(
        expression: KtExpression,
        modifier: KaValueParameterSymbol,
    ): Boolean? =
        when (val argument = expression.unwrapArgumentExpression()) {
            is KtNameReferenceExpression -> {
                val symbol = argument.mainReference.resolveToSymbol()
                val type = modifierClassId(modifier.returnType)
                val classId = (symbol as? KaClassLikeSymbol)?.classId
                val isCompanion = classId?.outerClassId == type && classId?.shortClassName?.asString() == "Companion"
                when {
                    symbol == modifier -> true
                    classId == type || isCompanion -> false
                    else -> null
                }
            }

            is KtDotQualifiedExpression -> {
                val selector = argument.selectorExpression
                if (selector is KtCallExpression) {
                    chainForwarding(selector.resolveCall(), argument.receiverExpression, modifier)
                } else {
                    selector?.let { forwarding(it, modifier) }
                }
            }

            is KtBinaryExpression -> {
                val left = argument.left
                if (left == null || argument.operationReference.getReferencedName() != "then") {
                    null
                } else {
                    val call = argument.resolveToCall()?.successfulCallOrNull<KaFunctionCall<*>>()
                    chainForwarding(call, left, modifier)
                }
            }

            else -> {
                null
            }
        }

    private fun KaSession.chainForwarding(
        call: KaFunctionCall<*>?,
        receiver: KtExpression,
        modifier: KaValueParameterSymbol,
    ): Boolean? {
        val type = modifierClassId(modifier.returnType)
        val signature = call?.signature
        val chain = signature?.symbol
        val callable = chain?.callableId
        val receiverType = chain?.receiverParameter?.returnType?.let { modifierClassId(it) }
        val isModifierChain = receiverType == type || callable?.classId == type
        if (signature == null || modifierClassId(signature.returnType) != type || !isModifierChain) return null

        val forwarded = forwarding(receiver, modifier)
        val chainName = callable?.callableName
        val appended =
            if (chainName?.asString() == "then") {
                call.valueArgumentMapping.keys
                    .singleOrNull()
                    ?.let { forwarding(it, modifier) }
            } else {
                false
            }
        return when {
            forwarded == true || appended == true -> true
            forwarded == false && appended == false -> false
            else -> null
        }
    }

    private fun KaSession.modifierClassId(type: KaType): ClassId? = (type.fullyExpandedType as? KaClassType)?.classId

    private fun KaSession.isEmittingUnitComposable(callee: KaFunctionSymbol): Boolean =
        callee.returnType.isUnitType &&
            callee.annotations.contains(ClassId.topLevel(FqName("androidx.compose.runtime.Composable"))) &&
            !callee.annotations.contains(ClassId.topLevel(FqName("androidx.compose.runtime.ReadOnlyComposable")))
}
