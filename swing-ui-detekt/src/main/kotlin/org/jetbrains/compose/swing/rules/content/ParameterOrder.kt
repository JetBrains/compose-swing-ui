// Copyright 2023 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0
// Adapted to the library's host-neutral rule API.

package org.jetbrains.compose.swing.rules.content

import com.intellij.psi.PsiElement
import io.nlopez.compose.core.util.composableLambdaTypes
import io.nlopez.compose.core.util.isComposableLambda
import io.nlopez.compose.core.util.isLambda
import io.nlopez.compose.core.util.lambdaTypes
import io.nlopez.compose.core.util.runIf
import org.jetbrains.compose.swing.rules.ComposeRulesConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.compose.swing.rules.core.modifier.customModifierTypes
import org.jetbrains.compose.swing.rules.core.modifier.declaresModifier
import org.jetbrains.compose.swing.rules.core.modifier.isComposable
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtVisitorVoid

/** Checks parameter order while preserving a composable's trailing lambda. */
internal object ParameterOrder : ComposeSwingRule {
    override val id = "ComposableParamOrder"
    override val description = "Parameters in Composable functions should be ordered correctly"
    override val severity = ComposeSwingRule.Severity.Error
    override val activeByDefault = true
    override val requiresAnalysisApi = false

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid {
        val composeConfig = ComposeRulesConfig(config)
        val customModifierTypes = config.customModifierTypes()
        return object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                if (element is KtFunction && element.isComposable()) {
                    reportParameterOrder(element, reporter, composeConfig, customModifierTypes)
                }
            }
        }
    }

    private fun reportParameterOrder(
        function: KtFunction,
        reporter: ComposeSwingRuleReporter,
        config: ComposeRulesConfig,
        customModifierTypes: Set<String>,
    ) {
        val currentOrder = function.valueParameters
        val file = function.containingKtFile
        val lambdaTypes = file.lambdaTypes(config)
        val hasTrailingFunction = currentOrder.lastOrNull()?.typeReference?.isLambda(lambdaTypes) == true
        val trailingLambda = if (hasTrailingFunction) listOf(currentOrder.last()) else emptyList()
        val (withDefaults, withoutDefaults) =
            currentOrder.runIf(hasTrailingFunction) { dropLast(1) }.partition { it.hasDefaultValue() }
        val sortedWithDefaults =
            withDefaults.sortedBy { parameter ->
                when {
                    !parameter.typeReference.declaresModifier(customModifierTypes) -> 2
                    parameter.name == "modifier" -> 0
                    else -> 1
                }
            }
        val properOrder = withoutDefaults + sortedWithDefaults + trailingLambda
        if (currentOrder == properOrder) return

        // A defaulted non-composable lambda can stay out of order to avoid making it trailing.
        val shouldBeLenient =
            !hasTrailingFunction && properOrder.lastOrNull()?.let { lastParam ->
                val typeRef = lastParam.typeReference
                lastParam.hasDefaultValue() &&
                    typeRef?.isLambda(lambdaTypes) == true &&
                    !typeRef.isComposableLambda(lambdaTypes, file.composableLambdaTypes(config))
            } == true
        if (!shouldBeLenient) {
            reporter.report(function.nameIdentifier ?: function, parameterOrderMessage(properOrder))
        }
    }
}

private fun parameterOrderMessage(properOrder: List<KtParameter>): String =
    "The parameters of this composable are out of order. Consistent ordering makes composable calls easier to read. " +
        "Put required parameters first, followed by defaulted modifiers and other defaulted parameters. " +
        "Keep any trailing lambda last. Use this order: ${properOrder.joinToString { it.name.orEmpty() }}."
