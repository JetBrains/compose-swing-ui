package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals

class SwingNodeFactoryReadsDeclaredValueTest {
    @ParameterizedTest(name = "reports {0}")
    @MethodSource("reported")
    fun `reports a factory that reads a value its composable passes in`(
        label: String,
        code: String,
        positions: List<String>,
    ) {
        assertEquals(positions, positionsIn(code), label)
    }

    @Test
    fun `reports a name at its first reference only`() {
        val code = inShow("SwingNode(factory = { JLabel(panel.name + panel.name) })")

        assertEquals(listOf("panel #3"), positionsIn(code))
    }

    @Test
    fun `says what to do instead for a value`() {
        val finding = lint(inShow("SwingNode(factory = { panel })")).single()

        assertEquals(
            "A new `panel` never reaches the component. `factory` runs only when the node is created, and " +
                "`update` does not apply `panel`. Apply `panel` in `update` with `set` or `update`. If the " +
                "component takes it only when it is built, wrap the node in a `key` on `panel`.",
            finding.message,
        )
    }

    @Test
    fun `says to read a function through rememberUpdatedState`() {
        val finding = lint(onClickSource()).single()

        assertEquals(
            "The component keeps calling the first `onClick` it got. `factory` runs only when the node is created, " +
                "so a later `onClick` never reaches it. Read `onClick` through `rememberUpdatedState` each time " +
                "the callback runs.",
            finding.message,
        )
    }

