/*
 * Copyright 2020 The Android Open Source Project
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
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a composable's primary lambda parameter that is not named `content`.
 * Every composable names its content slot the same way, so callers read them alike.
 */
internal object ComposableLambdaParameterNaming : ComposeSwingRule {
    override val id = "ComposableLambdaParameterNaming"
    override val description = "Primary composable lambda parameter not named `content`"
    override val severity = ComposeSwingRule.Severity.Warning
    override val activeByDefault = false
    override val requiresAnalysisApi = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        composableLambdaVisitor { parameter, type ->
            if (parameter.name != "content" && type.parameters.isEmpty()) {
                reporter.report(
                    parameter.nameIdentifier ?: parameter,
                    "Composable lambda parameter should be named `content`",
                )
            }
        }
}

/**
 * Reports a composable's primary lambda parameter that is not the last parameter.
 * A last lambda parameter can be passed as a trailing lambda, which is how content slots are written.
 */
internal object ComposableLambdaParameterPosition : ComposeSwingRule {
    override val id = "ComposableLambdaParameterPosition"
    override val description = "Non-trailing primary composable lambda parameter"
    override val severity = ComposeSwingRule.Severity.Warning
    override val activeByDefault = false
    override val requiresAnalysisApi = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        composableLambdaVisitor { parameter, _ ->
            val function = parameter.parent?.parent as? KtNamedFunction
            if (function != null && parameter != function.valueParameters.lastOrNull()) {
                reporter.report(
                    parameter.nameIdentifier ?: parameter,
                    "Composable lambda parameter should be the last parameter so it can be used as a trailing lambda",
                )
            }
        }
}

private fun composableLambdaVisitor(report: (KtParameter, KtFunctionType) -> Unit): KtVisitorVoid =
    object : KtVisitorVoid() {
        override fun visitNamedFunction(function: KtNamedFunction) {
            val parameter =
                analyze(function) {
                    if (Names.Runtime.Composable.classId !in function.symbol.annotations) return@analyze null
                    val returnType = function.symbol.returnType.fullyExpandedType as? KaClassType
                    if (returnType?.classId != StandardClassIds.Unit) return@analyze null
                    function.valueParameters
                        .mapNotNull { parameter ->
                            val reference = parameter.typeReference ?: return@mapNotNull null
                            val type =
                                when (val element = reference.typeElement) {
                                    is KtFunctionType -> element
                                    is KtNullableType -> element.innerType as? KtFunctionType
                                    else -> null
                                } ?: return@mapNotNull null
                            val annotations =
                                reference.annotationEntries +
                                    (reference.typeElement as? KtNullableType)?.annotationEntries.orEmpty() +
                                    (parameter.defaultValue as? KtAnnotatedExpression)?.annotationEntries.orEmpty()
                            val initializer = parameter.defaultValue
                            val composable =
                                Names.Runtime.Composable.classId in reference.type.annotations ||
                                    initializer
                                        ?.expressionType
                                        ?.annotations
                                        ?.contains(Names.Runtime.Composable.classId) == true ||
                                    (initializer as? KtNamedFunction)
                                        ?.symbol
                                        ?.annotations
                                        ?.contains(Names.Runtime.Composable.classId) == true ||
                                    (initializer as? KtLambdaExpression)
                                        ?.functionLiteral
                                        ?.symbol
                                        ?.annotations
                                        ?.contains(Names.Runtime.Composable.classId) == true ||
                                    annotations.any { annotation ->
                                        (annotation.typeReference?.type?.fullyExpandedType as? KaClassType)?.classId ==
                                            Names.Runtime.Composable.classId
                                    }
                            if (composable) parameter to type else null
                        }.singleOrNull()
                } ?: return
            report(parameter.first, parameter.second)
        }
    }
