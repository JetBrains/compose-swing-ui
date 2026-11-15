// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import org.jetbrains.compose.swing.rules.ComposeSwingRuleSets
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposeRulesCatalogTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "ComposableAnnotationNaming",
            "ComposableNestingDepth",
            "ComposableParamOrder",
            "CompositionLocalAllowlist",
            "ContentEmitterReturningValues",
            "ContentSlotReused",
            "ContentTrailingLambda",
            "DefaultsVisibility",
            "LambdaParameterEventTrailing",
            "LambdaParameterInRestartableEffect",
            "ModifierNotUsedAtRoot",
            "ModifierReused",
            "MultipleEmitters",
            "MutableParams",
            "MutableStateParam",
            "ParameterNaming",
            "PreviewAnnotationNaming",
            "PreviewNaming",
            "PreviewPublic",
            "RememberContentMissing",
            "StateParam",
            "UnstableCollections",
        ],
    )
    fun `every adapted rule reports through its provider`(ruleId: String) {
        val rule = detektRuleFactory(ruleId)(TestConfig("active" to true))
        val findings = rule.lint(sources.getValue(ruleId))

        assertEquals(if (ruleId == "ModifierReused") 2 else 1, findings.size, ruleId)
        findings.forEach { finding ->
            assertTrue(finding.message.isNotBlank(), ruleId)
            assertFalse("http://" in finding.message || "https://" in finding.message, ruleId)
            expectedMessages[ruleId]?.let { assertEquals(it, finding.message, ruleId) }
        }
        val catalogRule =
            ComposeSwingRuleSets.ruleSets.values
                .flatten()
                .single { it.id == ruleId }
        assertFalse(catalogRule.requiresAnalysisApi, ruleId)
        assertEquals(
            ruleId !in setOf("ComposableNestingDepth", "PreviewNaming", "UnstableCollections"),
            catalogRule.activeByDefault,
            ruleId,
        )
    }

    @Test
    fun `adapted rules accept compliant code through their provider`() {
        compliantSources.forEach { (ruleId, source) ->
            assertEquals(0, detektRuleFactory(ruleId)(TestConfig("active" to true)).lint(source).size, ruleId)
        }
    }

    @Test
    fun `counts real Swing emitters and local wrappers at the root`() {
        val source =
            """
            import androidx.compose.runtime.Composable
            import org.jetbrains.compose.swing.components.Label as Caption
            import org.jetbrains.compose.swing.components.layout.*

            @Composable private fun Leaf() = Caption("leaf")
            @Composable private fun Relay() { Leaf() }

            @Composable fun Imported() {
                Panel {}
                Caption("second")
            }

            @Composable fun Qualified() {
                org.jetbrains.compose.swing.components.Label("first")
                Relay()
            }

            @Composable fun NestedContent() {
                Panel {
                    Caption("first")
                    Caption("second")
                }
            }

            @Composable fun Returning(): Int {
                Relay()
                return 1
            }
            """.trimIndent()

        assertEquals(listOf("Imported", "Qualified"), findings("MultipleEmitters", source))
        assertEquals(listOf("Returning"), findings("ContentEmitterReturningValues", source))
        assertEquals(listOf("Imported", "Qualified", "NestedContent"), findings("ModifierMissing", source))
    }

    @Test
    fun `counts a virtual group once and permits menu fragments`() {
        val source =
            """
            import org.jetbrains.compose.swing.annotations.SwingMenuComposable as MenuContent
            import org.jetbrains.compose.swing.components.Label
            import org.jetbrains.compose.swing.components.menu.Menu
            import org.jetbrains.compose.swing.node.VirtualSwingNode

            annotation class SwingMenuComposable

            @Composable fun Group() {
                VirtualSwingNode { Label("first"); Label("second") }
            }

            @Composable fun Sibling() {
                VirtualSwingNode { Label("first"); Label("second") }
                Label("third")
            }

            @MenuContent @Composable fun ActualMenuFragment() { Menu("File"); Menu("Edit") }
            @SwingMenuComposable @Composable fun Namesake() { Menu("File"); Menu("Edit") }
            """.trimIndent()

        assertEquals(listOf("Sibling", "Namesake"), findings("MultipleEmitters", source))
    }

    @Test
    fun `counts emissions on the same path and follows immediate content`() {
        val source =
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.key
            import org.jetbrains.compose.swing.components.Label

            @Composable fun Alternatives(flag: Boolean) {
                if (flag) Label("first") else Label("other")
            }

            @Composable fun EarlyReturn(flag: Boolean) {
                if (flag) {
                    Label("first")
                    return
                }
                Label("other")
            }

            @Composable fun Repeated(items: List<String>) {
                for (item in items) Label(item)
            }

            @Composable fun UnderKey() {
                key(1) { Label("first") }
                Label("second")
            }

            @Composable fun ConditionalWithSibling(flag: Boolean) {
                if (flag) Label("first")
                Label("second")
            }

            @Composable fun ReturningFromBranch(flag: Boolean): Int {
                if (flag) {
                    Label("first")
                    return 1
                }
                return 0
            }
            """.trimIndent()

        assertEquals(listOf("Repeated", "UnderKey", "ConditionalWithSibling"), findings("MultipleEmitters", source))
        assertEquals(listOf("ReturningFromBranch"), findings("ContentEmitterReturningValues", source))
    }

    @Test
    fun `ignores namesakes shadowed references callbacks and separate roots`() {
        val source =
            """
            import androidx.compose.runtime.Composable
            import org.jetbrains.compose.swing.components.Label
            import org.jetbrains.compose.swing.window.Window
            import org.jetbrains.compose.swing.components.menu.PopupMenu
            import elsewhere.WebView

            @Composable private fun EmptyCycle() { OtherCycle() }
            @Composable private fun OtherCycle() { EmptyCycle() }

            @Composable fun Namesakes(): Int {
                fun Label(text: String) {}
                Label("first")
                elsewhere.Panel()
                WebView()
                WebView()
                return 1
            }

            @Composable fun Deferred() {
                remember { { Label("deferred") } }
                enqueue { Label("deferred") }
                @Composable fun Local() { Label("uncalled") }
                EmptyCycle()
            }

            @Composable fun SeparateRoots() {
                Window { Label("separate") }
                PopupMenu { Label("separate") }
            }
            """.trimIndent()

        assertEquals(emptyList(), findings("MultipleEmitters", source))
        assertEquals(emptyList(), findings("ContentEmitterReturningValues", source))
        val allVisibilities = TestConfig("checkModifiersForVisibility" to "all")
        assertEquals(listOf("Local"), findings("ModifierMissing", source, allVisibilities))
    }

    @Test
    fun `revisits a wrapper when the earlier result depended on an active caller`() {
        val source =
            """
            import org.jetbrains.compose.swing.components.Label

            @Composable private fun First(recur: Boolean) {
                if (recur) Second(false) else Label("first")
            }

            @Composable private fun Second(recur: Boolean) {
                if (recur) First(false)
            }

            @Composable fun Example() {
                First(false)
                Second(true)
            }
            """.trimIndent()

        assertEquals(listOf("Example"), findings("MultipleEmitters", source))
    }

    @Test
    fun `configured qualified emitters support aliases and the denylist takes precedence`() {
        val source =
            """
            import org.jetbrains.compose.swing.components.Label as Caption
            import custom.Node as Custom

            @Composable fun Example() {
                Caption("first")
                Custom()
                Custom()
            }
            """.trimIndent()
        val allowed = TestConfig("contentEmitters" to listOf("custom.Node"))
        val denied =
            TestConfig(
                "contentEmitters" to listOf("custom.Node"),
                "contentEmittersDenylist" to listOf("custom.Node"),
            )

        assertEquals(listOf("Example"), findings("MultipleEmitters", source, allowed))
        assertEquals(emptyList(), findings("MultipleEmitters", source, denied))
    }

    private fun findings(
        rule: String,
        source: String,
        config: TestConfig = TestConfig(),
    ): List<String> =
        detektRuleFactory(rule)(config)
            .lint(source)
            .map { (it.entity.ktElement as KtNamedFunction).name.orEmpty() }

    private val expectedMessages =
        mapOf(
            "DefaultsVisibility" to
                (
                    "`ExampleDefaults` has private visibility. The most visible `Example` " +
                        "composable that uses it is public, so some callers cannot access its defaults. " +
                        "Make `ExampleDefaults` public."
                ),
            "ComposableParamOrder" to
                (
                    "The parameters of this composable are out of order. Consistent ordering makes composable " +
                        "calls easier to read. Put required parameters first, followed by defaulted modifiers and " +
                        "other defaulted parameters. Keep any trailing lambda last. " +
                        "Use this order: required, optional."
                ),
            "ModifierReused" to
                (
                    "This modifier parameter is applied to more than one component. " +
                        "Each component receives the caller's customizations " +
                        "even when its chain adds other elements. " +
                        "Apply the parameter once to the root component and start a fresh modifier chain for each child."
                ),
            "StateParam" to
                (
                    "This composable takes a `State` object. That ties the component's inputs to how the caller " +
                        "stores its state. Pass the current value and use callbacks to request changes."
                ),
        )

    private val compliantSources =
        mapOf(
            "ModifierReused" to
                """
                import org.jetbrains.compose.swing.components.Label
                import org.jetbrains.compose.swing.modifier.SwingModifier

                @Composable fun Example(modifier: SwingModifier) { Label("only", modifier) }
                """.trimIndent(),
            "MultipleEmitters" to
                """
                import org.jetbrains.compose.swing.components.Label

                @Composable fun Example() { Label("text") }
                """.trimIndent(),
            "MutableParams" to
                """
                @Composable fun Example(items: List<String>) {}
                """.trimIndent(),
            "MutableStateParam" to
                """
                @Composable fun Example(value: String) {}
                """.trimIndent(),
            "ParameterNaming" to
                """
                @Composable fun Example(onClick: () -> Unit) {}
                """.trimIndent(),
            "PreviewAnnotationNaming" to
                """
                import org.jetbrains.compose.swing.tooling.Preview

                @Preview annotation class PreviewExample
                """.trimIndent(),
            "PreviewNaming" to
                """
                import org.jetbrains.compose.swing.tooling.Preview

                @Preview @Composable private fun ExamplePreview() {}
                """.trimIndent(),
            "PreviewPublic" to
                """
                import org.jetbrains.compose.swing.tooling.Preview

                @Preview @Composable private fun ExamplePreview() {}
                """.trimIndent(),
            "RememberContentMissing" to
                """
                @Composable fun Example() { val content = remember { movableContentOf { SwingNode() } } }
                """.trimIndent(),
            "StateParam" to
                """
                @Composable fun Example(value: String) {}
                """.trimIndent(),
            "UnstableCollections" to
                """
                @Composable fun Example(items: ImmutableList<String>) {}
                """.trimIndent(),
        )

    private val sources =
        mapOf(
            "ComposableAnnotationNaming" to
                """
                @ComposableTargetMarker annotation class Example
                """.trimIndent(),
            "ComposableNestingDepth" to
                """
                import org.jetbrains.compose.swing.node.SwingNode

                @Composable fun Example() {
                    SwingNode(content = {
                        SwingNode(content = {
                            SwingNode(content = { SwingNode(content = { SwingNode() }) })
                        })
                    })
                }
                """.trimIndent(),
            "ComposableParamOrder" to
                """
                @Composable fun Example(optional: Int = 0, required: Int) {}
                """.trimIndent(),
            "CompositionLocalAllowlist" to
                """
                val LocalForbidden = compositionLocalOf { 0 }
                """.trimIndent(),
            "ContentEmitterReturningValues" to
                """
                import org.jetbrains.compose.swing.components.Label

                @Composable fun Example(): Int { Label("text"); return 1 }
                """.trimIndent(),
            "ContentSlotReused" to
                """
                @Composable fun Example(content: @Composable () -> Unit) { content(); content() }
                """.trimIndent(),
            "ContentTrailingLambda" to
                """
                @Composable fun Example(content: @Composable () -> Unit, value: Int) {}
                """.trimIndent(),
            "DefaultsVisibility" to
                """
                private object ExampleDefaults { val value = 1 }
                @Composable internal fun Example(label: String) { Render(label, ExampleDefaults.value) }
                @Composable fun Example(value: Int = ExampleDefaults.value) {}
                """.trimIndent(),
            "LambdaParameterEventTrailing" to
                """
                import org.jetbrains.compose.swing.components.Label
                import org.jetbrains.compose.swing.modifier.SwingModifier

                @Composable fun Example(modifier: SwingModifier = SwingModifier, onClick: () -> Unit) { Label("text") }
                """.trimIndent(),
            "LambdaParameterInRestartableEffect" to
                """
                import androidx.compose.runtime.LaunchedEffect

                @Composable fun Example(onChange: () -> Unit) { LaunchedEffect(Unit) { onChange() } }
                """.trimIndent(),
            "ModifierNotUsedAtRoot" to
                """
                import org.jetbrains.compose.swing.components.Label
                import org.jetbrains.compose.swing.modifier.SwingModifier
                import org.jetbrains.compose.swing.node.SwingNode

                @Composable fun Example(modifier: SwingModifier) {
                    SwingNode(content = { Label("child", modifier) })
                }
                """.trimIndent(),
            "ModifierReused" to
                """
                import org.jetbrains.compose.swing.components.Label
                import org.jetbrains.compose.swing.modifier.SwingModifier

                @Composable fun Example(modifier: SwingModifier) { Label("first", modifier); Label("second", modifier) }
                """.trimIndent(),
            "MultipleEmitters" to
                """
                import org.jetbrains.compose.swing.components.Label

                @Composable fun Example() { Label("first"); Label("second") }
                """.trimIndent(),
            "MutableParams" to
                """
                import androidx.compose.runtime.Composable as Render

                @Render fun Example(items: MutableList<String>) {}
                """.trimIndent(),
            "MutableStateParam" to
                """
                @Composable fun Example(state: MutableState<String>) {}
                """.trimIndent(),
            "ParameterNaming" to
                """
                @Composable fun Example(onClicked: () -> Unit) {}
                """.trimIndent(),
            "PreviewAnnotationNaming" to
                """
                import org.jetbrains.compose.swing.tooling.Preview

                @Preview annotation class Example
                """.trimIndent(),
            "PreviewNaming" to
                """
                import org.jetbrains.compose.swing.tooling.Preview

                @Preview @Composable private fun Example() {}
                """.trimIndent(),
            "PreviewPublic" to
                """
                import org.jetbrains.compose.swing.tooling.Preview

                @Preview @Composable fun ExamplePreview() {}
                """.trimIndent(),
            "RememberContentMissing" to
                """
                @Composable fun Example() { val content = movableContentOf { SwingNode() } }
                """.trimIndent(),
            "StateParam" to
                """
                @Composable fun Example(state: State<String>) {}
                """.trimIndent(),
            "UnstableCollections" to
                """
                @Composable fun Example(items: List<String>) {}
                """.trimIndent(),
        )
}
