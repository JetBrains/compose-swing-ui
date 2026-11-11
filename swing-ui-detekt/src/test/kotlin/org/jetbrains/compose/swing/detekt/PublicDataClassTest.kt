// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublicDataClassTest {
    private fun lint(source: String) = PublicDataClass(Config.empty).lint(source)

    private fun assertReported(source: String) =
        assertEquals(1, lint(source).size, "expected one finding for:\n$source")

    private fun assertClean(source: String) =
        assertEquals(emptyList(), lint(source), "expected no finding for:\n$source")

    @Test
    fun `reports a data class declared public`() {
        val findings =
            lint(
                """
                package sample

                public data class Point(val x: Int, val y: Int)
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("copy"))
    }

    @Test
    fun `reports a data class with no visibility keyword`() {
        assertReported(
            """
            package sample

            data class Point(val x: Int, val y: Int)
            """.trimIndent(),
        )
    }

    @Test
    fun `reports a protected data class, the surface a subclass compiles against`() {
        assertReported(
            """
            package sample

            open class Holder {
                protected data class Row(val value: Int)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `reports a data class nested in a public class`() {
        assertReported(
            """
            package sample

            class Holder {
                data class Row(val value: Int)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `reports a data class in the companion of a public class`() {
        assertReported(
            """
            package sample

            class Holder {
                companion object {
                    data class Row(val value: Int)
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over an internal data class`() {
        assertClean(
            """
            package sample

            internal data class Point(val x: Int, val y: Int)
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a private data class`() {
        assertClean(
            """
            package sample

            private data class Point(val x: Int, val y: Int)
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a data class declared inside a function`() {
        assertClean(
            """
            package sample

            fun build() {
                data class Point(val x: Int, val y: Int)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a data class nested in an internal class`() {
        assertClean(
            """
            package sample

            internal class Holder {
                data class Row(val value: Int)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a data class nested in a private class`() {
        assertClean(
            """
            package sample

            private class Holder {
                data class Row(val value: Int)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a data class declared public inside an internal object`() {
        assertClean(
            """
            package sample

            internal object Impl {
                public data class Row(val value: Int)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a class that is not a data class`() {
        assertClean(
            """
            package sample

            public class Point(val x: Int, val y: Int)
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a value class`() {
        assertClean(
            """
            package sample

            @JvmInline
            public value class Pixels(val value: Int)
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a data object, which generates no constructor-shaped members`() {
        assertClean(
            """
            package sample

            public data object Origin
            """.trimIndent(),
        )
    }
}
