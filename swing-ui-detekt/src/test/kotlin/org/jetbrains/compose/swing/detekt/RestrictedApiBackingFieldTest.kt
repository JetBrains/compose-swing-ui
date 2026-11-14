// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.KotlinEnvironmentContainer
import dev.detekt.test.utils.createEnvironment
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RestrictedApiBackingFieldTest {
    private val env: KotlinEnvironmentContainer by lazy { createEnvironment() }

    private val subject =
        assertIs<AnalysisComposeSwingRuleAdapter>(composeSwingRule("RestrictedApi"))

    @ParameterizedTest(name = "{0}")
    @MethodSource("compiledUses")
    fun `reports a property of a compiled class by the use-site target of its restriction`(
        label: String,
        statement: String,
        expected: List<String>,
    ) {
        val findings =
            subject.lintWithContext(
                env,
                """
                import org.jetbrains.compose.swing.detekt.fixture.RestrictedLibraryProperties

                class NotSub(private val bar: RestrictedLibraryProperties) {
                    fun test() {
                        $statement
                    }
                }
                """.trimIndent(),
            )

        assertEquals(expected, findings.map { it.message }, label)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sourceUses")
    fun `reports a property of a source class by the use-site target of its restriction`(
        label: String,
        statement: String,
        expected: List<String>,
    ) {
        val findings =
            subject.lintWithContext(
                env,
                """
                import androidx.annotation.RestrictTo
                import androidx.annotation.RestrictTo.Scope.SUBCLASSES

                class Value

                open class Bar {
                    @RestrictTo(SUBCLASSES)
                    val plain: Value = Value()

                    @field:RestrictTo(SUBCLASSES)
                    val field: Value = Value()

                    @set:RestrictTo(SUBCLASSES)
                    var setterOnly: Value? = Value()

                    @get:RestrictTo(SUBCLASSES)
                    var getterOnly: Value? = Value()
                }

                class NotSub(private val bar: Bar) {
                    fun test() {
                        $statement
                    }
                }
                """.trimIndent(),
            )

        assertEquals(expected, findings.map { it.message }, label)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subclassUses")
    fun `does not report a property of a compiled class used from a subclass`(
        label: String,
        members: String,
    ) {
        val findings =
            subject.lintWithContext(
                env,
                """
                import org.jetbrains.compose.swing.detekt.fixture.RestrictedLibraryProperties

                class Sub : RestrictedLibraryProperties() {
                    $members
                }
                """.trimIndent(),
            )

        assertEquals(emptyList(), findings.map { it.message }, label)
    }

    companion object {
        private fun use(
            label: String,
            statement: String,
            vararg expected: String,
        ): Arguments = Arguments.of(label, statement, expected.toList())

        @JvmStatic
        fun compiledUses(): List<Arguments> {
            val plain = "RestrictedLibraryProperties.plain can only be accessed from subclasses"
            return listOf(
                use("read of an unqualified restriction", "val read = bar.plain", plain),
                use("argument of an unqualified restriction", "println(bar.plain)", plain),
                use("read of a setter-restricted property", "val read = bar.setterOnly"),
                use(
                    "write of a setter-restricted property",
                    "bar.setterOnly = null",
                    "RestrictedLibraryProperties.setterOnly can only be accessed from subclasses",
                ),
                use(
                    "read of a getter-restricted property",
                    "println(bar.getterOnly)",
                    "RestrictedLibraryProperties.getterOnly can only be accessed from subclasses",
                ),
                use("write of a getter-restricted property", "bar.getterOnly = null"),
            )
        }

        @JvmStatic
        fun sourceUses(): List<Arguments> {
            val plain = "Bar.plain can only be accessed from subclasses"
            return listOf(
                use("read of an unqualified restriction", "val read = bar.plain", plain),
                use("argument of an unqualified restriction", "println(bar.plain)", plain),
                use("read of a field-targeted restriction", "val read = bar.field"),
                use("read of a setter-restricted property", "val read = bar.setterOnly"),
                use(
                    "write of a setter-restricted property",
                    "bar.setterOnly = null",
                    "Bar.setterOnly can only be accessed from subclasses",
                ),
                use(
                    "read of a getter-restricted property",
                    "println(bar.getterOnly)",
                    "Bar.getterOnly can only be accessed from subclasses",
                ),
                use("write of a getter-restricted property", "bar.getterOnly = null"),
            )
        }

        @JvmStatic
        fun subclassUses(): List<Arguments> =
            listOf(
                Arguments.of("read of an unqualified restriction", "fun test() { val read = plain }"),
                Arguments.of("argument of an unqualified restriction", "fun test() { println(plain) }"),
                Arguments.of("read of a setter-restricted property", "fun test() { println(setterOnly) }"),
                Arguments.of(
                    "read from an inner class of the subclass",
                    "inner class Helper { fun test() { println(plain) } }",
                ),
            )
    }
}
