// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

internal class AndroidxAnnotationsRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("androidx-annotations")

    override fun instance(): RuleSet = detektRuleSet(ruleSetId)
}
