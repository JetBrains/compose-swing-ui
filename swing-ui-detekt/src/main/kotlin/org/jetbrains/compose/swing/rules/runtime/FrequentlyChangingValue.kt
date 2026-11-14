/*
 * Copyright 2025 The Android Open Source Project
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
import org.jetbrains.kotlin.analysis.api.resolution.KaVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a read of a `@FrequentlyChangingValue` value during composition.
 * Each change to such a value recomposes the composable that reads it.
 */
internal object FrequentlyChangingValue : ComposeSwingRule {
    override val id = "FrequentlyChangingValue"
    override val description = "Reading a value annotated with @FrequentlyChangingValue inside composition"
    private const val MESSAGE = "Reading a value annotated with @FrequentlyChangingValue inside composition"
    override val severity = ComposeSwingRule.Severity.Warning
    override val activeByDefault = true
    override val requiresAnalysisApi = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                val annotated =
                    analyze(expression) {
                        val symbol =
                            expression.resolveToCall()?.successfulFunctionCallOrNull()?.symbol
                                ?: return@analyze false
                        val supers = (symbol as? KaNamedFunctionSymbol)?.allOverriddenSymbols.orEmpty()
                        (supers + symbol).any {
                            Names.Runtime.Annotation.FrequentlyChangingValue.classId in it.annotations
                        }
                    }
                if (annotated && expression.isInComposition()) {
                    reporter.report(expression.calleeExpression ?: expression, MESSAGE)
                }
            }

            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                val annotated =
                    analyze(expression) {
                        val call = expression.resolveToCall()?.singleVariableAccessCall() ?: return@analyze false
                        if (call.kind !is KaVariableAccessCall.Kind.Read) return@analyze false
                        val property = call.symbol as? KaPropertySymbol ?: return@analyze false
                        val getter = property.getter ?: return@analyze false
                        val getters =
                            property.allOverriddenSymbols.mapNotNull { (it as? KaPropertySymbol)?.getter } +
                                getter.allOverriddenSymbols
                        (getters + getter).any {
                            Names.Runtime.Annotation.FrequentlyChangingValue.classId in it.annotations
                        }
                    }
                if (annotated && expression.isInComposition()) {
                    reporter.report(expression, MESSAGE)
                }
            }
        }

    private fun KtExpression.isInComposition(): Boolean {
        if (isInvokedWithinComposable()) return true
        return analyze(this) {
            val boundary =
                compositionBoundary(this@isInComposition) as? KtLambdaExpression ?: return@analyze false
            val annotated = boundary.parent as? KtAnnotatedExpression ?: return@analyze false
            annotated.annotationEntries.any {
                it.typeReference?.type?.isClassType(Names.Runtime.Composable.classId) == true
            }
        }
    }
}
