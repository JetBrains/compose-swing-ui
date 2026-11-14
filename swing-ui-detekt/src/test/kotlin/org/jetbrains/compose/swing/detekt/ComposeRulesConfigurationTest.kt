// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.RuleName
import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ComposeRulesConfigurationTest {
    @Test
    fun `provider-free validation configuration covers every library rule set`() {
        val exclusions =
            File("../config/detekt/rule-provider-overrides.yml")
                .readLines()
                .single { it.trimStart().startsWith("excludes:") }
        val providers =
            listOf(
                ComposeSwingInternalRuleSetProvider(),
                ComposeSwingRuleSetProvider(),
            )

        providers.forEach { provider ->
            val id = provider.instance().id.value
            assertTrue("\"$id\"" in exclusions, "No provider-free validation configuration for $id")
        }
    }

    @Test
    fun `custom modifiers add to library modifier types`() {
        val findings =
            ComposeSwingRuleSetProvider()
                .instance()
                .rules
                .getValue(RuleName("ModifierNaming"))(TestConfig("customModifiers" to listOf("CustomModifier")))
                .lint(
                    """
                    import org.jetbrains.compose.swing.modifier.SwingModifier

                    @Composable fun Custom(wrong: CustomModifier) {}
                    @Composable fun BuiltIn(wrong: SwingModifier) {}
                    """.trimIndent(),
                )

        assertEquals(listOf(3, 4), findings.map { it.entity.location.source.line })
    }

    @Test
    fun `content emitters add to library emitters`() {
        val findings =
            ComposeSwingRuleSetProvider()
                .instance()
                .rules
                .getValue(RuleName("ModifierMissing"))(TestConfig("contentEmitters" to listOf("CustomNode")))
                .lint(
                    """
                    import org.jetbrains.compose.swing.node.MenuNode
                    import org.jetbrains.compose.swing.node.SwingNode

                    @Composable fun Custom() { CustomNode() }
                    @Composable fun BuiltIn() { SwingNode() }
                    @Composable fun BuiltInMenu() { MenuNode() }
                    """.trimIndent(),
                )

        assertEquals(
            listOf("Custom", "BuiltIn", "BuiltInMenu"),
            findings.map { (it.entity.ktElement as? KtNamedFunction)?.name },
        )
    }

    @ParameterizedTest
    @CsvSource(
        "CustomNode;OtherNode, CustomNode",
        "CustomNode;OtherNode, OtherNode",
        "CustomNode;OtherNode;, OtherNode",
        "'CustomNode,OtherNode', CustomNode",
        "'CustomNode,OtherNode', OtherNode",
        "'CustomNode, OtherNode', OtherNode",
        "CustomNode, CustomNode",
    )
    fun `content emitters accept a scalar separated by semicolons or commas`(
        configured: String,
        emitter: String,
    ) {
        val findings =
            ComposeSwingRuleSetProvider()
                .instance()
                .rules
                .getValue(RuleName("ModifierMissing"))(TestConfig("contentEmitters" to configured))
                .lint(
                    """
                    @Composable fun Example() { $emitter() }
                    """.trimIndent(),
                )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports composition locals missing from the configured allowlist`() {
        val findings =
            ComposeSwingRuleSetProvider()
                .instance()
                .rules
                .getValue(RuleName("CompositionLocalAllowlist"))(
                TestConfig("allowedCompositionLocals" to listOf("LocalAllowed")),
            ).lint(
                """
                val LocalAllowed = compositionLocalOf { 0 }
                val LocalForbidden = compositionLocalOf { 0 }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertEquals("LocalForbidden", assertIs<KtProperty>(findings.single().entity.ktElement).name)
    }

    @Test
    fun `reports a private composable only when modifier checks cover all visibilities`() {
        val source =
            """
            import org.jetbrains.compose.swing.components.Label

            @Composable private fun Example() { Label("text") }
            """.trimIndent()
        val createRule = ComposeSwingRuleSetProvider().instance().rules.getValue(RuleName("ModifierMissing"))

        assertEquals(0, createRule(TestConfig()).lint(source).size)
        assertEquals(1, createRule(TestConfig("checkModifiersForVisibility" to "all")).lint(source).size)
    }

    @Test
    fun `reports nesting beyond the configured depth threshold`() {
        val source =
            """
            import org.jetbrains.compose.swing.components.Label
            import org.jetbrains.compose.swing.components.layout.Panel

            @Composable fun Example() { Panel { Label("text") } }
            """.trimIndent()
        val createRule = ComposeSwingRuleSetProvider().instance().rules.getValue(RuleName("ComposableNestingDepth"))

        assertEquals(0, createRule(TestConfig()).lint(source).size)
        assertEquals(1, createRule(TestConfig("composableNestingDepthThreshold" to 0)).lint(source).size)
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["ComposableNestingDepth", "ModifierNotUsedAtRoot", "ModifierReused", "LambdaParameterEventTrailing"],
    )
    fun `shares aliased emitter recognition across rules`(ruleId: String) {
        val source =
            """
            import org.jetbrains.compose.swing.components.layout.Panel as panel
            import org.jetbrains.compose.swing.components.Label as label
            import org.jetbrains.compose.swing.modifier.SwingModifier

            @Composable
            fun Example(modifier: SwingModifier, onClick: () -> Unit) {
                panel(modifier = modifier) { label(text = "child", modifier = modifier) }
            }
            """.trimIndent()

        assertEquals(if (ruleId == "ModifierReused") 2 else 1, findings(ruleId, source), ruleId)
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["ComposableNestingDepth", "ModifierNotUsedAtRoot", "ModifierReused", "LambdaParameterEventTrailing"],
    )
    fun `ignores emission across an aliased window boundary`(ruleId: String) {
        val source =
            """
            import org.jetbrains.compose.swing.components.layout.Panel as panel
            import org.jetbrains.compose.swing.components.Label as label
            import org.jetbrains.compose.swing.modifier.SwingModifier
            import org.jetbrains.compose.swing.window.Window as window

            @Composable
            fun Example(modifier: SwingModifier, onClick: () -> Unit) {
                window(onCloseRequest = onClick) {
                    panel(modifier = modifier) { label(text = "child", modifier = modifier) }
                }
            }
            """.trimIndent()

        assertEquals(0, findings(ruleId, source), ruleId)
    }

    @ParameterizedTest
    @ValueSource(strings = ["ComposableNestingDepth", "ModifierNotUsedAtRoot", "ModifierReused"])
    fun `does not treat a Swing node factory as child content`(ruleId: String) {
        val source =
            """
            import org.jetbrains.compose.swing.components.Label
            import org.jetbrains.compose.swing.modifier.SwingModifier
            import org.jetbrains.compose.swing.node.SwingNode

            @Composable
            fun Example(modifier: SwingModifier) {
                SwingNode(factory = {
                    Label(text = "child", modifier = modifier)
                    JPanel()
                }, modifier = modifier)
            }
            """.trimIndent()

        assertEquals(0, findings(ruleId, source), ruleId)
    }

    @Test
    fun `does not use a same-named local declaration as a Swing emitter`() {
        val source =
            """
            import org.jetbrains.compose.swing.modifier.SwingModifier

            fun Panel(modifier: SwingModifier, callback: () -> Unit) = callback()

            @Composable
            fun Example(modifier: SwingModifier, onClick: () -> Unit) {
                Panel(modifier) { onClick() }
            }
            """.trimIndent()

        assertEquals(0, findings("LambdaParameterEventTrailing", source))
    }

    @Test
    fun `does not treat a same-named noncomposable call as applying a modifier`() {
        val source =
            """
            import org.jetbrains.compose.swing.components.Label
            import org.jetbrains.compose.swing.components.layout.Panel as ActualPanel
            import org.jetbrains.compose.swing.modifier.SwingModifier

            fun Panel(modifier: SwingModifier) {}

            @Composable
            fun Example(modifier: SwingModifier) {
                ActualPanel {
                    Panel(modifier)
                    Label(text = "child", modifier = modifier)
                }
            }
            """.trimIndent()

        assertEquals(1, findings("ModifierNotUsedAtRoot", source))
        assertEquals(0, findings("ModifierReused", source))
    }

    @Test
    fun `treats the configured denylist as an emission boundary`() {
        val source =
            """
            import org.jetbrains.compose.swing.components.layout.Panel
            import org.jetbrains.compose.swing.components.Label
            import org.jetbrains.compose.swing.modifier.SwingModifier

            @Composable
            fun Example(modifier: SwingModifier) {
                Panel { Label(text = "child", modifier = modifier) }
            }
            """.trimIndent()

        assertEquals(0, findings("ModifierNotUsedAtRoot", source, denied = listOf("Panel")))
    }

    @Test
    fun `reports configured lambda types called in a restartable effect`() {
        val source =
            """
            import androidx.compose.runtime.LaunchedEffect

            @Composable fun Example(onChange: Callback) { LaunchedEffect(Unit) { onChange() } }
            """.trimIndent()
        val createRule =
            ComposeSwingRuleSetProvider().instance().rules.getValue(RuleName("LambdaParameterInRestartableEffect"))

        assertEquals(0, createRule(TestConfig()).lint(source).size)
        assertEquals(1, createRule(TestConfig("treatAsLambda" to listOf("Callback"))).lint(source).size)
    }

    @Test
    fun `reports captures in Compose Runtime and Lifecycle effects`() {
        val source =
            """
            import androidx.compose.runtime.DisposableEffect as Observe
            import androidx.compose.runtime.produceState
            import androidx.lifecycle.compose.LifecycleResumeEffect as WhileResumed
            import androidx.lifecycle.compose.LifecycleStartEffect

            @Composable
            fun Example(dispose: () -> Unit, produce: () -> Unit, stop: () -> Unit, pause: () -> Unit) {
                Observe(Unit) { onDispose { dispose() } }
                produceState(0, Unit) { produce() }
                LifecycleStartEffect(Unit) { onStopOrDispose { stop() } }
                WhileResumed(Unit) { onPauseOrDispose { pause() } }
            }
            """.trimIndent()
        val createRule =
            ComposeSwingRuleSetProvider().instance().rules.getValue(RuleName("LambdaParameterInRestartableEffect"))

        val findings = createRule(TestConfig()).lint(source)
        assertEquals(
            listOf("dispose", "pause", "produce", "stop"),
            findings.map { assertIs<KtParameter>(it.entity.ktElement).name.orEmpty() }.sorted(),
        )
    }

    @Test
    fun `accepts callbacks in lifecycle events and other same-name calls`() {
        val source =
            """
            import androidx.compose.runtime.DisposableEffect
            import androidx.lifecycle.Lifecycle
            import androidx.lifecycle.compose.LifecycleEventEffect
            import com.slack.circuit.retained.produceRetainedState
            import example.effects.LaunchedEffect

            @Composable
            fun Example(action: () -> Unit) {
                fun DisposableEffect(key: Any?, effect: () -> Unit) = effect()
                DisposableEffect(Unit) { action() }
                LifecycleEventEffect(Lifecycle.Event.ON_START) { action() }
                produceRetainedState(Unit) { action() }
                LaunchedEffect(Unit) { action() }
                KeyedEffect(Unit) { action() }
                example.effects.LifecycleStartEffect(Unit) { action() }
            }
            """.trimIndent()
        val createRule =
            ComposeSwingRuleSetProvider().instance().rules.getValue(RuleName("LambdaParameterInRestartableEffect"))

        assertEquals(0, createRule(TestConfig()).lint(source).size)
    }

    @Test
    fun `accepts configured lambda parameter names`() {
        val source = "@Composable fun Example(onValueSettled: () -> Unit) {}"
        val createRule = ComposeSwingRuleSetProvider().instance().rules.getValue(RuleName("ParameterNaming"))

        assertEquals(1, createRule(TestConfig()).lint(source).size)
        assertEquals(
            0,
            createRule(TestConfig("allowedLambdaParameterNames" to listOf("onValueSettled"))).lint(source).size,
        )
    }

    @Test
    fun `accepts previews named by the configured strategy`() {
        val source =
            """
            import org.jetbrains.compose.swing.tooling.Preview

            @Preview @Composable private fun PreviewExample() {}
            """.trimIndent()
        val createRule = ComposeSwingRuleSetProvider().instance().rules.getValue(RuleName("PreviewNaming"))

        assertEquals(1, createRule(TestConfig("active" to true)).lint(source).size)
        assertEquals(0, createRule(TestConfig("previewNamingStrategy" to "prefix")).lint(source).size)
        assertEquals(0, createRule(TestConfig("previewNamingStrategy" to "anywhere")).lint(source).size)
        assertEquals(1, createRule(TestConfig("previewNamingStrategy" to "invalid")).lint(source).size)
    }

    @Test
    fun `detekt annotation exclusions remain available`() {
        val source =
            """
            import org.jetbrains.compose.swing.components.Label

            @Ignored @Composable fun Example() { Label("text") }
            """.trimIndent()
        val createRule = ComposeSwingRuleSetProvider().instance().rules.getValue(RuleName("ModifierMissing"))

        assertEquals(1, createRule(TestConfig()).lint(source).size)
        assertEquals(0, createRule(TestConfig("ignoreAnnotated" to listOf("Ignored"))).lint(source).size)
        assertEquals(0, createRule(TestConfig("ignoreAnnotated" to listOf(" Ignored "))).lint(source).size)
        assertEquals(0, createRule(TestConfig("modifierMissingIgnoreAnnotated" to listOf("Ignored"))).lint(source).size)
    }

    private fun findings(
        ruleId: String,
        source: String,
        denied: List<String> = emptyList(),
    ): Int =
        ComposeSwingRuleSetProvider()
            .instance()
            .rules
            .getValue(RuleName(ruleId))(
            TestConfig(
                "active" to true,
                "composableNestingDepthThreshold" to 0,
                "contentEmittersDenylist" to denied,
            ),
        ).lint(source)
            .size
}
