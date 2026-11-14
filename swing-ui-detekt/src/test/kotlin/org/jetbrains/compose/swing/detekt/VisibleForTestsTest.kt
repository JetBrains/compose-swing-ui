// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.test.FakeLanguageVersionSettings
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.compileForTest
import dev.detekt.test.utils.createEnvironment
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VisibleForTestsTest {
    private val environment = createEnvironment()
    private val subject = assertIs<AnalysisComposeSwingRuleAdapter>(composeSwingRule("VisibleForTests"))

    @ParameterizedTest(name = "{0}")
    @MethodSource("references")
    fun `reports a reference that a VisibleForTesting member does not allow from non-test code`(
        label: String,
        source: String,
        expected: List<String>,
    ) {
        val jetBrainsLibrary =
            """
            package jb

            import org.jetbrains.annotations.VisibleForTesting

            class JetBrainsDeclaring {
                @VisibleForTesting fun hidden() {}
            }

            @VisibleForTesting
            class JetBrainsAnnotated {
                fun member() {}
            }
            """.trimIndent()
        val dependencies = arrayOf(LIBRARY, jetBrainsLibrary)
        val findings = subject.lintWithContext(environment, source, dependencyContents = dependencies)

        assertEquals(expected, findings.map { it.message }, label)
    }

    @Test
    fun `does not report a reference from the declaring class, a nested class or the declaring file`() {
        val findings = subject.lintWithContext(environment, LIBRARY)

        assertEquals(emptyList(), findings.map { it.message })
    }

    @Test
    fun `does not report a reference from a test source set`(
        @TempDir workspace: Path,
    ) {
        val source = reference("fun use(d: Declaring) = d.explicitPrivate()")
        val outsideTests = subject.lintWithContext(environment, source, dependencyContents = arrayOf(LIBRARY))
        assertEquals(1, outsideTests.size, "the same source is reported outside a test source set")

        val library = workspace.resolve("project/src/main/kotlin/Library.kt").apply { createParentDirectories() }
        library.writeText(LIBRARY)
        listOf("project/src/test/kotlin", "project/src/jvmTest/kotlin").forEach { root ->
            val file = workspace.resolve("$root/Use.kt").apply { createParentDirectories() }
            file.writeText(source)

            val findings = subject.visitFile(compileForTest(file), FakeLanguageVersionSettings())

            assertEquals(0, findings.size, "$root is a test source set")
        }
    }

    companion object {
        private const val PRIVATE = "should only be accessed from tests or within private scope"
        private const val PACKAGE = "should only be accessed from tests or within package private scope"
        private const val PROTECTED = "should only be accessed from tests or within protected scope"
        private const val TESTS_ONLY = "should only be accessed from tests"

        private val LIBRARY =
            """
            package lib

            import androidx.annotation.VisibleForTesting
            import androidx.annotation.VisibleForTesting.Companion.NONE

            open class Declaring {
                @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) fun explicitPrivate() {}

                @VisibleForTesting(otherwise = VisibleForTesting.Companion.PRIVATE) fun companionPrivate() {}

                @VisibleForTesting(VisibleForTesting.PRIVATE) fun positionalPrivate() {}

                @VisibleForTesting fun defaultPrivate() {}

                @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE) fun packagePrivate() {}

                @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED) fun protectedMember() {}

                @VisibleForTesting(otherwise = VisibleForTesting.NONE) fun unrestricted() {}

                @VisibleForTesting(NONE) fun importedNone() {}

                @get:VisibleForTesting(NONE) val openGetter = "test"

                @get:VisibleForTesting val restrictedGetter = "test"

                @set:VisibleForTesting var restrictedSetter = "test"

                @VisibleForTesting val restrictedProperty = "test"

                fun inside() {
                    explicitPrivate()
                    packagePrivate()
                    protectedMember()
                }

                class Nested {
                    fun inside(d: Declaring) = d.explicitPrivate()
                }
            }

            @VisibleForTesting
            class Annotated {
                fun member() {}
            }

            class AnnotatedConstructor @VisibleForTesting constructor()

            class WithCompanion {
                fun inside() = create()

                companion object {
                    @VisibleForTesting fun create() = WithCompanion()
                }
            }

            @VisibleForTesting fun topLevel() {}

            fun sameFile() = topLevel()

            fun sameFileClass(): Annotated = Annotated()

            @VisibleForTesting
            fun allowedCaller(d: Declaring) = d.explicitPrivate()
            """.trimIndent()

        private fun reference(
            body: String,
            packageName: String = "app",
        ): String =
            """
            package $packageName

            import lib.Annotated
            import lib.AnnotatedConstructor
            import lib.Declaring
            import lib.WithCompanion
            import lib.topLevel

            $body
            """.trimIndent()

        @JvmStatic
        fun references(): List<Arguments> = spellings() + properties() + declarations() + scopes() + exemptions()

        private fun spellings(): List<Arguments> =
            listOf(
                case(
                    "a named PRIVATE argument from another class",
                    "fun use(d: Declaring) = d.explicitPrivate()",
                    "Declaring.explicitPrivate $PRIVATE",
                ),
                case(
                    "a Companion-qualified PRIVATE argument",
                    "fun use(d: Declaring) = d.companionPrivate()",
                    "Declaring.companionPrivate $PRIVATE",
                ),
                case(
                    "a positional PRIVATE argument",
                    "fun use(d: Declaring) = d.positionalPrivate()",
                    "Declaring.positionalPrivate $PRIVATE",
                ),
                case(
                    "no argument, which defaults to PRIVATE",
                    "fun use(d: Declaring) = d.defaultPrivate()",
                    "Declaring.defaultPrivate $PRIVATE",
                ),
                case(
                    "PACKAGE_PRIVATE from another package",
                    "fun use(d: Declaring) = d.packagePrivate()",
                    "Declaring.packagePrivate $PACKAGE",
                ),
                case(
                    "PROTECTED from a class that is not a subclass",
                    "fun use(d: Declaring) = d.protectedMember()",
                    "Declaring.protectedMember $PROTECTED",
                ),
                case("NONE", "fun use(d: Declaring) = d.unrestricted()", "Declaring.unrestricted $TESTS_ONLY"),
                case(
                    "a statically imported NONE",
                    "fun use(d: Declaring) = d.importedNone()",
                    "Declaring.importedNone $TESTS_ONLY",
                ),
            )

        private fun properties(): List<Arguments> =
            listOf(
                case(
                    "a getter annotated NONE",
                    "fun use(d: Declaring) = d.openGetter",
                    "Declaring.openGetter $TESTS_ONLY",
                ),
                case(
                    "a read of a property with a restricted getter",
                    "fun use(d: Declaring) = d.restrictedGetter",
                    "Declaring.restrictedGetter $PRIVATE",
                ),
                case(
                    "a read of a property whose restriction is on its setter",
                    "fun use(d: Declaring) = d.restrictedSetter",
                ),
                case(
                    "a write of a property whose restriction is on its setter",
                    "fun use(d: Declaring) {\n    d.restrictedSetter = \"changed\"\n}",
                    "Declaring.restrictedSetter $PRIVATE",
                ),
                case(
                    "a property annotated without a use-site target",
                    "fun use(d: Declaring) = d.restrictedProperty",
                    "Declaring.restrictedProperty $PRIVATE",
                ),
            )

        private fun declarations(): List<Arguments> =
            listOf(
                case(
                    "a member of a class annotated as a whole",
                    "fun use(a: Annotated) = a.member()",
                    "Annotated $PRIVATE",
                    "Annotated.member $PRIVATE",
                ),
                case(
                    "an annotated constructor",
                    "fun use() = AnnotatedConstructor()",
                    "AnnotatedConstructor $PRIVATE",
                ),
                case("a top-level function from another file", "fun use() = topLevel()", "topLevel $PRIVATE"),
                case(
                    "a companion member from a class other than the one holding the companion",
                    "fun use() = WithCompanion.create()",
                    "Companion.create $PRIVATE",
                ),
            )

        private fun scopes(): List<Arguments> =
            listOf(
                case(
                    "PACKAGE_PRIVATE from the same package",
                    "fun use(d: Declaring) = d.packagePrivate()",
                    packageName = "lib",
                ),
                case(
                    "PRIVATE from the same package",
                    "fun use(d: Declaring) = d.explicitPrivate()",
                    "Declaring.explicitPrivate $PRIVATE",
                    packageName = "lib",
                ),
                case(
                    "PROTECTED from the same package",
                    "fun use(d: Declaring) = d.protectedMember()",
                    "Declaring.protectedMember $PROTECTED",
                    packageName = "lib",
                ),
                case(
                    "PROTECTED from a subclass",
                    "class Sub : Declaring() {\n    fun use() = protectedMember()\n}",
                ),
                case(
                    "PACKAGE_PRIVATE from a subclass in another package",
                    "class Sub : Declaring() {\n    fun use() = packagePrivate()\n}",
                    "Declaring.packagePrivate $PACKAGE",
                ),
                case(
                    "PRIVATE from a subclass",
                    "class Sub : Declaring() {\n    fun use() = explicitPrivate()\n}",
                    "Declaring.explicitPrivate $PRIVATE",
                ),
            )

        private fun exemptions(): List<Arguments> =
            listOf(
                case(
                    "a member that is itself VisibleForTesting",
                    "@androidx.annotation.VisibleForTesting\nfun use(d: Declaring) = d.explicitPrivate()",
                ),
                case(
                    "a class that is itself VisibleForTesting",
                    "@androidx.annotation.VisibleForTesting\nclass User {\n" +
                        "    fun use(d: Declaring) = d.explicitPrivate()\n}",
                ),
                case(
                    "a member annotated with org.jetbrains.annotations.VisibleForTesting",
                    "fun use(d: jb.JetBrainsDeclaring) = d.hidden()",
                ),
                case(
                    "a class annotated as a whole with org.jetbrains.annotations.VisibleForTesting",
                    "fun use(a: jb.JetBrainsAnnotated) = a.member()",
                ),
                case(
                    "a caller annotated with org.jetbrains.annotations.VisibleForTesting",
                    "@org.jetbrains.annotations.VisibleForTesting\nfun use(d: Declaring) = d.explicitPrivate()",
                ),
                case(
                    "a caller annotated with org.jetbrains.annotations.TestOnly",
                    "@org.jetbrains.annotations.TestOnly\nfun use(d: Declaring) = d.explicitPrivate()",
                ),
                case(
                    "a caller annotated with RestrictTo(TESTS)",
                    "@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.TESTS)\n" +
                        "fun use(d: Declaring) = d.explicitPrivate()",
                ),
            )

        private fun case(
            label: String,
            body: String,
            vararg expected: String,
            packageName: String = "app",
        ): Arguments =
            Arguments.of(
                label,
                reference(body, packageName),
                expected.toList(),
            )
    }
}
