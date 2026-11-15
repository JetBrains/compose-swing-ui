// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.core.modifier

import org.jetbrains.compose.swing.rules.ComposeRulesRule
import io.nlopez.compose.rules.ModifierShouldBeForwardedToEveryBranch as UpstreamModifierShouldBeForwardedToEveryBranch

/**
 * Reports branches that omit a modifier parameter forwarded by another branch of the same composable.
 */
internal object ModifierShouldBeForwardedToEveryBranch :
    ComposeRulesRule(
        id = "ModifierShouldBeForwardedToEveryBranch",
        description = "Forward modifiers to every applicable branch",
        visitor = { UpstreamModifierShouldBeForwardedToEveryBranch(ResolvedBranchForwarding::resolve) },
        requiresAnalysisApi = true,
        findingMessage = MISSING_BRANCH_MODIFIER_MESSAGE,
    )

private const val MISSING_BRANCH_MODIFIER_MESSAGE =
    "This branch omits the modifier parameter that another branch forwards. " +
        "Caller customizations would disappear when this branch is selected. " +
        "Pass the modifier to the composable in this branch."
