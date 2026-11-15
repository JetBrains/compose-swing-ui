// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.RuleName
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeRuntimeRuleSetProviderTest {
    @Test
    fun `provider registers runtime rules`() {
        val ruleSet = composeDetektRuleSets.single { it.id.value == "compose-runtime" }
        assertEquals(
            setOf(
                RuleName("AutoboxingStateValueProperty"),
                RuleName("ComposableLambdaParameterNaming"),
                RuleName("ComposableLambdaParameterPosition"),
                RuleName("ComposableNaming"),
                RuleName("CompositionLocalAllowlist"),
                RuleName("CompositionLocalNaming"),
                RuleName("CoroutineCreationDuringComposition"),
                RuleName("FlowOperatorInvokedInComposition"),
                RuleName("FrequentlyChangingValue"),
                RuleName("InvalidReadOnlyComposable"),
                RuleName("LambdaParameterInRestartableEffect"),
                RuleName("MissingNonRestartableComposable"),
                RuleName("MissingReadOnlyComposable"),
                RuleName("MutableCollectionMutableState"),
                RuleName("MutableStateAutoboxing"),
                RuleName("MutableStateParam"),
                RuleName("OpaqueUnitKey"),
                RuleName("ProduceStateDoesNotAssignValue"),
                RuleName("RememberContentMissing"),
                RuleName("RememberInComposition"),
                RuleName("RememberMissing"),
                RuleName("RememberReturnType"),
                RuleName("RememberSaveableSaverParameter"),
                RuleName("RetainRememberObserver"),
                RuleName("RetainUnitType"),
                RuleName("RetainingDoNotRetainType"),
                RuleName("StaleRememberUpdatedStateInRemember"),
                RuleName("StateFlowValueCalledInComposition"),
                RuleName("StateParam"),
                RuleName("UnnecessaryComposable"),
                RuleName("UnnecessaryLaunchedEffect"),
                RuleName("VarsWithoutStateBacking"),
            ),
            ruleSet.rules.keys,
        )
        ruleSet.rules.forEach { (name, createRule) -> assertEquals(name, createRule(Config.empty).ruleName) }
    }
}
