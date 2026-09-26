package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.assertCompiled
import org.jetbrains.compose.swing.assertRejected
import org.jetbrains.compose.swing.test.InProcessCompilerHarness
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test

/**
 * Pins that the content of every container with a scope of its own hides the `weight` of the row around
 * it. Content without a receiver passes the enclosing scopes through, as in androidx.
 *
 * A caller cannot be shown a rejected program by a running test, so these compile one with the official
 * Kotlin compiler driven in-process and read the diagnostics it emits.
 */
class ContainerContentScopeCompilationTest {
    @Test
    fun aSwingContainersContentInsideARowHidesItsWeight() {
        // Containers of Swing components, with `%s` standing for the child they hold.
        val swingHosts =
            listOf(
                "Panel { %s }",
                "Panel(PanelLayout.Border()) { %s }",
                "Layer { %s }",
                "ScrollPane { %s }",
                "SplitPane { %s }",
                "TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) { %s }",
                "LayeredPane { %s }",
                "DesktopPane { %s }",
                "Window(onCloseRequest = {}) { %s }",
                "Dialog(onCloseRequest = {}) { %s }",
                "ListBox(items = listOf(\"x\"), itemContent = { %s })",
                "Table(rows = listOf(\"x\")) { column(header = \"h\", cellContent = { %s }) { it } }",
                "Tree(root = \"x\", children = { emptyList() }, nodeContent = { %s })",
            )
        assertWeightHiddenInEveryHost(
            swingHosts,
            child = "Label(\"x\", modifier = SwingModifier.%s)",
        )
    }

    @Test
    fun aHostWithoutAReceiverKeepsTheEnclosingScopesInReach() {
        compile(
            """
            @Composable
            fun Nested() {
                Layer { GlassPane { Label("x", modifier = SwingModifier.view()) } }
                DesktopPane {
                    InternalFrame(title = "a", bounds = Rectangle(), onClose = {}) {
                        InternalFrame(title = "b", bounds = Rectangle(), onClose = {}) {}
                    }
                }
                TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                    Label("a", modifier = SwingModifier.tab("a", header = { Label("h", modifier = SwingModifier.tab("h")) }))
                }
                Window(onCloseRequest = {}) { GlassPane { GlassPane {} } }
                Window(onCloseRequest = {}) { MenuBar { MenuBar {} } }
                PopupMenu(anchor = rememberPopupAnchor(), expanded = true, onDismiss = {}) {
                    Menu("m") { MenuItem("x", onClick = {}, modifier = SwingModifier.$EXTRA) }
                }
                PopupMenu(anchor = rememberPopupAnchor(), expanded = true, onDismiss = {}) {
                    MenuNode(factory = { JMenu() }) { MenuItem("x", onClick = {}, modifier = SwingModifier.$EXTRA) }
                }
            }
            """,
        ).assertCompiled()
    }

    @Test
    fun aWrapperHostingNoChildrenPassesTheRowsScopeThrough() {
        compile(
            """
            @Composable
            fun Wrapped() {
                Row {
                    ProvideComponentDefaults { Label("x", modifier = SwingModifier.weight(1f).$EXTRA) }
                    key(1) { Label("y", modifier = SwingModifier.weight(1f).$EXTRA) }
                }
            }
            """,
        ).assertCompiled()
    }

    @Test
    fun aHostsOwnDeclarationsAndTheApplicationStayReachableInItsContent() {
        compile(
            """
            fun main() =
                application {
                    Window(onCloseRequest = ::exitApplication) {
                        MenuBar { Menu("File") { MenuItem("Quit", onClick = ::exitApplication) } }
                        GlassPane { Label("Busy") }
                        Row { Button("Quit", onClick = ::exitApplication, modifier = SwingModifier.weight(1f)) }
                    }
                }
            """,
        ).assertCompiled()
    }

    private companion object {
        const val EXTRA = "enabled(true)"

        /**
         * Resolves the compiler plugin classpath once at startup, so a test task that does not hand the
         * harness one reports a single failure here rather than the same failure in every case below.
         */
        @JvmStatic
        @BeforeAll
        fun verifyComposePluginClasspathAvailable() {
            InProcessCompilerHarness.resolveComposePluginClasspath()
        }

        /**
         * Asserts, in one compile, that every one of [hosts] inside a `Row` rejects the row's `weight` declared
         * on [child], a template taking the modifier call.
         */
        fun assertWeightHiddenInEveryHost(
            hosts: List<String>,
            child: String,
        ) {
            fun snippet(modifier: String) =
                hosts.withIndex().joinToString("\n\n") { (index, host) ->
                    fun hosted() = host.replace("%s", child.replace("%s", modifier))
                    """
                    @Composable
                    fun Declared$index() {
                        Row { ${hosted()} }
                    }
                    """.trimIndent()
                }
            compile(snippet("weight(1f)")).assertRejected(hosts.map { "weight" })
        }

        fun compile(declarations: String) =
            InProcessCompilerHarness.compileSnippet(
                "ContainerContentScopeSnippet.kt",
                """
                import androidx.compose.runtime.Composable
                import androidx.compose.runtime.key
                import org.jetbrains.compose.swing.components.*
                import org.jetbrains.compose.swing.components.button.*
                import org.jetbrains.compose.swing.components.desktop.*
                import org.jetbrains.compose.swing.components.layout.*
                import org.jetbrains.compose.swing.components.menu.*
                import org.jetbrains.compose.swing.components.selection.*
                import org.jetbrains.compose.swing.defaults.*
                import org.jetbrains.compose.swing.foundation.layout.*
                import org.jetbrains.compose.swing.modifier.SwingModifier
                import org.jetbrains.compose.swing.modifier.interaction.*
                import org.jetbrains.compose.swing.node.*
                import org.jetbrains.compose.swing.window.*
                import java.awt.Rectangle
                import java.awt.image.BufferedImage
                import javax.swing.JMenu
                import javax.swing.JPanel

                """.trimIndent() + declarations.trimIndent(),
            )
    }
}
