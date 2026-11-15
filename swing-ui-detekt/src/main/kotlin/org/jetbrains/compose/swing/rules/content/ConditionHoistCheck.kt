// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0
// Adapted to the library's host-neutral rule API.

package org.jetbrains.compose.swing.rules.content

import org.jetbrains.compose.swing.rules.ComposeRulesConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.compose.swing.rules.psi.hasExplicitArgumentMappedToAny
import org.jetbrains.compose.swing.rules.runtime.hasComposableAnnotation
import org.jetbrains.compose.swing.rules.runtime.isComposable
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.sourcePsiSafe
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/** Runs [ConditionHoistCheck] as a [ComposeSwingRule]. */
internal object ConditionHoist : ComposeSwingRule {
    override val id: String = "ConditionHoist"
    override val description: String =
        "Compose layouts with a single conditional expression should hoist the condition."
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid = ConditionHoistCheck(reporter, config)
}

// Upstream compose-rules structure, kept so later releases replay onto the fork without restructuring.

/**
 * Reports Compose layouts whose only content is a conditional composable child.
 */
@Suppress("ReturnCount", "TooManyFunctions")
internal class ConditionHoistCheck(
    private val reporter: ComposeSwingRuleReporter,
    config: ComposeSwingRuleConfig,
) : KtVisitorVoid() {
    private val ignoreCallsWithArgumentNames =
        config.valueOrDefault(
            "ignoreCallsWithArgumentNames",
            listOf(
                "modifier",
                "contentAlignment",
                "horizontalArrangement",
                "verticalAlignment",
            ),
        )

    private val ignoreCallsWithArgumentNamesSet by lazy { ignoreCallsWithArgumentNames.toSet() }

    private val configuredLayouts = ComposeRulesConfig(config).getList("contentEmitters", emptyList()).toSet()

    private val contentEmission = SwingContentEmission(config)

    override fun visitNamedFunction(function: KtNamedFunction) {
        if (!function.isComposable()) return

        checkBody(function.bodyExpression)
    }

    override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
        if (!accessor.isGetter) return
        if (!accessor.isComposable()) return

        checkBody(accessor.bodyExpression)
    }

    private fun checkBody(body: KtExpression?) {
        body ?: return
        val candidates =
            body
                .hoistableCallsInCurrentDeclaration()
                .asSequence()
                .filterNot { call -> call.hasExplicitArgumentMappedToAny(ignoreCallsWithArgumentNamesSet) }
                .filterNot { call -> call.hasExplicitNonLambdaArguments() }
                .mapNotNull { call -> call.singleComposableLambdaArgument() }
                .filterNot { lambda -> lambda.hasValueParameters() }
                .mapNotNull { lambda -> lambda.singleConditionalComposableChild() }
                .distinct()

        candidates.forEach { ifExpression -> reporter.report(ifExpression, CONDITION_HOIST_MESSAGE) }
    }

    private fun KtCallExpression.singleComposableLambdaArgument(): KtLambdaExpression? =
        directLambdaArguments().singleOrNull { lambda -> lambda.isComposable() }

    private fun KtCallExpression.directLambdaArguments(): List<KtLambdaExpression> =
        (
            valueArguments.mapNotNull { argument -> argument.getArgumentExpression() as? KtLambdaExpression } +
                lambdaArguments.mapNotNull { argument -> argument.getLambdaExpression() }
        ).distinct()

    private fun KtCallExpression.hasExplicitNonLambdaArguments(): Boolean =
        valueArguments.any { argument -> argument.getArgumentExpression() !is KtLambdaExpression }

    @OptIn(KaExperimentalApi::class)
    private fun KtCallExpression.isHoistableLayout(): Boolean {
        if (parent is KtSafeQualifiedExpression) return false
        return runCatching {
            analyze(this) {
                val call = this@isHoistableLayout.resolveCall() ?: return@analyze false
                val symbol = call.signature.symbol
                if (!symbol.hasComposableAnnotation()) return@analyze false

                val callableId = symbol.callableId
                val fqName = callableId?.asSingleFqName()?.asString()
                if (fqName in DefaultHoistableLayouts) {
                    call.valueArgumentMapping.values
                        .singleOrNull()
                        ?.name
                        ?.asString() == "content"
                } else {
                    fqName in configuredLayouts ||
                        callableId?.callableName?.asString() in configuredLayouts ||
                        calleeExpression?.text in configuredLayouts
                }
            }
        }.getOrDefault(false)
    }

    private fun KtLambdaExpression.hasValueParameters(): Boolean =
        valueParameters.isNotEmpty() ||
            runCatching {
                analyze(this) {
                    (functionLiteral.functionType as? KaFunctionType)?.parameterTypes?.isNotEmpty() == true ||
                        (expectedType as? KaFunctionType)?.parameterTypes?.isNotEmpty() == true
                }
            }.getOrDefault(false)

    private fun KtLambdaExpression.singleConditionalComposableChild(): KtIfExpression? {
        val ifExpression = bodyExpression?.statements?.singleOrNull() as? KtIfExpression ?: return null
        if (ifExpression.`else`?.hasWork() == true) return null
        if (ifExpression.condition?.dependsOn(this) == true) return null
        if (ifExpression.then?.hasEmittedComposableCall() != true) return null
        return ifExpression
    }

    private fun KtExpression.hasWork(): Boolean =
        this !is KtBlockExpression ||
            statements.any { statement ->
                // The initializer of a local lambda value only declares deferred work.
                statement !is KtProperty || statement.initializer !is KtLambdaExpression
            }

    @OptIn(KaExperimentalApi::class)
    private fun KtExpression.dependsOn(lambda: KtLambdaExpression): Boolean =
        runCatching {
            analyze(this) {
                val lambdaSymbol = lambda.functionLiteral.symbol
                val receiver = lambdaSymbol.receiverParameter
                val expressions = sequenceOf(this@dependsOn) + collectDescendantsOfType<KtExpression>()
                expressions.any { expression ->
                    when (expression) {
                        is KtReturnExpression -> {
                            expression.resolveSymbol() == lambdaSymbol
                        }

                        is KtThisExpression -> {
                            receiver != null && expression.resolveSymbol() == receiver
                        }

                        else -> {
                            val call =
                                receiver?.let {
                                    expression
                                        .resolveToCall()
                                        ?.successfulCallOrNull<KaCallableMemberCall<*, *>>()
                                        ?.partiallyAppliedSymbol
                                }
                            call != null &&
                                (
                                    call.dispatchReceiver.isReceiver(receiver) ||
                                        call.extensionReceiver.isReceiver(receiver)
                                )
                        }
                    }
                }
            }
        }.getOrDefault(true)

    @OptIn(KaExperimentalApi::class)
    private fun KaReceiverValue?.isReceiver(receiver: KaSymbol?): Boolean =
        when (this) {
            is KaImplicitReceiverValue -> symbol == receiver
            is KaSmartCastedReceiverValue -> original.isReceiver(receiver)
            else -> false
        }

    @OptIn(KaExperimentalApi::class)
    private fun KtElement.hasEmittedComposableCall(): Boolean {
        var hasEmittedComposableCall = false
        accept(
            object : KtTreeVisitorVoid() {
                override fun visitCallExpression(expression: KtCallExpression) {
                    if (hasEmittedComposableCall) return
                    hasEmittedComposableCall =
                        runCatching {
                            analyze(expression) {
                                val symbol = expression.resolveCall()?.signature?.symbol ?: return@analyze false
                                val fqName = symbol.callableId?.asSingleFqName()?.asString()
                                val declaration =
                                    symbol.sourcePsiSafe<KtNamedFunction>()?.takeIf {
                                        it.containingKtFile == expression.containingKtFile
                                    }
                                val emits =
                                    symbol.hasComposableAnnotation() &&
                                        contentEmission.emitsContent(
                                            expression,
                                            resolvedFqName = fqName,
                                            declaration = declaration,
                                        )
                                if (emits) return@analyze true
                                val content = fqName?.let { contentEmission.transparentContent(expression, it) }
                                content?.bodyExpression?.hasEmittedComposableCall() == true
                            }
                        }.getOrDefault(false)
                    if (!hasEmittedComposableCall) super.visitCallExpression(expression)
                }

                override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
                    // Deferred lambda bodies are not emitted by this branch.
                }

                override fun visitNamedFunction(function: KtNamedFunction) {
                    // Nested function bodies are not emitted by this branch.
                }

                override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
                    // Nested accessor bodies are not emitted by this branch.
                }

                override fun visitClassOrObject(classOrObject: KtClassOrObject) {
                    // Member bodies are not emitted by this branch.
                }
            },
        )
        return hasEmittedComposableCall
    }

    private fun KtElement.hoistableCallsInCurrentDeclaration(): List<KtCallExpression> {
        val calls = mutableListOf<KtCallExpression>()
        accept(
            object : KtTreeVisitorVoid() {
                override fun visitCallExpression(expression: KtCallExpression) {
                    if (expression.isHoistableLayout()) {
                        calls += expression
                    }
                    super.visitCallExpression(expression)
                }

                override fun visitNamedFunction(function: KtNamedFunction) {
                    // Nested function bodies are visited as their own declarations.
                }

                override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
                    // Nested accessor bodies are visited as their own declarations.
                }

                override fun visitClassOrObject(classOrObject: KtClassOrObject) {
                    // Member bodies are not part of the current composable declaration.
                }
            },
        )
        return calls
    }
}

private val DefaultHoistableLayouts =
    setOf(
        "org.jetbrains.compose.swing.components.layout.Panel",
        "org.jetbrains.compose.swing.foundation.layout.Box",
        "org.jetbrains.compose.swing.foundation.layout.Column",
        "org.jetbrains.compose.swing.foundation.layout.Row",
    )

private const val CONDITION_HOIST_MESSAGE =
    "This layout stays mounted when the condition for its only content is false. An empty Swing container can still " +
        "take up space. Move the condition outside the layout if the container should appear only with its content."
