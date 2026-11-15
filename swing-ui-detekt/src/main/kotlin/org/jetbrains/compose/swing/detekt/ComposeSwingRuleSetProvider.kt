// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/**
 * Detekt checks for Swing modifiers, styles, node declarations, and previews.
 */
public class ComposeSwingRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("compose-swing-core")

    override fun instance(): RuleSet = detektRuleSet(ruleSetId)
}
