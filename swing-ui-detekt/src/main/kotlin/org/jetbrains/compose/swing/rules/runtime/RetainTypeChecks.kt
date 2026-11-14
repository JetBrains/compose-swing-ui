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

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWildcardType
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.psi.KtCallExpression

private val containerTypes =
    setOf(
        "androidx.collection.ArraySet",
        "androidx.collection.CircularArray",
        "androidx.collection.FloatObjectMap",
        "androidx.collection.IntObjectMap",
        "androidx.collection.LongObjectMap",
        "androidx.collection.LruCache",
        "androidx.collection.ObjectFloatMap",
        "androidx.collection.ObjectIntMap",
        "androidx.collection.ObjectLongMap",
        "androidx.collection.OrderedScatterSet",
        "androidx.collection.ScatterMap",
        "androidx.collection.SparseArrayCompat",
        "androidx.lifecycle.LiveData",
        "arrow.core.Either",
        "arrow.core.Option",
        "com.google.common.base.Optional",
        "com.google.common.collect.Multimap",
        "com.google.common.collect.Table",
        "java.util.Collection",
        "java.util.Map",
        "kotlin.Pair",
        "kotlin.Result",
        "kotlin.Triple",
        "kotlin.collections.Collection",
        "kotlin.collections.Map",
        "kotlinx.collections.immutable.ImmutableCollection",
        "kotlinx.collections.immutable.ImmutableMap",
        "kotlinx.coroutines.flow.Flow",
    )

private fun PsiType.containerParameters(): List<PsiType> =
    if (this is PsiClassType && containerTypes.any { InheritanceUtil.isInheritor(this, it) }) {
        parameters.mapNotNull { if (it is PsiWildcardType) it.bound else it }
    } else {
        emptyList()
    }

internal fun PsiType.doNotRetainAnnotation(): PsiAnnotation? =
    (this as? PsiClassType)?.resolve()?.getAnnotation("androidx.compose.runtime.annotation.DoNotRetain")
        ?: superTypes.firstNotNullOfOrNull {
            (it as? PsiClassType)?.resolve()?.getAnnotation("androidx.compose.runtime.annotation.DoNotRetain")
        }
        ?: containerParameters().firstNotNullOfOrNull { it.doNotRetainAnnotation() }

@OptIn(KaExperimentalApi::class)
internal fun checkRetainTypes(
    expression: KtCallExpression,
    reporter: ComposeSwingRuleReporter,
    message: (PsiType, Boolean) -> String?,
) {
    analyze(expression) {
        val call = expression.resolveToCall()?.successfulFunctionCallOrNull() ?: return@analyze
        val id = call.symbol.callableId ?: return@analyze
        if (id.packageName.asString() != "androidx.compose.runtime.retain" || id.callableName.asString() != "retain") {
            return@analyze
        }
        val location = expression.calleeExpression ?: expression
        expression.expressionType?.asPsiType(expression, allowErrorTypes = false)?.let { type ->
            message(type, false)?.let { reporter.report(location, it) }
        }
        expression.valueArguments.forEach { argument ->
            val value = argument.getArgumentExpression() ?: return@forEach
            val type = value.expressionType?.asPsiType(value, allowErrorTypes = false) ?: return@forEach
            message(type, true)?.let { reporter.report(location, it) }
        }
    }
}
