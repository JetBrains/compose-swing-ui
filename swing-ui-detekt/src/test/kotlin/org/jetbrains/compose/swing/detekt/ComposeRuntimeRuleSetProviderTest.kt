// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.RuleName
import dev.detekt.api.RuleSetId
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeRuntimeRuleSetProviderTest {
    @Test
    fun `provider registers runtime rules`() {
        val ruleSet = ComposeRuntimeRuleSetProvider().instance()
        assertEquals(RuleSetId("compose-runtime"), ruleSet.id)
        assertEquals(
            setOf(
                RuleName("ComposableNaming"),
                RuleName("CompositionLocalNaming"),
                RuleName("CoroutineCreationDuringComposition"),
                RuleName("FlowOperatorInvokedInComposition"),
                RuleName("MutableStateAutoboxing"),
                RuleName("OpaqueUnitKey"),
                RuleName("ProduceStateDoesNotAssignValue"),
                RuleName("RememberInComposition"),
                RuleName("RememberMissing"),
                RuleName("RememberReturnType"),
            ),
            ruleSet.rules.keys,
        )
        ruleSet.rules.forEach { (name, createRule) -> assertEquals(name, createRule(Config.empty).ruleName) }
    }
}
