// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/**
 * Provides the `compose-runtime` rule set to detekt.
 * Its rules check how composables use the Compose runtime, such as `remember`, `retain` and `produceState`.
 */
internal class ComposeRuntimeRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("compose-runtime")

    override fun instance(): RuleSet = detektRuleSet(ruleSetId)
}
