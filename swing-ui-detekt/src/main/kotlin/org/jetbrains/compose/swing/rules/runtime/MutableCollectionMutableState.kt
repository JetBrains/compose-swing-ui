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
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a `MutableState` created over a mutable collection type.
 * Changes made inside the collection do not notify the state, so the composable does not recompose.
 */
internal object MutableCollectionMutableState : ComposeSwingRule {
    override val id = "MutableCollectionMutableState"
    override val description = "Creating a MutableState object with a mutable collection type"
    override val severity = ComposeSwingRule.Severity.Warning
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
                        if (id.packageName.asString() != Names.Runtime.PackageName.javaPackageName ||
                            id.callableName.asString() != Names.Runtime.MutableStateOf.shortName
                        ) {
                            return@analyze false
                        }
                        val expressionType = expression.expressionType as? KaClassType ?: return@analyze false
                        val type = expressionType.typeArguments.firstOrNull()?.type ?: return@analyze false
                        isMutableCollection(type)
                    }
                if (report) reporter.report(expression.calleeExpression ?: expression, description)
            }
        }
}

private fun KaSession.isMutableCollection(kaType: KaType): Boolean {
    // MutableCollection::class.qualifiedName == Collection::class.qualifiedName, so using hardcoded
    // strings instead
    val kotlinImmutableTypes = listOf("kotlin.collections.Collection", "kotlin.collections.Map")

    val guavaImmutableTypePrefix = "com.google.common.collect.Immutable"

    val kotlinMutableTypes =
        listOf("kotlin.collections.MutableCollection", "kotlin.collections.MutableMap")

    val javaMutableTypes = listOf("java.util.Collection", "java.util.Map")

    // Guava precedes Kotlin mutable types, which precede their read-only supertypes.
    fun classify(types: List<String?>): Boolean? =
        when {
            types.any { it?.startsWith(guavaImmutableTypePrefix) == true } -> false
            types.any { it in kotlinMutableTypes } -> true
            types.any { it in kotlinImmutableTypes } -> false
            types.any { it in javaMutableTypes } -> true
            else -> null
        }

    return classify(listOf(fqn(kaType)))
        ?: classify(kaType.allSupertypes(false).map { fqn(it) }.toList())
        ?: false
}

private fun KaSession.fqn(kaType: KaType): String? =
    kaType
        // For platform types (Java types with unknown nullability) try and get a concrete type
        // first - lower bound will match the non-null type.
        .lowerBoundIfFlexible()
        .expandedSymbol
        ?.classId
        ?.asFqNameString()
