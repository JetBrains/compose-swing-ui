// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.runtime

import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports state created during composition without `remember`.
 * Checks calls to `@StateFactoryMarker` functions, constructors of `State` types, and `State` object literals.
 */
internal object RememberMissing : ComposeSwingRule {
    override val id = "RememberMissing"
    override val description = "State variables should be remembered"
    override val severity = ComposeSwingRule.Severity.Error
    override val activeByDefault = true
    override val requiresAnalysisApi = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid = UnrememberedStateCreation(reporter)
}
