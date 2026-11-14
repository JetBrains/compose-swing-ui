// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationList
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaKotlinPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression

/**
 * Compares the `@IntRange` or `@FloatRange` declared on the function, property, parameter or variable an argument
 * reads with the range its parameter declares, and reports each side on which the read value can leave that range.
 * A read of something that declares no range of the parameter's kind is not reported.
 */
internal object CalleeRange {
    fun KaSession.violationMessage(
        argument: KtExpression,
        parameter: KaAnnotationList,
    ): String? {
        val read = (argument as? KtQualifiedExpression)?.selectorExpression ?: argument
        val symbol =
            read.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.symbol ?: return null
        val declared = declarationsOf(symbol)
        val leak = IntLeak.of(parameter, declared) ?: FloatLeak.of(parameter, declared)
        return leak?.let { "Value ${(symbol as? KaNamedSymbol)?.name} $it. Pass a value within the range." }
    }

    private fun declarationsOf(symbol: KaCallableSymbol): List<KaAnnotationList> =
        when (symbol) {
            is KaKotlinPropertySymbol -> {
                listOfNotNull(symbol.annotations, symbol.getter?.annotations, symbol.backingFieldSymbol?.annotations)
            }

            else -> {
                listOf(symbol.annotations)
            }
        }
}

private object IntLeak {
    fun of(
        parameter: KaAnnotationList,
        declared: List<KaAnnotationList>,
    ): String? {
        val allowed = parameter.annotationOf(INT_RANGE_CLASS_ID)?.intBounds()
        val read = declared.firstNotNullOfOrNull { it.annotationOf(INT_RANGE_CLASS_ID) }?.intBounds()
        return if (allowed == null || read == null) null else read.leakBeyond(allowed)
    }

    private fun IntBounds.leakBeyond(allowed: IntBounds): String? {
        val lower = "\u2265 ${allowed.from}".takeUnless { allowed.from == Long.MIN_VALUE }
        val upper = "\u2264 ${allowed.to}".takeUnless { allowed.to == Long.MAX_VALUE }
        return describeLeak(
            listOfNotNull(
                (if (from == Long.MIN_VALUE) "unbounded below" else "as low as $from").takeIf { from < allowed.from },
                (if (to == Long.MAX_VALUE) "unbounded above" else "as high as $to").takeIf { to > allowed.to },
            ),
            listOfNotNull(lower, upper),
        )
    }
}

private object FloatLeak {
    fun of(
        parameter: KaAnnotationList,
        declared: List<KaAnnotationList>,
    ): String? {
        val allowed = parameter.annotationOf(FLOAT_RANGE_CLASS_ID)?.floatBounds()
        val read = declared.firstNotNullOfOrNull { it.annotationOf(FLOAT_RANGE_CLASS_ID) }?.floatBounds()
        return if (allowed == null || read == null) null else read.leakBeyond(allowed)
    }

    private fun FloatBounds.leakBeyond(allowed: FloatBounds): String? {
        val lower = "${if (allowed.fromInclusive) "\u2265" else ">"} ${allowed.from}"
        val upper = "${if (allowed.toInclusive) "\u2264" else "<"} ${allowed.to}"
        return describeLeak(
            listOfNotNull(
                (if (from == Double.NEGATIVE_INFINITY) "unbounded below" else "as low as $from")
                    .takeIf { leaksBelow(allowed) },
                (if (to == Double.POSITIVE_INFINITY) "unbounded above" else "as high as $to")
                    .takeIf { leaksAbove(allowed) },
            ),
            listOfNotNull(
                lower.takeUnless { allowed.from == Double.NEGATIVE_INFINITY },
                upper.takeUnless { allowed.to == Double.POSITIVE_INFINITY },
            ),
        )
    }

    private fun FloatBounds.leaksBelow(allowed: FloatBounds): Boolean =
        allowed.from > Double.NEGATIVE_INFINITY &&
            (from < allowed.from || (from == allowed.from && fromInclusive && !allowed.fromInclusive))

    private fun FloatBounds.leaksAbove(allowed: FloatBounds): Boolean =
        allowed.to < Double.POSITIVE_INFINITY &&
            (to > allowed.to || (to == allowed.to && toInclusive && !allowed.toInclusive))
}

private fun describeLeak(
    sides: List<String>,
    requirement: List<String>,
): String? =
    sides
        .takeIf { it.isNotEmpty() }
        ?.let {
            "can be ${it.joinToString(" and ")}, outside the declared range, " +
                "which requires ${requirement.joinToString(" and ")}"
        }
