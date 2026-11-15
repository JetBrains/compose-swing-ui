// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt.composerules

import dev.detekt.api.Config
import org.jetbrains.compose.swing.detekt.toDetektRule
import org.jetbrains.compose.swing.rules.runtime.MissingNonRestartableComposable
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertTrue

class MissingNonRestartableComposablePreviewTest {
    private class Case(
        val name: String,
        val declarations: String,
        val annotation: String,
        val reported: Boolean,
    )

    private val lookAlike =
        """
        package com.example.other

        annotation class Preview
        """.trimIndent()

    private val cases =
        listOf(
            Case("a swing preview", "", "@org.jetbrains.compose.swing.tooling.Preview", reported = false),
            Case(
                "a swing preview through a meta-annotation",
                "@org.jetbrains.compose.swing.tooling.Preview annotation class SwingVariants",
                "@SwingVariants",
                reported = false,
            ),
            Case("a look-alike preview from another package", "", "@com.example.other.Preview", reported = true),
        )

    @TestFactory
    fun `ignoresPreviews recognizes real Swing previews`(): List<DynamicTest> =
        cases.map { case ->
            dynamicTest(case.name) {
                val code =
                    codeWithFakeCompose(
                        """
                        ${case.declarations}

                        @Composable
                        fun Content() {}

                        ${case.annotation}
                        @Composable
                        fun Forwarding() = Content()
                        """.trimIndent(),
                    )

                val findings =
                    MissingNonRestartableComposable
                        .toDetektRule(Config.empty)
                        .lintWithAnalysisApi(code, swingPreviewDependency, lookAlike)

                if (case.reported) {
                    assertTextLocations(findings, "Forwarding")
                } else {
                    assertTrue(findings.isEmpty())
                }
            }
        }
}

internal val swingPreviewDependency: String =
    """
    package org.jetbrains.compose.swing.tooling

    annotation class Preview
    """.trimIndent()
