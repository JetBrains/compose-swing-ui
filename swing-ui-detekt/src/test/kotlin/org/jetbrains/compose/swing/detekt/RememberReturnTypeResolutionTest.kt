// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import kotlin.test.Test
import kotlin.test.assertEquals

class RememberReturnTypeResolutionTest {
    @Test
    fun `resolves imported aliases and ignores unrelated functions`() {
        val source =
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.remember as keep

            fun remember(block: () -> Unit) = block()

            @Composable
            fun example() {
                remember { }
                keep { check(true) }
            }
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("RememberReturnType", source)
        assertEquals(1, findings.size)
        val location =
            findings
                .single()
                .entity.location.text
        assertEquals("keep", source.substring(location.start, location.end))
    }
}
