/*
 * Copyright() 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.compose.swing.rules.runtime

import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a read of a `State` value that boxes a primitive, and names the property that reads it without boxing.
 */
internal object AutoboxingStateValueProperty : ComposeSwingRule {
    override val id = "AutoboxingStateValueProperty"
    override val description = "State access causes value to be autoboxed"
    override val severity = ComposeSwingRule.Severity.Warning
    override val activeByDefault = true
    override val requiresAnalysisApi = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                if (expression !is KtNameReferenceExpression) return
                var target: KtExpression =
                    (expression.parent as? KtQualifiedExpression)?.takeIf { it.selectorExpression == expression }
                        ?: expression
                while (target.parent is KtParenthesizedExpression) {
                    target = target.parent as KtParenthesizedExpression
                }
                val parent = target.parent
                val write =
                    (
                        parent is KtBinaryExpression && parent.left == target &&
                            parent.operationToken in KtTokens.ALL_ASSIGNMENTS
                    ) ||
                        (
                            parent is KtUnaryExpression && parent.baseExpression == target &&
                                parent.operationToken in listOf(KtTokens.MINUSMINUS, KtTokens.PLUSPLUS)
                        )
                val replacement =
                    analyze(expression) {
                        val property =
                            expression.resolveToCall()?.successfulVariableAccessCall()?.symbol as? KaPropertySymbol
                                ?: return@analyze null
                        val accessor = if (write) property.setter else property.getter
                        val annotation = accessor?.annotations?.get(annotationId)?.firstOrNull() ?: return@analyze null
                        val argument =
                            annotation.arguments.firstOrNull {
                                it.name.asString() == "preferredPropertyName"
                            }
                        ((argument?.expression as? KaAnnotationValue.ConstantValue)?.value?.value as? String)
                            ?: "<unknown replacement>"
                    }
                if (replacement != null) {
                    val access = if (write) "Assigning" else "Reading"
                    reporter.report(
                        expression,
                        "$access `${expression.getReferencedName()}` will cause an autoboxing operation. " +
                            "Use `$replacement` to avoid unnecessary allocations.",
                    )
                }
            }
        }

    private val annotationId = ClassId.fromString("androidx/compose/runtime/snapshots/AutoboxingStateValueProperty")
}
