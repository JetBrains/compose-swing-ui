// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0
// Assertions use kotlin.test in place of AssertJ.

package org.jetbrains.compose.swing.detekt.composerules

import dev.detekt.api.Config
import dev.detekt.api.SourceLocation
import dev.detekt.test.TestConfig
import org.jetbrains.compose.swing.detekt.toDetektRule
import org.jetbrains.compose.swing.rules.content.ConditionHoist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Upstream compose-rules structure, kept so later releases replay onto the fork without restructuring.
@Suppress("LargeClass", "LongMethod")
class ConditionHoistCheckTest {
    private val rule = ConditionHoist.toDetektRule(Config.empty)

    @Test
    fun `reports layout with a single conditional composable child`() {
        val findings =
            rule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.foundation.layout.Column
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean) {
                    Column {
                        if (showText) {
                            Text("Hello")
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertEquals(1, findings.size)
        assertEquals(SourceLocation(10, 9), findings[0].entity.location.source)
    }

    @Test
    fun `reports layout with a single expression conditional composable child`() {
        val findings =
            rule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.foundation.layout.Column
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean) {
                    Column {
                        if (showText) Text("Hello")
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertEquals(1, findings.size)
        assertEquals(SourceLocation(10, 9), findings[0].entity.location.source)
    }

    @Test
    fun `reports expression body layout with a single conditional composable child`() {
        val findings =
            rule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.foundation.layout.Column
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean) = Column {
                    if (showText) {
                        Text("Hello")
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertEquals(1, findings.size)
        assertEquals(SourceLocation(9, 5), findings[0].entity.location.source)
    }

    @Test
    fun `reports property getter layout with a single conditional composable child`() {
        val findings =
            rule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.foundation.layout.Column
                import org.jetbrains.compose.swing.components.Label as Text

                val Content
                    @Composable get() = Column {
                        if (showText()) {
                            Text("Hello")
                        }
                    }

                fun showText(): Boolean = true
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertEquals(1, findings.size)
        assertEquals(SourceLocation(9, 9), findings[0].entity.location.source)
    }

    @Test
    fun `does not report conditional with composable else branch`() {
        val findings =
            rule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.foundation.layout.Column
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean) {
                    Column {
                        if (showText) {
                            Text("Hello")
                        } else {
                            Text("Fallback")
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report conditional with single expression composable else branch`() {
        val findings =
            rule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.foundation.layout.Column
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean) {
                    Column {
                        if (showText) {
                            Text("Hello")
                        } else Text("Fallback")
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report conditional with composable lambda parameter in else branch`() {
        val findings =
            rule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.BasicTextField
                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.foundation.layout.Column
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean) {
                    BasicTextField { innerTextField ->
                        Column {
                            if (showText) {
                                Text("Hint")
                            } else {
                                innerTextField()
                            }
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports conditional when else branch only declares deferred composable content`() {
        val findings =
            rule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.foundation.layout.Column
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean) {
                    Column {
                        if (showText) {
                            Text("Hello")
                        } else {
                            val content: @Composable () -> Unit = {
                                Text("Fallback")
                            }
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertEquals(1, findings.size)
        assertEquals(SourceLocation(10, 9), findings[0].entity.location.source)
    }

    @Test
    fun `does not report conditional that only declares deferred composable content`() {
        val findings =
            rule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.foundation.layout.Column
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean) {
                    Column {
                        if (showText) {
                            val content: @Composable () -> Unit = {
                                Text("Hello")
                            }
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report conditional in one slot when call has another composable slot`() {
        val customRule = ConditionHoist.toDetektRule(TestConfig("contentEmitters" to listOf("ListItem")))
        val findings =
            customRule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import com.example.compose.fake.Icon
                import com.example.compose.fake.ListItem
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showIcon: Boolean) {
                    ListItem(
                        headlineContent = {
                            Text("Title")
                        },
                        trailingContent = {
                            if (showIcon) {
                                Icon()
                            }
                        },
                    )
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report conditional in a single slot call with semantic arguments`() {
        val customRule = ConditionHoist.toDetektRule(TestConfig("contentEmitters" to listOf("AnimatedVisibility")))
        val findings =
            customRule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.AnimatedVisibility
                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(expanded: Boolean, showHint: Boolean) {
                    AnimatedVisibility(visible = expanded) {
                        if (showHint) {
                            Text("Hint")
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report conditional inside a non layout wrapper`() {
        val findings =
            rule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.AppTheme
                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean) {
                    AppTheme {
                        if (showText) {
                            Text("Hello")
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports configured custom layout with a single conditional composable child`() {
        val customRule = ConditionHoist.toDetektRule(TestConfig("contentEmitters" to listOf("DesignColumn")))
        val findings =
            customRule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import com.example.compose.fake.DesignColumn
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean) {
                    DesignColumn {
                        if (showText) {
                            Text("Hello")
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertEquals(1, findings.size)
        assertEquals(SourceLocation(10, 9), findings[0].entity.location.source)
    }

    @Test
    fun `keeps default layouts when custom content emitters are configured`() {
        val customRule = ConditionHoist.toDetektRule(TestConfig("contentEmitters" to listOf("DesignColumn")))
        val findings =
            customRule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.foundation.layout.Column
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean) {
                    Column {
                        if (showText) {
                            Text("Hello")
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertEquals(1, findings.size)
        assertEquals(SourceLocation(10, 9), findings[0].entity.location.source)
    }

    @Test
    fun `does not report conditional with composable siblings in the same layout`() {
        val findings =
            rule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.foundation.layout.Column
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean) {
                    Column {
                        Text("Header")
                        if (showText) {
                            Text("Hello")
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report conditional with non composable siblings in the same layout`() {
        val findings =
            rule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.foundation.layout.Column
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean) {
                    Column {
                        val label = "Hello"
                        if (showText) {
                            Text(label)
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report conditional nested in non-layout content`() {
        val findings =
            rule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.foundation.layout.Column
                import org.jetbrains.compose.swing.components.Label as Text

                data class Item(val name: String, val visible: Boolean)

                @Composable
                fun Content(items: List<Item>) {
                    Column {
                        items.forEach { item ->
                            if (item.visible) {
                                Text(item.name)
                            }
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report conditional that depends on a composable receiver scope`() {
        val customRule = ConditionHoist.toDetektRule(TestConfig("contentEmitters" to listOf("BoxWithConstraints")))
        val findings =
            customRule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.BoxWithConstraints
                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content() {
                    BoxWithConstraints {
                        if (maxWidth > 600) {
                            Text("Wide")
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report simple conditional that depends on a composable receiver scope`() {
        val customRule = ConditionHoist.toDetektRule(TestConfig("contentEmitters" to listOf("BoxWithConstraints")))
        val findings =
            customRule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.BoxWithConstraints
                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content() {
                    BoxWithConstraints {
                        if (shouldShow) {
                            Text("Visible")
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report conditional that calls a composable receiver scope function`() {
        val customRule = ConditionHoist.toDetektRule(TestConfig("contentEmitters" to listOf("BoxWithConstraints")))
        val findings =
            customRule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.BoxWithConstraints
                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content() {
                    BoxWithConstraints {
                        if (shouldShow()) {
                            Text("Visible")
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report conditional that depends on receiver scope extension property`() {
        val customRule = ConditionHoist.toDetektRule(TestConfig("contentEmitters" to listOf("BoxWithConstraints")))
        val findings =
            customRule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.BoxWithConstraints
                import com.example.compose.fake.BoxWithConstraintsScope
                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.components.Label as Text

                val BoxWithConstraintsScope.isExpanded: Boolean
                    get() = maxWidth > 600

                @Composable
                fun Content() {
                    BoxWithConstraints {
                        if (isExpanded) {
                            Text("Visible")
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report conditional that calls a receiver scope extension function`() {
        val customRule = ConditionHoist.toDetektRule(TestConfig("contentEmitters" to listOf("BoxWithConstraints")))
        val findings =
            customRule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.BoxWithConstraints
                import com.example.compose.fake.BoxWithConstraintsScope
                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.components.Label as Text

                fun BoxWithConstraintsScope.isExpanded(): Boolean = maxWidth > 600

                @Composable
                fun Content() {
                    BoxWithConstraints {
                        if (isExpanded()) {
                            Text("Visible")
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports scoped layout conditional that does not depend on receiver scope`() {
        val customRule = ConditionHoist.toDetektRule(TestConfig("contentEmitters" to listOf("ScopedColumn")))
        val findings =
            customRule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import com.example.compose.fake.ScopedColumn
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean) {
                    ScopedColumn {
                        if (showText) {
                            Text("Hello")
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertEquals(1, findings.size)
        assertEquals(SourceLocation(10, 9), findings[0].entity.location.source)
    }

    @Test
    fun `does not report conditional in a slot lambda with parameters`() {
        val customRule = ConditionHoist.toDetektRule(TestConfig("contentEmitters" to listOf("Items")))
        val findings =
            customRule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import com.example.compose.fake.Items
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean) {
                    Items(listOf("Hello")) { item ->
                        if (showText) {
                            Text(item)
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report conditional in a slot lambda with inferred parameters`() {
        val customRule = ConditionHoist.toDetektRule(TestConfig("contentEmitters" to listOf("Scaffold")))
        val findings =
            customRule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import com.example.compose.fake.Scaffold
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean) {
                    Scaffold {
                        if (showText) {
                            Text("Hello")
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report layout with explicit ignored arguments`() {
        val findings =
            rule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.foundation.layout.Column
                import org.jetbrains.compose.swing.foundation.layout.Modifier
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean, modifier: Modifier) {
                    Column(modifier = modifier) {
                        if (showText) {
                            Text("Hello")
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report layout with positional explicit ignored arguments`() {
        val findings =
            rule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.foundation.layout.Column
                import org.jetbrains.compose.swing.foundation.layout.Modifier
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean, modifier: Modifier) {
                    Column(modifier) {
                        if (showText) {
                            Text("Hello")
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report conditionals outside composable functions`() {
        val findings =
            rule.lintWithAnalysisApi(
                """
                package com.example

                import org.jetbrains.compose.swing.foundation.layout.Column
                import org.jetbrains.compose.swing.components.Label as Text

                fun Content(showText: Boolean) {
                    Column {
                        if (showText) {
                            Text("Hello")
                        }
                    }
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
                allowCompilationErrors = true,
            )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports local composable function conditionals only once`() {
        val findings =
            rule.lintWithAnalysisApi(
                """
                package com.example

                import com.example.compose.fake.Composable
                import org.jetbrains.compose.swing.foundation.layout.Column
                import org.jetbrains.compose.swing.components.Label as Text

                @Composable
                fun Content(showText: Boolean) {
                    @Composable
                    fun LocalContent() {
                        Column {
                            if (showText) {
                                Text("Hello")
                            }
                        }
                    }

                    LocalContent()
                }
                """.trimIndent(),
                *fakeLayoutRuntime(),
            )

        assertEquals(1, findings.size)
        assertEquals(SourceLocation(12, 13), findings[0].entity.location.source)
    }

    private fun fakeLayoutRuntime(): Array<String> =
        arrayOf(
            conditionHoistFoundationLayout,
            conditionHoistLabel,
            codeWithFakeCompose(
                """
                import org.jetbrains.compose.swing.foundation.layout.Modifier

                interface ColumnScope

                @Composable
                fun ScopedColumn(
                    modifier: Modifier = Modifier,
                    content: @Composable ColumnScope.() -> Unit,
                ) {
                    val scope = object : ColumnScope {
                    }
                    scope.content()
                }

                @Composable
                fun DesignColumn(
                    content: @Composable () -> Unit,
                ) {
                    content()
                }

                @Composable
                fun AppTheme(
                    content: @Composable () -> Unit,
                ) {
                    content()
                }

                @Composable
                fun Icon() {
                }

                @Composable
                fun ListItem(
                    headlineContent: @Composable () -> Unit,
                    trailingContent: @Composable () -> Unit,
                ) {
                    headlineContent()
                    trailingContent()
                }

                @Composable
                fun AnimatedVisibility(
                    visible: Boolean,
                    content: @Composable () -> Unit,
                ) {
                    if (visible) {
                        content()
                    }
                }

                @Composable
                fun <T> Items(
                    items: List<T>,
                    itemContent: @Composable (T) -> Unit,
                ) {
                    items.forEach { itemContent(it) }
                }

                @Composable
                fun BasicTextField(
                    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit,
                ) {
                    decorationBox {}
                }

                interface PaddingValues

                @Composable
                fun Scaffold(
                    content: @Composable (PaddingValues) -> Unit,
                ) {
                    content(object : PaddingValues {
                    })
                }

                interface ParentScope {
                    val shouldShow: Boolean
                    fun shouldShow(): Boolean
                }

                interface BoxWithConstraintsScope : ParentScope {
                    val maxWidth: Int
                }

                @Composable
                fun BoxWithConstraints(
                    content: @Composable BoxWithConstraintsScope.() -> Unit,
                ) {
                    val scope = object : BoxWithConstraintsScope {
                        override val maxWidth: Int = 0
                        override val shouldShow: Boolean = false
                        override fun shouldShow(): Boolean = false
                    }
                    scope.content()
                }
                """.trimIndent(),
            ),
        )
}
