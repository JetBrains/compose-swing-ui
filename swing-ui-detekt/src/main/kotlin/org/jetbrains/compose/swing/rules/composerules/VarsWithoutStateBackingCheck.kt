// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0

package org.jetbrains.compose.swing.rules.composerules

import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.parents

/** Runs [VarsWithoutStateBackingCheck] as a [ComposeSwingRule]. */
internal object VarsWithoutStateBacking : ComposeSwingRule {
    override val id: String = "VarsWithoutStateBacking"
    override val description: String = "Local var properties in composable scopes should be backed by observable state."
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid = VarsWithoutStateBackingCheck(reporter)
}

/**
 * Reports local `var` properties in composable scopes when they are not backed by observable state.
 */
internal class VarsWithoutStateBackingCheck(
    private val reporter: ComposeSwingRuleReporter,
) : KtVisitorVoid() {
    override fun visitProperty(property: KtProperty) {
        if (property.isVar && property.isInCheckedComposableScope() && !property.hasComposeStateDelegate()) {
            reporter.report(property, VARS_WITHOUT_STATE_BACKING_MESSAGE)
        }
    }

    private fun KtProperty.isInCheckedComposableScope(): Boolean {
        var inComposition = false
        for (scope in parents.toList().asReversed()) {
            when (scope) {
                is KtLambdaExpression -> {
                    inComposition = scope.isComposable() || (inComposition && scope.isEagerStdlibScopeFunctionLambda())
                }

                is KtNamedFunction, is KtPropertyAccessor, is KtClassOrObject -> {
                    val startsComposition =
                        when (scope) {
                            is KtNamedFunction -> scope.isComposable()
                            is KtPropertyAccessor -> scope.isGetter && scope.isComposable()
                            else -> false
                        }
                    if (inComposition && !startsComposition) return false
                    inComposition = startsComposition
                }
            }
        }
        return inComposition
    }

    internal companion object {
        const val VARS_WITHOUT_STATE_BACKING_MESSAGE =
            "This mutable local variable is declared during composition without observable state. " +
                "Changes to it do not schedule recomposition. " +
                "Store the value in remembered mutable state."
    }
}
