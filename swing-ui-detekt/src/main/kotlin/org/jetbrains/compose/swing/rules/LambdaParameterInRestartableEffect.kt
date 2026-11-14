// Copyright 2024 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0
// Adapted to the library's host-neutral rule API.

package org.jetbrains.compose.swing.rules

import com.intellij.psi.PsiElement
import io.nlopez.compose.core.util.findAllChildrenByClass
import io.nlopez.compose.core.util.findShadowingRedeclarations
import io.nlopez.compose.core.util.isLambda
import io.nlopez.compose.core.util.lambdaTypes
import org.jetbrains.compose.swing.rules.composerules.isResolvedCallToAnyNamed
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.isDotSelector
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/** Reports lambda parameters captured by restartable effects without a corresponding effect key. */
internal object LambdaParameterInRestartableEffect : ComposeSwingRule {
    override val id = "LambdaParameterInRestartableEffect"
    override val description = "Lambda parameters should not be used inside restartable effects"
    override val severity = ComposeSwingRule.Severity.Error
    override val activeByDefault = true
    override val requiresAnalysisApi = false

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid {
        val composeConfig = ComposeRulesConfig(config)
        return object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                if (element is KtFunction && element.isComposable()) {
                    reportUnkeyedParameters(element, reporter, composeConfig)
                }
            }
        }
    }

    private fun reportUnkeyedParameters(
        composable: KtFunction,
        reporter: ComposeSwingRuleReporter,
        config: ComposeRulesConfig,
    ) {
        val effects =
            composable
                .findAllChildrenByClass<KtCallExpression>()
                .mapNotNull { call -> call.restartableEffectName()?.let { call to it } }
                .toList()
        if (effects.isEmpty()) return

        val lambdaTypes = composable.containingKtFile.lambdaTypes(config)
        val lambdaParameters =
            composable.valueParameters
                .filter { it.isLambdaParameter || it.typeReference?.isLambda(lambdaTypes) == true }
                .mapNotNull { parameter -> parameter.name?.let { it to parameter } }
                .toMap()
        val lambdaParameterNames = lambdaParameters.keys
        if (lambdaParameterNames.isEmpty()) return

        val unkeyedLambdaParameterNames =
            effects
                .asSequence()
                .flatMap { (effect, effectName) ->
                    val usedLambdaParameterNames =
                        parametersReferencedInEffect(effect, effectName, lambdaParameterNames).toSet()
                    val keyedLambdaParameterNames =
                        effect.valueArguments
                            .asSequence()
                            .mapNotNull { it.getArgumentExpression() }
                            .filterIsInstance<KtReferenceExpression>()
                            .map { it.text }
                            .filter { it in usedLambdaParameterNames }
                            .toSet()
                    val shadowedParameters =
                        usedLambdaParameterNames.filter {
                            effect.findShadowingRedeclarations(parameterName = it, stopAt = composable).any() ||
                                effect.parents
                                    .takeWhile { parent -> parent != composable }
                                    .filterIsInstance<KtFunction>()
                                    .any { function ->
                                        function.valueParameters.any { parameter -> parameter.name == it }
                                    }
                        }
                    (usedLambdaParameterNames - keyedLambdaParameterNames - shadowedParameters).asSequence()
                }.toSet()
        for (parameterName in unkeyedLambdaParameterNames) {
            val parameter = lambdaParameters.getValue(parameterName)
            reporter.report(parameter.nameIdentifier ?: parameter, LAMBDA_IN_RESTARTABLE_EFFECT_MESSAGE)
        }
    }

    private fun parametersReferencedInEffect(
        effect: KtCallExpression,
        effectName: String,
        lambdaParameterNames: Set<String>,
    ): Sequence<String> {
        val body =
            effect.lambdaArguments
                .lastOrNull()
                ?.getLambdaExpression()
                ?.bodyExpression ?: return emptySequence()
        val calls = body.findAllChildrenByClass<KtCallExpression>().filterNot { it.isDotSelector() }
        val effectScopeCalls =
            when (effectName) {
                "DisposableEffect" -> setOf("onDispose")
                "LifecycleStartEffect" -> setOf("onStopOrDispose")
                "LifecycleResumeEffect" -> setOf("onPauseOrDispose")
                else -> emptySet()
            }
        val invoked =
            calls
                .mapNotNull { it.calleeExpression?.text }
                .filter { it in lambdaParameterNames && it !in effectScopeCalls }
        val forwarded =
            calls.flatMap { call ->
                call.valueArguments.mapNotNull { forwardedParameter(it.getArgumentExpression(), lambdaParameterNames) }
            }
        return invoked + forwarded
    }

    private fun KtCallExpression.restartableEffectName(): String? {
        val name = (calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ?: return null
        val qualified = (parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression === this }
        return if (qualified != null) {
            name.takeIf { "${qualified.receiverExpression.text}.$name" in restartableEffectFqNames }
        } else {
            importedRestartableEffectName(name)
        }
    }

    private fun KtCallExpression.importedRestartableEffectName(name: String): String? {
        val effect = restartableEffectFqNames.firstOrNull { containingKtFile.resolvesImportedName(name, it) }
        if (effect != null && !hasVisibleNamesake(name)) return effect.substringAfterLast('.')

        val candidate = effect ?: restartableEffectFqNames.firstOrNull { it.endsWith(".$name") }
        return candidate?.takeIf { isResolvedCallToAnyNamed(setOf(it)) }?.substringAfterLast('.')
    }

    private fun forwardedParameter(
        expression: KtExpression?,
        lambdaParameterNames: Set<String>,
    ): String? =
        when (expression) {
            is KtReferenceExpression -> {
                expression.text.takeIf { it in lambdaParameterNames }
            }

            is KtIfExpression -> {
                expression.then?.text?.takeIf { it in lambdaParameterNames }
                    ?: expression.`else`?.text?.takeIf { it in lambdaParameterNames }
            }

            else -> {
                null
            }
        }
}

internal fun KtCallExpression.hasVisibleNamesake(name: String): Boolean =
    containingKtFile.declarations.any { it.name == name } ||
        parents.any { owner ->
            when (owner) {
                is KtBlockExpression -> {
                    owner.statements.any { it.endOffset <= startOffset && it.declaresName(name) }
                }

                is KtClassOrObject -> {
                    owner.declaresMember(name)
                }

                is KtFunction -> {
                    owner.name == name || owner.valueParameters.any { it.name == name }
                }

                else -> {
                    false
                }
            }
        }

private fun KtClassOrObject.declaresMember(name: String): Boolean =
    declarations.any { it.name == name } ||
        primaryConstructorParameters.any { it.name == name } ||
        companionObjects.any { companion -> companion.declarations.any { it.name == name } }

private fun KtExpression.declaresName(name: String): Boolean =
    when (this) {
        is KtNamedDeclaration -> this.name == name
        is KtDestructuringDeclaration -> entries.any { it.name == name }
        else -> false
    }

private val restartableEffectFqNames =
    setOf(
        "androidx.compose.runtime.DisposableEffect",
        "androidx.compose.runtime.LaunchedEffect",
        "androidx.compose.runtime.produceState",
        "androidx.lifecycle.compose.LifecycleResumeEffect",
        "androidx.lifecycle.compose.LifecycleStartEffect",
    )

private const val LAMBDA_IN_RESTARTABLE_EFFECT_MESSAGE =
    "This effect captures a callback outside its keys. " +
        "The running effect can keep calling an old callback after the parameter changes. " +
        "Add the callback as a key if changing it should restart the effect. To keep the effect running, " +
        "wrap the callback in `rememberUpdatedState` and read its value inside the effect."
