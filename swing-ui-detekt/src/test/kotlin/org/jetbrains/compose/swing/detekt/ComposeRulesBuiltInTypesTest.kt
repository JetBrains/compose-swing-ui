// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import dev.detekt.test.utils.KotlinAnalysisApiEngine
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeRulesBuiltInTypesTest {
    @ParameterizedTest
    @ValueSource(strings = ["SwingModifier", "org.jetbrains.compose.swing.modifier.SwingModifier"])
    fun `recognizes library modifier types without configuration`(type: String) {
        val findings =
            ComposeSwingRuleSetProvider()
                .instance()
                .rules
                .getValue(RuleName("ModifierNaming"))(Config.empty)
                .lint(
                    """
                    import org.jetbrains.compose.swing.modifier.SwingModifier

                    @Composable fun Example(wrong: $type) {}
                    """.trimIndent(),
                )

        assertEquals(1, findings.size)
        assertEquals(
            "This modifier parameter has an inconsistent name. " +
                "A consistent name helps callers find where to customize the component. " +
                "Name a sole parameter `modifier`. With several modifier parameters, a primary parameter " +
                "may be named `modifier`. Give the others names ending in `Modifier`.",
            findings.single().message,
        )
    }

    @ParameterizedTest
    @CsvSource(
        "SwingModifier, org.jetbrains.compose.swing.modifier.SwingModifier, true",
        "Chain, org.jetbrains.compose.swing.modifier.SwingModifier as Chain, true",
        "org.jetbrains.compose.swing.modifier.SwingModifier, '', true",
        "Modifier, androidx.compose.ui.Modifier, false",
        "GlanceModifier, androidx.glance.GlanceModifier, false",
        "SwingModifier, other.SwingModifier, false",
        "SwingModifier, '', false",
    )
    fun `modifier rules share the Swing default and accept configured modifier types`(
        type: String,
        modifierImport: String,
        recognized: Boolean,
    ) {
        val source =
            """
            import org.jetbrains.compose.swing.components.Label
            import org.jetbrains.compose.swing.components.layout.Panel
            ${modifierImport.takeIf { it.isNotEmpty() }?.let { "import $it" }.orEmpty()}

            @Composable fun Options(label: String = "", chain: $type = TODO()) {}
            @Composable fun $type.Extension() { Label("receiver") }

            @Composable
            fun Example(modifier: $type, onClick: () -> Unit) {
                Panel(modifier = modifier) { Label("child", modifier) }
            }
            """.trimIndent()
        val counts =
            mapOf(
                "ComposableParamOrder" to 1,
                "LambdaParameterEventTrailing" to 1,
                "ModifierMissing" to 0,
                "ModifierNaming" to 1,
                "ModifierNotUsedAtRoot" to 1,
                "ModifierReused" to 2,
            )

        for ((ruleId, count) in counts) {
            val createRule = ComposeSwingRuleSetProvider().instance().rules.getValue(RuleName(ruleId))
            val defaultCount =
                when {
                    recognized -> count
                    ruleId == "ModifierMissing" -> 2
                    else -> 0
                }
            assertEquals(defaultCount, createRule(Config.empty).lint(source).size, ruleId)
            if (!recognized) {
                val configured = modifierImport.ifEmpty { type }
                val config = TestConfig("customModifiers" to listOf(configured))
                assertEquals(count, createRule(config).lint(source).size, "$ruleId with $configured")
            }
        }
    }

    @ParameterizedTest
    @CsvSource(
        "SwingModifier, org.jetbrains.compose.swing.modifier.SwingModifier, true",
        "Chain, org.jetbrains.compose.swing.modifier.SwingModifier as Chain, true",
        "org.jetbrains.compose.swing.modifier.SwingModifier, '', true",
        "Modifier, androidx.compose.ui.Modifier, false",
        "SwingModifier, other.SwingModifier, false",
    )
    fun `tracks a parameter alias through a Swing modifier chain`(
        companion: String,
        modifierImport: String,
        recognized: Boolean,
    ) {
        val source =
            """
            import org.jetbrains.compose.swing.components.Label
            import org.jetbrains.compose.swing.components.layout.Panel
            import org.jetbrains.compose.swing.modifier.SwingModifier as Swing
            ${modifierImport.takeIf { it.isNotEmpty() }?.let { "import $it" }.orEmpty()}

            @Composable
            fun Example(modifier: Swing) {
                Panel(modifier = modifier) {
                    val alias = modifier
                    Label("child", $companion.then(alias).then(Swing))
                }
            }
            """.trimIndent()

        assertEquals(if (recognized) 1 else 0, rule("ModifierNotUsedAtRoot").lint(source).size)
        assertEquals(if (recognized) 2 else 0, rule("ModifierReused").lint(source).size)
    }

    @Test
    fun `recognizes a Swing modifier alongside other wildcard imports`() {
        val source =
            """
            import androidx.compose.runtime.*
            import org.jetbrains.compose.swing.components.Label
            import org.jetbrains.compose.swing.modifier.*

            @Composable
            fun Example(modifier: SwingModifier = SwingModifier) {
                Label("child", modifier)
            }
            """.trimIndent()

        assertTrue(rule("ModifierMissing").lint(source).isEmpty())
    }

    @Test
    fun `tracks an outer modifier alias through a shadowing modifier parameter`() {
        val source =
            """
            import org.jetbrains.compose.swing.components.Label
            import org.jetbrains.compose.swing.components.layout.Panel
            import org.jetbrains.compose.swing.modifier.SwingModifier

            @Composable fun Slot(content: @Composable (SwingModifier) -> Unit) {}

            @Composable
            fun Example(modifier: SwingModifier) {
                val saved = modifier
                Panel(modifier = modifier) {
                    Slot(content = { modifier -> Label("child", modifier.then(saved)) })
                }
            }
            """.trimIndent()

        val config = TestConfig("contentEmitters" to listOf("Slot"))
        assertEquals(1, rule("ModifierNotUsedAtRoot", config).lint(source).size)
        assertEquals(2, rule("ModifierReused", config).lint(source).size)
    }

    @Test
    fun `ignores an imported companion name shadowed by a lambda parameter`() {
        val source =
            """
            import org.jetbrains.compose.swing.components.Label
            import org.jetbrains.compose.swing.components.layout.Panel
            import org.jetbrains.compose.swing.modifier.SwingModifier as Chain

            class Pipeline { fun then(value: Chain): Chain = TODO() }

            @Composable fun Slot(content: @Composable (Pipeline) -> Unit) {}

            @Composable
            fun Example(modifier: Chain) {
                Panel(modifier = modifier) {
                    Slot(content = { Chain -> Label("child", Chain.then(modifier)) })
                }
            }
            """.trimIndent()

        val config = TestConfig("contentEmitters" to listOf("Slot"))
        assertEquals(0, rule("ModifierNotUsedAtRoot", config).lint(source).size)
        assertEquals(0, rule("ModifierReused", config).lint(source).size)
    }

    @ParameterizedTest
    @ValueSource(strings = ["SwingNode", "MenuNode"])
    fun `recognizes library content emitters without configuration`(emitter: String) {
        val findings =
            ComposeSwingRuleSetProvider()
                .instance()
                .rules
                .getValue(RuleName("ModifierMissing"))(Config.empty)
                .lint(
                    """
                    import org.jetbrains.compose.swing.node.$emitter
                    import javax.swing.JPanel

                    @Composable fun Example() { $emitter(factory = { JPanel() }) }
                    """.trimIndent(),
                )

        assertEquals(1, findings.size)
        assertEquals(
            "This composable emits content without accepting a modifier parameter. " +
                "Callers cannot customize its root component. " +
                "Add a `SwingModifier` parameter with the empty modifier as its default and " +
                "pass it to the root component.",
            findings.single().message,
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["PreviewNaming", "PreviewPublic"])
    fun `recognizes real previews in plain Detekt`(id: String) {
        val source =
            """
            import other.Preview
            import org.jetbrains.compose.swing.tooling.Preview as SwingExample

            @SwingExample
            annotation class Variants

            @Variants
            annotation class AllVariants

            annotation class PreviewWrapper

            @Preview @Composable fun Unrelated() {}
            @PreviewWrapper @Composable fun Wrapper() {}
            @SwingExample @Composable fun Aliased() {}
            @org.jetbrains.compose.swing.tooling.Preview @Composable fun Qualified() {}
            @AllVariants @Composable fun ViaMetaAnnotation() {}
            """.trimIndent()

        assertEquals(
            listOf("Aliased", "Qualified", "ViaMetaAnnotation"),
            rule(id).lint(source).textsIn(source),
        )
    }

    @Test
    fun `requires the runtime composable annotation on Swing previews`() {
        val source =
            """
            import androidx.compose.runtime.Composable as InComposition
            import org.jetbrains.compose.swing.tooling.Preview as SwingPreview

            annotation class Composable
            annotation class Preview
            @SwingPreview annotation class PreviewVariants

            @SwingPreview private fun Missing() {}
            @SwingPreview @Composable private fun ForeignComposable() {}
            @PreviewVariants private fun ThroughMetaAnnotation() {}
            @SwingPreview @InComposition private fun Valid() {}
            @SwingPreview @androidx.compose.runtime.Composable private fun Qualified() {}
            @Preview private fun ForeignPreview() {}
            private fun OrdinaryFunction() {}
            """.trimIndent()

        val swingPreview = "package org.jetbrains.compose.swing.tooling\nannotation class Preview"
        val runtime = "package androidx.compose.runtime\nannotation class Composable"
        val findings =
            ComposeSwingRuleLint.lint(
                "PreviewNeedsComposableAnnotation",
                source,
                dependencyContents = arrayOf(swingPreview, runtime),
            )
        assertEquals(listOf("Missing", "ForeignComposable", "ThroughMetaAnnotation"), findings.textsIn(source))
    }

    @Test
    fun `requires a top-level Swing preview without arguments`() {
        val source =
            """
            import androidx.compose.runtime.Composable
            import other.Preview
            import org.jetbrains.compose.swing.tooling.Preview as SwingPreview

            @SwingPreview annotation class PreviewVariants

            @SwingPreview @Composable private fun WithArgument(label: String) {}
            @PreviewVariants @Composable private fun WithDefault(label: String = "") {}
            @SwingPreview @Composable private fun String.WithReceiver() {}
            context(label: String)
            @SwingPreview @Composable private fun WithContext() {}
            class Owner {
                @SwingPreview @Composable private fun Member() {}
            }
            @SwingPreview @Composable private fun Valid() {}
            @Preview @Composable private fun ForeignPreview(label: String) {}
            """.trimIndent()

        assertEquals(
            listOf("WithArgument", "WithDefault", "WithReceiver", "WithContext", "Member"),
            rule("PreviewSignature").lint(source).textsIn(source),
        )
    }

    @Test
    fun `requires the preview prefix only on real meta-annotations in plain Detekt`() {
        val source =
            """
            import org.jetbrains.compose.swing.tooling.Preview as SwingExample

            annotation class Preview
            @Preview annotation class Unrelated
            @SwingExample annotation class Variants
            @Variants annotation class Themed
            @Variants annotation class PreviewVariants

            @First annotation class Second
            @Second annotation class First
            """.trimIndent()

        assertEquals(listOf("Variants", "Themed"), rule("PreviewAnnotationNaming").lint(source).textsIn(source))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "PreviewAnnotationNaming",
            "PreviewNaming",
            "PreviewNeedsComposableAnnotation",
            "PreviewPublic",
            "PreviewSignature",
        ],
    )
    fun `resolves aliased meta-annotations across files`(id: String) {
        val source =
            """
            import androidx.compose.runtime.Composable as RuntimeComposable
            import example.previews.Themes as Variants
            import example.previews.Preview

            annotation class Composable
            typealias ComposableAlias = RuntimeComposable

            @Variants annotation class Meta
            @Preview annotation class Unrelated
            @Meta @Composable fun Themed(label: String = "") {}
            @Meta @ComposableAlias private fun ValidPreview() {}
            @Preview @Composable fun Other() {}
            """.trimIndent()
        val swingPreview =
            """
            package org.jetbrains.compose.swing.tooling

            annotation class Preview
            """.trimIndent()
        val previewVariants =
            """
            package example.previews

            import org.jetbrains.compose.swing.tooling.Preview as SwingPreview

            annotation class Preview
            @SwingPreview annotation class Themes
            """.trimIndent()
        val runtime = "package androidx.compose.runtime\nannotation class Composable"

        val dependencies = listOf(swingPreview, previewVariants, runtime)
        val findings =
            if (id == "PreviewNeedsComposableAnnotation") {
                ComposeSwingRuleLint.lint(id, source, dependencyContents = dependencies.toTypedArray())
            } else {
                KotlinAnalysisApiEngine().use { engine ->
                    rule(id).lint(engine.compile(source, dependencyCodes = dependencies))
                }
            }
        val expected = if (id == "PreviewAnnotationNaming") "Meta" else "Themed"
        assertEquals(listOf(expected), findings.textsIn(source))
    }

    @Test
    fun `reports a non-composable factory whose body is composed`() {
        val source =
            """
            package sample

            import org.jetbrains.compose.swing.modifier.SwingModifier
            import org.jetbrains.compose.swing.modifier.composed

            fun SwingModifier.glow(): SwingModifier = composed { this }
            """.trimIndent()
        val findings = lintModifierComposed(source)

        assertEquals(1, findings.size)
        val message = findings.single().message
        assertTrue("Consider a `SwingModifier.ComponentNode`" in message)
        assertTrue("Keep `composed` when the modifier must call composables." in message)
        assertTrue("Modifier.Node" !in message && "https://" !in message)
        val location =
            findings
                .single()
                .entity
                .location
                .text
        assertEquals("glow", source.substring(location.start, location.end))
    }

    @Test
    fun `reports a qualified composed call returned from a block body`() {
        val findings =
            lintModifierComposed(
                """
                package sample

                import org.jetbrains.compose.swing.modifier.SwingModifier
                import org.jetbrains.compose.swing.modifier.composed

                fun SwingModifier.glow(): SwingModifier {
                    return this.composed { this }
                }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports composed imported under an alias on an explicitly qualified receiver`() {
        val findings =
            lintModifierComposed(
                """
                package sample

                import org.jetbrains.compose.swing.modifier.composed as buildInComposition

                fun org.jetbrains.compose.swing.modifier.SwingModifier.glow() =
                    this@glow.buildInComposition { this }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports composed called on the SwingModifier companion inside a factory`() {
        val findings =
            lintModifierComposed(
                """
                package sample

                import org.jetbrains.compose.swing.modifier.SwingModifier
                import org.jetbrains.compose.swing.modifier.composed

                fun SwingModifier.glow(): SwingModifier = SwingModifier.composed { this }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
    }

    @Test
    fun `reports the library extension in its own package or from its wildcard import`() {
        val sources =
            listOf(
                "package org.jetbrains.compose.swing.modifier",
                "package sample\n\nimport org.jetbrains.compose.swing.modifier.*",
            )
        sources.forEach { preamble ->
            val source = "$preamble\n\nfun SwingModifier.glow(): SwingModifier = composed { this }"
            assertEquals(1, lintModifierComposed(source).size, preamble)
        }
    }

    @Test
    fun `reports a configured receiver type alias using the library extension`() {
        val source =
            """
            package sample

            import org.jetbrains.compose.swing.modifier.SwingModifier
            import org.jetbrains.compose.swing.modifier.composed

            typealias LocalModifier = SwingModifier

            fun LocalModifier.glow(): LocalModifier = composed { this }
            """.trimIndent()
        val rule =
            ComposeSwingRuleSetProvider()
                .instance()
                .rules
                .getValue(RuleName("ModifierComposed"))(TestConfig("customModifiers" to listOf("LocalModifier")))

        assertEquals(0, lintModifierComposed(source).size)
        assertEquals(1, rule.lint(source).size)
    }

    @Test
    fun `accepts composed inside a factory annotated with Composable`() {
        val findings =
            lintModifierComposed(
                """
                package sample

                import androidx.compose.runtime.Composable
                import org.jetbrains.compose.swing.modifier.SwingModifier
                import org.jetbrains.compose.swing.modifier.composed

                @Composable
                fun SwingModifier.glow(): SwingModifier = composed { this }
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not report composed on a receiver that is not a SwingModifier`() {
        val findings =
            lintModifierComposed(
                """
                package sample

                fun String.composed(factory: () -> Int): Int = factory()

                fun String.glow(): Int = composed { 1 }
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not report a different imported composed or a local overload`() {
        val sources =
            listOf(
                """
                import sample.helpers.composed

                fun SwingModifier.glow(): SwingModifier = composed { this }
                """.trimIndent(),
                """
                import org.jetbrains.compose.swing.modifier.composed

                fun SwingModifier.glow(): SwingModifier {
                    fun SwingModifier.composed(factory: () -> SwingModifier) = factory()
                    return this.composed { this }
                }
                """.trimIndent(),
            )
        sources.forEach { body ->
            val source = "package sample\n\nimport org.jetbrains.compose.swing.modifier.SwingModifier\n$body"
            assertEquals(0, lintModifierComposed(source).size, body)
        }
    }

    @Test
    fun `does not report an unrelated qualified member or a non-factory use`() {
        val findings =
            lintModifierComposed(
                """
                package sample

                import org.jetbrains.compose.swing.modifier.SwingModifier
                import org.jetbrains.compose.swing.modifier.composed

                class Builder {
                    fun composed(factory: () -> SwingModifier): SwingModifier = factory()
                }

                fun SwingModifier.glow(builder: Builder): SwingModifier = builder.composed { this }

                fun glow(modifier: SwingModifier): SwingModifier = modifier.composed { this }
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    @Test
    fun `does not report a factory built with then`() {
        val findings =
            lintModifierComposed(
                """
                package sample

                import org.jetbrains.compose.swing.modifier.SwingModifier

                private object Glow : SwingModifier.Element

                fun SwingModifier.glow(): SwingModifier = then(Glow)
                """.trimIndent(),
            )

        assertEquals(0, findings.size)
    }

    private fun rule(
        id: String,
        config: Config = Config.empty,
    ): Rule = ComposeSwingRuleSetProvider().instance().rules.getValue(RuleName(id))(config)

    private fun lintModifierComposed(source: String) = rule("ModifierComposed").lint(source)
}
