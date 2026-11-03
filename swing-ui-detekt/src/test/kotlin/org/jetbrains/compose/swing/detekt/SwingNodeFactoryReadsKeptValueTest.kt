package org.jetbrains.compose.swing.detekt

import org.jetbrains.compose.swing.detekt.SwingNodeFactoryReadsDeclaredValueTest.Companion.inShow
import org.jetbrains.compose.swing.detekt.SwingNodeFactoryReadsDeclaredValueTest.Companion.lint
import org.jetbrains.compose.swing.detekt.SwingNodeFactoryReadsDeclaredValueTest.Companion.updaterSource
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals

class SwingNodeFactoryReadsKeptValueTest {
    @ParameterizedTest(name = "passes {0}")
    @MethodSource("passed")
    fun `does not report a factory that reads only what the node keeps`(
        label: String,
        code: String,
    ) {
        assertEquals(emptyList(), lint(code).textsIn(code), label)
    }

    companion object {
        private fun clean(
            label: String,
            body: String,
        ): Arguments = Arguments.of(label, inShow(body))

        private fun cleanFile(
            label: String,
            code: String,
        ): Arguments = Arguments.of(label, code.trimIndent())

        @JvmStatic
        fun passed(): List<Arguments> =
            passedInvocations() +
                passedLocals() +
                passedMembers() +
                passedUpdateAndKey() +
                passedReconcile() +
                passedWritesAndReturns() +
                passedShadowing()

        private fun passedInvocations(): List<Arguments> =
            listOf(
                forwarded("a forwarded factory parameter", "factory()"),
                forwarded("a factory invoked before a chained call", "factory().also(::println)"),
                forwarded("a factory invoked explicitly", "factory.invoke()"),
                forwarded("a parenthesized factory invoked as the callee", "(factory)()"),
                forwarded("a parenthesized factory invoked explicitly", "(factory).invoke()"),
                forwarded(
                    "an explicitly invoked factory alias",
                    "factory.invoke()",
                    "typealias Factory = () -> JPanel",
                    "Factory",
                ),
            )

        private fun forwarded(
            label: String,
            factoryBody: String,
            declaration: String = "",
            type: String = "() -> JPanel",
        ): Arguments =
            cleanFile(
                label,
                """
                package sample

                import androidx.compose.runtime.Composable
                import javax.swing.JPanel
                import org.jetbrains.compose.swing.node.SwingNode

                $declaration

                @Composable
                fun show(factory: $type) {
                    SwingNode(factory = { $factoryBody })
                }
                """,
            )

        private fun passedLocals(): List<Arguments> =
            listOf(
                clean("a remembered local", "val view = remember { JPanel() }\nSwingNode(factory = { view })"),
                clean(
                    "a local remembered with keys",
                    "val view = remember(panel) { JPanel() }\nSwingNode(factory = { view })",
                ),
                clean(
                    "a local delegated to remember",
                    "val view by remember { mutableStateOf(JPanel()) }\nSwingNode(factory = { view })",
                ),
                clean(
                    "a rememberMirrorState local",
                    "val mirror = rememberMirrorState(JPanel())\nSwingNode(factory = { JLabel(mirror.toString()) })",
                ),
                clean(
                    "a rememberUpdatedState local",
                    "val latest = rememberUpdatedState(panel)\nSwingNode(factory = { JLabel(latest.value.name) })",
                ),
                clean(
                    "a by-delegate local",
                    "val count by mutableStateOf(0)\nSwingNode(factory = { JLabel(count.toString()) })",
                ),
                clean("a plain val", "val view = JPanel()\nSwingNode(factory = { view })"),
                clean(
                    "a value the factory declares",
                    "SwingNode(factory = { val view = JPanel(); view })",
                ),
            )

        private fun passedMembers(): List<Arguments> =
            listOf(
                cleanFile(
                    "a class property",
                    """
                    package sample

                    import androidx.compose.runtime.Composable
                    import javax.swing.JLabel
                    import org.jetbrains.compose.swing.node.SwingNode

                    class Mine {
                        private val title = "Title"

                        @Composable
                        fun mount(panel: Int) {
                            SwingNode(factory = { JLabel(title) })
                        }
                    }
                    """,
                ),
                clean(
                    "an anonymous object's own member named like a parameter",
                    "SwingNode(factory = { object { val panel = JLabel(\"x\") }.panel })",
                ),
                cleanFile(
                    "a top-level constant inside a composable",
                    """
                    package sample

                    import androidx.compose.runtime.Composable
                    import javax.swing.JLabel
                    import org.jetbrains.compose.swing.node.SwingNode

                    private const val TITLE = "Title"

                    @Composable
                    fun show() {
                        SwingNode(factory = { JLabel(TITLE) })
                    }
                    """,
                ),
                cleanFile(
                    "a plain fun that is not composable",
                    """
                    package sample

                    import javax.swing.JPanel
                    import org.jetbrains.compose.swing.node.SwingNode

                    fun show(panel: JPanel) {
                        SwingNode(factory = { panel })
                    }
                    """,
                ),
            )

        private fun passedUpdateAndKey(): List<Arguments> =
            listOf(
                clean(
                    "a value applied by set",
                    "SwingNode(factory = { JLabel(panel.name) }, update = { set(panel) { } })",
                ),
                clean(
                    "a value applied by update",
                    "SwingNode(factory = { JLabel(panel.name) }, update = { update(panel) { } })",
                ),
                clean(
                    "a value applied by update passed positionally",
                    "SwingNode({ JLabel(panel.name) }, { set(panel) { } })",
                ),
                clean(
                    "a value applied by a parenthesized update lambda",
                    "SwingNode(factory = { JLabel(panel.name) }, update = ({ set(panel) { } }))",
                ),
                clean(
                    "a value applied by set with a named argument",
                    "SwingNode(factory = { JLabel(panel.name) }, update = { set(value = panel) { } })",
                ),
                clean(
                    "a value applied by set on an explicit this",
                    "SwingNode(factory = { JLabel(panel.name) }, update = { this.set(panel) { } })",
                ),
                clean(
                    "a value applied by set on a labeled this",
                    "SwingNode(factory = { JLabel(panel.name) }, update = { this@SwingNode.set(panel) { } })",
                ),
                clean(
                    "a lambda parameter named by an enclosing key",
                    "listOf(panel).forEach { item -> key(item) { SwingNode(factory = { item }) } }",
                ),
                clean("a value wrapped in key", "key(panel) { SwingNode(factory = { JLabel(panel.name) }) }"),
                clean(
                    "a value wrapped in key with a named block",
                    "key(panel, block = { SwingNode(factory = { JLabel(panel.name) }) })",
                ),
                cleanFile(
                    "a value wrapped in an imported-alias key",
                    """
                    package sample

                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.key as keyed
                    import javax.swing.JLabel
                    import javax.swing.JPanel
                    import org.jetbrains.compose.swing.node.SwingNode

                    @Composable
                    fun show(panel: JPanel) {
                        keyed(panel) { SwingNode(factory = { JLabel(panel.name) }) }
                    }
                    """,
                ),
            )

        private fun passedReconcile(): List<Arguments> =
            listOf(
                cleanFile(
                    "a value read in a direct reconcile block is applied",
                    updaterSource("SwingNode(factory = { JLabel(text) }, update = { reconcile { this.text = text } })"),
                ),
                cleanFile(
                    "a nested read in a direct reconcile block on this is applied",
                    updaterSource(
                        "SwingNode(factory = { JLabel(text) }, " +
                            "update = { this.reconcile { if (flag) this.text = text } })",
                    ),
                ),
                cleanFile(
                    "a value read in a direct reconcileWithChildren block is applied",
                    updaterSource(
                        "SwingNode(factory = { JLabel(text) }, " +
                            "update = { reconcileWithChildren { component.text = text } })",
                    ),
                ),
                cleanFile(
                    "a nested read in a direct reconcileWithChildren block on this is applied",
                    updaterSource(
                        "SwingNode(factory = { JLabel(text) }, " +
                            "update = { this.reconcileWithChildren { if (flag) component.text = text } })",
                    ),
                ),
            )

        private fun passedWritesAndReturns(): List<Arguments> =
            listOf(
                clean(
                    "a value set before a return from update",
                    "SwingNode(factory = { JLabel(panel.name) }, update = { set(panel) { }; return@SwingNode })",
                ),
                clean(
                    "a value set on a parenthesized this",
                    "SwingNode(factory = { JLabel(panel.name) }, update = { (this).set(panel) { } })",
                ),
                clean(
                    "a value set on a parenthesized labeled this",
                    "SwingNode(factory = { JLabel(panel.name) }, update = { (this@SwingNode).set(panel) { } })",
                ),
                clean(
                    "a value set on a safe-qualified this",
                    "SwingNode(factory = { JLabel(panel.name) }, update = { this?.set(panel) { } })",
                ),
                clean(
                    "a parenthesized set statement",
                    "SwingNode(factory = { JLabel(panel.name) }, update = { (set(panel) { }) })",
                ),
                clean(
                    "a labeled set statement",
                    "SwingNode(factory = { JLabel(panel.name) }, update = { l@ set(panel) { } })",
                ),
                clean(
                    "a return targeting the set block",
                    "SwingNode(factory = { JLabel(panel.name) }, update = { set(panel) { return@set } })",
                ),
                clean(
                    "a return targeting an earlier set block",
                    "SwingNode(factory = { JLabel(panel.name) }, update = { " +
                        "set(Unit) { return@set }; set(panel) { } })",
                ),
                clean(
                    "a return targeting a local function",
                    "SwingNode(factory = { JLabel(panel.name) }, update = { " +
                        "fun local() { return }; set(panel) { } })",
                ),
                clean(
                    "a return targeting an object method",
                    "SwingNode(factory = { JLabel(panel.name) }, update = { " +
                        "object : Runnable { override fun run() { return } }; set(panel) { } })",
                ),
                clean(
                    "a return targeting an inner lambda with the update label",
                    "SwingNode(factory = { JLabel(panel.name) }, update = { " +
                        "run SwingNode@{ return@SwingNode }; set(panel) { } })",
                ),
            )

        private fun passedShadowing(): List<Arguments> =
            listOf(
                clean(
                    "a destructured lambda parameter",
                    "listOf(panel to 1).forEach { (view, _) -> SwingNode(factory = { JLabel(view.name) }) }",
                ),
                cleanFile(
                    "a function annotated with a Composable from another package",
                    """
                    package sample

                    import javax.swing.JPanel
                    import org.jetbrains.compose.swing.node.SwingNode
                    import other.Composable

                    @Composable
                    fun show(panel: JPanel) {
                        SwingNode(factory = { panel })
                    }
                    """,
                ),
                cleanFile(
                    "a scope function's it inside the factory when the composable has a parameter named it",
                    """
                    package sample

                    import androidx.compose.runtime.Composable
                    import javax.swing.JPanel
                    import org.jetbrains.compose.swing.node.SwingNode

                    @Composable
                    fun show(it: JPanel) {
                        SwingNode(factory = { JPanel().also { it.name = "x" } })
                    }
                    """,
                ),
            )
    }
}
