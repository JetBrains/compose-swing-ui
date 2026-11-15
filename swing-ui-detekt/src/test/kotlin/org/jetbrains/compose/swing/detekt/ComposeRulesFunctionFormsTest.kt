// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import org.jetbrains.compose.swing.rules.ComposeRulesRule
import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleSets
import org.jetbrains.compose.swing.rules.content.ContentEmitterReturningValues
import org.jetbrains.compose.swing.rules.content.MultipleContentEmitters
import org.jetbrains.compose.swing.rules.content.ParameterOrder
import org.jetbrains.compose.swing.rules.core.modifier.ModifierMissing
import org.jetbrains.compose.swing.rules.runtime.CompositionLocalAllowlist
import org.jetbrains.compose.swing.rules.runtime.LambdaParameterInRestartableEffect
import org.jetbrains.compose.swing.rules.runtime.RememberMissing
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals

class ComposeRulesFunctionFormsTest {
    @ParameterizedTest
    @MethodSource("composeRulesRuleIds")
    fun `function literals, constructors and accessors are linted without a report or a failure`(ruleId: String) {
        val rule = composeRules.single { it.id == ruleId }
        val detektRule = rule.toDetektRule(TestConfig("active" to true))
        val findings =
            if (detektRule is RequiresAnalysisApi) {
                detektRule.lintWithContext(environment, source)
            } else {
                detektRule.lint(source)
            }

        assertEquals(emptyList(), findings.map { it.message }, rule.id)
    }

    companion object {
        private val environment = createEnvironment()

        private val source =
            """
            class Holder(val value: Int) {
                constructor(text: String) : this(text.length)

                val doubled: Int
                    get() = value * 2

                var stored: Int = 0
                    set(newValue) {
                        field = newValue
                    }
            }

            val lambda = { value: Int -> value }
            val anonymous = fun(value: Int): Int = value
            val composableLambda: @Composable () -> Unit = @Composable { }
            val composableAnonymous: @Composable () -> Unit = @Composable fun() {}
            val composableWithParameter: @Composable (Int) -> Unit = @Composable { count -> Child(count) }
            val composableAnonymousWithParameter: @Composable (Int) -> Unit = @Composable fun(count: Int) { Child(count) }
            """.trimIndent()

        private val composeRules: List<ComposeSwingRule> =
            ComposeSwingRuleSets.ruleSets.values
                .flatten()
                .filterIsInstance<ComposeRulesRule>() +
                listOf(
                    CompositionLocalAllowlist,
                    ContentEmitterReturningValues,
                    LambdaParameterInRestartableEffect,
                    ModifierMissing,
                    MultipleContentEmitters,
                    ParameterOrder,
                    RememberMissing,
                )

        @JvmStatic
        fun composeRulesRuleIds(): List<String> = composeRules.map { it.id }
    }
}