    companion object {
        private val environment = createEnvironment()

        private fun positionsIn(code: String): List<String> =
            lint(code).map { positionOf(code, it.entity.location.text.start, it.entity.location.text.end) }

        internal fun lint(code: String) =
            SwingNodeFactoryReadsDeclaredValue(Config.empty)
                .lintWithContext(environment, code, *stubSources().toTypedArray())

        private fun stubSources(): List<String> = composeStubs() + swingNodeStubs()

        private fun composeStubs(): List<String> =
            listOf(
                COMPOSABLE_STUBS,
                """
package androidx.compose.runtime

fun <T> key(vararg keys: Any?, block: @Composable () -> T): T = block()

fun <T> remember(vararg keys: Any?, calculation: () -> T): T = calculation()

fun <T> rememberUpdatedState(newValue: T): State<T> = mutableStateOf(newValue)

interface State<T> {
    val value: T
}

interface MutableState<T> : State<T>

private class StubState<T>(override val value: T) : MutableState<T>

fun <T> mutableStateOf(value: T): MutableState<T> = StubState(value)

operator fun <T> State<T>.getValue(thisObj: Any?, property: kotlin.reflect.KProperty<*>): T = value
""",
                """
package other

annotation class Composable
""",
            )

        private fun swingNodeStubs(): List<String> =
            listOf(
                NODE_UPDATER_STUBS,
                UPDATING_NODE_STUBS,
                """
package org.jetbrains.compose.swing.node

public class MirrorState<V>(initial: V)

public fun <V> rememberMirrorState(initial: V): MirrorState<V> = MirrorState(initial)
""",
            )

        internal fun inShow(body: String): String =
            """
package sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import javax.swing.JLabel
import javax.swing.JPanel
import org.jetbrains.compose.swing.node.MenuNode
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.rememberMirrorState

class Holder(val panel: JPanel)

@Composable
fun show(panel: JPanel, holder: Holder, flag: Boolean) {
$body
}"""

        private fun onClickSource(): String =
            """
            package sample

            import androidx.compose.runtime.Composable
            import javax.swing.JButton
            import org.jetbrains.compose.swing.node.SwingNode

            @Composable
            fun show(onClick: () -> Unit) {
                SwingNode(factory = { JButton().also { button -> button.addActionListener { onClick() } } })
            }
            """.trimIndent()

        private fun case(
            label: String,
            body: String,
            vararg positions: String,
        ): Arguments = Arguments.of(label, inShow(body), positions.toList())

        private fun file(
            label: String,
            code: String,
            vararg positions: String,
        ): Arguments = Arguments.of(label, code.trimIndent(), positions.toList())

        private fun updaterCase(
            label: String,
            body: String,
            vararg positions: String,
        ): Arguments = file(label, updaterSource(body), *positions)

        internal fun updaterSource(body: String): String =
            """
            package sample

            import androidx.compose.runtime.Composable
            import javax.swing.JLabel
            import org.jetbrains.compose.swing.node.SwingNode
            import org.jetbrains.compose.swing.node.SwingNodeUpdater
            import org.jetbrains.compose.swing.node.reconcileWithChildren

            @Composable
            fun show(
                foreign: SwingNodeUpdater<JLabel>,
                text: String,
                flag: Boolean,
                deferred: MutableList<() -> Unit>,
            ) {
                $body
            }
            """

        @JvmStatic
        fun reported(): List<Arguments> =
            reportedParameters() +
                reportedLambdaParameters() +
                reportedForeignUpdaters() +
                reportedDeferredWrites() +
                reportedNestedWrites() +
                reportedReturns() +
                reportedFunctionTypes()

        private fun reportedParameters(): List<Arguments> =
            listOf(
                case("a parameter", "SwingNode(factory = { panel })", "panel #3"),
                case("a parameter passed positionally", "SwingNode({ panel })", "panel #3"),
                case("a parameter in a MenuNode", "MenuNode(factory = { panel })", "panel #3"),
                case(
                    "a parameter read in a lambda inside the factory",
                    "SwingNode(factory = { listOf(1).map { panel }.first() })",
                    "panel #3",
                ),
                case(
                    "a parameter read by a labeled return",
                    "SwingNode(factory = { if (flag) return@SwingNode panel; JPanel() })",
                    "flag #2",
                    "panel #3",
                ),
                case("a parameter read through a receiver", "SwingNode(factory = { holder.panel })", "holder #2"),
                file(
                    "a parameter read by a node inside the key argument, not its block",
                    """
                    package sample

                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.key
                    import javax.swing.JLabel
                    import org.jetbrains.compose.swing.node.SwingNode

                    @Composable
                    fun show(text: String) {
                        key(run { SwingNode(factory = { JLabel(text) }); Unit }) {}
                    }
                    """,
                    "text #2",
                ),
                case(
                    "a parameter under a key that does not name it",
                    "key(Unit, block = { SwingNode(factory = { panel }) })",
                    "panel #3",
                ),
            )

        private fun reportedLambdaParameters(): List<Arguments> =
            listOf(
                case(
                    "a forEach item",
                    "listOf(panel).forEach { item -> SwingNode(factory = { item }) }",
                    "item #2",
                ),
                case(
                    "an enclosing lambda parameter over a parameter of the same name",
                    "listOf(JPanel()).forEach { panel -> SwingNode(factory = { panel }) }",
                    "panel #4",
                ),
                case(
                    "an implicit it of an enclosing lambda",
                    "listOf(panel).forEach { SwingNode(factory = { JLabel(it.toString()) }) }",
                    "it",
                ),
                case(
                    "a parameter and an enclosing lambda parameter read through a nested lambda",
                    "listOf(1).forEach { n -> SwingNode(factory = { JLabel(panel.name + n) }) }",
                    "panel #3",
                    "n #37",
                ),
            )

        private fun reportedForeignUpdaters(): List<Arguments> =
            listOf(
                updaterCase(
                    "a value read in a reconcileWithChildren on another updater",
                    "SwingNode(factory = { JLabel(text) }, " +
                        "update = { foreign.reconcileWithChildren { component.text = text } })",
                    "text #2",
                ),
                updaterCase(
                    "a value set on an updater that is not the node's own",
                    "SwingNode(factory = { JLabel(text) }, update = { foreign.set(text) { this.text = it } })",
                    "text #2",
                ),
                updaterCase(
                    "a value set inside a scope function on an updater that is not the node's own",
                    "SwingNode(factory = { JLabel(text) }, update = { foreign.apply { set(text) { } } })",
                    "text #2",
                ),
                updaterCase(
                    "a value set through another name for the node's own updater",
                    "SwingNode(factory = { JLabel(text) }, update = { this.apply { set(text) { this.text = it } } })",
                    "text #2",
                ),
            )

        private fun reportedDeferredWrites(): List<Arguments> =
            listOf(
                updaterCase(
                    "a value read in a deferred reconcileWithChildren",
                    "SwingNode(factory = { JLabel(text) }, " +
                        "update = { deferred += { reconcileWithChildren { component.text = text } } })",
                    "text #2",
                ),
                updaterCase(
                    "a value set in a callback the update stores",
                    "SwingNode(factory = { JLabel(text) }, update = { deferred += { set(text) { this.text = it } } })",
                    "text #2",
                ),
                updaterCase(
                    "a value set in a local function the update never calls",
                    "SwingNode(factory = { JLabel(text) }, update = { fun later() { set(text) { this.text = it } } })",
                    "text #2",
                ),
                updaterCase(
                    "a value set in an object expression of the update",
                    "SwingNode(\n" +
                        "factory = { JLabel(text) },\n" +
                        "update = { object : Runnable { override fun run() { set(text) { this.text = it } } } },\n" +
                        ")",
                    "text #2",
                ),
            )

        private fun reportedNestedWrites(): List<Arguments> =
            listOf(
                updaterCase(
                    "a value read in a reconcileWithChildren under a condition",
                    "SwingNode(factory = { JLabel(text) }, " +
                        "update = { if (flag) reconcileWithChildren { component.text = text } })",
                    "text #2",
                ),
                updaterCase(
                    "a reconcile block that writes a constant instead of the parameter",
                    "SwingNode(factory = { JLabel(text) }, update = { reconcile { this.text = \"fixed\" } })",
                    "text #2",
                ),
                updaterCase(
                    "a value read in a reconcile under a condition",
                    "SwingNode(factory = { JLabel(text) }, update = { if (flag) reconcile { this.text = text } })",
                    "text #2",
                ),
                updaterCase(
                    "a value read in a reconcile on another updater",
                    "SwingNode(factory = { JLabel(text) }, update = { foreign.reconcile { this.text = text } })",
                    "text #2",
                ),
                updaterCase(
                    "a value set in a lambda passed to an inline function",
                    "SwingNode(factory = { JLabel(text) }, update = { listOf(1).forEach { set(text) { } } })",
                    "text #2",
                ),
                updaterCase(
                    "a value set in an immediately invoked lambda",
                    "SwingNode(factory = { JLabel(text) }, update = { ({ set(text) { this.text = it } })() })",
                    "text #2",
                ),
                file(
                    "a value set in a lambda that an inline function never runs",
                    updaterSource(
                        "SwingNode(factory = { JLabel(text) }, update = { ignore { set(text) { this.text = it } } })",
                    ) + "\n\ninline fun ignore(block: () -> Unit) {}\n",
                    "text #2",
                ),
                updaterCase(
                    "a value set under a condition",
                    "SwingNode(factory = { JLabel(text) }, update = { if (flag) set(text) { this.text = it } })",
                    "text #2",
                ),
            )

        private fun reportedReturns(): List<Arguments> =
            listOf(
                updaterCase(
                    "a value read in a reconcileWithChildren after a conditional return from update",
                    "SwingNode(factory = { JLabel(text) }, update = { if (flag) return@SwingNode; " +
                        "reconcileWithChildren { component.text = text } })",
                    "text #2",
                ),
                updaterCase(
                    "a value read in a reconcile after a conditional return from update",
                    "SwingNode(factory = { JLabel(text) }, update = { if (flag) return@SwingNode; " +
                        "reconcile { this.text = text } })",
                    "text #2",
                ),
                updaterCase(
                    "a value set after a conditional return from update",
                    "SwingNode(factory = { JLabel(text) }, update = { if (flag) return@SwingNode; set(text) { } })",
                    "text #2",
                ),
                updaterCase(
                    "a value set after a return from a labeled update lambda",
                    "SwingNode(factory = { JLabel(text) }, update = l@{ if (flag) return@l; set(text) { } })",
                    "text #2",
                ),
                updaterCase(
                    "a value set after an elvis return from update",
                    "SwingNode(factory = { JLabel(text) }, update = { text.takeIf { flag } ?: return@SwingNode; " +
                        "set(text) { } })",
                    "text #2",
                ),
                updaterCase(
                    "a value set after a return from update inside forEach",
                    "SwingNode(factory = { JLabel(text) }, update = { " +
                        "listOf(1).forEach { if (flag) return@SwingNode }; set(text) { } })",
                    "text #2",
                ),
                updaterCase(
                    "a value whose set argument can return from update",
                    "SwingNode(factory = { JLabel(text) }, update = { set(if (flag) return@SwingNode else text) { } })",
                    "text #2",
                ),
                updaterCase(
                    "a value set after an unconditional return from update",
                    "SwingNode(factory = { JLabel(text) }, update = { return@SwingNode; set(text) { } })",
                    "text #2",
                ),
                updaterCase(
                    "a value updated after a conditional return from update",
                    "SwingNode(factory = { JLabel(text) }, update = { if (flag) return@SwingNode; update(text) { } })",
                    "text #2",
                ),
                updaterCase(
                    "a value set in a statement whose block returns from update",
                    "SwingNode(factory = { JLabel(text) }, update = { set(text) { return@SwingNode } })",
                    "text #2",
                ),
            )

        private fun reportedFunctionTypes(): List<Arguments> =
            listOf(
                file(
                    "a function-typed parameter invoked in a listener",
                    onClickSource(),
                    "onClick #2",
                ),
                file(
                    "a function-typed parameter invoked in a local function",
                    """
                    package sample

                    import androidx.compose.runtime.Composable
                    import javax.swing.JButton
                    import org.jetbrains.compose.swing.node.SwingNode

                    @Composable
                    fun show(callback: () -> Unit) {
                        SwingNode(factory = {
                            fun handle() = callback()
                            JButton().apply { addActionListener { handle() } }
                        })
                    }
                    """,
                    "callback #2",
                ),
            ) + reportedFunctionTypeMembers()

        private fun reportedFunctionTypeMembers(): List<Arguments> =
            listOf(
                file(
                    "a function-typed parameter invoked in a member of an object literal",
                    """
                    package sample

                    import androidx.compose.runtime.Composable
                    import java.awt.event.ActionEvent
                    import java.awt.event.ActionListener
                    import javax.swing.JButton
                    import org.jetbrains.compose.swing.node.SwingNode

                    @Composable
                    fun show(callback: () -> Unit) {
                        SwingNode(factory = {
                            JButton().apply {
                                addActionListener(object : ActionListener {
                                    override fun actionPerformed(e: ActionEvent) {
                                        callback()
                                    }
                                })
                            }
                        })
                    }
                    """,
                    "callback #2",
                ),
                file(
                    "a factory extension named invoke",
                    """
                    package sample

                    import androidx.compose.runtime.Composable
                    import javax.swing.JPanel
                    import org.jetbrains.compose.swing.node.SwingNode

                    fun (() -> JPanel).invoke(tag: Int): JPanel = this()

                    @Composable
                    fun show(factory: () -> JPanel) {
                        SwingNode(factory = { factory.invoke(1) })
                    }
                    """,
                    "factory #3",
                ),
            )
    }
}
