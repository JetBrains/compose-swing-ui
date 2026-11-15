// Copyright 2023-2026 Nacho Lopez, JetBrains s.r.o., and contributors.
// SPDX-License-Identifier: Apache-2.0
// Adapted from compose-rules to recognize Swing component emission.
package org.jetbrains.compose.swing.rules.content

import io.nlopez.compose.core.ComposeKtConfig
import io.nlopez.compose.core.ComposeKtVisitor
import io.nlopez.compose.core.Emitter
import io.nlopez.compose.core.report
import io.nlopez.compose.core.util.findAllChildrenByClass
import io.nlopez.compose.core.util.isLambda
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.core.modifier.customModifierTypes
import org.jetbrains.compose.swing.rules.core.modifier.declaresModifier
import org.jetbrains.compose.swing.rules.core.modifier.hasComposableAnnotation
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction
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

private const val COMPOSABLE_NESTING_DEPTH_MESSAGE =
    "This composable nests content more deeply than the configured limit. " +
        "Deep nesting makes it harder to see how content is organized. " +
        "Extract an inner section into a private composable."

private const val LAMBDA_PARAMETER_EVENT_TRAILING_MESSAGE =
    "This required event callback is the last parameter of a composable that emits content. " +
        "Trailing lambda syntax makes the callback look like a slot for child composables. " +
        "Move the event callback before the modifier parameter."
