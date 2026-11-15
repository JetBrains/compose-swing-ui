// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.compose.swing.detekt.composerules

import dev.detekt.api.Config
import dev.detekt.api.Rule
import org.intellij.lang.annotations.Language
import org.jetbrains.compose.swing.detekt.toDetektRule
import org.jetbrains.compose.swing.rules.runtime.MissingNonRestartableComposable
import org.jetbrains.compose.swing.rules.runtime.MissingNonRestartableComposableCheck
import org.jetbrains.compose.swing.rules.runtime.MissingReadOnlyComposable
import org.jetbrains.compose.swing.rules.runtime.MissingReadOnlyComposableCheck
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MissingNonRestartableComposableCheckSwingTest {
    private val rule = MissingNonRestartableComposable.toDetektRule(Config.empty)

    @ParameterizedTest(name = "getter the rule must accept: {0}")
    @ValueSource(
        strings = [
            "val Wrapper: String @Composable get() = Text()",
            "val Wrapper: Unit get() = Unit",
            "val Wrapper: Unit @Composable inline get() = Content()",
            "val Wrapper: Unit @ReadOnlyComposable @Composable get() = Content()",
            "open class Host { open val Wrapper: Unit @Composable get() = Content() }",
            "interface Contract { val Wrapper: Unit @Composable get() = Content() }",
        ],
    )
    fun `does not report composable getters that cannot be non-restartable`(declaration: String) {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
                @Composable
                fun Content() {}

                @Composable
                fun Text(): String = "text"

                $declaration
                """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource(
        "org.jetbrains.compose.swing.detekt.composerules." +
            "MissingNonRestartableComposableCheckSwingTest\$PropertiesWithSetter#cases",
    )
    fun `reports only the getter of a property that also has a setter`(
        label: String,
        checkedRule: Rule,
        code: String,
        message: String,
    ) {
        val findings = checkedRule.lintWithAnalysisApi(codeWithFakeCompose(code))

        assertEquals(listOf(message), findings.map { it.message }, label)
    }

    @Test
    fun `does not report a body with several statements or no call`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
                @Composable
                fun Content() {}

                @Composable
                fun TwoCalls() {
                    Content()
                    Content()
                }

                @Composable
                fun Declaration() {
                    val value = 1
                }
                """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report a wrapper that returns a property selection`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
                class Holder(val marker: Unit)

                @Composable
                fun Wrapper(holder: Holder): Unit = holder.marker
                """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report a wrapper around a plain function`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
                fun plain() {}

                @Composable
                fun Wrapper() = plain()
                """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports a member wrapper that calls through this`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
                class Host {
                    @Composable
                    fun Render() {}

                    @Composable
                    fun Wrapper() = this.Render()
                }
                """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertTextLocations(findings, "Wrapper")
    }

    @Test
    fun `reports wrappers that call through a type or package qualifier`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
                object Section {
                    @Composable
                    fun Render() {}
                }

                class Outer {
                    class Inner {
                        companion object {
                            @Composable
                            fun Render() {}
                        }
                    }
                }

                @Composable
                fun ObjectWrapper() = Section.Render()

                @Composable
                fun NestedWrapper() = Outer.Inner.Render()

                @Composable
                fun PackageWrapper() = com.example.compose.fake.Section.Render()
                """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(3, findings.size)
        assertTextLocations(findings, "NestedWrapper", "ObjectWrapper", "PackageWrapper")
    }

    @Test
    fun `does not report wrappers that call through a value receiver`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
                class Tool {
                    @Composable
                    fun Render() {}
                }

                object Section {
                    val tool = Tool()

                    fun make() = Tool()
                }

                val topLevel = Tool()

                @Composable
                fun TopLevelProperty() = topLevel.Render()

                @Composable
                fun ObjectProperty() = Section.tool.Render()

                @Composable
                fun ObjectCall() = Section.make().Render()

                @Composable
                fun ConstructorCall() = Tool().Render()
                """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report a wrapper that passes values read from outside its parameters`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
                val topLevel = "top"

                object Defaults {
                    val name = "name"
                }

                @Composable
                fun Content(value: String) {}

                @Composable
                fun TopLevelValue() = Content(topLevel)

                @Composable
                fun ObjectProperty() = Content(Defaults.name)
                """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports a wrapper whose function carries a non-preview annotation`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
                @Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
                annotation class Tag

                @Composable
                fun Content() {}

                @Deprecated("old")
                @Composable
                fun DeprecatedWrapper() = Content()

                @Tag
                @Composable
                fun TaggedWrapper() = Content()
                """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(2, findings.size)
        assertTextLocations(findings, "DeprecatedWrapper", "TaggedWrapper")
    }

    @Test
    fun `does not report a wrapper around a final override`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
                open class Base {
                    @Composable
                    open fun Render() {}
                }

                class Impl : Base() {
                    @Composable
                    final override fun Render() {}
                }

                @Composable
                fun Wrapper(impl: Impl) = impl.Render()
                """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report a wrapper around an external composable`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
                @Composable
                external fun Native()

                @Composable
                fun Wrapper() = Native()
                """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report a wrapper around a tailrec composable`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
                @Composable
                tailrec fun Countdown(count: Int) {
                    if (count > 0) Countdown(count - 1)
                }

                @Composable
                fun Wrapper(count: Int) = Countdown(count)
                """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    object PropertiesWithSetter {
        @JvmStatic
        fun cases(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    "MissingNonRestartableComposable",
                    MissingNonRestartableComposable.toDetektRule(Config.empty),
                    """
                    @Composable
                    fun Content() {}

                    var Wrapper: Unit
                        @Composable
                        get() = Content()
                        set(value) {}
                    """.trimIndent(),
                    MissingNonRestartableComposableCheck.MISSING_NON_RESTARTABLE_COMPOSABLE_MESSAGE,
                ),
                Arguments.of(
                    "MissingReadOnlyComposable",
                    MissingReadOnlyComposable.toDetektRule(Config.empty),
                    """
                    @ReadOnlyComposable
                    @Composable
                    fun currentValue(): Int = 42

                    var value: Int
                        @Composable
                        get() = currentValue()
                        set(newValue) {
                            newValue.toString()
                        }
                    """.trimIndent(),
                    MissingReadOnlyComposableCheck.MISSING_READ_ONLY_COMPOSABLE_MESSAGE,
                ),
            )
    }
}
