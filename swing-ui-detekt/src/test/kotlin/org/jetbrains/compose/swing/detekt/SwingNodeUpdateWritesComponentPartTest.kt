// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Finding
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SwingNodeUpdateWritesComponentPartTest {
    @ParameterizedTest(name = "reports {0}")
    @MethodSource("reported")
    fun `reports the first write to a part the class builds`(
        label: String,
        code: String,
        positions: List<String>,
    ) {
        assertEquals(positions, lint(code).positionsIn(code), label)
    }

    @ParameterizedTest(name = "passes {0}")
    @MethodSource("passed")
    fun `does not report a write that leaves the parts alone`(
        label: String,
        code: String,
    ) {
        assertEquals(emptyList(), lint(code).textsIn(code), label)
    }

    @Test
    fun `names the callee, the part and the class and says what to do instead`() {
        val finding = lint(card("set(text) { title.text = it }")).single()

        assertEquals(
            "This `update` writes `title`, a child that `Card` builds itself. That child is not a node, so no " +
                "modifier, style or test tag reaches it, and its values are pushed by hand. Build the children as " +
                "composables in a library container, or give `Card` setters for what its children show and call " +
                "those from `update`.",
            finding.message,
        )
    }

    @Test
    fun `names the superclass that builds the part, not the class of the node`() {
        val finding = lint(of("SubCard()", "set(text) { caption.text = it }")).single()

        assertEquals(
            "This `update` writes `caption`, a child that `BaseCard` builds itself. That child is not a node, so no " +
                "modifier, style or test tag reaches it, and its values are pushed by hand. Build the children as " +
                "composables in a library container, or give `BaseCard` setters for what its children show and call " +
                "those from `update`.",
            finding.message,
        )
    }

    private fun List<Finding>.positionsIn(code: String) =
        map { positionOf(code, it.entity.location.text.start, it.entity.location.text.end) }

    private fun lint(code: String) =
        assertIs<AnalysisComposeSwingRuleAdapter>(composeSwingRule("SwingNodeUpdateWritesComponentPart"))
            .lintWithContext(
                environment,
                code,
                COMPOSABLE_STUBS,
                NODE_UPDATER_STUBS,
                UPDATING_NODE_STUBS,
                """
                package other

                fun outside(): javax.swing.JLabel = javax.swing.JLabel()
                """.trimIndent(),
            )

    companion object {
        private val environment = createEnvironment()

        private fun inShow(body: String): String =
            """
            package sample

            import androidx.compose.runtime.Composable
            import javax.swing.JLabel
            import javax.swing.JPanel
            import org.jetbrains.compose.swing.node.MenuNode
            import org.jetbrains.compose.swing.node.SwingNode
            import org.jetbrains.compose.swing.node.SwingNode as Node
            import org.jetbrains.compose.swing.detekt.fixture.LibraryCard
            """.trimIndent() + "\n\n" + componentSources() + "\n\n" + inheritanceSources() +
                "\n@Composable\nfun show(text: String, flag: Boolean) {\n$body\n}"

        private fun componentSources(): String =
            """
            class Card : JPanel() {
                val title = JLabel()
                val body = JLabel().apply { text = "" }
                val note = JLabel().also { it.text = "" }
                val tag = JLabel().run { this }
                val count = JLabel().let { it }
                val qualified = javax.swing.JLabel()
                var delta = JLabel()
                val made = make()
                val madeThenApplied = make().apply { text = "" }
                val borrowed = java.util.Objects.requireNonNull(JLabel())
            }

            fun make(): JLabel = JLabel()

            class Tipped : JPanel() {
                val tip = createToolTip()
            }

            class Extended : JPanel() {
                val title = label()
            }

            fun Extended.label(): JLabel = JLabel()

            class Elsewhere : JPanel() {
                val title = other.outside()
            }

            class Source {
                fun label(): JLabel = JLabel()
            }

            class Remote(src: Source) : JPanel() {
                val title = src.label()
            }

            class Helped : JPanel() {
                val title = build()
                private fun build(): JLabel = JLabel()
            }
            """.trimIndent()

        private fun inheritanceSources(): String =
            """
            open class Generic<T> : JPanel() {
                val caption = JLabel()
            }

            class Specific : Generic<String>()

            open class BaseCard : JPanel() {
                val caption = JLabel()
            }

            class SubCard : BaseCard()

            class Handed(val title: JLabel) : JPanel()

            class Later : JPanel() {
                lateinit var title: JLabel
            }

            class Lazy : JPanel() {
                val title: JLabel by lazy { JLabel() }
            }

            class Parent : JPanel() {
                val title = JLabel()
            }
            """.trimIndent()

        private fun card(update: String): String = inShow("SwingNode(factory = { Card() }, update = { $update })")

        private fun of(
            factory: String,
            update: String,
        ): String = inShow("SwingNode(factory = { $factory }, update = { $update })")

        private fun case(
            label: String,
            code: String,
            vararg positions: String,
        ): Arguments = Arguments.of(label, code, positions.toList())

        @JvmStatic
        fun reported(): List<Arguments> = reportedParts() + reportedWriteForms() + reportedRepeats()

        private fun reportedWriteForms(): List<Arguments> =
            listOf(
                case("an augmented assignment to a part", card("set(text) { title.text += it }"), "title.text += it"),
                case("a setter call on a part", card("set(text) { title.setText(it) }"), "title.setText(it)"),
            )

        private fun reportedParts(): List<Arguments> =
            listOf(
                case("an assignment to a part", card("set(text) { title.text = it }"), "title.text = it"),
                case("an assignment through this", card("set(text) { this.title.text = it }"), "this.title.text = it"),
                case(
                    "a visibility write that follows a text write, at the first write only",
                    card("set(text) { this.body.text = it; this.body.isVisible = it.isNotEmpty() }"),
                    "this.body.text = it",
                ),
                case(
                    "an assignment to a part through a smart cast of the receiver",
                    inShow(
                        "SwingNode<JPanel>(factory = { Card() }, " +
                            "update = { set(text) { if (this is Card) title.text = it } })",
                    ),
                    "title.text = it",
                ),
                case(
                    "a node call through an import alias of SwingNode",
                    inShow("Node(factory = { Card() }, update = { set(text) { title.text = it } })"),
                    "title.text = it",
                ),
                case("a part that an apply builds", card("set(text) { body.text = it }"), "body.text = it"),
                case("a part that an also builds", card("set(text) { note.text = it }"), "note.text = it"),
                case("a part that a run builds", card("set(text) { tag.text = it }"), "tag.text = it"),
                case("a part that a let builds", card("set(text) { count.text = it }"), "count.text = it"),
                case(
                    "a part that a qualified constructor builds",
                    card("set(text) { qualified.text = it }"),
                    "qualified.text = it",
                ),
                case(
                    "a part that a source helper function builds",
                    card("set(text) { made.text = it }"),
                    "made.text = it",
                ),
                case(
                    "a part that a source helper function and an apply build",
                    card("set(text) { madeThenApplied.text = it }"),
                    "madeThenApplied.text = it",
                ),
                case(
                    "a part that a member helper builds",
                    of("Helped()", "set(text) { title.text = it }"),
                    "title.text = it",
                ),
                case(
                    "a part of a generic source superclass",
                    of("Specific()", "set(text) { caption.text = it }"),
                    "caption.text = it",
                ),
                case("a safe-qualified assignment", card("set(text) { title?.text = it }"), "title?.text = it"),
                case("a safe-qualified setter call", card("set(text) { title?.setText(it) }"), "title?.setText(it)"),
                case("a var part that has an initializer", card("set(text) { delta.text = it }"), "delta.text = it"),
                case(
                    "a part of a source superclass",
                    of("SubCard()", "set(text) { caption.text = it }"),
                    "caption.text = it",
                ),
            )

        private fun reportedRepeats(): List<Arguments> =
            listOf(
                case(
                    "each part once across two blocks",
                    card("set(text) { title.text = it }; update(flag) { title.isVisible = it; body.text = text }"),
                    "title.text = it",
                    "body.text = text",
                ),
                case(
                    "the same part in two node calls",
                    inShow(
                        "SwingNode(factory = { Card() }, update = { set(text) { title.text = it } })\n" +
                            "MenuNode(factory = { Card() }, update = { set(text) { title.text = it } })",
                    ),
                    "title.text = it #1",
                    "title.text = it #2",
                ),
            )

        @JvmStatic
        fun passed(): List<Arguments> = passedComponents() + passedWrites()

        private fun passedComponents(): List<Arguments> =
            listOf(
                case("a component class from a library", of("LibraryCard()", "set(text) { title.text = it }")),
                case("a helper that another object declares", of("Remote(Source())", "set(text) { title.text = it }")),
                case(
                    "a part that a constructor parameter hands in",
                    of("Handed(JLabel())", "set(text) { title.text = it }"),
                ),
                case("a part that is assigned later", of("Later()", "set(text) { title.text = it }")),
                case("a part that a delegate supplies", of("Lazy()", "set(text) { title.text = it }")),
                case("an initializer that calls a JDK function", card("set(text) { borrowed.text = it }")),
                case(
                    "a helper that the component inherits from the JDK",
                    of("Tipped()", "set(text) { tip.tipText = it }"),
                ),
                case("a top-level helper of another file", of("Elsewhere()", "set(text) { title.text = it }")),
                case("an extension helper", of("Extended()", "set(text) { title.text = it }")),
            )

        private fun passedWrites(): List<Arguments> =
            listOf(
                case("a write through apply", card("set(text) { title.apply { text = it } }")),
                case("a write through with", card("set(text) { with(title) { text = it } }")),
                case("a write to the component itself", card("set(text) { name = it }")),
                case("a write to the component through this", card("set(text) { this.name = it }")),
                case("a read of a part", card("set(text) { val shown = title.text; name = shown }")),
                case("a getter call on a part", card("set(text) { title.getText() }")),
                case("a write in a reconcile block of the updater", card("reconcile { title.text = text }")),
                case("a write in an init block of the updater", card("init { title.text = text }")),
                case("a setter call that does not resolve", card("set(text) { title.setMissing(it) }")),
                case("an assignment that does not resolve", card("set(text) { title.missing = it }")),
                case(
                    "a block of an updater that another node's update captured",
                    card(
                        "val outer = this; " +
                            "SwingNode(factory = { Card() }, update = { outer.set(text) { title.text = it } })",
                    ),
                ),
                case("a part of another object", card("set(text) { Parent().title.text = it }")),
                case(
                    "a part of another object in a nested lambda",
                    card("set(text) { run { Parent().title.text = it } }"),
                ),
            )
    }
}
