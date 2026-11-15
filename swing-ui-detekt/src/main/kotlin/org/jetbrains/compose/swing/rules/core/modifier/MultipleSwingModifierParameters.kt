// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.core.modifier

import com.intellij.psi.PsiElement
import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.compose.swing.rules.core.hasComposableAnnotation
import org.jetbrains.compose.swing.rules.core.isInOverrideOrActualSignature
import org.jetbrains.compose.swing.rules.core.isOverrideOrActual
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeAliasSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.unwrapNullability

/**
 * Reports a composable, or a `@Composable` function type, that takes more than one `SwingModifier` parameter.
 *
 * A composable applies the one chain it is given to the root of what it emits. A second chain has no
 * place to go: a caller cannot tell which part of the content each one styles, and the composable has no
 * single root to hold either. Give each part its own composable, or take the second as a parameter of
 * another type that names what it styles.
 *
 * Value parameters and context parameters both count, at any visibility, and so do the context and value
 * parameters of a `@Composable` function type, wherever it sits. A parameter counts when its type, with type
 * aliases expanded, is `SwingModifier`. A declaration is composable when it carries
 * `androidx.compose.runtime.Composable`, and so is a function type that does, written out, named through a type
 * alias, or written as a function class such as `Function2` with type arguments. A type alias is reported
 * at its uses, not at its declaration. Each alias use or function class type is reported once, since the
 * chains it expands to are not written there, and a type reference nested in a reported alias use is not
 * reported again.
 *
 * The first chain parameter is left alone and every further one is reported. An `override` or `actual` is
 * skipped, and so is a function type in its signature, since the declaration it answers for is reported
 * instead. A function type in a body is still checked. Accepted limit: an override that widens a base's `Any`
 * to a two-chain function type, such as a return type, goes unreported, because the base declares no chain for
 * it to answer for.
 *
 * The rule resolves types, so it requires the Analysis API. A type that does not resolve is never reported.
 */
internal object MultipleSwingModifierParameters : ComposeSwingRule {
    override val id: String = "MultipleSwingModifierParameters"
    override val description: String = "A composable should take at most one `SwingModifier` parameter"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                if (function.nameIdentifier == null || function.isOverrideOrActual()) return
                analyze(function) {
                    if (function.symbol.hasComposableAnnotation()) extraChains(function) else emptyList()
                }.forEach { (element, message) -> reporter.report(element, message) }
            }

            override fun visitProperty(property: KtProperty) {
                if (property.isOverrideOrActual()) return
                analyze(property) {
                    val getter = (property.symbol as? KaPropertySymbol)?.getter
                    if (getter?.hasComposableAnnotation() == true) extraChains(property) else emptyList()
                }.forEach { (element, message) -> reporter.report(element, message) }
            }

            @OptIn(KaExperimentalApi::class)
            override fun visitTypeReference(typeReference: KtTypeReference) {
                if (typeReference.parents.any { it is KtTypeAlias }) return
                val element = typeReference.typeElement?.unwrapNullability()
                val written = element is KtFunctionType || element is KtUserType
                if (!written || typeReference.isInOverrideOrActualSignature()) return
                analyze(typeReference) {
                    val alias = isAliasUse(typeReference)
                    val type = typeReference.type.fullyExpandedType
                    when {
                        typeReference.parents
                            .filterIsInstance<KtTypeReference>()
                            .any { isAliasUse(it) && containsExtraChains(it.type) } -> {
                            emptyList()
                        }

                        alias -> {
                            if (containsExtraChains(type)) {
                                listOf(furtherChain(typeReference, FUNCTION_TYPE_PARAMETER))
                            } else {
                                emptyList()
                            }
                        }

                        type !is KaFunctionType || !type.hasComposableAnnotation() -> {
                            emptyList()
                        }

                        element is KtFunctionType -> {
                            extraChains(element)
                        }

                        else -> {
                            extraChains(typeReference, type)
                        }
                    }
                }.forEach { (element, message) -> reporter.report(element, message) }
            }

            @OptIn(KaExperimentalApi::class)
            private fun KaSession.isAliasUse(typeReference: KtTypeReference): Boolean =
                (typeReference.typeElement?.unwrapNullability() as? KtUserType)
                    ?.referenceExpression
                    ?.mainReference
                    ?.resolveToSymbols()
                    ?.singleOrNull() is KaTypeAliasSymbol

            private fun KaSession.extraChains(declaration: KtCallableDeclaration): List<Pair<PsiElement, String>> =
                (declaration.contextParameters + declaration.valueParameters)
                    .filter { parameter -> parameter.typeReference?.let { isChain(it.type) } == true }
                    .drop(1)
                    .map { parameter ->
                        furtherChain(parameter, "The `${parameter.name}` parameter")
                    }

            private fun KaSession.extraChains(functionType: KtFunctionType): List<Pair<PsiElement, String>> {
                val written: List<Pair<KtElement, KtTypeReference>> =
                    functionType.contextReceiversTypeReferences.map { it to it } +
                        functionType.parameters.mapNotNull { parameter ->
                            parameter.typeReference?.let { parameter to it }
                        }
                return written
                    .filter { (_, typeReference) -> isChain(typeReference.type) }
                    .drop(1)
                    .map { (element, _) ->
                        val name = (element as? KtParameter)?.name
                        furtherChain(element, name?.let { "The `$it` parameter" } ?: FUNCTION_TYPE_PARAMETER)
                    }
            }

            @OptIn(KaExperimentalApi::class)
            private fun KaSession.extraChains(
                typeReference: KtTypeReference,
                type: KaFunctionType,
            ): List<Pair<PsiElement, String>> {
                val chains = (type.contextReceivers.map { it.type } + type.parameterTypes).count { isChain(it) }
                if (chains <= 1) return emptyList()
                return listOf(furtherChain(typeReference, FUNCTION_TYPE_PARAMETER))
            }

            @OptIn(KaExperimentalApi::class)
            private fun KaSession.containsExtraChains(type: KaType): Boolean =
                when (val expanded = type.fullyExpandedType) {
                    is KaFunctionType -> {
                        val parameters = expanded.contextReceivers.map { it.type } + expanded.parameterTypes
                        (expanded.hasComposableAnnotation() && parameters.count { isChain(it) } > 1) ||
                            parameters.any { containsExtraChains(it) } ||
                            expanded.receiverType?.let { containsExtraChains(it) } == true ||
                            containsExtraChains(expanded.returnType)
                    }

                    is KaClassType -> {
                        expanded.typeArguments.any { argument ->
                            argument.type?.let { containsExtraChains(it) } == true
                        }
                    }

                    else -> {
                        false
                    }
                }

            private fun furtherChain(
                element: PsiElement,
                subject: String,
            ): Pair<PsiElement, String> = element to furtherChainMessage(subject)

            private fun KaSession.isChain(type: KaType): Boolean =
                (type.fullyExpandedType as? KaClassType)?.classId == ClassId.topLevel(FqName(SWING_MODIFIER_FQN))
        }
}

private const val FUNCTION_TYPE_PARAMETER: String = "The function type's parameter"

private fun furtherChainMessage(subject: String): String =
    "$subject is another `$SWING_MODIFIER` parameter. A modifier changes the outer behavior and " +
        "appearance of the component a composable emits, so a second one has no component of its " +
        "own to reach. Keep one `$SWING_MODIFIER` parameter. Take an inner part as content in a " +
        "slot, or split the composable in two."
