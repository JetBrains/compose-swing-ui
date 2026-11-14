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
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a read of `StateFlow.value` during composition, which does not subscribe the composable to the flow.
 * The composable does not recompose when the value changes, so its read goes stale.
 */
internal object StateFlowValueCalledInComposition : ComposeSwingRule {
    override val id = "StateFlowValueCalledInComposition"
    override val description = "StateFlow.value should not be called within composition"
    override val severity = ComposeSwingRule.Severity.Error
    override val activeByDefault = true
    override val requiresAnalysisApi = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                if (expression !is KtNameReferenceExpression) return
                if (expression.getReferencedName() != "value") return
                val inheritsFromStateFlow =
                    analyze(expression) {
                        val property =
                            expression.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.symbol
                                as? KaPropertySymbol ?: return@analyze false
                        val owner = property.containingDeclaration as? KaClassSymbol ?: return@analyze false
                        val stateFlow =
                            findClass(ClassId.topLevel(FqName("kotlinx.coroutines.flow.StateFlow")))
                                ?: return@analyze false
                        owner == stateFlow || owner.isSubClassOf(stateFlow)
                    }
                if (inheritsFromStateFlow && expression.isInvokedWithinComposable()) {
                    reporter.report(expression, description)
                }
            }
        }
}
