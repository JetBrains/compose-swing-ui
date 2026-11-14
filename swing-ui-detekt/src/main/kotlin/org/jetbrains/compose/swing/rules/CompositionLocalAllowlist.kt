// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules

import io.nlopez.compose.core.util.declaresCompositionLocal
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid

/** Reports a composition local declaration whose name is absent from the allowlist. */
internal object CompositionLocalAllowlist : ComposeSwingRule {
    override val id = "CompositionLocalAllowlist"
    override val description = "CompositionLocal usage should be from an allowlist"
    override val severity = ComposeSwingRule.Severity.Error
    override val activeByDefault = true
    override val requiresAnalysisApi = false

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid {
        val allowed = ComposeRulesConfig(config).getSet("allowedCompositionLocals", emptySet())
        return object : KtVisitorVoid() {
            override fun visitProperty(property: KtProperty) {
                val name = property.nameIdentifier
                if (property.declaresCompositionLocal && name?.text !in allowed) {
                    reporter.report(name ?: property, COMPOSITION_LOCAL_NOT_IN_ALLOWLIST_MESSAGE)
                }
            }
        }
    }
}

private const val COMPOSITION_LOCAL_NOT_IN_ALLOWLIST_MESSAGE =
    "This composition local is missing from the allowlist. Composition locals introduce implicit dependencies. " +
        "Pass the value as a parameter or add the composition local to the allowlist if callers need it."
