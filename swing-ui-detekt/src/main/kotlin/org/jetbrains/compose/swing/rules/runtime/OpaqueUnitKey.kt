/*
 * Copyright 2023 The Android Open Source Project
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
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a key argument whose expression always returns `Unit`.
 * Such a key never changes, so it never restarts the work it keys.
 */
internal object OpaqueUnitKey : ComposeSwingRule {
    override val id = "OpaqueUnitKey"
    override val description = "Passing an expression which always returns `Unit` as a key argument"
    override val severity = ComposeSwingRule.Severity.Warning
    override val activeByDefault = true
    override val requiresAnalysisApi = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                analyze(expression) {
                    val call = expression.resolveToCall()?.successfulFunctionCallOrNull() ?: return@analyze
                    val callableId = call.symbol.callableId ?: return@analyze
                    if (callableId.packageName.asString() != Names.Runtime.PackageName.javaPackageName ||
                        callableId.callableName.asString() !in methodNames
                    ) {
                        return@analyze
                    }
                    call.valueArgumentMapping.forEach { (argument, signature) ->
                        val parameter = signature.symbol
                        val type = parameter.returnType.fullyExpandedType as? KaClassType
                        if (!parameter.isVararg &&
                            (type?.classId != StandardClassIds.Any || !type.isMarkedNullable)
                        ) {
                            return@forEach
                        }
                        val varargElement =
                            if (type?.classId == StandardClassIds.Array) {
                                type.typeArguments
                                    .singleOrNull()
                                    ?.type
                                    ?.fullyExpandedType as? KaClassType
                            } else {
                                type
                            }
                        if (parameter.isVararg && varargElement?.classId != StandardClassIds.Any) {
                            return@forEach
                        }
                        val argumentType = argument.expressionType?.fullyExpandedType as? KaClassType
                        if (argumentType?.classId != StandardClassIds.Unit) return@forEach
                        var unwrapped = argument
                        while (unwrapped is KtParenthesizedExpression) {
                            unwrapped = unwrapped.expression ?: break
                        }
                        val symbol = (unwrapped as? KtNameReferenceExpression)?.mainReference?.resolveToSymbols()
                        if (symbol
                                ?.filterIsInstance<KaClassSymbol>()
                                ?.any { it.classId == StandardClassIds.Unit } == true
                        ) {
                            return@forEach
                        }
                        reporter.report(
                            argument,
                            "Implicitly passing `Unit` as argument to ${parameter.name.asString()}",
                        )
                    }
                }
            }
        }

    private val methodNames =
        setOf(
            "DisposableEffect",
            "LaunchedEffect",
            "ReusableContent",
            "key",
            "produceState",
            "remember",
            "rememberSaveable",
        )
}
