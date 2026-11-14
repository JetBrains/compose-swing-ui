// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0
// Adapted to the library's host-neutral rule API.

package org.jetbrains.compose.swing.rules.composerules

import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtVisitorVoid

/** Runs [InvalidReadOnlyComposableCheck] as a [ComposeSwingRule]. */
internal object InvalidReadOnlyComposable : ComposeSwingRule {
    override val id: String = "InvalidReadOnlyComposable"
    override val description: String =
        "Functions marked as @ReadOnlyComposable should only call other " +
            "read-only composables."
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid = InvalidReadOnlyComposableCheck(reporter)
}

/**
 * Reports `@ReadOnlyComposable` declarations that make composable calls which are not also
 * `@ReadOnlyComposable`.
 */
internal class InvalidReadOnlyComposableCheck(
    private val reporter: ComposeSwingRuleReporter,
) : KtVisitorVoid() {
    override fun visitNamedFunction(function: KtNamedFunction) {
        if (!function.isComposable() || !function.isReadOnlyComposable()) return

        function.bodyExpression?.findFirstNonReadOnlyComposableCall()?.let { call ->
            reporter.report(call, INVALID_READ_ONLY_COMPOSABLE_MESSAGE)
        }
    }

    override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
        if (!accessor.isGetter || !accessor.isComposable() || !accessor.isReadOnlyComposable()) return

        accessor.bodyExpression?.findFirstNonReadOnlyComposableCall()?.let { call ->
            reporter.report(call, INVALID_READ_ONLY_COMPOSABLE_MESSAGE)
        }
    }

    private fun KtElement.findFirstNonReadOnlyComposableCall(): KtCallExpression? {
        var firstInvalidCall: KtCallExpression? = null
        var firstInvalidPropertyRead: KtSimpleNameExpression? = null
        accept(
            object : KtTreeVisitorVoid() {
                override fun visitCallExpression(expression: KtCallExpression) {
                    if (firstInvalidCall != null || firstInvalidPropertyRead != null) return
                    if (expression.isComposableCall() && !expression.isReadOnlyComposableCall()) {
                        firstInvalidCall = expression
                        return
                    }
                    super.visitCallExpression(expression)
                }

                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    if (firstInvalidCall != null || firstInvalidPropertyRead != null) return
                    if (
                        expression.isComposablePropertyRead() &&
                        !expression.isReadOnlyComposablePropertyRead()
                    ) {
                        firstInvalidPropertyRead = expression
                        return
                    }
                    super.visitSimpleNameExpression(expression)
                }

                override fun visitNamedFunction(function: KtNamedFunction) {
                    // Nested function bodies are not evaluated by the read-only declaration.
                }

                override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
                    // Nested accessor bodies are not evaluated by the read-only declaration.
                }

                override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
                    if (lambdaExpression.isEagerScopeFunctionLambda()) {
                        super.visitLambdaExpression(lambdaExpression)
                    }
                }
            },
        )
        firstInvalidPropertyRead?.let { reporter.report(it, INVALID_READ_ONLY_COMPOSABLE_MESSAGE) }
        return firstInvalidCall
    }

    internal companion object {
        const val INVALID_READ_ONLY_COMPOSABLE_MESSAGE =
            "This `@ReadOnlyComposable` declaration calls a composable that is not read-only. " +
                "The read-only contract forbids calls that may write to the composition. " +
                "Remove `@ReadOnlyComposable` or replace the call with a read-only operation."
    }
}
