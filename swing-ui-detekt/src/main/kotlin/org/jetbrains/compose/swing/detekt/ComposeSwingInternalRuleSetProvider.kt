// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/**
 * Rules this project holds itself to that say nothing about this library's API. They state things about
 * Kotlin generally, and are declared here because no published ruleset provides them.
 */
internal class ComposeSwingInternalRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("compose-swing-internal")

    override fun instance(): RuleSet = detektRuleSet(ruleSetId)
}
