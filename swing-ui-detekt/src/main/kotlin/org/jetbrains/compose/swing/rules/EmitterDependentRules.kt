// Copyright 2023-2026 Nacho Lopez, JetBrains s.r.o., and contributors.
// SPDX-License-Identifier: Apache-2.0
// Adapted from compose-rules to recognize Swing component emission.
package org.jetbrains.compose.swing.rules

import io.nlopez.compose.core.ComposeKtConfig
import io.nlopez.compose.core.ComposeKtVisitor
import io.nlopez.compose.core.Emitter
import io.nlopez.compose.core.report
import io.nlopez.compose.core.util.findAllChildrenByClass
import io.nlopez.compose.core.util.isLambda
import io.nlopez.compose.core.util.obtainAllModifierNames
import io.nlopez.compose.core.util.rootExpression
import io.nlopez.compose.core.util.walkBackwards
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.parents

/** Limits emitter nesting in the current Swing composition. */
internal class ComposableNestingDepth(
    config: ComposeSwingRuleConfig,
) : ComposeKtVisitor {
    private val content = SwingContentEmission(config)

    override val isOptIn: Boolean = true

    override fun visitComposable(
        function: KtFunction,
        emitter: Emitter,
        config: ComposeKtConfig,
    ) {
        val body = function.bodyExpression ?: return
        val threshold = config.getInt("composableNestingDepthThreshold", 3)
        val deepest =
            body
                .findAllChildrenByClass<KtCallExpression>()
                .filter { content.includes(it, function) && content.emitsContent(it) }
                .maxOfOrNull { call ->
                    call.parents
                        .takeWhile { it != function }
                        .filterIsInstance<KtCallExpression>()
                        .count { content.emitsContent(it) }
                } ?: return

        if (deepest > threshold) {
            emitter.report(function, COMPOSABLE_NESTING_DEPTH_MESSAGE)
        }
    }
}

/** Checks the placement of event callbacks on content-emitting composables. */
internal class LambdaParameterEventTrailing(
    config: ComposeSwingRuleConfig,
) : ComposeKtVisitor {
    private val content = SwingContentEmission(config)
    private val customModifierTypes = config.customModifierTypes()

    override fun visitComposable(
        function: KtFunction,
        emitter: Emitter,
        config: ComposeKtConfig,
    ) {
        // The modifier gives the required callback a natural place before optional parameters.
        if (function.valueParameters.none { it.typeReference.declaresModifier(customModifierTypes) }) return
        val trailingParam =
            function.valueParameters.lastOrNull()?.takeIf { parameter ->
                val type = parameter.typeReference
                type != null && type.isLambda() && !type.hasComposableAnnotation() &&
                    !parameter.hasDefaultValue() && parameter.name?.startsWith("on") == true
            } ?: return

        if (content.count(function) > 0) {
            emitter.report(trailingParam, LAMBDA_PARAMETER_EVENT_TRAILING_MESSAGE)
        }
    }
}

/** Checks that callers can customize the root component through its modifier. */
internal class ModifierNotUsedAtRoot(
    config: ComposeSwingRuleConfig,
) : ComposeKtVisitor {
    private val content = SwingContentEmission(config)
    private val customModifierTypes = config.customModifierTypes()

    override fun visitComposable(
        function: KtFunction,
        emitter: Emitter,
        config: ComposeKtConfig,
    ) {
        val modifier = function.valueParameters.firstOrNull { it.name == "modifier" }
        if (!modifier?.typeReference.declaresModifier(customModifierTypes)) return
        val code = function.bodyBlockExpression ?: return

        val modifiers = code.obtainAllModifierNames("modifier").toSet()

        val errors =
            code
                .findAllChildrenByClass<KtCallExpression>()
                .filter { content.includes(it, code) && content.mayApplyModifier(it) }
                .filter { callExpression ->
                    callExpression.parents
                        .takeWhile { it != code }
                        .filterIsInstance<KtCallExpression>()
                        .takeWhile { !content.isEmissionBoundary(it) }
                        .any { content.emitsContent(it) }
                }.mapNotNull { callExpression ->
                    callExpression.modifierArguments(function, modifiers, customModifierTypes).firstOrNull()
                }

        for (valueArgument in errors) {
            emitter.report(valueArgument, MODIFIER_NOT_USED_AT_ROOT_MESSAGE)
        }
    }
}

