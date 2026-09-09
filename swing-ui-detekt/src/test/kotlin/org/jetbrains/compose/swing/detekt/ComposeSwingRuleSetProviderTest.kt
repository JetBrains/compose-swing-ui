package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.RuleName
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeSwingRuleSetProviderTest {
    @Test
    fun `service loader discovers both providers`() {
        val expected =
            setOf(
                ComposeSwingRuleSetProvider::class.java,
                ComposeSwingInternalRuleSetProvider::class.java,
            )
        val discovered =
            ServiceLoader
                .load(RuleSetProvider::class.java)
                .map { it.javaClass }
                .filter { it in expected }
                .toSet()

        assertEquals(expected, discovered)
    }

    @Test
    fun `public provider registers every published rule`() {
        val ruleSet = ComposeSwingRuleSetProvider().instance()

        assertEquals(RuleSetId("compose-swing"), ruleSet.id)
        assertEquals(
            setOf(
                RuleName("ComposableSwingModifierFactory"),
                RuleName("ModifierNodeInspectableProperties"),
                RuleName("SwingModifierFactoryExtensionFunction"),
                RuleName("SwingModifierFactoryReturnType"),
                RuleName("SwingModifierThen"),
                RuleName("SwingModifierUnreferencedReceiver"),
                RuleName("SwingModifierWithoutDefault"),
                RuleName("UnheldColumnComparator"),
                RuleName("UnusedAnimatedContentTargetState"),
                RuleName("UnusedTransitionTargetState"),
            ),
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
}
