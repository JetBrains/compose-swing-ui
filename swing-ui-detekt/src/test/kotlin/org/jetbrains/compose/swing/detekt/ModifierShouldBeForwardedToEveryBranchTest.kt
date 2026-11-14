// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Finding
import dev.detekt.test.TestConfig
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModifierShouldBeForwardedToEveryBranchTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `reports branches by whether each forwards the modifier to a callee that takes one`(case: Case) {
        val example =
            """
            @Composable
            fun Example(flag: Boolean, ${case.parameters}) {
                ${case.branchSyntax.body(case.first, case.second)}
            }
            """.trimIndent()
        val imports = "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.ReadOnlyComposable"
        val declarations =
            """
            interface Modifier {
                infix fun then(other: Modifier): Modifier = other
                companion object : Modifier
            }

            interface SwingModifier {
                companion object : SwingModifier
            }

            fun chooseModifier(candidate: Modifier): Modifier = Modifier

            @Composable fun Widget(modifier: Modifier = Modifier) {}

            @Composable fun Swing(modifier: SwingModifier = SwingModifier) {}

            @ReadOnlyComposable @Composable fun Reading(modifier: Modifier = Modifier) {}

            @Composable fun Plain(text: String = "") {}

            fun recordLayout(modifier: Modifier = Modifier) {}
            """.trimIndent()
        val (source, dependencies) =
            when (case.placement) {
                Placement.SameFile -> {
                    "$imports\n$declarations\n$example" to emptyArray()
                }

                Placement.OtherFile -> {
                    val widgetNames =
                        listOf("Modifier", "SwingModifier", "Widget", "Swing", "Reading", "Plain", "recordLayout") +
                            "chooseModifier"
                    val widgetImports = widgetNames.joinToString("\n") { "import widgets.$it" }
                    "$imports\n$widgetImports\n$example" to arrayOf("package widgets\n$imports\n$declarations")
                }
            }
        val findings =
            ComposeSwingRuleLint.lint(
                "ModifierShouldBeForwardedToEveryBranch",
                source,
                TestConfig("customModifiers" to listOf("Modifier")),
                dependencyContents = dependencies,
            )

        assertEquals(case.reportedOffsets(source), findings.map { it.entity.location.text.start }, case.toString())
        assertTrue(findings.all { it.message == EXPECTED_MESSAGE })
    }

    @Test
    fun `reports forwarding for aliased and fully qualified SwingModifier`() {
        val finding = lintAliasedModifier("org.jetbrains.compose.swing.modifier", "SwingModifier").single()

        assertEquals(EXPECTED_MESSAGE, finding.message)
    }

    @ParameterizedTest
    @CsvSource(
        "androidx.compose.ui, Modifier",
        "androidx.glance, GlanceModifier",
        "custom, SwingModifier",
    )
    fun `checks foreign modifier types only when configured`(
        modifierPackage: String,
        modifierName: String,
    ) {
        assertEquals(0, lintAliasedModifier(modifierPackage, modifierName).size)
        assertEquals(
            EXPECTED_MESSAGE,
            lintAliasedModifier(
                modifierPackage,
                modifierName,
                TestConfig("customModifiers" to listOf("$modifierPackage.$modifierName")),
            ).single().message,
        )
    }

    private fun lintAliasedModifier(
        modifierPackage: String,
        modifierName: String,
        config: Config = Config.empty,
    ): List<Finding> =
        ComposeSwingRuleLint.lint(
            "ModifierShouldBeForwardedToEveryBranch",
            """
            import androidx.compose.runtime.Composable
            import $modifierPackage.$modifierName as Chain

            @Composable
            fun Widget(modifier: $modifierPackage.$modifierName = Chain) {}

            @Composable
            fun Example(flag: Boolean, modifier: Chain = Chain) {
                if (flag) Widget(modifier) else Widget()
            }
            """.trimIndent(),
            config,
            """
            package $modifierPackage

            interface $modifierName {
                companion object : $modifierName
            }
            """.trimIndent(),
        )

    enum class Placement {
        SameFile,
        OtherFile,
    }

    enum class BranchSyntax(
        val body: (first: String, second: String) -> String,
    ) {
        If({ first, second -> "if (flag) { $first } else { $second }" }),
        When({ first, second -> "when (flag) { true -> { $first } else -> { $second } }" }),
    }

    enum class Branch {
        First,
        Second,
    }

    class Case(
        val placement: Placement,
        val branchSyntax: BranchSyntax,
        val parameters: String,
        val first: String,
        val second: String,
        val omitting: Branch?,
    ) {
        fun reportedOffsets(source: String): List<Int> {
            val firstOffset = source.indexOf(first)
            return when (omitting) {
                Branch.First -> listOf(firstOffset)
                Branch.Second -> listOf(source.indexOf(second, firstOffset + first.length))
                null -> emptyList()
            }
        }

        override fun toString() = "$placement $branchSyntax ($parameters) `$first` / `$second` -> $omitting"
    }

    companion object {
        private val signatures =
            listOf(
                "modifier: Modifier = Modifier",
                "rowModifier: Modifier = Modifier, modifier: Modifier = Modifier",
            )

        private val forms =
            listOf(
                Triple("Widget(modifier = modifier)", "Widget()", Branch.Second),
                Triple("Widget()", "Widget(modifier = modifier)", Branch.First),
                Triple("Widget(modifier)", "Widget()", Branch.Second),
                Triple("Widget(modifier = modifier)", "Widget(modifier = Modifier)", Branch.Second),
                Triple("Widget(modifier = modifier)", "Widget(modifier = modifier then Modifier)", null),
                Triple("Widget()", "Widget(modifier = modifier then Modifier)", Branch.First),
                Triple("Widget(modifier = modifier)", "Widget(modifier)", null),
                Triple("Widget(modifier = modifier)", "Plain()", null),
                Triple("Widget()", "Widget()", null),
                Triple("Widget(modifier)", "Swing()", null),
                Triple("Widget(modifier)", "Reading()", null),
                Triple("Widget(modifier)", "recordLayout()", null),
                Triple("Widget(chooseModifier(modifier))", "Widget()", null),
                Triple("Widget(modifier)", "Widget(chooseModifier(modifier))", null),
                Triple("Widget()", "Widget(Modifier.then(modifier))", Branch.First),
                Triple("Widget()", "Widget(Modifier then modifier)", Branch.First),
            )

        @JvmStatic
        fun cases(): Stream<Arguments> =
            Placement.entries
                .flatMap { placement ->
                    BranchSyntax.entries.flatMap { branchSyntax ->
                        signatures.flatMap { parameters ->
                            forms.map { (first, second, omitting) ->
                                Arguments.of(Case(placement, branchSyntax, parameters, first, second, omitting))
                            }
                        }
                    }
                }.stream()
    }
}

private const val EXPECTED_MESSAGE =
    "This branch omits the modifier parameter that another branch forwards. " +
        "Caller customizations would disappear when this branch is selected. " +
        "Pass the modifier to the composable in this branch."
