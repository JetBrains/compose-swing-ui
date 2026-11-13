// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Reports a `Transition.animateValue`, `animateFloat`, `animateInt` or `animateColor` call whose
 * state-to-value lambda never reads the state it is handed.
 *
 * That lambda is asked for the value one state animates toward, and it is asked for states other than
 * the current one, so a lambda answering from anything else returns a value for the wrong state and is
 * evaluated again whenever what it read last changes.
 *
 * The call is found by name, since the receiver's type takes a compile classpath this rule does not
 * require. The state-to-value mapping is the lambda passed as `targetValueByState`, else the trailing
 * lambda; the animation spec that may precede it takes no parameter of its own.
 */
public class UnusedTransitionTargetState(
    config: Config,
) : Rule(config, "A transition's value lambda should use the state it receives") {
    override val ruleName: RuleName = RuleName("UnusedTransitionTargetState")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text !in
            setOf("animateValue", "animateFloat", "animateInt", "animateColor")
        ) {
            return
        }
        val lambda =
            (expression.lambdaArgumentNamed("targetValueByState") ?: expression.trailingLambda())
                ?.takeUnless { it.readsTheValueItTakes() } ?: return
        report(
            Finding(
                entity = Entity.from(lambda.parameterOrSelf()),
                message = UNUSED_TRANSITION_TARGET_STATE_MESSAGE,
            ),
        )
    }
}

/**
 * Reports an `AnimatedContent` or `Crossfade` call, including `Transition.Crossfade`, whose `content` or
 * `contentKey` lambda never reads the state it is handed.
 *
 * Both animate between the content of two states, and reach both through these lambdas. A `content`
 * lambda that answers from anything else builds the same content for the state being left and the state
 * being entered, which then animates on top of itself; a `contentKey` lambda that does gives every state
 * one key, so the content already on screen is treated as the one to keep.
 */
public class UnusedAnimatedContentTargetState(
    config: Config,
) : Rule(
        config,
        "The `content` and `contentKey` lambdas of `AnimatedContent` and `Crossfade` should use the state " +
            "they receive",
    ) {
    override val ruleName: RuleName = RuleName("UnusedAnimatedContentTargetState")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text !in setOf("AnimatedContent", "Crossfade")) return
        val content = expression.lambdaArgumentNamed("content") ?: expression.trailingLambda()
        val reported =
            listOf(
                content to UNUSED_ANIMATED_CONTENT_TARGET_STATE_CONTENT_MESSAGE,
                expression.lambdaArgumentNamed("contentKey") to
                    UNUSED_ANIMATED_CONTENT_TARGET_STATE_CONTENT_KEY_MESSAGE,
            )
        reported.forEach { (lambda, message) ->
            if (lambda == null || lambda.readsTheValueItTakes()) return@forEach
            report(
                Finding(
                    entity = Entity.from(lambda.parameterOrSelf()),
                    message = message,
                ),
            )
        }
    }
}

/** The lambda [this] call takes outside its parentheses, if it takes one. */
internal fun KtCallExpression.trailingLambda(): KtLambdaExpression? =
    lambdaArguments.lastOrNull()?.getLambdaExpression()

/** The lambda [this] call takes under [name], if that argument is named and is a lambda. */
internal fun KtCallExpression.lambdaArgumentNamed(name: String): KtLambdaExpression? =
    valueArguments
        .firstOrNull { it.getArgumentName()?.asName?.identifier == name }
        ?.getArgumentExpression()
        ?.let { KtPsiUtil.deparenthesize(it) as? KtLambdaExpression }

/**
 * Whether [this] lambda reads the single value it takes, which a lambda declaring no parameter reads as
 * `it`. A reference counts only if it resolves lexically to that value: not when it is the selector of a
 * qualified expression, and not when a nested lambda or function declares the same name as a parameter.
 * An implicit `it` of a nested lambda counts as a read, since without type information it cannot be told
 * from the value: a nested lambda of type `() -> Unit` or `Scope.() -> Unit` has no `it` at all. A
 * parameter declared as `_` is never read. A lambda declaring more than one parameter is not the shape
 * these rules describe, and counts as reading its value.
 */
internal fun KtLambdaExpression.readsTheValueItTakes(): Boolean {
    val parameters = valueParameters
    if (parameters.size > 1) return true
    val names = parameters.singleOrNull()?.namesItBinds() ?: setOf("it")
    return bodyExpression
        ?.collectDescendantsOfType<KtNameReferenceExpression>()
        ?.any { it.getReferencedName() in names && it.readsValueOf(this) } == true
}

private fun KtNameReferenceExpression.readsValueOf(lambda: KtLambdaExpression): Boolean {
    val name = getReferencedName()
    if ((parent as? KtQualifiedExpression)?.selectorExpression == this || parent is KtValueArgumentName) return false
    return generateSequence(parent) { it.parent }
        .takeWhile { it !== lambda }
        .none { scope ->
            when (scope) {
                is KtLambdaExpression -> {
                    scope.valueParameters.any { name in it.namesItBinds() }
                }

                is KtNamedFunction -> {
                    scope.valueParameters.any { name in it.namesItBinds() }
                }

                else -> {
                    false
                }
            }
        }
}

/** The names [this] parameter binds: its own, or those of the components it is destructured into. */
internal fun KtParameter.namesItBinds(): Set<String> =
    destructuringDeclaration?.entries?.mapNotNull { it.name }?.toSet() ?: setOfNotNull(name)

/** Where [this] lambda's unread value is reported: the parameter declaring it, or the lambda itself. */
internal fun KtLambdaExpression.parameterOrSelf(): KtElement = valueParameters.singleOrNull() ?: this

private const val UNUSED_TRANSITION_TARGET_STATE_MESSAGE: String =
    "This lambda does not use the state it receives. The transition calls it for both its " +
        "current and its target state, so a lambda that ignores the state can give the value " +
        "of the wrong state. Compute the value from the state parameter, and name the " +
        "parameter first if it is `_`. If the value does not depend on the state, it does " +
        "not belong in this transition."

private const val UNUSED_ANIMATED_CONTENT_TARGET_STATE_CONTENT_MESSAGE: String =
    "This `content` lambda does not use the state it receives. `AnimatedContent` and " +
        "`Crossfade` call it for the state animating out as well as the state animating in, so " +
        "the outgoing content can show the new state while it leaves. Build the content from " +
        "the lambda's state parameter."

private const val UNUSED_ANIMATED_CONTENT_TARGET_STATE_CONTENT_KEY_MESSAGE: String =
    "This `contentKey` lambda does not use the state it receives. States with equal keys " +
        "keep the same content instead of animating between them, so different states can get " +
        "one key and no animation. Build the key from the lambda's state parameter."
