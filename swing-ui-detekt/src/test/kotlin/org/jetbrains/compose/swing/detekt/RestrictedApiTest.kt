// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Finding
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.KotlinEnvironmentContainer
import dev.detekt.test.utils.createEnvironment
import org.jetbrains.compose.swing.rules.androidx.ModuleBoundary
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RestrictedApiTest {
    private val env: KotlinEnvironmentContainer by lazy { createEnvironment() }

    private val subject =
        assertIs<AnalysisComposeSwingRuleAdapter>(composeSwingRule("RestrictedApi"))

    private fun lint(source: String): List<Finding> =
        subject
            .lintWithContext(env, source)
            .also { findings ->
                findings.forEach { finding ->
                    val location = finding.entity.location.text
                    val expected = finding.message.substringBefore(" can only").substringAfterLast('.')
                    assertEquals(expected, source.substring(location.start, location.end))
                }
            }

    @Test
    fun `does not report a call to a member without RestrictTo`() {
        val findings =
            lint(
                """
                package test.pkg

                class Plain {
                    fun method() {}
                }

                fun use(plain: Plain) {
                    plain.method()
                }
                """.trimIndent(),
            )
        assertEquals(0, findings.size)
    }

    @Test
    fun `reports a function and a property restricted to subclasses when used outside them`() {
        val findings =
            lint(
                """
                package test.pkg

                import androidx.annotation.RestrictTo

                open class Class1 {
                    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
                    fun onSomething() {}

                    @get:RestrictTo(RestrictTo.Scope.SUBCLASSES)
                    var counter: Int = 0
                }

                class NotSubClass {
                    fun test(cls: Class1) {
                        cls.onSomething()
                        val c = cls.counter
                    }
                }
                """.trimIndent(),
            )
        assertEquals(2, findings.size)
        assertEquals("Class1.onSomething can only be called from subclasses", findings[0].message)
        assertEquals("Class1.counter can only be accessed from subclasses", findings[1].message)
    }

    @Test
    fun `does not report a subclass-restricted member used from within a subclass`() {
        val findings =
            lint(
                """
                package test.pkg

                import androidx.annotation.RestrictTo

                open class Class1 {
                    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
                    fun onSomething() {}
                }

                class SubClass : Class1() {
                    fun test() {
                        onSomething()
                    }
                }
                """.trimIndent(),
            )
        assertEquals(0, findings.size)
    }

    @Test
    fun `does not report a subclass-restricted member used from within the declaring class itself`() {
        val findings =
            lint(
                """
                package test.pkg

                import androidx.annotation.RestrictTo

                open class Class1 {
                    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
                    fun onSomething() {}

                    fun test() {
                        onSomething()
                    }
                }
                """.trimIndent(),
            )
        assertEquals(0, findings.size)
    }

    @Test
    fun `reports a test-restricted class constructed from non-test source`() {
        val findings =
            lint(
                """
                package test.pkg

                import androidx.annotation.RestrictTo

                @RestrictTo(RestrictTo.Scope.TESTS)
                open class RestrictedClass

                class MyClass : RestrictedClass()
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
        assertEquals("RestrictedClass can only be called from tests", findings.single().message)
    }

    @Test
    fun `reports a test-restricted nested class reference and a call to its member`() {
        val findings =
            lint(
                """
                package test.pkg

                import androidx.annotation.RestrictTo
                import androidx.annotation.RestrictTo.Scope.TESTS

                class Outer {
                    private lateinit var innerInstance: Inner

                    @RestrictTo(TESTS)
                    inner class Inner {
                        fun method() {}
                    }

                    private fun outerMethod() {
                        innerInstance.method()
                    }
                }
                """.trimIndent(),
            )
        assertEquals(2, findings.size)
        assertEquals("Inner can only be accessed from tests", findings[0].message)
        assertEquals("Inner.method can only be called from tests", findings[1].message)
    }

    @Test
    fun `reports a call to a subclass-restricted constructor`() {
        val findings =
            lint(
                """
                package test.pkg

                import androidx.annotation.RestrictTo

                class Foo {
                    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
                    constructor()
                }

                @RestrictTo(RestrictTo.Scope.SUBCLASSES)
                val foo = Foo()
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
        assertEquals("Foo can only be called from subclasses", findings.single().message)
    }

    @Test
    fun `does not inherit a restriction from a supertype`() {
        val findings =
            lint(
                """
                package test.pkg

                import androidx.annotation.RestrictTo

                @RestrictTo(RestrictTo.Scope.TESTS)
                open class RestrictedParent

                open class Parent : RestrictedParent() {
                    fun myMethod() {}
                }

                class Cls : Parent() {
                    override fun myMethod() {
                        super.myMethod()
                    }
                }
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
        assertEquals("RestrictedParent can only be called from tests", findings.single().message)
    }

    @Test
    fun `reports both scopes independently when an annotation combines them`() {
        val findings =
            lint(
                """
                package test.pkg

                import androidx.annotation.RestrictTo
                import androidx.annotation.RestrictTo.Scope.SUBCLASSES
                import androidx.annotation.RestrictTo.Scope.TESTS

                open class Class1 {
                    @RestrictTo(TESTS, SUBCLASSES)
                    fun onSomething() {}
                }

                class NotSubClass {
                    fun test(cls: Class1) {
                        cls.onSomething()
                    }
                }
                """.trimIndent(),
            )
        assertEquals(2, findings.size)
        assertEquals("Class1.onSomething can only be called from tests", findings[0].message)
        assertEquals("Class1.onSomething can only be called from subclasses", findings[1].message)
    }

    @Test
    fun `does not report a library-restricted member used from within the same compiled source`() {
        val findings =
            lint(
                """
                package test.pkg

                import androidx.annotation.RestrictTo

                class LibraryCode {
                    @RestrictTo(RestrictTo.Scope.LIBRARY)
                    fun restricted() {}
                }

                fun use(code: LibraryCode) {
                    code.restricted()
                }
                """.trimIndent(),
            )
        assertEquals(0, findings.size)
    }

    @Test
    fun `matches upstream's getter and setter use-site targets, reported as property access`() {
        val findings =
            lint(
                """
                package test.pkg

                import androidx.annotation.RestrictTo
                import androidx.annotation.RestrictTo.Scope.SUBCLASSES

                class Foo
                open class Bar {
                    @RestrictTo(SUBCLASSES)
                    val foo1: Foo = Foo()

                    @field:RestrictTo(SUBCLASSES)
                    val foo2: Foo = Foo()

                    @set:RestrictTo(SUBCLASSES)
                    var foo3: Foo? = Foo()

                    @get:RestrictTo(SUBCLASSES)
                    var foo4: Foo? = Foo()
                }

                class NotSub(private val bar: Bar) {
                    fun test() {
                        val a = bar.foo1
                        val b = bar.foo2
                        val c = bar.foo3
                        bar.foo3 = null
                        val d = bar.foo4
                        bar.foo4 = null
                    }
                }
                """.trimIndent(),
            )
        assertEquals(3, findings.size)
        assertEquals("Bar.foo1 can only be accessed from subclasses", findings[0].message)
        assertEquals("Bar.foo3 can only be accessed from subclasses", findings[1].message)
        assertEquals("Bar.foo4 can only be accessed from subclasses", findings[2].message)
    }

    @Test
    fun `does not report an annotation that declares no scope`() {
        val findings =
            lint(
                """
                package test.pkg

                import androidx.annotation.RestrictTo

                open class Class1 {
                    @RestrictTo()
                    fun onSomething() {}
                }

                class NotSubClass {
                    fun test(cls: Class1) {
                        cls.onSomething()
                    }
                }
                """.trimIndent(),
            )
        assertEquals(0, findings.size)
    }

    @Test
    fun `treats a library group as the library boundary, alongside a second scope`() {
        val findings =
            lint(
                """
                package test.pkg

                import androidx.annotation.RestrictTo
                import androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX
                import androidx.annotation.RestrictTo.Scope.TESTS

                open class Class1 {
                    @RestrictTo(LIBRARY_GROUP_PREFIX, TESTS)
                    fun onSomething() {}
                }

                class NotSubClass {
                    fun test(cls: Class1) {
                        cls.onSomething()
                    }
                }
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
        assertEquals("Class1.onSomething can only be called from tests", findings.single().message)
    }

    @Test
    fun `does not report a subclass-restricted member used inside an object literal that subclasses it`() {
        val findings =
            lint(
                """
                package test.pkg

                import androidx.annotation.RestrictTo

                open class Class1 {
                    @RestrictTo(RestrictTo.Scope.SUBCLASSES)
                    fun onSomething() {}
                }

                fun make(): Class1 =
                    object : Class1() {
                        fun test() {
                            onSomething()
                        }
                    }
                """.trimIndent(),
            )
        assertEquals(0, findings.size)
    }

    @Test
    fun `reports a restricted top-level function, which has no declaring class`() {
        val findings =
            lint(
                """
                package test.pkg

                import androidx.annotation.RestrictTo

                @RestrictTo(RestrictTo.Scope.TESTS)
                fun helper() {}

                fun use() {
                    helper()
                }
                """.trimIndent(),
            )
        assertEquals(1, findings.size)
        assertEquals("helper can only be called from tests", findings.single().message)
    }

    @Test
    fun `reports library restrictions on compiled declarations`() {
        val source =
            """
            import org.jetbrains.compose.swing.detekt.fixture.RestrictedLibrary
            fun use(api: RestrictedLibrary) {
                api.library()
                api.group()
                api.prefix()
            }
            """.trimIndent()
        val findings = subject.lintWithContext(env, source)
        assertEquals(3, findings.size)
        listOf("library", "group", "prefix").forEachIndexed { index, name ->
            val finding = findings[index]
            assertEquals("RestrictedLibrary.$name can only be called from within the same library", finding.message)
            val location = finding.entity.location.text
            assertEquals(name, source.substring(location.start, location.end))
            assertEquals(index + 3, source.substring(0, location.start).count { it == '\n' } + 1)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("moduleMembership")
    fun `decides whether a compiled declaration belongs to the module of the reference`(
        label: String,
        reference: String,
        roots: List<String>,
        expected: Boolean,
        @TempDir workspace: Path,
    ) {
        listOf("app", "app/nested", "other").forEach { module ->
            workspace
                .resolve(module)
                .createDirectories()
                .resolve("build.gradle.kts")
                .createFile()
        }
        workspace.resolve("orphan").createDirectories()

        val actual = ModuleBoundary.isWithinModule(workspace.resolve(reference), roots.map(workspace::resolve))

        assertEquals(expected, actual, label)
    }

    companion object {
        @JvmStatic
        fun moduleMembership(): List<Arguments> =
            listOf(
                Arguments.of(
                    "a root under the build directory of the module holding the reference",
                    "app/src/main/kotlin/Use.kt",
                    listOf("app/build/classes/kotlin/main"),
                    true,
                ),
                Arguments.of(
                    "every root under the build directory of the module holding the reference",
                    "app/src/main/kotlin/Use.kt",
                    listOf("app/build/classes/kotlin/main", "app/build/libs/app.jar"),
                    true,
                ),
                Arguments.of(
                    "a root under the build directory of another module",
                    "app/src/main/kotlin/Use.kt",
                    listOf("other/build/classes/kotlin/main"),
                    false,
                ),
                Arguments.of(
                    "a root under the build directory of an enclosing module rather than the nearest one",
                    "app/nested/src/main/kotlin/Use.kt",
                    listOf("app/build/classes/kotlin/main"),
                    false,
                ),
                Arguments.of(
                    "a jar outside any build directory",
                    "app/src/main/kotlin/Use.kt",
                    listOf("cache/modules/library.jar"),
                    false,
                ),
                Arguments.of(
                    "a root inside the module but outside its build directory",
                    "app/src/main/kotlin/Use.kt",
                    listOf("app/libs/library.jar"),
                    false,
                ),
                Arguments.of(
                    "a root beside the build directory whose name only starts the same",
                    "app/src/main/kotlin/Use.kt",
                    listOf("app/build-cache/classes"),
                    false,
                ),
                Arguments.of(
                    "one root under the build directory and one outside it",
                    "app/src/main/kotlin/Use.kt",
                    listOf("app/build/classes/kotlin/main", "cache/modules/library.jar"),
                    false,
                ),
                Arguments.of(
                    "a reference with no module root",
                    "orphan/src/Use.kt",
                    listOf("orphan/build/classes/kotlin/main"),
                    false,
                ),
            )
    }
}
