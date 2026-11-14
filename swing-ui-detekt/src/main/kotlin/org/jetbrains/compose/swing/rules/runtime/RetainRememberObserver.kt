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
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a value that `retain { ... }` returns and that implements `RememberObserver` without `RetainObserver`.
 * Only a `RetainObserver` receives the retain lifecycle callbacks, so a retained value that remembers needs it.
 */
internal object RetainRememberObserver : ComposeSwingRule {
    override val id = "RetainRememberObserver"
    override val description =
        "Values returned by `retain { ... }` must not implement RememberObserver " +
            "unless they also implement RetainObserver."
    override val severity = ComposeSwingRule.Severity.Error
    override val activeByDefault = true
    override val requiresAnalysisApi = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            @OptIn(KaExperimentalApi::class)
            override fun visitCallExpression(expression: KtCallExpression) {
                val typeName =
                    analyze(expression) {
                        val call = expression.resolveToCall()?.successfulFunctionCallOrNull() ?: return@analyze null
                        val id = call.symbol.callableId ?: return@analyze null
                        if (id.packageName.asString() != "androidx.compose.runtime.retain" ||
                            id.callableName.asString() != "retain"
                        ) {
                            return@analyze null
                        }
                        val type = expression.expressionType ?: return@analyze null
                        val remember = ClassId.fromString("androidx/compose/runtime/RememberObserver")
                        val retain = ClassId.fromString("androidx/compose/runtime/retain/RetainObserver")
                        if (!type.isSubtypeOf(remember) || type.isSubtypeOf(retain)) return@analyze null
                        type.asPsiType(expression, allowErrorTypes = false)?.canonicalText
                    }
                if (typeName != null) {
                    reporter.report(
                        expression.calleeExpression ?: expression,
                        "Declared retained type `$typeName` implements `RememberObserver` but not `RetainObserver`.",
                    )
                }
            }
        }
}
