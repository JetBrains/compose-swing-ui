// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals

class ComposableNamingResolvedReturnTypeTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `reports each composable whose name contradicts its resolved return type once`(
        label: String,
        declarations: String,
        reportedNames: List<String>,
    ) {
        val source = sourceOf(declarations)

        val findings = RuntimeRuleLint.lint("ComposableNaming", source)

        assertEquals(
            reportedNames,
            findings.map { source.substring(it.entity.location.text.start, it.entity.location.text.end) },
            label,
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messageCases")
    fun `states which way the name should change`(
        label: String,
        declarations: String,
        message: String,
    ) {
        val findings = RuntimeRuleLint.lint("ComposableNaming", sourceOf(declarations))

        assertEquals(listOf(message), findings.map { it.message }, label)
    }

    companion object {
        private fun sourceOf(declarations: String): String =
            "package androidx.compose.runtime.foo\n\n" +
                "import androidx.compose.runtime.Composable\n\n" +
                "class Scope\n\n$declarations"

        private fun case(
            label: String,
            declarations: String,
            vararg reportedNames: String,
        ): Arguments = Arguments.of(label, declarations, reportedNames.toList())

        @JvmStatic
        fun cases(): Stream<Arguments> = Stream.concat(declaredTypeCases(), inferredTypeCases())

        private fun declaredTypeCases(): Stream<Arguments> =
            Stream.of(
                case("a lowercase Unit function", "@Composable\nfun button() {}", "button"),
                case("an uppercase Unit function", "@Composable\nfun Button() {}"),
                case("a lowercase value function", "@Composable\nfun getInt(): Int { return 5 }"),
                case("an uppercase value function", "@Composable\nfun GetInt(): Int { return 5 }", "GetInt"),
                case(
                    "functions that are not composable",
                    "fun button() {}\nfun Button() {}\nfun GetInt(): Int { return 5 }\nfun getInt(): Int { return 5 }",
                ),
                case(
                    "operator functions",
                    "object Test {\n    @Composable\n    operator fun invoke() {}\n\n" +
                        "    @Composable\n    operator fun unaryPlus() {}\n}",
                ),
                case(
                    "overrides of a generic return type",
                    "interface Test<T> {\n    @Composable\n    operator fun invoke()\n\n" +
                        "    @Composable\n    fun generic(): T\n}\n\n" +
                        "object TestImpl : Test<Unit> {\n    @Composable\n    override fun invoke() {}\n\n" +
                        "    @Composable\n    override fun generic() {}\n}",
                ),
                case(
                    "nested and internal functions",
                    "object Holder {\n    @Composable\n    internal fun button() {}\n\n" +
                        "    @Composable\n    internal fun GetInt(): Int { return 5 }\n}",
                    "button",
                    "GetInt",
                ),
                case(
                    "an override of an unresolved supertype",
                    "class MyImpl : MyComposer {\n    @Composable\n    override fun button() {}\n}",
                ),
                case(
                    "an uppercase expression body that returns a value",
                    "@Composable\nfun Theme() = \"dark\"",
                    "Theme",
                ),
                case("a lowercase expression body that returns a value", "@Composable\nfun theme() = \"dark\""),
                case("a lowercase Unit extension", "@Composable\nfun Scope.render() {}", "render"),
                case("an uppercase Unit extension", "@Composable\nfun Scope.Render() {}"),
            )

        private fun inferredTypeCases(): Stream<Arguments> =
            Stream.of(
                case(
                    "a lowercase expression body that returns Unit",
                    "@Composable\nfun Button() {}\n\n@Composable\nfun wrapper() = Button()",
                    "wrapper",
                ),
                case(
                    "an escaped uppercase expression body that returns a value",
                    "@Composable\nfun `Theme Factory`() = \"dark\"",
                    "`Theme Factory`",
                ),
                case(
                    "an escaped lowercase expression body that returns Unit",
                    "@Composable\nfun Button() {}\n\n@Composable\nfun `paint button`() = Button()",
                    "`paint button`",
                ),
                case(
                    "an escaped uppercase expression body that returns Unit",
                    "@Composable\nfun Button() {}\n\n@Composable\nfun `Paint button`() = Button()",
                ),
                case("an expression body that does not resolve", "@Composable\nfun Widget() = missing()"),
                case("a Unit alias as the return type", "typealias Done = Unit\n\n@Composable\nfun Widget(): Done {}"),
                case("a qualified Unit as the return type", "@Composable\nfun Widget(): kotlin.Unit {}"),
            )

        @JvmStatic
        fun messageCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    "a value in an expression body",
                    "@Composable\nfun Theme() = \"dark\"",
                    VALUE_MESSAGE,
                ),
                Arguments.of(
                    "Unit in an extension",
                    "@Composable\nfun Scope.render() {}",
                    UNIT_MESSAGE,
                ),
                Arguments.of(
                    "a value in a declared type",
                    "@Composable\nfun GetInt(): Int { return 5 }",
                    VALUE_MESSAGE,
                ),
            )
    }
}

private const val UNIT_MESSAGE =
    "This composable returns `Unit` and starts with a lowercase letter. Capitalized names identify composable " +
        "content at the call site. Rename the function to start with an uppercase letter."

private const val VALUE_MESSAGE =
    "This composable returns a value and starts with an uppercase letter. Lowercase names distinguish " +
        "value-producing functions from composable content. Rename the function to start with a lowercase letter."
