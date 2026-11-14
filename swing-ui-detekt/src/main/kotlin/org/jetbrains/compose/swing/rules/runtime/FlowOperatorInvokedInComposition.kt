/*
 * Copyright 2021 The Android Open Source Project
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
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a Flow operator function called during composition, which builds a new flow on every recomposition.
 */
internal object FlowOperatorInvokedInComposition : ComposeSwingRule {
    override val id = "FlowOperatorInvokedInComposition"
    override val description = "Flow operator functions should not be invoked within composition"
    override val severity = ComposeSwingRule.Severity.Error
    override val activeByDefault = true
    override val requiresAnalysisApi = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                val isOperator =
                    analyze(expression) {
                        val symbol =
                            expression
                                .resolveToCall()
                                ?.singleFunctionCallOrNull()
                                ?.symbol
                                ?: return@analyze false
                        if (!symbol.isExtension || Names.Runtime.Composable.classId in symbol.annotations) {
                            return@analyze false
                        }
                        val receiver = symbol.receiverType ?: return@analyze false
                        val flow = ClassId.topLevel(FqName("kotlinx.coroutines.flow.Flow"))
                        receiver.isSubtypeOf(flow) && symbol.returnType.isSubtypeOf(flow)
                    }
                if (isOperator && expression.isInvokedWithinComposable()) {
                    reporter.report(expression.calleeExpression ?: expression, description)
                }
            }
        }
}
