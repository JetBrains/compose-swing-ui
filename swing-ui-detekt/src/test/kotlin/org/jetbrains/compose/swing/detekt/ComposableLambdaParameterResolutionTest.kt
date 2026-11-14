// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import kotlin.test.Test
import kotlin.test.assertEquals

class ComposableLambdaParameterResolutionTest {
    @Test
    fun `resolves composable and unit while ignoring unrelated annotations and non unit functions`() {
        val source =
            """
            import androidx.compose.runtime.Composable as Compose

            annotation class Composable
            @Compose fun valid(text: @Compose () -> Unit) {}
            @Composable fun unrelated(text: @Compose () -> Unit) {}
            @Compose fun wrongType(text: @Compose () -> Unit): Int = 1
            @Compose fun wrongLambda(text: @Composable () -> Unit) {}
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("ComposableLambdaParameterNaming", source)
        assertEquals(1, findings.size)
        val location =
            findings
                .single()
                .entity.location.text
        assertEquals(source.indexOf("text:"), location.start)
        assertEquals("text", source.substring(location.start, location.end))
    }
}