/** Checks that each execution path uses a modifier parameter once. */
internal class ModifierReused(
    config: ComposeSwingRuleConfig,
) : ComposeKtVisitor {
    private val content = SwingContentEmission(config)
    private val customModifierTypes = config.customModifierTypes()

    override fun visitComposable(
        function: KtFunction,
        emitter: Emitter,
        config: ComposeKtConfig,
    ) {
        if (content.count(function) == 0) return

        val composableBlockExpression = function.bodyBlockExpression ?: return
        val initialModifierNames = function.modifierParameters(customModifierTypes).mapNotNull { it.name }.toSet()

        initialModifierNames
            .flatMap { modifierName ->
                val names = composableBlockExpression.obtainAllModifierNames(modifierName).toSet()

                fun KtCallExpression.usesModifier(): Boolean =
                    content.includes(this, composableBlockExpression) &&
                        content.mayApplyModifier(this) &&
                        modifierArguments(function, names, customModifierTypes).isNotEmpty()

                composableBlockExpression
                    .findAllChildrenByClass<KtCallExpression>()
                    .filter { it.usesModifier() }
                    .flatMap { call ->
                        // Earlier sibling calls and enclosing calls can run on the same execution path.
                        val usages =
                            call
                                .walkBackwards(stopAtParent = composableBlockExpression)
                                .mapNotNull { element ->
                                    element as? KtCallExpression
                                        ?: (element as? KtQualifiedExpression)?.selectorExpression as? KtCallExpression
                                }.filter { it.usesModifier() }
                                .toSet()
                        usages.takeIf { it.size > 1 }.orEmpty()
                    }
            }.distinct()
            .forEach { emitter.report(it, MODIFIER_REUSED_MESSAGE, false) }
    }
}

private fun KtCallExpression.modifierArguments(
    function: KtFunction,
    modifiers: Set<String>,
    customModifierTypes: Set<String>,
): List<KtValueArgument> {
    val shadowed =
        parents
            .takeWhile { it != function }
            .filterIsInstance<KtCallableDeclaration>()
            .flatMap { it.valueParameters }
            .flatMap { parameter ->
                parameter.name?.let(::listOf)
                    ?: parameter.destructuringDeclaration
                        ?.entries
                        .orEmpty()
                        .mapNotNull { it.name }
            }.toSet()
    val accessible = modifiers - shadowed
    if (accessible.isEmpty()) return emptyList()
    return valueArguments.filter { argument ->
        val expression = KtPsiUtil.deparenthesize(argument.getArgumentExpression())
        expression.referencesModifier(accessible) ||
            (
                expression is KtDotQualifiedExpression &&
                    expression.prependsModifier(modifiers, accessible, customModifierTypes)
            )
    }
}

private fun KtExpression?.referencesModifier(modifiers: Set<String>): Boolean =
    when (this) {
        is KtReferenceExpression -> text in modifiers
        is KtDotQualifiedExpression -> rootExpression.text in modifiers
        else -> false
    }

private fun KtDotQualifiedExpression.prependsModifier(
    modifiers: Set<String>,
    accessible: Set<String>,
    customModifierTypes: Set<String>,
): Boolean {
    val chain = generateSequence<KtExpression>(this) { (it as? KtDotQualifiedExpression)?.receiverExpression }
    return (rootExpression.text in modifiers || chain.any { it.isModifierChainRoot(customModifierTypes) }) &&
        chain.filterIsInstance<KtDotQualifiedExpression>().any { link ->
            val selector = link.selectorExpression as? KtCallExpression
            selector?.calleeExpression?.text == "then" &&
                selector.valueArguments.any {
                    KtPsiUtil.deparenthesize(it.getArgumentExpression()).referencesModifier(accessible)
                }
        }
}

private fun KtExpression.isModifierChainRoot(customModifierTypes: Set<String>): Boolean =
    when {
        text == SWING_MODIFIER_FQN || text in customModifierTypes -> {
            true
        }

        this is KtNameReferenceExpression -> {
            isLibraryCompanionValue(SWING_MODIFIER_FQN) ||
                customModifierTypes.any { '.' in it && isLibraryCompanionValue(it) }
        }

        else -> {
            false
        }
    }

private const val COMPOSABLE_NESTING_DEPTH_MESSAGE =
    "This composable nests content more deeply than the configured limit. " +
        "Deep nesting makes it harder to see how content is organized. " +
        "Extract an inner section into a private composable."

private const val LAMBDA_PARAMETER_EVENT_TRAILING_MESSAGE =
    "This required event callback is the last parameter of a composable that emits content. " +
        "Trailing lambda syntax makes the callback look like a slot for child composables. " +
        "Move the event callback before the modifier parameter."

private const val MODIFIER_NOT_USED_AT_ROOT_MESSAGE =
    "This modifier parameter is applied to a child component. " +
        "The caller's customizations then affect only that child. " +
        "Apply the parameter once at the start of the modifier chain on the outermost component that accepts it."

private const val MODIFIER_REUSED_MESSAGE =
    "This modifier parameter is applied to more than one component. " +
        "Each component receives the caller's customizations even when its chain adds other elements. " +
        "Apply the parameter once to the root component and start a fresh modifier chain for each child."
