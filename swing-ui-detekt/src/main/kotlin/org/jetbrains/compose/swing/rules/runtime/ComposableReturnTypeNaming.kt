// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.runtime

import io.nlopez.compose.core.Decision
import io.nlopez.compose.rules.Naming
import org.jetbrains.compose.swing.rules.ComposeRulesConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.compose.swing.rules.isComposable
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports a composable function whose name contradicts its resolved return type: a lowercase name on a function
 * that returns `Unit`, or an uppercase name on a function that returns anything else.
 */
internal class ComposableReturnTypeNaming(
    private val reporter: ComposeSwingRuleReporter,
    config: ComposeSwingRuleConfig,
) : KtVisitorVoid() {
    private val composeConfig = ComposeRulesConfig(config)
    private val allowedNames =
        config.valueOrDefault("allowedComposableFunctionNames", emptyList<String>()).map(::Regex)

    override fun visitNamedFunction(function: KtNamedFunction) {
        val identifier = function.nameIdentifier
        if (identifier == null || function.hasModifier(KtTokens.OPERATOR_KEYWORD) ||
            function.hasModifier(KtTokens.OVERRIDE_KEYWORD)
        ) {
            return
        }
        val message = violation(function) ?: return
        val allowed = allowedNames.any { pattern -> pattern.matches(function.name.orEmpty()) }
        if (!allowed || upstreamReports(function)) reporter.report(identifier, message)
    }

    private fun upstreamReports(function: KtNamedFunction): Boolean {
        if (!function.isComposable()) return false
        var reported = false
        Naming().visitComposable(
            function,
            { _, _, _ ->
                reported = true
                Decision.Ignore
            },
            composeConfig,
        )
        return reported
    }

    /** The finding for [function] from its name and resolved return type alone, or null when they agree. */
    private fun violation(function: KtNamedFunction): String? {
        val capitalized = function.name?.firstOrNull()?.isUpperCase()
        val returnsUnit = if (capitalized == null) null else returnsUnit(function)
        return when {
            returnsUnit == null || returnsUnit == capitalized -> null
            returnsUnit -> LOWERCASE_UNIT_MESSAGE
            else -> UPPERCASE_VALUE_MESSAGE
        }
    }

    /** Whether [function] resolves to a Unit return type, or null when it is not composable or does not resolve. */
    private fun returnsUnit(function: KtNamedFunction): Boolean? =
        analyze(function) {
            val symbol = function.symbol
            val type = symbol.returnType
            if (Names.Runtime.Composable.classId in symbol.annotations && type !is KaErrorType) {
                (type.fullyExpandedType as? KaClassType)?.classId == StandardClassIds.Unit
            } else {
                null
            }
        }
}

private const val LOWERCASE_UNIT_MESSAGE =
    "This composable returns `Unit` and starts with a lowercase letter. Capitalized names identify composable " +
        "content at the call site. Rename the function to start with an uppercase letter."

private const val UPPERCASE_VALUE_MESSAGE =
    "This composable returns a value and starts with an uppercase letter. Lowercase names distinguish " +
        "value-producing functions from composable content. Rename the function to start with a lowercase letter."
