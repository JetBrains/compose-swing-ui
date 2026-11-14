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

class RestrictedApiTestContextAnnotationTest {
    private val env: KotlinEnvironmentContainer by lazy { createEnvironment() }

    private val subject =
        assertIs<AnalysisComposeSwingRuleAdapter>(composeSwingRule("RestrictedApi"))

    @ParameterizedTest(name = "{0}")
    @MethodSource("annotations")
    fun `decides the test context by the resolved annotation class, not the written name`(
        label: String,
        import: String,
        annotation: String,
        expectedFindings: Int,
    ) {
        val findings =
            subject.lintWithContext(
                env,
                """
                package test.pkg

                import androidx.annotation.RestrictTo
                $import

                @RestrictTo(RestrictTo.Scope.TESTS)
                open class RestrictedClass

                annotation class Marker

                $annotation
                fun forTests(): RestrictedClass = RestrictedClass()
                """.trimIndent(),
            )
        assertEquals(expectedFindings, findings.size, label)
    }

    companion object {
        @JvmStatic
        fun annotations(): List<Arguments> {
            // The return type and the constructor call each name the restricted class.
            val reportedReferences = 2
            return listOf(
                Arguments.of(
                    "VisibleForTesting by its own name",
                    "import androidx.annotation.VisibleForTesting",
                    "@VisibleForTesting",
                    0,
                ),
                Arguments.of(
                    "VisibleForTesting renamed by an import alias",
                    "import androidx.annotation.VisibleForTesting as TestHook",
                    "@TestHook",
                    0,
                ),
                Arguments.of(
                    "TestOnly renamed by an import alias",
                    "import org.jetbrains.annotations.TestOnly as TestHook",
                    "@TestHook",
                    0,
                ),
                Arguments.of(
                    "a fully qualified VisibleForTesting",
                    "",
                    "@androidx.annotation.VisibleForTesting",
                    0,
                ),
                Arguments.of(
                    "an unrelated annotation aliased to the name VisibleForTesting",
                    "import test.pkg.Marker as VisibleForTesting",
                    "@VisibleForTesting",
                    reportedReferences,
                ),
                Arguments.of(
                    "an unrelated annotation aliased to the name TestOnly",
                    "import test.pkg.Marker as TestOnly",
                    "@TestOnly",
                    reportedReferences,
                ),
            )
        }
    }
}
