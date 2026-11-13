package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals

class SwingNodeUpdateRepeatsInvalidationTest {
    @ParameterizedTest(name = "reports {0}")
    @MethodSource("reported")
    fun `reports an invalidation that the writes before it already request`(
        label: String,
        code: String,
        positions: List<String>,
    ) {
        assertEquals(positions, lint(code).textsIn(code), label)
    }

    @ParameterizedTest(name = "passes {0}")
    @MethodSource("passed")
    fun `does not report an invalidation that adds a request`(
        label: String,
        code: String,
    ) {
        assertEquals(emptyList(), lint(code).textsIn(code), label)
    }

    @Test
    fun `names the call, the property, the setter and the way out`() {
        val finding = lint(source("JLabel", "set(f) { font = f; revalidate() }")).single()

        assertEquals(
            "This `revalidate` call repeats the layout request already made by `JComponent.setFont`. " +
                "It adds a second layout on every change. Remove the call.",
            finding.message,
        )
    }

    @Test
    fun `joins several writes and setters with and`() {
        val finding = lint(source("JLabel", "set(f) { font = it; text = \"a\"; revalidate() }")).single()

        assertEquals(
            "This `revalidate` call repeats the layout request already made by `JComponent.setFont` and " +
                "`JLabel.setText`. It adds a second layout on every change. Remove the call.",
            finding.message,
        )
    }

    @Test
    fun `names a property and a setter once when the property is written twice`() {
        val finding = lint(source("JLabel", "set(f) { font = it; font = f.deriveFont(12f); revalidate() }")).single()

        assertEquals(
            "This `revalidate` call repeats the layout request already made by `JComponent.setFont`. " +
                "It adds a second layout on every change. Remove the call.",
            finding.message,
        )
    }

    private fun lint(code: String) =
        SwingNodeUpdateRepeatsInvalidation(Config.empty)
            .lintWithContext(
                environment,
                code,
                COMPOSABLE_STUBS,
                NODE_UPDATER_STUBS,
                UPDATING_NODE_STUBS,
                """
                package other

                import javax.swing.JLabel
                import org.jetbrains.compose.swing.node.SwingNodeUpdater

                fun SwingNode(factory: () -> JLabel, update: SwingNodeUpdater<JLabel>.() -> Unit) {}
                """.trimIndent(),
            )

