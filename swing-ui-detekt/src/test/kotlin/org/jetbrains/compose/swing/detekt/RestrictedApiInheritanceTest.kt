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

class RestrictedApiInheritanceTest {
    private val env: KotlinEnvironmentContainer by lazy { createEnvironment() }

    private val subject =
        assertIs<AnalysisComposeSwingRuleAdapter>(composeSwingRule("RestrictedApi"))

    @ParameterizedTest(name = "{0}")
    @MethodSource("inheritedUses")
    fun `reports the restricted compiled member a class inherits or overrides where it is used or overridden`(
        label: String,
        body: String,
        expected: List<String>,
    ) {
        val source =
            """
            import org.jetbrains.compose.swing.detekt.fixture.RestrictedLibrary
            import org.jetbrains.compose.swing.detekt.fixture.RestrictedLibraryParent

            $body
            """.trimIndent()

        val actual =
            subject.lintWithContext(env, source).map { finding ->
                val location = finding.entity.location.text
                "${source.substring(location.start, location.end)}: ${finding.message}"
            }

        assertEquals(expected, actual, label)
    }

    companion object {
        @JvmStatic
        fun inheritedUses(): List<Arguments> {
            val library = "from within the same library"
            return listOf(
                Arguments.of(
                    "an unqualified call to a member inherited from a compiled class",
                    "class Child : RestrictedLibrary() { fun test() { library() } }",
                    listOf("library: RestrictedLibrary.library can only be called $library"),
                ),
                Arguments.of(
                    "an unqualified call to a member inherited through an intermediate class",
                    "open class Mid : RestrictedLibrary()\nclass Child : Mid() { fun test() { library() } }",
                    listOf("library: RestrictedLibrary.library can only be called $library"),
                ),
                Arguments.of(
                    "a call to an inherited member through this",
                    "class Child : RestrictedLibrary() { fun test() { this.library() } }",
                    listOf("library: RestrictedLibrary.library can only be called $library"),
                ),
                Arguments.of(
                    "an unqualified read of a property inherited from a restricted compiled class",
                    "class Child : RestrictedLibraryParent() { fun test() = value }",
                    listOf(
                        "RestrictedLibraryParent: RestrictedLibraryParent can only be called $library",
                        "value: RestrictedLibraryParent.value can only be accessed $library",
                    ),
                ),
                Arguments.of(
                    "an override of a function of a restricted compiled class",
                    "class Child : RestrictedLibraryParent() { override fun method() {} }",
                    listOf(
                        "RestrictedLibraryParent: RestrictedLibraryParent can only be called $library",
                        "method: RestrictedLibraryParent.method can only be called $library",
                    ),
                ),
                Arguments.of(
                    "an override of a property of a restricted compiled class",
                    "class Child : RestrictedLibraryParent() { override val value: Int get() = 1 }",
                    listOf(
                        "RestrictedLibraryParent: RestrictedLibraryParent can only be called $library",
                        "value: RestrictedLibraryParent.value can only be accessed $library",
                    ),
                ),
                Arguments.of(
                    "an override of a restricted member of a compiled class that is not itself restricted",
                    "class Child : RestrictedLibrary() { override fun library() {} }",
                    listOf("library: RestrictedLibrary.library can only be called $library"),
                ),
                Arguments.of(
                    "a call to a member that an intermediate class overrides",
                    "open class Mid : RestrictedLibrary() { override fun library() {} }\n" +
                        "class Child : Mid() { fun test() { library() } }",
                    listOf("library: RestrictedLibrary.library can only be called $library"),
                ),
                Arguments.of(
                    "an override of a member of a class declared in source",
                    "open class Base { open fun method() {} }\nclass Child : Base() { override fun method() {} }",
                    emptyList<String>(),
                ),
            )
        }
    }
}
