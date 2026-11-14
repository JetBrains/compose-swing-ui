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
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a value or key of a type annotated `@DoNotRetain` that a `retain` call keeps.
 * The annotation marks a type that retained state must not hold.
 */
internal object RetainingDoNotRetainType : ComposeSwingRule {
    override val id = "RetainingDoNotRetainType"
    override val description = "Types annotated with DoNotRetain must not be retained as values or keys."
    override val severity = ComposeSwingRule.Severity.Error
    override val activeByDefault = true
    override val requiresAnalysisApi = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                checkRetainTypes(expression, reporter) { type, key ->
                    val annotation = type.doNotRetainAnnotation()
                    if (annotation == null) {
                        null
                    } else {
                        val reason =
                            annotation.parameterList.attributes
                                .firstOrNull { it.name == "explanation" }
                                ?.literalValue
                                .orEmpty()
                        val subject = if (key) "Key type ${type.canonicalText}" else type.canonicalText
                        val detail = if (reason.isEmpty()) "" else ": $reason"
                        "$subject is annotated as `@DoNotRetain`$detail"
                    }
                }
            }
        }
}