    companion object {
        private val environment = createEnvironment()

        private fun source(
            component: String,
            update: String,
            node: String = "SwingNode",
            factory: String = "{ $component() }",
        ): String =
            """
            package sample

            import androidx.compose.runtime.Composable
            import java.awt.Color
            import java.awt.Font
            import javax.swing.*
            import org.jetbrains.compose.swing.node.MenuNode
            import org.jetbrains.compose.swing.node.SwingNode

            class Gauge : JPanel() {
                var level: Int = 0
            }

            fun applyColumns(target: JComponent, n: Int): Int = n

            fun JLabel.applyGap(n: Int): Int {
                iconTextGap = n
                return n
            }

            @Composable
            fun show(f: Font, c: Color, n: Int, peer: JLabel) {
                $node(factory = $factory, update = { $update })
            }
            """.trimIndent()

        private fun case(
            label: String,
            component: String,
            update: String,
            vararg positions: String,
        ): Arguments = Arguments.of(label, source(component, update), positions.toList())

        private fun clean(
            label: String,
            component: String,
            update: String,
        ): Arguments = Arguments.of(label, source(component, update))

        @JvmStatic
        fun reported(): List<Arguments> = reportedLayout() + reportedRepaint() + reportedBlocks()

        private fun reportedLayout(): List<Arguments> =
            listOf(
                case(
                    "a revalidate after the font, with a local declared first",
                    "JLabel",
                    "set(n) { val base = f.deriveFont(n.toFloat()); font = base; revalidate() }",
                    "revalidate()",
                ),
                case("an invalidate after the font", "JLabel", "set(f) { font = f; invalidate() }", "invalidate()"),
                case(
                    "a revalidate on this after a font written through this",
                    "JLabel",
                    "set(f) { this.font = f; this.revalidate() }",
                    "this.revalidate()",
                ),
                case(
                    "a revalidate after the label text",
                    "JLabel",
                    "set(\"a\") { text = it; revalidate() }",
                    "revalidate()",
                ),
                case(
                    "a revalidate after the button text",
                    "JButton",
                    "set(\"a\") { text = it; revalidate() }",
                    "revalidate()",
                ),
                case(
                    "a revalidate after the font and the text",
                    "JLabel",
                    "set(f) { font = it; text = \"a\"; revalidate() }",
                    "revalidate()",
                ),
                case("a revalidate before the font", "JLabel", "set(f) { revalidate(); font = it }", "revalidate()"),
                case(
                    "a revalidate after a setter call",
                    "JLabel",
                    "set(f) { setFont(it); revalidate() }",
                    "revalidate()",
                ),
                case(
                    "a revalidate after a setter call on this",
                    "JLabel",
                    "set(f) { this.setFont(it); this.revalidate() }",
                    "this.revalidate()",
                ),
                case(
                    "a revalidate and a repaint after the font, each reported",
                    "JLabel",
                    "set(f) { font = it; revalidate(); repaint() }",
                    "revalidate()",
                    "repaint()",
                ),
                case(
                    "a revalidate and a repaint after the foreground, the repaint only",
                    "JLabel",
                    "set(c) { foreground = it; revalidate(); repaint() }",
                    "repaint()",
                ),
            )

        private fun reportedRepaint(): List<Arguments> =
            listOf(
                case("a repaint after the foreground", "JLabel", "set(c) { foreground = it; repaint() }", "repaint()"),
                case(
                    "a repaint after the foreground and the background",
                    "JPanel",
                    "set(c) { foreground = it; background = it; repaint() }",
                    "repaint()",
                ),
                case("a repaint after the font", "JLabel", "set(f) { font = it; repaint() }", "repaint()"),
                case("a repaint after the border", "JPanel", "set(null) { border = it; repaint() }", "repaint()"),
                case("a repaint after the label icon", "JLabel", "set(null) { icon = it; repaint() }", "repaint()"),
                case("a repaint after the button icon", "JButton", "set(null) { icon = it; repaint() }", "repaint()"),
                case(
                    "a repaint after the label alignment",
                    "JLabel",
                    "set(n) { horizontalAlignment = it; repaint() }",
                    "repaint()",
                ),
                case(
                    "a repaint after the button alignment",
                    "JButton",
                    "set(n) { horizontalAlignment = it; repaint() }",
                    "repaint()",
                ),
                case(
                    "a repaint with arguments",
                    "JLabel",
                    "set(c) { foreground = it; repaint(0, 0, 1, 1) }",
                    "repaint(0, 0, 1, 1)",
                ),
            )

        private fun reportedBlocks(): List<Arguments> =
            listOf(
                case(
                    "a call in an update block",
                    "JLabel",
                    "update(f) { font = it; revalidate() }",
                    "revalidate()",
                ),
                case(
                    "a call in each of two blocks",
                    "JLabel",
                    "set(f) { font = it; revalidate() }; set(c) { foreground = it; repaint() }",
                    "revalidate()",
                    "repaint()",
                ),
                Arguments.of(
                    "a call in a MenuNode",
                    source("JMenuItem", "set(f) { font = it; revalidate() }", node = "MenuNode"),
                    listOf("revalidate()"),
                ),
            )

        @JvmStatic
        fun passed(): List<Arguments> =
            passedInvalidationOnly() + passedUnlistedWrites() + passedOtherStatements() + passedOutsideBlocks()

        private fun passedInvalidationOnly(): List<Arguments> =
            listOf(
                clean(
                    "a revalidate after the foreground, which only repaints",
                    "JLabel",
                    "set(c) { foreground = it; revalidate() }",
                ),
                clean("an invalidate after the background", "JLabel", "set(c) { background = it; invalidate() }"),
                clean("a revalidate after the border", "JPanel", "set(null) { border = it; revalidate() }"),
                clean("a revalidate after the label icon", "JLabel", "set(null) { icon = it; revalidate() }"),
                clean("a revalidate after the button icon", "JButton", "set(null) { icon = it; revalidate() }"),
                clean(
                    "a revalidate after the label alignment",
                    "JLabel",
                    "set(n) { horizontalAlignment = it; revalidate() }",
                ),
                clean(
                    "a revalidate after the font and the foreground",
                    "JLabel",
                    "set(f) { font = it; foreground = null; revalidate() }",
                ),
            )

        private fun passedUnlistedWrites(): List<Arguments> =
            listOf(
                clean(
                    "a revalidate after the columns, which only invalidate",
                    "JTextField",
                    "set(n) { columns = it; revalidate() }",
                ),
                clean(
                    "a revalidate after the rows, which only invalidate",
                    "JTextArea",
                    "set(n) { rows = it; revalidate() }",
                ),
                clean(
                    "a revalidate after a property of a source class",
                    "Gauge",
                    "set(n) { level = it; revalidate() }",
                ),
                clean("a repaint after the columns", "JTextField", "set(n) { columns = it; repaint() }"),
                clean(
                    "a revalidate after the columns setter call",
                    "JTextField",
                    "set(n) { setColumns(it); revalidate() }",
                ),
                clean(
                    "a revalidate after the font and the columns",
                    "JTextField",
                    "set(f) { font = it; columns = n; revalidate() }",
                ),
                clean(
                    "a revalidate after a write to the opaque flag",
                    "JLabel",
                    "set(true) { isOpaque = it; revalidate() }",
                ),
            )

        private fun passedOtherStatements(): List<Arguments> =
            listOf(
                clean("a revalidate with no write", "JLabel", "set(n) { revalidate() }"),
                clean("a write with no invalidation", "JLabel", "set(f) { font = it }"),
                clean(
                    "a revalidate with a call beside the write",
                    "JLabel",
                    "set(f) { font = it; println(it); revalidate() }",
                ),
                clean(
                    "a revalidate after a write in a condition",
                    "JLabel",
                    "set(f) { if (n > 0) font = it; revalidate() }",
                ),
                clean("a revalidate on another receiver", "JLabel", "set(f) { font = it; peer.revalidate() }"),
                clean("a write on another receiver", "JLabel", "set(f) { peer.font = it; revalidate() }"),
                clean("a validate after the font", "JLabel", "set(f) { font = it; validate() }"),
                clean("a doLayout after the font", "JLabel", "set(f) { font = it; doLayout() }"),
                clean(
                    "a local whose initializer passes the component",
                    "JLabel",
                    "set(n) { val applied = applyColumns(this, n); font = f; revalidate() }",
                ),
                clean(
                    "a local whose initializer calls the component",
                    "JLabel",
                    "set(n) { val w = getWidth(); font = f; revalidate() }",
                ),
                clean("a compound assignment", "JLabel", "set(\"a\") { text += it; revalidate() }"),
                clean(
                    "a revalidate after a local whose extension call takes the component",
                    "JLabel",
                    "set(n) { val applied = applyGap(n); font = f; revalidate() }",
                ),
            )

        private fun passedOutsideBlocks(): List<Arguments> =
            listOf(
                Arguments.of(
                    "a revalidate in the factory",
                    source("JLabel", "", factory = "{ JLabel().apply { font = null; revalidate() } }"),
                ),
                Arguments.of(
                    "a call of another SwingNode",
                    source("JLabel", "set(f) { font = it; revalidate() }", node = "other.SwingNode"),
                ),
            )
    }
}
