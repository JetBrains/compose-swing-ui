package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Finding
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals

class SwingNodeUpdateConditionalCallTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("reported")
    fun `reports conditional positional calls`(
        label: String,
        body: String,
        found: String,
    ) {
        val code = source(body)
        val positions =
            lint(code).map { positionOf(code, it.entity.location.text.start, it.entity.location.text.end) }
        assertEquals(listOf(found), positions, label)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("passed")
    fun `does not report unconditional or unrelated calls`(
        label: String,
        body: String,
    ) {
        assertEquals(emptyList(), lint(source(body)), label)
    }

    private fun source(body: String): String =
        """
        import javax.swing.JLabel
        import org.jetbrains.compose.swing.node.*
        import org.jetbrains.compose.swing.node.SwingNode
        import org.jetbrains.compose.swing.node.SwingNode as Alias
        fun sample(flag: Boolean, other: SwingNodeUpdater<JLabel>, nullable: String?) {
            $body
        }
        """.trimIndent()

    private fun lint(code: String): List<Finding> =
        SwingNodeUpdateConditionalCall(Config.empty).lintWithContext(
            environment,
            code,
            COMPOSABLE_STUBS,
            NODE_UPDATER_STUBS,
            UPDATING_NODE_STUBS,
            """
            package org.jetbrains.compose.swing.node
            import java.awt.Component
            class MirrorState<V>
            fun <C : Component, V> SwingNodeUpdater<C>.declare(
                value: V,
                mirror: MirrorState<V>,
                read: C.() -> V,
                write: C.(V) -> Unit,
            ) {}
            fun <C : Component> SwingNodeUpdater<C>.declare(debug: String) {}
            """.trimIndent(),
        )

    @Test
    fun `says why the call must not be skipped`() {
        val code =
            """
            import javax.swing.JLabel
            import org.jetbrains.compose.swing.node.*
            fun sample(flag: Boolean) {
                SwingNode(factory = { JLabel() }, update = { if (flag) set(1) {} })
            }
            """.trimIndent()
        val finding =
            SwingNodeUpdateConditionalCall(Config.empty)
                .lintWithContext(environment, code, COMPOSABLE_STUBS, NODE_UPDATER_STUBS, UPDATING_NODE_STUBS)
                .single()
        assertEquals(
            "This `set` call does not run on every recomposition. " +
                "When it is skipped, the calls after it can apply values that did not change " +
                "and miss values that did. " +
                "Make the call a direct statement of `update`, and put the condition or loop " +
                "into the value it passes.",
            finding.message,
        )
    }

    @Test
    fun `says why a call after an early return must not be skipped`() {
        val code =
            """
            import javax.swing.JLabel
            import org.jetbrains.compose.swing.node.*
            fun sample(flag: Boolean) {
                SwingNode(factory = { JLabel() }, update = { if (flag) return@SwingNode; set(1) {} })
            }
            """.trimIndent()
        val finding =
            SwingNodeUpdateConditionalCall(Config.empty)
                .lintWithContext(environment, code, COMPOSABLE_STUBS, NODE_UPDATER_STUBS, UPDATING_NODE_STUBS)
                .single()
        assertEquals(
            "An early return from `update` can skip this `set` call. " +
                "When it is skipped, the calls after it can apply values that did not change " +
                "and miss values that did. " +
                "Return from `update` only after its last `set`, `update` or `declare` call, " +
                "or put the condition into the value the call passes.",
            finding.message,
        )
    }

    @Test
    fun `says a call whose argument returns from update must not be skipped`() {
        val code =
            """
            import javax.swing.JLabel
            import org.jetbrains.compose.swing.node.*
            fun sample(nullable: String?) {
                SwingNode(factory = { JLabel() }, update = { set(nullable ?: return@SwingNode) {} })
            }
            """.trimIndent()
        val finding =
            SwingNodeUpdateConditionalCall(Config.empty)
                .lintWithContext(environment, code, COMPOSABLE_STUBS, NODE_UPDATER_STUBS, UPDATING_NODE_STUBS)
                .single()
        assertEquals(
            "An early return from `update` can skip this `set` call. " +
                "When it is skipped, the calls after it can apply values that did not change " +
                "and miss values that did. " +
                "Return from `update` only after its last `set`, `update` or `declare` call, " +
                "or put the condition into the value the call passes.",
            finding.message,
        )
    }

    companion object {
        private val environment = createEnvironment()

        @JvmStatic
        fun reported(): List<Arguments> =
            listOf(
                reportedCase("if", "if (flag) set(1) {}", "set(1) {}"),
                reportedCase("when", "when (flag) { true -> update(1) {}; false -> Unit }", "update(1) {}"),
                reportedCase("loop", "for (x in 0..1) set(x) {}", "set(x) {}"),
                reportedCase("try", "try { set(1) {} } finally {}", "set(1) {}"),
                reportedCase("elvis", "nullable ?: kotlin.run { this@SwingNode.set(1) {}; \"x\" }", "set(1) {}"),
                reportedCase("scope", "nullable?.let { set(it) {} }", "set(it) {}"),
                reportedCase("return", "if (flag) return@SwingNode; set(1) {}", "set(1) {}"),
                reportedCase("forEach", "listOf(1, 2).forEach { set(it) {} }", "set(it) {}"),
                reportedCase("run", "kotlin.run { set(1) {} }", "set(1) {}"),
                reportedCase("and", "flag && (set(1) {} == Unit)", "set(1) {}"),
                reportedCase("direct elvis", "nullable ?: set(1) {}", "set(1) {}"),
                reportedCase(
                    "return in value",
                    "set(nullable ?: return@SwingNode) {}",
                    "set(nullable ?: return@SwingNode) {}",
                ),
                reportedCase("when subject", "when (set(1) {}) { else -> Unit }", "set(1) {}"),
                reportedCase(
                    "declare",
                    "if (flag) declare(\"x\", MirrorState<String>(), { \"\" }, { })",
                    "declare(\"x\", MirrorState<String>(), { \"\" }, { })",
                ),
                reportedCase("block expression", "set(1, block = if (flag) { set(2) {}; {} } else ({}))", "set(2) {}"),
                reportedCase("parenthesized", "if (flag) (set(1) {})", "set(1) {}"),
                reportedCase("labeled", "if (flag) label@ set(2) {}", "set(2) {}"),
                node("menu", "MenuNode", "set(1) {}"),
                node("alias", "Alias", "set(1) {}"),
            )

        @JvmStatic
        fun passed(): List<Arguments> =
            listOf(
                case("value", "set(if (flag) 1 else 2) {}"),
                case("own block", "set(1) { if (flag) this@SwingNode.set(2) {} }"),
                case("other receiver", "if (flag) other.set(1) {}"),
                case("unresolved", "if (flag) missing(1)"),
                case(
                    "unconditional",
                    "set(1) {}; update(2) {}; declare(\"x\", MirrorState<String>(), { \"\" }, { })",
                ),
                case("unrelated declare", "if (flag) declare(\"x\")"),
                case("foreign declare", "if (flag) other.declare(\"x\", MirrorState<String>(), { \"\" }, { })"),
                case("unresolved argument", "if (flag) set(missing) {}"),
                case("unresolved receiver", "if (flag) missing.set(1) {}"),
                case("direct parenthesized", "(set(1) {}); label@ set(2) {}; (this@SwingNode.set(3) {})"),
                case("qualified direct call", "this@SwingNode.set(1) {}"),
                case("return from another lambda", "listOf(1).forEach { return@forEach }; set(1) {}"),
                case("return after call", "set(1) {}; if (flag) return@SwingNode"),
            )

        private fun node(
            label: String,
            function: String,
            found: String,
        ): Arguments = Arguments.of(label, "$function(factory = { JLabel() }, update = { if (flag) set(1) {} })", found)

        private fun reportedCase(
            label: String,
            body: String,
            found: String,
        ): Arguments = Arguments.of(label, "SwingNode(factory = { JLabel() }, update = { $body })", found)

        private fun case(
            label: String,
            body: String,
        ): Arguments = Arguments.of(label, "SwingNode(factory = { JLabel() }, update = { $body })")
    }
}
