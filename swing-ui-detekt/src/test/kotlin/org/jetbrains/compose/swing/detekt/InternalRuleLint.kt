// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import dev.detekt.test.lint

/** Lints [source] with the rule [ruleId] of [ComposeSwingInternalRuleSetProvider], the way detekt creates it. */
internal fun lintInternalRule(
    ruleId: String,
    source: String,
): List<Finding> =
    ComposeSwingInternalRuleSetProvider()
        .instance()
        .rules
        .getValue(RuleName(ruleId))(Config.empty)
        .lint(source)

internal fun composeSwingRule(ruleId: String): Rule =
    ComposeSwingRuleSetProvider().instance().rules.getValue(RuleName(ruleId))(Config.empty)
