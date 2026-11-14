// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.RuleName
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComposeSwingRuleSetProviderTest {
    @Test
    fun `service loader discovers all providers`() {
        val expected =
            setOf(
                ComposeRuntimeRuleSetProvider::class.java,
                ComposeSwingInternalRuleSetProvider::class.java,
                ComposeSwingRuleSetProvider::class.java,
            )
        val discovered =
            ServiceLoader
                .load(RuleSetProvider::class.java)
                .map { it.javaClass }
                .filter { it in expected }
                .toSet()

        assertEquals(expected, discovered)
    }

    private val publishedRuleNames =
        setOf(
            RuleName("ArgumentOutsideDeclaredRange"),
            RuleName("ComposableAnnotationNaming"),
            RuleName("ComposableNestingDepth"),
            RuleName("ComposableParamOrder"),
            RuleName("ComposableSwingModifierFactory"),
            RuleName("CompositionLocalAllowlist"),
            RuleName("ConditionHoist"),
            RuleName("ContentEmitterReturningValues"),
            RuleName("ContentSlotReused"),
            RuleName("ContentTrailingLambda"),
            RuleName("DefaultsVisibility"),
            RuleName("InvalidReadOnlyComposable"),
            RuleName("LambdaParameterEventTrailing"),
            RuleName("LambdaParameterInRestartableEffect"),
            RuleName("MissingNonRestartableComposable"),
            RuleName("MissingReadOnlyComposable"),
            RuleName("ModifierComposed"),
            RuleName("ModifierMissing"),
            RuleName("ModifierNaming"),
            RuleName("ModifierNodeInspectableProperties"),
            RuleName("ModifierNotUsedAtRoot"),
            RuleName("ModifierReused"),
            RuleName("ModifierShouldBeForwardedToEveryBranch"),
            RuleName("MultipleEmitters"),
            RuleName("MultipleSwingModifierParameters"),
            RuleName("MultipleSwingNodeCalls"),
            RuleName("MutableParams"),
            RuleName("MutableStateParam"),
            RuleName("ParameterNaming"),
            RuleName("PreviewAnnotationNaming"),
            RuleName("PreviewNaming"),
            RuleName("PreviewNeedsComposableAnnotation"),
            RuleName("PreviewPublic"),
            RuleName("PreviewSignature"),
            RuleName("RememberContentMissing"),
            RuleName("RestrictedApi"),
            RuleName("StaleRememberUpdatedStateInRemember"),
            RuleName("StateParam"),
            RuleName("SwingComponentInComposableSignature"),
            RuleName("SwingModifierFactoryExtensionFunction"),
            RuleName("SwingModifierFactoryReturnType"),
            RuleName("SwingModifierThen"),
            RuleName("SwingModifierUnreferencedReceiver"),
            RuleName("SwingModifierWithoutDefault"),
            RuleName("SwingNodeFactoryReadsDeclaredValue"),
            RuleName("SwingNodeFactoryReusesComponent"),
            RuleName("SwingNodeUpdateConditionalCall"),
            RuleName("SwingNodeUpdateRepeatsInvalidation"),
            RuleName("SwingNodeUpdateStoresCallback"),
            RuleName("SwingNodeUpdateWritesComponentPart"),
            RuleName("SwingStyleWithoutDefault"),
            RuleName("UnheldColumnComparator"),
            RuleName("UnnecessaryComposable"),
            RuleName("UnnecessaryLaunchedEffect"),
            RuleName("UnstableCollections"),
            RuleName("UnusedAnimatedContentTargetState"),
            RuleName("UnusedTransitionTargetState"),
            RuleName("VarsWithoutStateBacking"),
            RuleName("VisibleForTests"),
        )

    @Test
    fun `public provider registers every published rule`() {
        val ruleSet = ComposeSwingRuleSetProvider().instance()

        assertEquals(RuleSetId("compose-swing"), ruleSet.id)
        assertEquals(
            publishedRuleNames,
            ruleSet.rules.keys,
        )
        ruleSet.rules.forEach { (name, createRule) -> assertEquals(name, createRule(Config.empty).ruleName) }
    }

    @Test
    fun `internal provider registers only repository rules`() {
        val ruleSet = ComposeSwingInternalRuleSetProvider().instance()

        assertEquals(RuleSetId("compose-swing-internal"), ruleSet.id)
        assertEquals(
            setOf(
                RuleName("PreferImportsOverFqn"),
                RuleName("PublicDataClass"),
            ),
            ruleSet.rules.keys,
        )
        ruleSet.rules.forEach { (name, createRule) -> assertEquals(name, createRule(Config.empty).ruleName) }
    }

    @Test
    fun `every provider registers exactly the rules config yml declares for its rule set`() {
        val config = assertNotNull(javaClass.getResource("/config/config.yml"), "config.yml").readText().lines()
        listOf(
            ComposeRuntimeRuleSetProvider(),
            ComposeSwingInternalRuleSetProvider(),
            ComposeSwingRuleSetProvider(),
        ).forEach { provider ->
            val setId = provider.ruleSetId.value
            val setStart = config.indexOfFirst { it == "$setId:" }
            assertTrue(setStart >= 0, "config.yml has no rule set $setId")
            val declared =
                config
                    .drop(setStart + 1)
                    .takeWhile { it.isBlank() || it.startsWith(" ") || it.startsWith("#") }
                    .filter { it.startsWith("  ") && !it.startsWith("   ") && it.endsWith(":") }
                    .map { RuleName(it.trim().removeSuffix(":")) }
                    .toSet()

            assertEquals(declared, provider.instance().rules.keys, "rules of $setId against config.yml")
        }
    }
}
