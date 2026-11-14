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
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a call to `async` or `launch` made during composition, which starts work on every recomposition.
 * Such a call belongs in a `LaunchedEffect`, which starts it again only when its keys change.
 */
internal object CoroutineCreationDuringComposition : ComposeSwingRule {
    override val id = "CoroutineCreationDuringComposition"
    override val description = "Calls to `async` or `launch` should happen inside a LaunchedEffect and not composition"
    override val severity = ComposeSwingRule.Severity.Error
    override val activeByDefault = true
    override val requiresAnalysisApi = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                val name =
                    analyze(expression) {
                        val id =
                            expression
                                .resolveToCall()
                                ?.singleFunctionCallOrNull()
                                ?.symbol
                                ?.callableId
                                ?: return@analyze null
                        val name = id.callableName.asString()
                        val pkg = id.packageName.asString()
                        name.takeIf {
                            it in setOf("async", "launch", "launchIn") &&
                                pkg in setOf("kotlinx.coroutines", "kotlinx.coroutines.flow")
                        }
                    } ?: return
                if (expression.isInvokedWithinComposable()) {
                    reporter.report(
                        expression.calleeExpression ?: expression,
                        "Calls to $name should happen inside a LaunchedEffect and not composition",
                    )
                }
            }
        }
}
