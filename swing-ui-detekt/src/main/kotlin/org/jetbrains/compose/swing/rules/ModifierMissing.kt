// Copyright 2023-2026 Nacho Lopez, JetBrains s.r.o., and contributors.
// SPDX-License-Identifier: Apache-2.0
package org.jetbrains.compose.swing.rules

import com.intellij.psi.PsiElement
import io.nlopez.compose.core.ComposeKtConfig
import io.nlopez.compose.core.ComposeKtVisitor
import io.nlopez.compose.core.Emitter
import io.nlopez.compose.core.report
import io.nlopez.compose.core.util.definedInInterface
import io.nlopez.compose.core.util.isAnnotatedWith
import io.nlopez.compose.core.util.isInternal
import io.nlopez.compose.core.util.isOverride
import io.nlopez.compose.core.util.returnsValue
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.isPublic

/** Reports a composable function that emits Swing content and accepts no modifier. */
internal object ModifierMissing : ComposeSwingRule {
    override val id: String = "ModifierMissing"
    override val description: String = "Composable functions should accept a modifier parameter"
    override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error
    override val activeByDefault: Boolean = true
    override val requiresAnalysisApi: Boolean = false

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid {
        val composeConfig = ComposeRulesConfig(config)
        val customModifierTypes = config.customModifierTypes()
        val emission = SwingContentEmission(config)
        val ignoredAnnotations =
            composeConfig.getList("ignoreAnnotated", emptyList()).map(String::trim).toSet() +
                composeConfig.getSet("modifierMissingIgnoreAnnotated", emptySet())
        return object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                val function = element as? KtFunction ?: return
                if (!function.shouldCheckModifier(composeConfig, customModifierTypes) ||
                    function.isAnnotatedWith(ignoredAnnotations)
                ) {
                    return
                }
                if (emission.count(function) > 0) {
                    reporter.report(
                        function.nameIdentifier ?: function,
                        MISSING_MODIFIER_MESSAGE,
                    )
                }
            }
        }
    }
}

/** Checks modifier parameter names within a composable declaration. */
internal class ModifierNaming(
    config: ComposeSwingRuleConfig,
) : ComposeKtVisitor {
    private val customModifierTypes = config.customModifierTypes()

    override fun visitComposable(
        function: KtFunction,
        emitter: Emitter,
        config: ComposeKtConfig,
    ) {
        val modifiers = function.modifierParameters(customModifierTypes)
        for (parameter in modifiers) {
            val name = parameter.name.orEmpty()
            val valid =
                if (modifiers.size == 1) name == "modifier" else name.endsWith("Modifier", ignoreCase = true)
            if (!valid) emitter.report(parameter, MODIFIER_NAMING_MESSAGE)
        }
    }
}

private fun KtFunction.shouldCheckModifier(
    config: ComposeKtConfig,
    customModifierTypes: Set<String>,
): Boolean =
    isComposable() &&
        acceptsCheckedVisibility(config) &&
        !returnsValue &&
        !isOverride &&
        !definedInInterface &&
        !hasSwingPreviewAnnotation() &&
        !receiverTypeReference.declaresModifier(customModifierTypes) &&
        valueParameters.none { it.typeReference.declaresModifier(customModifierTypes) }

private fun KtFunction.acceptsCheckedVisibility(config: ComposeKtConfig): Boolean =
    when (config.getString("checkModifiersForVisibility", "only_public")) {
        "public_and_internal" -> isPublic || isInternal
        "all" -> true
        else -> isPublic
    }

internal fun ComposeSwingRuleConfig.customModifierTypes(): Set<String> =
    valueOrDefault("customModifiers", emptyList<String>()).toSet()

internal fun KtTypeReference?.declaresModifier(customModifierTypes: Set<String>): Boolean =
    declaresSwingModifier() ||
        (this?.typeElement as? KtUserType)?.let { type ->
            type.text in customModifierTypes ||
                customModifierTypes.any { '.' in it && type.declaresLibraryType(it) }
        } == true

internal fun KtFunction.modifierParameters(customModifierTypes: Set<String>): List<KtParameter> =
    valueParameters.filter { it.typeReference.declaresModifier(customModifierTypes) }

private const val MISSING_MODIFIER_MESSAGE =
    "This composable emits content without accepting a modifier parameter. " +
        "Callers cannot customize its root component. " +
        "Add a `SwingModifier` parameter with the empty modifier as its default and pass it to the root component."

private const val MODIFIER_NAMING_MESSAGE =
    "This modifier parameter has an inconsistent name. A consistent name helps callers find where to customize " +
        "the component. Name a sole parameter `modifier`. With several modifier parameters, a primary parameter " +
        "may be named `modifier`. Give the others names ending in `Modifier`."
