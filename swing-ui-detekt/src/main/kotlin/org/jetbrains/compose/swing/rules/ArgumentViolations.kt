// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationList
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtWhenExpression

/**
 * The violations of the declared range and size of a parameter, each paired with the expression to report.
 * [declarations] holds the annotations of the parameter and of the parameters it overrides, the first violated wins.
 * A conditional argument is checked arm by arm, so the report lands on the arm that breaks the declaration.
 */
internal fun KaSession.argumentViolations(
    argument: KtExpression,
    declarations: List<KaAnnotationList>,
): List<Pair<KtExpression, String>> =
    argument.valueArms().mapNotNull { arm ->
        declarations.firstNotNullOfOrNull { armViolation(arm, it) }?.let { arm to it }
    }

private fun KaSession.armViolation(
    arm: KtExpression,
    declaration: KaAnnotationList,
): String? {
    val constant = arm.evaluate()
    val rangeViolation =
        declaration.rangeViolationMessage(constant)
            ?: if (constant == null) with(CalleeRange) { violationMessage(arm, declaration) } else null
    return rangeViolation
        ?: with(KnownSize) { of(arm, constant) }?.let { SizeRequirement.of(declaration)?.violationMessage(it) }
}

/**
 * The expressions an argument can evaluate to: each arm of an `if` or `when`, through parentheses and blocks.
 */
private fun KtExpression.valueArms(): List<KtExpression> =
    when (this) {
        is KtParenthesizedExpression -> expression?.valueArms().orEmpty()
        is KtIfExpression -> listOfNotNull(then, `else`).flatMap { it.valueArms() }
        is KtWhenExpression -> entries.flatMap { it.expression?.valueArms().orEmpty() }
        is KtBlockExpression -> listOfNotNull(statements.lastOrNull()).flatMap { it.valueArms() }
        else -> listOf(this)
    }
