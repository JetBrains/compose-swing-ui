// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetProvider
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal val composeDetektRuleSets: List<RuleSet> by lazy {
    val providerPackage = ComposeSwingRuleSetProvider::class.java.packageName
    ServiceLoader
        .load(RuleSetProvider::class.java)
        .filter { it.javaClass.packageName == providerPackage }
        .map { provider ->
            provider.instance().also { assertEquals(provider.ruleSetId, it.id) }
        }
}

internal fun detektRuleFactory(ruleId: String): (Config) -> Rule {
    val name = RuleName(ruleId)
    return composeDetektRuleSets.mapNotNull { it.rules[name] }.single()
}

class ComposeSwingRuleSetProviderTest {
    @Test
    fun `service loader discovers one provider for each rule set`() {
        val expectedIds = expectedRules.keys + "compose-runtime"
        val discoveredIds = composeDetektRuleSets.map { it.id.value }

        assertEquals(expectedIds.sorted(), discoveredIds.sorted())
    }

    @Test
    fun `providers register only the rules belonging to their rule sets`() {
        composeDetektRuleSets.filter { it.id.value != "compose-runtime" }.forEach { ruleSet ->
            assertEquals(
                expectedRules.getValue(ruleSet.id.value),
                ruleSet.rules.keys
                    .map { it.value }
                    .toSet(),
            )
            ruleSet.rules.forEach { (name, createRule) -> assertEquals(name, createRule(Config.empty).ruleName) }
        }
    }

    @Test
    fun `every provider registers exactly the rules config yml declares for its rule set`() {
        val config = assertNotNull(javaClass.getResource("/config/config.yml"), "config.yml").readText().lines()
        composeDetektRuleSets.forEach { ruleSet ->
            val setId = ruleSet.id.value
            val setStart = config.indexOfFirst { it == "$setId:" }
            assertTrue(setStart >= 0, "config.yml has no rule set $setId")
            val declared =
                config
                    .drop(setStart + 1)
                    .takeWhile { it.isBlank() || it.startsWith(" ") || it.startsWith("#") }
                    .filter { it.startsWith("  ") && !it.startsWith("   ") && it.endsWith(":") }
                    .map { RuleName(it.trim().removeSuffix(":")) }
                    .toSet()

            assertEquals(declared, ruleSet.rules.keys, "rules of $setId against config.yml")
        }
    }

    private val expectedRules =
        mapOf(
            "compose-swing-core" to
                setOf(
                    "ComposableSwingModifierFactory",
                    "ModifierComposed",
                    "ModifierMissing",
                    "ModifierNaming",
                    "ModifierNodeInspectableProperties",
                    "ModifierNotUsedAtRoot",
                    "ModifierReused",
                    "ModifierShouldBeForwardedToEveryBranch",
                    "MultipleSwingModifierParameters",
                    "MultipleSwingNodeCalls",
                    "PreviewAnnotationNaming",
                    "PreviewNaming",
                    "PreviewNeedsComposableAnnotation",
                    "PreviewPublic",
                    "PreviewSignature",
                    "SwingComponentInComposableSignature",
                    "SwingModifierFactoryExtensionFunction",
                    "SwingModifierFactoryReturnType",
                    "SwingModifierThen",
                    "SwingModifierUnreferencedReceiver",
                    "SwingModifierWithoutDefault",
                    "SwingNodeFactoryReadsDeclaredValue",
                    "SwingNodeFactoryReusesComponent",
                    "SwingNodeUpdateConditionalCall",
                    "SwingNodeUpdateRepeatsInvalidation",
                    "SwingNodeUpdateStoresCallback",
                    "SwingNodeUpdateWritesComponentPart",
                    "SwingStyleWithoutDefault",
                ),
            "compose-swing-content" to
                setOf(
                    "ComposableAnnotationNaming",
                    "ComposableNestingDepth",
                    "ComposableParamOrder",
                    "ConditionHoist",
                    "ContentEmitterReturningValues",
                    "ContentSlotReused",
                    "ContentTrailingLambda",
                    "DefaultsVisibility",
                    "LambdaParameterEventTrailing",
                    "MultipleEmitters",
                    "MutableParams",
                    "ParameterNaming",
                    "UnstableCollections",
                ),
            "compose-swing-components" to
                setOf(
                    "UnheldColumnComparator",
                ),
            "compose-swing-animation" to
                setOf(
                    "UnusedAnimatedContentTargetState",
                    "UnusedTransitionTargetState",
                ),
            "androidx-annotations" to
                setOf(
                    "ArgumentOutsideDeclaredRange",
                    "RestrictedApi",
                    "VisibleForTests",
                ),
            "compose-swing-internal" to
                setOf(
                    "PreferImportsOverFqn",
                    "PublicDataClass",
                ),
        )
}
