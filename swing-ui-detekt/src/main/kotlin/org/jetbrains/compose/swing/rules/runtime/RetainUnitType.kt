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
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a `retain` call whose calculation returns `Unit`.
 * Such a call retains no value that a composable can read back.
 */
internal object RetainUnitType : ComposeSwingRule {
    override val id = "RetainUnitType"
    override val description = "`retain` calls must not return `Unit`"
    override val severity = ComposeSwingRule.Severity.Error
    override val activeByDefault = true
    override val requiresAnalysisApi = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                val report =
                    analyze(expression) {
                        val call = expression.resolveToCall()?.successfulFunctionCallOrNull() ?: return@analyze false
                        val id = call.symbol.callableId ?: return@analyze false
                        if (id.packageName.asString() != "androidx.compose.runtime.retain" ||
                            id.callableName.asString() != "retain"
                        ) {
                            return@analyze false
                        }
                        val type = expression.expressionType?.fullyExpandedType as? KaClassType
                        if (type?.classId != StandardClassIds.Unit) return@analyze false
                        val explicitType =
                            expression.typeArguments
                                .singleOrNull()
                                ?.typeReference
                                ?.type
                                ?.fullyExpandedType as? KaClassType
                        if (explicitType?.classId == StandardClassIds.Unit) return@analyze true
                        val parameter = call.symbol.valueParameters.lastOrNull() ?: return@analyze false
                        val argument =
                            call.valueArgumentMapping.entries
                                .firstOrNull { it.value.symbol == parameter }
                                ?.key
                        if (argument !is KtLambdaExpression) return@analyze true
                        yieldsUnit(argument)
                    }
                if (report) {
                    reporter.report(
                        expression.calleeExpression ?: expression,
                        "`retain` calls must not return `Unit`.",
                    )
                }
            }
        }
}
