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
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a `Saver` passed to the vararg `inputs` of `rememberSaveable`, not to its `saver` parameter.
 * Passed as an input, the `Saver` is taken as a key rather than as the saver.
 */
internal object RememberSaveableSaverParameter : ComposeSwingRule {
    override val id = "RememberSaveableSaverParameter"
    override val description =
        "`Saver` objects should be passed to the saver parameter, not the vararg `inputs` parameter"
    override val severity = ComposeSwingRule.Severity.Error
    override val activeByDefault = true
    override val requiresAnalysisApi = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                val argument =
                    analyze(expression) {
                        val call = expression.resolveToCall()?.successfulFunctionCallOrNull() ?: return@analyze null
                        val id = call.symbol.callableId ?: return@analyze null
                        if (id.packageName.asString() != "androidx.compose.runtime.saveable" ||
                            id.callableName.asString() != "rememberSaveable"
                        ) {
                            return@analyze null
                        }
                        val arguments =
                            call.valueArgumentMapping.filter { (_, parameter) ->
                                val type = parameter.symbol.returnType.fullyExpandedType as? KaClassType
                                parameter.symbol.isVararg || type?.classId in arrayTypes
                            }
                        val candidate = arguments.keys.singleOrNull() ?: return@analyze null
                        if (candidate.expressionType?.isSubtypeOf(saver) == true) candidate else null
                    }
                if (argument != null) {
                    reporter.report(
                        argument,
                        "Passing `Saver` instance to vararg `inputs`",
                    )
                }
            }
        }

    private val saver = ClassId.fromString("androidx/compose/runtime/saveable/Saver")
    private val arrayTypes =
        listOf(
            "Array",
            "BooleanArray",
            "ByteArray",
            "CharArray",
            "DoubleArray",
            "FloatArray",
            "IntArray",
            "LongArray",
            "ShortArray",
        ).map { ClassId.fromString("kotlin/$it") }.toSet()
}
