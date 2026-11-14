// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SwingNodeFactoryReusesComponentTest {
    @ParameterizedTest(name = "reports {0}")
    @MethodSource("reported")
    fun `reports a factory that hands back a component declared outside it`(
        label: String,
        code: String,
        positions: List<String>,
    ) {
        assertEquals(positions, positionsIn(code), label)
    }

    @ParameterizedTest(name = "passes {0}")
    @MethodSource("passed")
    fun `does not report a factory that builds its component`(
        label: String,
        code: String,
    ) {
        assertEquals(emptyList(), positionsIn(code), label)
    }

    @Test
    fun `says what to do instead`() {
        val finding = lint(inShow("SwingNode(factory = { panel })")).single()

        assertEquals(
            "This `factory` returns a component it did not build. A node that is released and rebuilt calls " +
                "`factory` again, so the new node gets the same component another node still holds. Build the " +
                "component inside `factory`. To configure a part that its parent built and keeps, use " +
                "`ExistingSwingNode`.",
            finding.message,
        )
    }

    private fun positionsIn(code: String) =
        lint(code).map { positionOf(code, it.entity.location.text.start, it.entity.location.text.end) }

    private fun lint(code: String) =
        assertIs<AnalysisComposeSwingRuleAdapter>(composeSwingRule("SwingNodeFactoryReusesComponent"))
            .lintWithContext(
                environment,
                code,
                """
package org.jetbrains.compose.swing.node

import java.awt.Component

public fun <T : Component> SwingNode(factory: () -> T, update: () -> Unit = {}) {}

public fun <T : Component> SwingNode(factory: () -> T, update: () -> Unit = {}, content: () -> Unit) {}

public fun <T : Component> MenuNode(factory: () -> T, update: () -> Unit = {}) {}
""",
                """
package other

import javax.swing.JPanel

fun SwingNode(factory: () -> JPanel) {}
""",
                """
package sample

import javax.swing.JPanel

val shared = JPanel()
""",
            )

    companion object {
        private val environment = createEnvironment()

        private fun case(
            label: String,
            body: String,
            vararg positions: String,
        ): Arguments = Arguments.of(label, inShow(body), positions.toList())

        private fun clean(
            label: String,
            body: String,
        ): Arguments = Arguments.of(label, inShow(body))

        private fun inShow(body: String): String =
            """
package sample

import javax.swing.JPanel
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.node.SwingNode

class Holder(val panel: JPanel) {
    val inner: Holder get() = this

    fun build(): JPanel = JPanel()
}

fun build(): JPanel = JPanel()

fun notNode(factory: () -> JPanel) {}

fun show(panel: JPanel, holder: Holder, flag: Boolean, pairs: List<Pair<JPanel, Int>>) {
$body
}"""

        private fun file(
            label: String,
            code: String,
            vararg positions: String,
        ): Arguments = Arguments.of(label, code.trimIndent(), positions.toList())

        private fun cleanFile(
            label: String,
            code: String,
        ): Arguments = Arguments.of(label, code.trimIndent())

        @JvmStatic
        fun reported(): List<Arguments> =
            reportedNames() +
                reportedPropertyAccesses() +
                reportedBranchesAndReturns() +
                reportedWrappers() +
                reportedOuterScopes() +
                reportedPackageQualifiedProperties() +
                reportedThisOfClasses() +
                reportedThisOfReceivers() +
                reportedImports()

        private fun reportedNames(): List<Arguments> =
            listOf(
                case("a captured name", "SwingNode(factory = { panel })", "panel #3"),
                case("a captured name passed positionally", "SwingNode({ panel })", "panel #3"),
                case("a captured name in a MenuNode", "MenuNode(factory = { panel })", "panel #3"),
                case("a parenthesized name", "SwingNode(factory = { (panel) })", "panel #3"),
                case("a name with an explicit type argument", "SwingNode<JPanel>(factory = { panel })", "panel #3"),
                case(
                    "the node's qualified name",
                    "org.jetbrains.compose.swing.node.SwingNode(factory = { panel })",
                    "panel #3",
                ),
            )

        private fun reportedPropertyAccesses(): List<Arguments> =
            listOf(
                case("a property access", "SwingNode(factory = { holder.panel })", "holder.panel"),
                case(
                    "a safe property access",
                    "SwingNode(factory = { holder?.panel ?: JPanel() })",
                    "holder?.panel",
                ),
                case(
                    "a property access on a property access",
                    "SwingNode(factory = { holder.inner.panel })",
                    "holder.inner.panel",
                ),
            )

        private fun reportedBranchesAndReturns(): List<Arguments> =
            listOf(
                case(
                    "an if with one captured branch",
                    "SwingNode(factory = { if (flag) panel else JPanel() })",
                    "panel #3",
                ),
                case(
                    "a when with one captured branch",
                    "SwingNode(factory = { when (flag) { true -> JPanel(); else -> panel } })",
                    "panel #3",
                ),
                case(
                    "a try with a captured catch",
                    "SwingNode(factory = { try { JPanel() } catch (e: Exception) { panel } })",
                    "panel #3",
                ),
                case(
                    "both branches captured",
                    "SwingNode(factory = { if (flag) panel else holder.panel })",
                    "panel #3",
                    "holder.panel",
                ),
                case(
                    "a labeled return",
                    "SwingNode(factory = { if (flag) return@SwingNode panel; JPanel() })",
                    "panel #3",
                ),
                case("a return through an explicit label", "SwingNode(factory = f@{ return@f panel })", "panel #3"),
                case(
                    "a labeled return inside a nested lambda without a label",
                    "SwingNode(factory = { listOf(1).forEach { return@SwingNode panel }; JPanel() })",
                    "panel #3",
                ),
                case(
                    "a return to a factory label that an inner call name shares",
                    "SwingNode(factory = run@{ run inner@{ return@run panel }; JPanel() })",
                    "panel #3",
                ),
                case(
                    "an outer name beside a local of the same name",
                    "SwingNode(factory = { val panel = if (flag) return@SwingNode panel else JPanel(); panel })",
                    "panel #4",
                ),
            )

        private fun reportedWrappers(): List<Arguments> =
            listOf(
                case("a non-null assertion", "SwingNode(factory = { panel!! })", "panel!!"),
                case("a cast", "SwingNode(factory = { panel as JPanel })", "panel as JPanel"),
                case(
                    "a safe cast",
                    "SwingNode(factory = { panel as? JPanel ?: JPanel() })",
                    "panel as? JPanel",
                ),
                case("the captured left of an elvis", "SwingNode(factory = { panel ?: JPanel() })", "panel #3"),
                case("an elvis under a cast", "SwingNode(factory = { (panel ?: JPanel()) as JPanel })", "panel #3"),
                case(
                    "an if under a non-null assertion",
                    "SwingNode(factory = { (if (flag) panel else JPanel())!! })",
                    "panel #3",
                ),
            )

        private fun reportedOuterScopes(): List<Arguments> =
            listOf(
                case(
                    "the outer lambda's it",
                    "listOf(panel).forEach { SwingNode(factory = { it }) }",
                    "it",
                ),
                case(
                    "an implicit it past a lambda with parameters",
                    "listOf(panel).forEach { SwingNode(factory = { JPanel().let { x -> return@SwingNode it } }) }",
                    "it",
                ),
                case(
                    "a for-loop variable after its loop",
                    "SwingNode(factory = { for (panel in listOf(JPanel())) {}; panel })",
                    "panel #4",
                ),
                case(
                    "a destructured for-loop variable after its loop",
                    "SwingNode(factory = { for ((panel, _) in pairs) {}; panel })",
                    "panel #4",
                ),
                case(
                    "a destructured lambda parameter after its lambda",
                    "SwingNode(factory = { pairs.first().let { (panel, _) -> panel }; panel })",
                    "panel #5",
                ),
            )

        private fun reportedPackageQualifiedProperties(): List<Arguments> =
            listOf(
                file(
                    "a property of the same package qualified by the package",
                    """
                    package sample

                    import javax.swing.JPanel
                    import org.jetbrains.compose.swing.node.SwingNode

                    val panel = JPanel()

                    fun show() {
                        SwingNode(factory = { sample.panel })
                    }
                    """,
                    "sample.panel",
                ),
                file(
                    "a property of another file qualified by the package",
                    """
                    package sample

                    import org.jetbrains.compose.swing.node.SwingNode

                    fun show() {
                        SwingNode(factory = { sample.shared })
                    }
                    """,
                    "sample.shared",
                ),
            )

        private fun reportedThisOfClasses(): List<Arguments> =
            listOf(
                file(
                    "the this of the enclosing class",
                    """
                    package sample

                    import javax.swing.JPanel
                    import org.jetbrains.compose.swing.node.SwingNode

                    class Mine : JPanel() {
                        fun mount() {
                            SwingNode(factory = { this })
                        }
                    }
                    """,
                    "this",
                ),
                file(
                    "a labeled this of the enclosing class",
                    """
                    package sample

                    import javax.swing.JPanel
                    import org.jetbrains.compose.swing.node.SwingNode

                    class Outer {
                        val panel = JPanel()

                        fun mount() {
                            SwingNode(factory = { this@Outer.panel })
                        }
                    }
                    """,
                    "this@Outer.panel",
                ),
            )

        private fun reportedThisOfReceivers(): List<Arguments> =
            listOf(
                file(
                    "the this of an extension receiver",
                    """
                    package sample

                    import javax.swing.JPanel
                    import org.jetbrains.compose.swing.node.SwingNode

                    fun JPanel.mount() {
                        SwingNode(factory = { this })
                    }
                    """,
                    "this",
                ),
                file(
                    "the this of a receiver lambda around the node",
                    """
                    package sample

                    import javax.swing.JPanel
                    import org.jetbrains.compose.swing.node.SwingNode

                    fun mount(panel: JPanel) {
                        with(panel) {
                            SwingNode(factory = { this })
                        }
                    }
                    """,
                    "this",
                ),
            )

        private fun reportedImports(): List<Arguments> =
            listOf(
                file(
                    "an aliased import",
                    """
                    package sample

                    import javax.swing.JPanel
                    import org.jetbrains.compose.swing.node.SwingNode as Node

                    fun show(panel: JPanel) {
                        Node(factory = { panel })
                    }
                    """,
                    "panel #2",
                ),
                file(
                    "a wildcard import",
                    """
                    package sample

                    import javax.swing.JPanel
                    import org.jetbrains.compose.swing.node.*

                    fun show(panel: JPanel) {
                        SwingNode(factory = { panel })
                    }
                    """,
                    "panel #2",
                ),
                file(
                    "a wildcard import before another wildcard import",
                    """
                    package sample

                    import org.jetbrains.compose.swing.node.*
                    import javax.swing.*

                    fun show(panel: JPanel) {
                        SwingNode(factory = { panel })
                    }
                    """,
                    "panel #2",
                ),
                file(
                    "a wildcard import after another wildcard import",
                    """
                    package sample

                    import javax.swing.*
                    import org.jetbrains.compose.swing.node.*

                    fun show(panel: JPanel) {
                        SwingNode(factory = { panel })
                    }
                    """,
                    "panel #2",
                ),
            )

        @JvmStatic
        fun passed(): List<Arguments> =
            passedBuiltComponents() +
                passedOwnedNames() +
                passedOwnedReceivers() +
                passedOtherCalls() +
                passedOtherNodes()

        private fun passedBuiltComponents(): List<Arguments> =
            listOf(
                clean("a new component", "SwingNode(factory = { JPanel() })"),
                clean("a custom factory function", "SwingNode(factory = { build() })"),
                clean("a custom factory function on a holder", "SwingNode(factory = { holder.build() })"),
                clean(
                    "a component built and configured",
                    "var p: JPanel? = null\nSwingNode(factory = { JPanel().also { p = it } })",
                ),
                clean("a factory held in a name", "val factory = { JPanel() }\nSwingNode(factory = factory)"),
                clean("a callable reference", "SwingNode(factory = ::JPanel)"),
                clean("a call in an if branch", "SwingNode(factory = { if (flag) JPanel() else build() })"),
                clean(
                    "a labeled return of a call",
                    "SwingNode(factory = { if (flag) return@SwingNode build(); JPanel() })",
                ),
                clean("a trailing lambda, which is the content", "SwingNode(factory = { JPanel() }) { panel }"),
            )

        private fun passedOwnedNames(): List<Arguments> =
            listOf(
                clean("a local the lambda declares", "SwingNode(factory = { val p = JPanel(); p })"),
                clean("a holder the lambda declares", "SwingNode(factory = { val h = Holder(JPanel()); h.panel })"),
                clean(
                    "a local that shadows an outer name",
                    "SwingNode(factory = { val panel = JPanel(); panel })",
                ),
                clean(
                    "a labeled return shadowed by a nested label",
                    "SwingNode(factory = { run SwingNode@{ return@SwingNode panel }; JPanel() })",
                ),
                clean(
                    "a for-loop variable used inside its loop",
                    "SwingNode(factory = { for (panel in listOf(JPanel())) { return@SwingNode panel }; JPanel() })",
                ),
                clean(
                    "a destructured for-loop variable used inside its loop",
                    "SwingNode(factory = { for ((panel, _) in pairs) { return@SwingNode panel }; JPanel() })",
                ),
                clean(
                    "a destructured lambda parameter used inside its lambda",
                    "SwingNode(factory = { pairs.first().let { (panel, _) -> return@SwingNode panel } })",
                ),
                cleanFile(
                    "a property of a value the factory builds, qualified by the package of its type",
                    """
                    package sample

                    import javax.swing.JPanel
                    import org.jetbrains.compose.swing.node.SwingNode

                    class Box(val panel: JPanel)

                    fun show() {
                        SwingNode(factory = { val box = sample.Box(JPanel()); box.panel })
                    }
                    """,
                ),
                clean("an implicit it of a lambda the factory runs", "SwingNode(factory = { JPanel().let { it } })"),
                clean(
                    "an implicit it returned from a lambda the factory runs",
                    "SwingNode(factory = { JPanel().let { return@SwingNode it } })",
                ),
            )

        private fun passedOwnedReceivers(): List<Arguments> =
            listOf(
                clean("a this of a lambda the factory runs", "SwingNode(factory = { JPanel().run { this } })"),
                clean(
                    "a this returned from a lambda the factory runs",
                    "SwingNode(factory = { JPanel().run { return@SwingNode this } })",
                ),
                clean(
                    "a labeled this of a lambda the factory runs, by call name",
                    "SwingNode(factory = { JPanel().apply { return@SwingNode this@apply } })",
                ),
                clean(
                    "a labeled this of a lambda the factory runs, by label",
                    "SwingNode(factory = { with(JPanel()) outer@{ return@SwingNode this@outer } })",
                ),
                cleanFile(
                    "the this of a receiver lambda inside the factory of a class member",
                    """
                    package sample

                    import javax.swing.JPanel
                    import org.jetbrains.compose.swing.node.SwingNode

                    class Mine : JPanel() {
                        fun mount() {
                            SwingNode(factory = { JPanel().apply { name = "x" } })
                            SwingNode(factory = { with(JPanel()) { this } })
                        }
                    }
                    """,
                ),
            )

        private fun passedOtherCalls(): List<Arguments> =
            listOf(
                clean("a function that is not a node", "notNode(factory = { panel })"),
                clean(
                    "a return to the call name of a parenthesized inner lambda",
                    "SwingNode(factory = run@{ run(({ return@run panel })); JPanel() })",
                ),
                clean(
                    "a return to the call name of a backticked inner lambda",
                    "SwingNode(factory = run@{ `run` { return@run panel }; JPanel() })",
                ),
            )

        private fun passedOtherNodes(): List<Arguments> =
            listOf(
                cleanFile(
                    "a qualified name of another package",
                    """
                    package sample

                    import javax.swing.JPanel

                    fun show(panel: JPanel) {
                        other.SwingNode(factory = { panel })
                    }
                    """,
                ),
                cleanFile(
                    "a node of the same name in another package",
                    """
                    package sample

                    import javax.swing.JPanel
                    import other.SwingNode

                    fun show(panel: JPanel) {
                        SwingNode(factory = { panel })
                    }
                    """,
                ),
                cleanFile(
                    "a wildcard import of the node package beside an explicit import of another SwingNode",
                    """
                    package sample

                    import javax.swing.JPanel
                    import other.SwingNode
                    import org.jetbrains.compose.swing.node.*

                    fun show(panel: JPanel) {
                        SwingNode(factory = { panel })
                    }
                    """,
                ),
                cleanFile(
                    "a local function that shadows the imported node",
                    """
                    package sample

                    import javax.swing.JPanel
                    import org.jetbrains.compose.swing.node.SwingNode

                    fun show(panel: JPanel) {
                        fun SwingNode(factory: () -> JPanel) {}
                        SwingNode(factory = { panel })
                    }
                    """,
                ),
            )
    }
}
