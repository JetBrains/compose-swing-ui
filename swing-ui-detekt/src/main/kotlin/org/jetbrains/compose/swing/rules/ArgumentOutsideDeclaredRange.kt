/*
 * Copyright (C) 2017 The Android Open Source Project
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

// Ported from RangeDetector in Android Lint's tools/base/lint/libs/lint-checks.

package org.jetbrains.compose.swing.rules

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationList
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a constant argument outside the `@IntRange` or `@FloatRange` its parameter declares, an argument whose own
 * declared range can leave it, and an argument whose known size breaks the parameter's `@Size`.
 * It also reports a declared range that no value can satisfy.
 */
internal object ArgumentOutsideDeclaredRange : ComposeSwingRule {
    override val id: String = "ArgumentOutsideDeclaredRange"
    override val description: String = "An argument or declared range is outside its range bounds."
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid =
        object : KtVisitorVoid() {
            @OptIn(KaExperimentalApi::class)
            override fun visitAnnotationEntry(entry: KtAnnotationEntry) {
                if (entry.isInTestSource()) return
                val message =
                    analyze(entry) {
                        val classId = (entry.typeReference?.type?.fullyExpandedType as? KaClassType)?.classId
                        if (classId != INT_RANGE_CLASS_ID && classId != FLOAT_RANGE_CLASS_ID) return@analyze null
                        val call = entry.resolveCall() ?: return@analyze null
                        val arguments =
                            call.valueArgumentMapping
                                .mapNotNull { (expression, parameter) ->
                                    val value =
                                        expression.evaluateAsAnnotationValue() as? KaAnnotationValue.ConstantValue
                                    val constant = value?.value
                                    val name = parameter.symbol.name.asString()
                                    constant
                                        ?.takeUnless { it is KaConstantValue.ErrorValue }
                                        ?.let { name to it }
                                }.toMap()
                        when (classId) {
                            INT_RANGE_CLASS_ID -> intBounds(arguments::get).impossibleMessage()
                            FLOAT_RANGE_CLASS_ID -> floatBounds(arguments::get).impossibleMessage()
                            else -> null
                        }
                    } ?: return
                reporter.report(entry, message)
            }

            override fun visitCallExpression(expression: KtCallExpression) {
                if (expression.isInTestSource()) return
                reportArguments(expression)
            }

            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                if (!expression.isInTestSource()) reportArguments(expression)
            }

            override fun visitArrayAccessExpression(expression: KtArrayAccessExpression) {
                if (!expression.isInTestSource() && !expression.isAssignmentTarget()) reportArguments(expression)
            }

            private fun reportArguments(expression: KtExpression) {
                val violations =
                    analyze(expression) {
                        functionCallsOf(expression)
                            .flatMap { call ->
                                val function = call.symbol
                                call.valueArgumentMapping.flatMap { (argument, signature) ->
                                    val parameter = signature.symbol
                                    val index = function.valueParameters.indexOf(parameter)
                                    val inherited =
                                        (function as? KaNamedFunctionSymbol)
                                            ?.allOverriddenSymbols
                                            .orEmpty()
                                            .filterIsInstance<KaNamedFunctionSymbol>()
                                            .mapNotNull { it.valueParameters.getOrNull(index) }
                                    argumentViolations(
                                        argument,
                                        listOf(parameter.annotations) + inherited.map { it.annotations },
                                    )
                                }
                            }.distinct()
                    }
                violations.forEach { (argument, message) -> reporter.report(argument, message) }
            }
        }
}

/**
 * A file under a test source set is a test source; a file is under the source set named by the last `/src/` directory
 * of its path, and `test` or a name ending in `Test` marks a test source set.
 */
internal fun PsiElement.isInTestSource(): Boolean = containingFile?.virtualFile?.path?.isTestSourcePath() == true

internal fun String.isTestSourcePath(): Boolean = contains(Regex("/src/(test|[^/]+Test)/(?!.*/src/)"))

