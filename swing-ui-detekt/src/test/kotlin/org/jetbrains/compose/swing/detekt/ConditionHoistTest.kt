// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.SourceLocation
import dev.detekt.test.TestConfig
import org.jetbrains.compose.swing.detekt.composerules.assertStartSourceLocations
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConditionHoistTest {
    @Test
    fun `reports default Swing layouts and configured custom layouts at the condition`() {
        val source =
            """
            import androidx.compose.runtime.Composable
            import org.jetbrains.compose.swing.components.Label as Text
            import org.jetbrains.compose.swing.components.layout.Panel
            import org.jetbrains.compose.swing.foundation.layout.Column as Stack
            import org.jetbrains.compose.swing.foundation.layout.Box
            import org.jetbrains.compose.swing.foundation.layout.Row

            @Composable fun Custom(content: @Composable () -> Unit) { content() }

            @Composable fun Example(show: Boolean) {
                Stack { if (show) Text("column") }
                Panel { if (show) Text("panel") }
                Custom { if (show) Text("custom") }
                Box { if (show) Text("box") }
                Row { if (show) Child() }
            }

            @Composable fun Child() { Text("wrapper") }
            """.trimIndent()

        val config = TestConfig("contentEmitters" to listOf("Custom"))
        val findings = ComposeSwingRuleLint.lint("ConditionHoist", source, config, label, panel, column)

        assertStartSourceLocations(
            findings,
            SourceLocation(11, 13),
            SourceLocation(12, 13),
            SourceLocation(13, 14),
            SourceLocation(14, 11),
            SourceLocation(15, 11),
        )
        assertEquals(
            "This layout stays mounted when the condition for its only content is false. " +
                "An empty Swing container can still take up space. " +
                "Move the condition outside the layout if the container should appear only with its content.",
            findings.first().message,
        )
    }

    @Test
    fun `keeps required containers and rejects unrelated layout names and non UI branches`() {
        val source =
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.SideEffect
            import org.jetbrains.compose.swing.components.Label
            import org.jetbrains.compose.swing.components.layout.Panel
            import org.jetbrains.compose.swing.foundation.layout.Column as Stack
            import org.jetbrains.compose.swing.node.MenuNode
            import org.jetbrains.compose.swing.node.SwingNode
            import javax.swing.JPanel
            import javax.swing.JMenu

            @Composable fun Column(content: @Composable () -> Unit) { content() }
            @Composable fun WebView(content: @Composable () -> Unit) { content() }
            @Composable fun Label() {}
            @Composable fun Example(show: Boolean) {
                Column { if (show) Label("same short name") }
                WebView { if (show) Label("unrelated") }
                SwingNode(factory = { JPanel() }) { if (show) Label("swing bridge") }
                MenuNode(factory = { JMenu() }) { if (show) Label("menu bridge") }
                Stack { if (show) SideEffect {} }
                Stack { if (show) Label() }
                Panel(style = {}) { if (show) Label("styled") }
            }
            """.trimIndent()

        val findings = ComposeSwingRuleLint.lint("ConditionHoist", source, Config.empty, label, panel, column, nodes)

        assertTrue(findings.isEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = ["Custom", "sample.Custom"])
    fun `resolves configured aliases and emission through providers`(configuredName: String) {
        val source =
            """
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.CompositionLocalProvider
            import androidx.compose.runtime.compositionLocalOf
            import org.jetbrains.compose.swing.components.Label
            import org.jetbrains.compose.swing.foundation.layout.Column
            import sample.Custom as Wrapper

            val LocalFlag = compositionLocalOf { false }

            @Composable fun Example(show: Boolean) {
                Wrapper { if (show) Label("wrapped") }
                Column {
                    if (show) {
                        CompositionLocalProvider(LocalFlag provides true) {
                            Label("provided")
                        }
                    }
                }
                Column {
                    if (show) {
                        CompositionLocalProvider(LocalFlag provides true) {}
                    }
                }
            }
            """.trimIndent()

        val config = TestConfig("contentEmitters" to listOf(configuredName))
        val findings = ComposeSwingRuleLint.lint("ConditionHoist", source, config, label, column, customLayout)

        assertStartSourceLocations(findings, SourceLocation(11, 15), SourceLocation(13, 9))
    }

    @Test
    fun `requires emission from the selected same file overload`() {
        val source =
            """
            package sample

            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.SideEffect
            import org.jetbrains.compose.swing.components.Label
            import org.jetbrains.compose.swing.foundation.layout.Column

            @Composable fun Child(value: String) { Label(value) }
            @Composable fun Child(value: Int) { SideEffect {} }

            @Composable fun Example(show: Boolean) {
                Column { if (show) Child("visible") }
                Column { if (show) Child(0) }
            }
            """.trimIndent()

        val findings = ComposeSwingRuleLint.lint("ConditionHoist", source, Config.empty, label, column)

        assertStartSourceLocations(findings, SourceLocation(12, 14))
    }

    private val label =
        """
        package org.jetbrains.compose.swing.components

        import androidx.compose.runtime.Composable

        @Composable fun Label(text: String) {}
        """.trimIndent()

    private val column =
        """
        package org.jetbrains.compose.swing.foundation.layout

        import androidx.compose.runtime.Composable

        interface ColumnScope
        interface BoxScope
        interface RowScope

        @Composable inline fun Column(crossinline content: @Composable ColumnScope.() -> Unit) {}
        @Composable inline fun Box(crossinline content: @Composable BoxScope.() -> Unit) {}
        @Composable inline fun Row(crossinline content: @Composable RowScope.() -> Unit) {}
        """.trimIndent()

    private val panel =
        """
        package org.jetbrains.compose.swing.components.layout

        import androidx.compose.runtime.Composable

        interface PanelScope
        fun interface PanelStyle { fun apply() }

        @Composable fun Panel(
            style: PanelStyle = PanelStyle {},
            content: @Composable PanelScope.() -> Unit,
        ) {}
        """.trimIndent()

    private val nodes =
        """
        package org.jetbrains.compose.swing.node

        import androidx.compose.runtime.Composable
        import java.awt.Component

        @Composable inline fun <reified T : Component> SwingNode(
            noinline factory: () -> T,
            crossinline content: @Composable () -> Unit,
        ) {}

        @Composable inline fun <reified T : Component> MenuNode(
            noinline factory: () -> T,
            content: @Composable () -> Unit = {},
        ) {}
        """.trimIndent()

    private val customLayout =
        """
        package sample

        import androidx.compose.runtime.Composable

        @Composable fun Custom(content: @Composable () -> Unit) { content() }
        """.trimIndent()
}
