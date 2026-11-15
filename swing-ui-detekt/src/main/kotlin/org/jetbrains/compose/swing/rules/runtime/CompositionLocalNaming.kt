// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.runtime

import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleReporter
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports `CompositionLocal` properties whose name does not start with `Local`.
 * A property is reported when its resolved type is a `CompositionLocal`, whatever expression initializes it.
 */
internal object CompositionLocalNaming : ComposeSwingRule {
    override val id = "CompositionLocalNaming"
    override val description = "CompositionLocal properties should be prefixed with 'Local'"
    override val severity = ComposeSwingRule.Severity.Error
    override val activeByDefault = true
    override val requiresAnalysisApi = true

    override fun createVisitor(
        reporter: ComposeSwingRuleReporter,
        config: ComposeSwingRuleConfig,
    ): KtVisitorVoid = CompositionLocalPropertyNaming(reporter, COMPOSITION_LOCAL_NAMING_MESSAGE)
}

private const val COMPOSITION_LOCAL_NAMING_MESSAGE =
    "This composition local is missing the `Local` prefix. " +
        "The prefix distinguishes values read from the composition from ordinary properties. " +
        "Rename the property to start with `Local`."
