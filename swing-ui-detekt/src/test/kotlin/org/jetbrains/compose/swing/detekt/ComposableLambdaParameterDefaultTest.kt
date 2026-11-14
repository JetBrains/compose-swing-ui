// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import kotlin.test.Test
import kotlin.test.assertEquals

class ComposableLambdaParameterDefaultTest {
    private fun naming(declaration: String) =
        RuntimeRuleLint
            .lint(
                "ComposableLambdaParameterNaming",
                "import androidx.compose.runtime.Composable\n$declaration",
            ).size

    @Test
    fun `a default value annotated as composable makes the parameter a composable lambda`() {
        assertEquals(1, naming("@Composable fun Button(text: () -> Unit = @Composable {}) {}"))
        assertEquals(1, naming("@Composable fun Button(text: (() -> Unit)? = @Composable {}) {}"))
    }

    @Test
    fun `an anonymous composable function default makes the parameter a composable lambda`() {
        assertEquals(1, naming("@Composable fun Button(text: () -> Unit = @Composable fun() {}) {}"))
    }

    @Test
    fun `a default value that is not composable leaves the parameter a plain lambda`() {
        assertEquals(0, naming("@Composable fun Button(text: () -> Unit = {}) {}"))
        assertEquals(0, naming("@Composable fun Button(text: (() -> Unit)? = null) {}"))
    }

    @Test
    fun `a parameter that is not a function type is ignored`() {
        assertEquals(0, naming("@Composable fun Button(text: String = \"\", count: Int?) {}"))
    }
}
