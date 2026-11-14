// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import kotlin.test.Test
import kotlin.test.assertEquals

class OpaqueUnitKeyScopeTest {
    private fun lint(source: String) = RuntimeRuleLint.lint("OpaqueUnitKey", source)

    @Test
    fun `a remember that is not the runtime one is ignored`() {
        val source =
            """
            fun remember(key: Any?, calculation: () -> Int) = calculation()
            fun produceUnit() {}
            fun test() {
                remember(produceUnit()) { 1 }
            }
            """.trimIndent()
        assertEquals(0, lint(source).size)
    }

    @Test
    fun `a runtime function that is not a key taker is ignored`() {
        val source =
            """
            import androidx.compose.runtime.mutableStateOf
            fun produceUnit() {}
            fun test() {
                mutableStateOf(produceUnit())
            }
            """.trimIndent()
        assertEquals(0, lint(source).size)
    }

    @Test
    fun `a parenthesized Unit object is not reported but a parenthesized call is`() {
        val source =
            """
            import androidx.compose.runtime.remember
            import androidx.compose.runtime.Composable
            fun produceUnit() {}
            @Composable
            fun test() {
                remember((Unit)) { 1 }
                remember((produceUnit())) { 1 }
            }
            """.trimIndent()
        assertEquals(1, lint(source).size)
    }

    @Test
    fun `an unresolved call is ignored and a key of another type is not reported`() {
        val source =
            """
            import androidx.compose.runtime.remember
            import androidx.compose.runtime.Composable
            @Composable
            fun test() {
                remember(missing()) { 1 }
                remember(42) { 1 }
                remember("key") { 1 }
            }
            """.trimIndent()
        assertEquals(0, lint(source).size)
    }
}
