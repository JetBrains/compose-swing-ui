// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Finding
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.KotlinEnvironmentContainer
import dev.detekt.test.utils.createEnvironment
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("libraryScopes")
    fun `decides each library scope by the Maven coordinates of the reference and of the compiled declaration`(
        label: String,
        module: String?,
        library: String?,
        expected: List<String?>,
    ) {
        val source =
            """
            import org.jetbrains.compose.swing.detekt.fixture.RestrictedLibrary
            fun use(api: RestrictedLibrary) {
                api.library()
                api.group()
                api.prefix()
            }
            """.trimIndent()

        val findings = restrictedApiRule(module, library).lintWithContext(env, source)

        val actual =
            findings.map { finding ->
                val location = finding.entity.location.text
                source.substring(location.start, location.end) to finding.message
            }
        val wanted =
            listOf("library", "group", "prefix").zip(expected).mapNotNull { (name, restriction) ->
                restriction?.let { name to "RestrictedLibrary.$name can only be called $it" }
            }
        assertEquals(wanted, actual, label)
    }

    companion object {
        private fun library(artifact: String) = "from within the same library ($artifact)"

        private fun group(
            referenced: String,
            from: String,
        ) = "from within the same library group (referenced groupId=`$referenced` from groupId=`$from`)"

        private fun prefix(
            referenced: String,
            prefix: String,
            from: String?,
        ) = "from within the same library group prefix (referenced groupId=`$referenced` with prefix $prefix" +
            from?.let { " from groupId=`$it`" }.orEmpty() + ")"

        @JvmStatic
        fun libraryScopes(): List<Arguments> =
            listOf(
                Arguments.of("the same group and artifact", "org.a:app", "org.a:app", listOf(null, null, null)),
                Arguments.of("the same group, another artifact", "org.a:app", "org.a:lib", listOf(null, null, null)),
                Arguments.of(
                    "another group, the same artifact",
                    "org.a:lib",
                    "org.b:lib",
                    listOf(null, group("org.b", "org.a"), null),
                ),
                Arguments.of(
                    "another group and artifact in one prefix",
                    "org.a:app",
                    "org.b:lib",
                    listOf(library("org.b:lib"), group("org.b", "org.a"), null),
                ),
                Arguments.of(
                    "another group and artifact in another prefix",
                    "com.a:app",
                    "org.b:lib",
                    listOf(library("org.b:lib"), group("org.b", "com.a"), prefix("org.b", "org", "com.a")),
                ),
                Arguments.of(
                    "groups without a dot",
                    "a:app",
                    "b:lib",
                    listOf(library("b:lib"), group("b", "a"), prefix("b", "\"\"", "a")),
                ),
                Arguments.of(
                    "an unknown module and a known library",
                    null,
                    "org.b:lib",
                    listOf(
                        library("org.b:lib"),
                        group("org.b", "<unknown>"),
                        prefix("org.b", "org", null),
                    ),
                ),
                Arguments.of("a known module and an unknown library", "org.a:app", null, listOf(null, null, null)),
                Arguments.of("an unknown module and an unknown library", null, null, listOf(null, null, null)),
            )
    }
}
