// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0
// Adapted to the library's host-neutral rule API.

package org.jetbrains.compose.swing.rules.runtime

import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.compose.swing.rules.psi.isEagerScopeFunctionLambda
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/** Runs [UnnecessaryComposableCheck] as a [ComposeSwingRule]. */
internal object UnnecessaryComposable : ComposeSwingRule {
    override val id: String = "UnnecessaryComposable"
    override val description: String =
        "Composable declarations that do not use composition should not be marked as @Composable."
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid = UnnecessaryComposableCheck(reporter)
}

// Upstream compose-rules structure, kept so later releases replay onto the fork without restructuring.

/**
 * Reports `@Composable` declarations that do not use composition and can remove the annotation.
 */
@Suppress("ReturnCount")
internal class UnnecessaryComposableCheck(
    private val reporter: ComposeSwingRuleReporter,
) : KtVisitorVoid() {
    override fun visitNamedFunction(function: KtNamedFunction) {
        if (!function.isComposable()) return
        if (function.isReadOnlyComposable()) return
        if (function.isContractDeclaration()) return
        if (function.hasComposableSlotParameter()) return
        val body = function.bodyExpression ?: return
        if ((body as? KtBlockExpression)?.statements?.isEmpty() == true) return
        if (body.usesComposition()) return
        if (function.valueParameters.any { parameter -> parameter.defaultValue?.usesComposition() == true }) return

        reportUnnecessaryComposable(function)
    }

    override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
        if (!accessor.isGetter) return
        if (!accessor.isComposable()) return
        if (accessor.isReadOnlyComposable()) return
        if (accessor.isContractDeclaration()) return
        val body = accessor.bodyExpression ?: return
        if ((body as? KtBlockExpression)?.statements?.isEmpty() == true) return
        if (body.usesComposition()) return

        reportUnnecessaryComposable(accessor)
    }

    private fun reportUnnecessaryComposable(element: KtElement) {
        reporter.report(element, UNNECESSARY_COMPOSABLE_MESSAGE)
    }

    private fun KtElement.usesComposition(): Boolean {
        var usesComposition = false
        accept(
            object : KtTreeVisitorVoid() {
                override fun visitCallExpression(expression: KtCallExpression) {
                    if (usesComposition) return
                    if (expression.isComposableCall()) {
                        usesComposition = true
                        return
                    }
                    super.visitCallExpression(expression)
                }

                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    if (usesComposition) return
                    if (expression.isComposablePropertyRead() || expression.isDelegatedComposeStateRead()) {
                        usesComposition = true
                        return
                    }
                    super.visitSimpleNameExpression(expression)
                }

                override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                    if (usesComposition) return
                    if (expression.isCompositionLocalCurrentRead() || expression.isComposeStateValueReadExpression()) {
                        usesComposition = true
                        return
                    }
                    super.visitDotQualifiedExpression(expression)
                }

                override fun visitSafeQualifiedExpression(expression: KtSafeQualifiedExpression) {
                    if (usesComposition) return
                    if (expression.isComposeStateValueReadExpression()) {
                        usesComposition = true
                        return
                    }
                    super.visitSafeQualifiedExpression(expression)
                }

                override fun visitNamedFunction(function: KtNamedFunction) {
                    // Nested function bodies are not evaluated by the composable declaration.
                }

                override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
                    // Nested accessor bodies are not evaluated by the composable declaration.
                }

                override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
                    if (lambdaExpression.isEagerScopeFunctionLambda()) {
                        super.visitLambdaExpression(lambdaExpression)
                    }
                }
            },
        )
        return usesComposition
    }

    private fun KtNamedFunction.isContractDeclaration(): Boolean {
        if (hasAnyContractModifier()) return true
        return getStrictParentOfType<KtClass>()?.isInterface() == true
    }

    private fun KtPropertyAccessor.isContractDeclaration(): Boolean {
        if (property.hasAnyContractModifier()) return true
        return getStrictParentOfType<KtClass>()?.isInterface() == true
    }

    private fun KtNamedFunction.hasComposableSlotParameter(): Boolean =
        valueParameters.any(KtParameter::hasComposableType) ||
            receiverTypeReference?.hasComposableType() == true

    internal companion object {
        const val UNNECESSARY_COMPOSABLE_MESSAGE =
            "This declaration is marked `@Composable` without using composition. " +
                "The annotation restricts where callers can use it. " +
                "Remove `@Composable`."
    }
}

private val ContractModifiers =
    setOf(
        KtTokens.OVERRIDE_KEYWORD,
        KtTokens.OPEN_KEYWORD,
        KtTokens.ABSTRACT_KEYWORD,
        KtTokens.EXPECT_KEYWORD,
        KtTokens.ACTUAL_KEYWORD,
        KtTokens.EXTERNAL_KEYWORD,
    )

private fun KtNamedFunction.hasAnyContractModifier(): Boolean = ContractModifiers.any(::hasModifier)

private fun KtProperty.hasAnyContractModifier(): Boolean = ContractModifiers.any(::hasModifier)

private fun KtQualifiedExpression.isComposeStateValueReadExpression(): Boolean =
    isComposeStateValueRead() && !isAssignmentLeftHandSide()

private fun KtElement.isAssignmentLeftHandSide(): Boolean =
    (parent as? KtBinaryExpression)?.let { expression ->
        expression.operationToken == KtTokens.EQ && expression.left == this
    } == true
