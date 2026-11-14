// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import kotlin.test.Test
import kotlin.test.assertEquals

class ProduceStateAssignmentTest {
    private fun lint(body: String) =
        RuntimeRuleLint.lint(
            "ProduceStateDoesNotAssignValue",
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.produceState
            class Holder { var value = 0 }
            @Composable
            fun Test(holder: Holder) {
                $body
            }
            """.trimIndent(),
        )

    @Test
    fun `compound assignment and increment of value count as assignments`() {
        assertEquals(0, lint("produceState(0) { value += 1 }").size)
        assertEquals(0, lint("produceState(0) { value++ }").size)
        assertEquals(0, lint("produceState(0) { this.value = 1 }").size)
    }

    @Test
    fun `assigning a value that is not the produced state is reported`() {
        assertEquals(1, lint("produceState(0) { holder.value = 1 }").size)
        assertEquals(1, lint("produceState(0) { val other = 1 }").size)
    }

    @Test
    fun `a produceState that is not the runtime one is ignored`() {
        val source =
            """
            fun produceState(initial: Int, producer: () -> Unit) = producer()
            fun test() {
                produceState(0) { }
            }
            """.trimIndent()
        assertEquals(0, RuntimeRuleLint.lint("ProduceStateDoesNotAssignValue", source).size)
    }
}
