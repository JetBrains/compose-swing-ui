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

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a `produceState` call whose producer lambda never assigns `value`.
 * Without an assignment the state never leaves its initial value.
 */
internal object ProduceStateDoesNotAssignValue : ComposeSwingRule {
    override val id = "ProduceStateDoesNotAssignValue"
    override val description = "produceState calls should assign `value` inside the producer lambda"
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
                        if (id.packageName.asString() != "androidx.compose.runtime" ||
                            id.callableName.asString() != "produceState"
                        ) {
                            return@analyze false
                        }
                        val producer =
                            call.valueArgumentMapping.entries
                                .firstOrNull {
                                    it.value.symbol.name
                                        .asString() == "producer"
                                }?.key ?: return@analyze false
                        !hasValueAssignment(producer) && !callsStateFunction(producer)
                    }
                if (report) reporter.report(expression.calleeExpression ?: expression, description)
            }
        }

    private fun KaSession.callsStateFunction(producer: KtExpression): Boolean =
        (
            listOfNotNull(producer as? KtCallExpression) +
                PsiTreeUtil.findChildrenOfType(producer, KtCallExpression::class.java)
        ).any { expression ->
            val symbol = expression.resolveToCall()?.successfulFunctionCallOrNull()?.symbol ?: return@any false
            val types = symbol.valueParameters.map { it.returnType } + listOfNotNull(symbol.receiverType)
            types.any { type ->
                type.isSubtypeOf(produceStateScope) ||
                    (type.fullyExpandedType as? KaClassType)?.classId == mutableState
            }
        }

    private fun KaSession.hasValueAssignment(producer: KtExpression): Boolean =
        PsiTreeUtil.findChildrenOfType(producer, KtBinaryExpression::class.java).any { expression ->
            expression.operationToken in KtTokens.ALL_ASSIGNMENTS && isMutableStateValue(expression.left)
        } ||
            PsiTreeUtil.findChildrenOfType(producer, KtUnaryExpression::class.java).any { expression ->
                expression.operationToken in listOf(KtTokens.MINUSMINUS, KtTokens.PLUSPLUS) &&
                    expression.baseExpression.rightmostName() == "value"
            }

    private fun KaSession.isMutableStateValue(expression: KtExpression?): Boolean {
        val reference = (expression as? KtQualifiedExpression)?.selectorExpression ?: expression
        return expression.rightmostName() == "value" &&
            reference
                ?.resolveToCall()
                ?.successfulVariableAccessCall()
                ?.dispatchReceiver
                ?.type
                ?.isSubtypeOf(mutableState) == true
    }

    private fun KtExpression?.rightmostName(): String? =
        when (this) {
            is KtNameReferenceExpression -> getReferencedName()
            is KtQualifiedExpression -> selectorExpression.rightmostName()
            else -> null
        }

    private val mutableState = ClassId.fromString("androidx/compose/runtime/MutableState")
    private val produceStateScope = ClassId.fromString("androidx/compose/runtime/ProduceStateScope")
}
