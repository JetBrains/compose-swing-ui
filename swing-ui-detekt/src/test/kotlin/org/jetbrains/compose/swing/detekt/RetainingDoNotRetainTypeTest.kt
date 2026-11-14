// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RetainingDoNotRetainTypeTest {
    @Test
    fun `explanations and key descriptions are reported for marked types and collections`() {
        val source =
            """
            import androidx.compose.runtime.annotation.DoNotRetain
            import androidx.compose.runtime.retain.retain
            @DoNotRetain("Unretainable for testing")
            open class WithCause
            @DoNotRetain class WithoutCause
            class Derived : WithCause()
            class Safe
            fun test() {
                retain { WithCause() }
                retain { WithoutCause() }
                retain { Derived() }
                retain { listOf(WithCause()) }
                retain { mapOf("Bar" to WithoutCause()) }
                retain { Derived() to 20 }
                retain(WithCause()) { 42 }
                retain(WithoutCause()) { 42 }
                retain(Derived()) { 42 }
                retain(listOf(WithCause())) { 42 }
                retain(mapOf("Bar" to WithoutCause())) { 42 }
                retain(Derived() to 20) { 42 }
                retain { Safe() }
            }
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("RetainingDoNotRetainType", source)
        assertEquals(12, findings.size)
        assertEquals(6, findings.count { it.message.startsWith("Key type ") })
        assertEquals(8, findings.count { "Unretainable for testing" in it.message })
        findings.forEach { finding ->
            val location = finding.entity.location.text
            assertEquals("retain", source.substring(location.start, location.end))
            assertTrue("@DoNotRetain" in finding.message)
        }
    }
}
