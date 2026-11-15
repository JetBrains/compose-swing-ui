// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0
// Adapted to the library's host-neutral rule API.

package org.jetbrains.compose.swing.rules.runtime

import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.compose.swing.rules.psi.isEagerScopeFunctionCall
import org.jetbrains.compose.swing.rules.psi.isEagerScopeFunctionLambda
import org.jetbrains.compose.swing.rules.psi.isLambdaLessLibraryCall
import org.jetbrains.compose.swing.rules.psi.isPropertyReadWithExecutableAccess
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/** Runs [MissingReadOnlyComposableCheck] as a [ComposeSwingRule]. */
internal object MissingReadOnlyComposable : ComposeSwingRule {
    override val id: String = "MissingReadOnlyComposable"
    override val description: String =
        "Composable declarations that only call read-only composables should be " +
            "marked as @ReadOnlyComposable."
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid = MissingReadOnlyComposableCheck(reporter)
}

// Upstream compose-rules structure, kept so later releases replay onto the fork without restructuring.

/**
 * Reports composable declarations that call only read-only composables, and can therefore be marked
 * `@ReadOnlyComposable`.
 */
@Suppress("ReturnCount", "UnsafeCallOnNullableType")
internal class MissingReadOnlyComposableCheck(
    private val reporter: ComposeSwingRuleReporter,
) : KtVisitorVoid() {
    override fun visitNamedFunction(function: KtNamedFunction) {
        if (!function.isComposable()) return
        if (function.isReadOnlyComposable()) return
        if (function.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return
        if (function.bodyExpression == null) return
        if ((function.bodyExpression as? KtBlockExpression)?.statements?.isEmpty() == true) return

        if (function.bodyExpression!!.readOnlyComposableUsage().canBeReadOnlyComposable) {
            reportMissingReadOnlyComposable(function)
        }
    }

    override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
        if (!accessor.isGetter) return
        if (!accessor.isComposable()) return
        if (accessor.isReadOnlyComposable()) return
        if (accessor.property.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return
        if (accessor.bodyExpression == null) return

        if (accessor.bodyExpression!!.readOnlyComposableUsage().canBeReadOnlyComposable) {
            reportMissingReadOnlyComposable(accessor)
        }
    }

    private fun reportMissingReadOnlyComposable(element: KtElement) {
        reporter.report(element, MISSING_READ_ONLY_COMPOSABLE_MESSAGE)
    }

    private fun KtElement.readOnlyComposableUsage(): ReadOnlyComposableUsage {
        var hasReadOnlyComposableUsage = false
        var hasNonReadOnlyComposableUsage = false

        accept(
            object : KtTreeVisitorVoid() {
                override fun visitCallExpression(expression: KtCallExpression) {
                    if (expression.isComposableCall()) {
                        if (expression.isReadOnlyComposableCall()) {
                            hasReadOnlyComposableUsage = true
                        } else {
                            hasNonReadOnlyComposableUsage = true
                            return
                        }
                    } else if (!expression.isEagerScopeFunctionCall() && !expression.isLambdaLessLibraryCall()) {
                        hasNonReadOnlyComposableUsage = true
                        return
                    }
                    super.visitCallExpression(expression)
                }

                override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                    if (expression.isComposablePropertyRead()) {
                        if (expression.isReadOnlyComposablePropertyRead()) {
                            hasReadOnlyComposableUsage = true
                        } else {
                            hasNonReadOnlyComposableUsage = true
                            return
                        }
                    } else if (expression.isPropertyReadWithExecutableAccess()) {
                        hasNonReadOnlyComposableUsage = true
                        return
                    }
                    super.visitSimpleNameExpression(expression)
                }

                override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                    if (expression.isCompositionLocalCurrentRead()) {
                        hasReadOnlyComposableUsage = true
                        expression.receiverExpression.accept(this)
                        return
                    }
                    super.visitDotQualifiedExpression(expression)
                }

                override fun visitArrayAccessExpression(expression: KtArrayAccessExpression) {
                    hasNonReadOnlyComposableUsage = true
                }

                override fun visitBinaryExpression(expression: KtBinaryExpression) {
                    val operationToken = expression.operationToken
                    if (
                        operationToken in KtTokens.ALL_ASSIGNMENTS ||
                        (!expression.isIdentityOrNullCheck() && !expression.isBuiltInOrLibraryOperator())
                    ) {
                        hasNonReadOnlyComposableUsage = true
                        return
                    }
                    super.visitBinaryExpression(expression)
                }

                override fun visitUnaryExpression(expression: KtUnaryExpression) {
                    if (expression.operationToken in IncrementOrDecrementOperators) {
                        hasNonReadOnlyComposableUsage = true
                        return
                    }
                    super.visitUnaryExpression(expression)
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

        return ReadOnlyComposableUsage(
            hasReadOnlyComposableUsage = hasReadOnlyComposableUsage,
            hasNonReadOnlyComposableUsage = hasNonReadOnlyComposableUsage,
        )
    }

    internal companion object {
        const val MISSING_READ_ONLY_COMPOSABLE_MESSAGE =
            "This composable does not write to the composition. " +
                "Without a read-only contract, other read-only composables cannot call it. " +
                "Mark it `@ReadOnlyComposable`."
    }
}

private val IncrementOrDecrementOperators = setOf(KtTokens.PLUSPLUS, KtTokens.MINUSMINUS)

/** Identity checks and comparisons with `null` never call `equals`. */
private fun KtBinaryExpression.isIdentityOrNullCheck(): Boolean =
    when (operationToken) {
        KtTokens.EQEQEQ, KtTokens.EXCLEQEQEQ -> {
            true
        }

        KtTokens.EQEQ, KtTokens.EXCLEQ -> {
            listOfNotNull(left, right).any { operand ->
                KtPsiUtil.isNullConstant(operand)
            }
        }

        else -> {
            false
        }
    }

private data class ReadOnlyComposableUsage(
    val hasReadOnlyComposableUsage: Boolean,
    val hasNonReadOnlyComposableUsage: Boolean,
) {
    val canBeReadOnlyComposable: Boolean
        get() = hasReadOnlyComposableUsage && !hasNonReadOnlyComposableUsage
}
