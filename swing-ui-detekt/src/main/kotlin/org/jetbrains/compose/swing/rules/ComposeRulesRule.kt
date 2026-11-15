// Copyright 2023-2026 Nacho Lopez, JetBrains s.r.o., and contributors.
// SPDX-License-Identifier: Apache-2.0
package org.jetbrains.compose.swing.rules

import com.intellij.psi.PsiElement
import io.nlopez.compose.core.ComposeKtVisitor
import io.nlopez.compose.core.Decision
import io.nlopez.compose.core.Emitter
import org.jetbrains.compose.swing.rules.core.modifier.isComposable
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Runs a compose-rules `ComposeKtVisitor` on a class or function. Findings on declarations use their name
 * identifiers when available.
 */
internal open class ComposeRulesRule private constructor(
    final override val id: String,
    final override val description: String,
    private val configuredVisitor: (ComposeSwingRuleConfig) -> ComposeKtVisitor,
    final override val activeByDefault: Boolean,
    final override val requiresAnalysisApi: Boolean,
    private val findingMessage: String?,
) : ComposeSwingRule {
    constructor(
        id: String,
        description: String,
        visitor: () -> ComposeKtVisitor,
        activeByDefault: Boolean = true,
        requiresAnalysisApi: Boolean = false,
        findingMessage: String,
    ) : this(id, description, { _ -> visitor() }, activeByDefault, requiresAnalysisApi, findingMessage)

    constructor(
        id: String,
        description: String,
        configuredVisitor: (ComposeSwingRuleConfig) -> ComposeKtVisitor,
        activeByDefault: Boolean = true,
    ) : this(id, description, configuredVisitor, activeByDefault, requiresAnalysisApi = false, findingMessage = null)

    final override val severity: ComposeSwingRule.Severity = ComposeSwingRule.Severity.Error

    final override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid {
        val composeConfig = ComposeRulesConfig(config)
        val emitter =
            Emitter { element, message, _ ->
                reporter.report(
                    (element as? KtNamedDeclaration)?.nameIdentifier ?: element,
                    findingMessage ?: message,
                )
                Decision.Ignore
            }
        return object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                if (element !is KtClassOrObject && element !is KtFunction) return
                val upstream = configuredVisitor(config)
                if (element is KtClass) upstream.visitClass(element, emitter, composeConfig)
                if (element is KtClassOrObject) upstream.visitClassOrObject(element, emitter, composeConfig)
                if (element is KtFunction) {
                    upstream.visitFunction(element, emitter, composeConfig)
                    if (element.isComposable()) {
                        upstream.visitComposable(element, emitter, composeConfig)
                    }
                }
            }
        }
    }
}