internal val INT_RANGE_CLASS_ID = ClassId.topLevel(FqName("androidx.annotation.IntRange"))
internal val FLOAT_RANGE_CLASS_ID = ClassId.topLevel(FqName("androidx.annotation.FloatRange"))

/** The inclusive bounds an `@IntRange` declares; an unset bound is the widest `Long`. */
internal class IntBounds(
    val from: Long,
    val to: Long,
) {
    fun impossibleMessage(): String? =
        (
            "This declared range holds no value because from ($from) is greater than to ($to). " +
                "Use ordered bounds."
        ).takeIf { from > to }

    fun violationMessage(value: Long): String? {
        val lower = if (from == Long.MIN_VALUE) null else "\u2265 $from"
        val upper = if (to == Long.MAX_VALUE) null else "\u2264 $to"
        val bounds = listOfNotNull(lower, upper).joinToString(" and ")
        return (
            "Value $value is outside the declared range, which requires $bounds. " +
                "Pass a value within the range."
        ).takeUnless { value in from..to }
    }
}

/** The bounds an `@FloatRange` declares and whether each is inclusive; an unset bound is infinite. */
internal class FloatBounds(
    val from: Double,
    val to: Double,
    val fromInclusive: Boolean,
    val toInclusive: Boolean,
) {
    fun impossibleMessage(): String? =
        (
            "This declared range holds no value because its bounds exclude every value. " +
                "Use bounds that include a value."
        ).takeIf { from > to || (from == to && (!fromInclusive || !toInclusive)) }

    fun violationMessage(value: Double): String? {
        val above = if (fromInclusive) value >= from else value > from
        val below = if (toInclusive) value <= to else value < to
        val lower = if (from == Double.NEGATIVE_INFINITY) null else "${if (fromInclusive) "\u2265" else ">"} $from"
        val upper = if (to == Double.POSITIVE_INFINITY) null else "${if (toInclusive) "\u2264" else "<"} $to"
        val bounds = listOfNotNull(lower, upper).joinToString(" and ")
        return (
            "Value $value is outside the declared range, which requires $bounds. " +
                "Pass a value within the range."
        ).takeUnless { above && below }
    }
}

internal fun KaAnnotationList.rangeViolationMessage(constant: KaConstantValue?): String? {
    val safeConstant = constant.takeUnless { it == null || it is KaConstantValue.ErrorValue }
    val number = safeConstant?.value as? Number ?: return null
    val intMessage = annotationOf(INT_RANGE_CLASS_ID)?.intBounds()?.violationMessage(number.toLong())
    val floatMessage = annotationOf(FLOAT_RANGE_CLASS_ID)?.floatBounds()?.violationMessage(number.toDouble())
    return intMessage ?: floatMessage
}

internal fun KaAnnotationList.annotationOf(classId: ClassId): KaAnnotation? = this[classId].firstOrNull()

internal fun KaAnnotation.intBounds(): IntBounds = intBounds(::constantArgument)

private fun intBounds(argument: (String) -> KaConstantValue?): IntBounds =
    IntBounds(
        (argument("from")?.value as? Number)?.toLong() ?: Long.MIN_VALUE,
        (argument("to")?.value as? Number)?.toLong() ?: Long.MAX_VALUE,
    )

internal fun KaAnnotation.floatBounds(): FloatBounds = floatBounds(::constantArgument)

private fun floatBounds(argument: (String) -> KaConstantValue?): FloatBounds =
    FloatBounds(
        (argument("from")?.value as? Number)?.toDouble() ?: Double.NEGATIVE_INFINITY,
        (argument("to")?.value as? Number)?.toDouble() ?: Double.POSITIVE_INFINITY,
        (argument("fromInclusive")?.value as? Boolean) ?: true,
        (argument("toInclusive")?.value as? Boolean) ?: true,
    )

internal fun KaAnnotation.constantArgument(name: String): KaConstantValue? {
    val value = arguments.firstOrNull { it.name.asString() == name }?.expression
    return (value as? KaAnnotationValue.ConstantValue)?.value
}
