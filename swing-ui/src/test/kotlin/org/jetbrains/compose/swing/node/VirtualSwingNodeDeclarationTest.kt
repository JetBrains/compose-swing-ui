// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.assumeKeyboardFocusIsPossible
import org.jetbrains.compose.swing.assumeWindowBecomesFocused
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.FocusRequester
import org.jetbrains.compose.swing.modifier.interaction.focusRequester
import org.jetbrains.compose.swing.modifier.interaction.rememberFocusRequester
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.style.NestedProbe
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Group declarations merge with child declarations and stop at component boundaries; unchanged declarations are not
 * published again, and a changed group declares each standing component once.
 */
class VirtualSwingNodeDeclarationTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeDeclarationCases#declarationReaches")
    fun groupDeclarationsReachTheirComponents(
        case: String,
        content: @Composable () -> Unit,
        expected: Map<String, List<String>>,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        setContent { SwingNode(factory = { host }, content = content) }

        for ((text, tags) in expected) {
            onNodeWithText(text).assertProperty(tags, "$case: $text") { host.recording.tagsOf(this) }
        }
    }

    @Test
    fun groupDeclarationsStopAtTheFirstComponent() = runComposeSwingTest {
        val outer = MeasuringHost()
        val inner = MeasuringHost()
        setContent {
            SwingNode(factory = {
                outer
            }) { VirtualSwingNode(SwingModifier.mark("A")) { SwingNode(factory = { inner }) { Label("x") } } }
        }

        assertEquals(listOf("A"), outer.recording.tagsOf(inner))
        onNodeWithText("x").assertProperty(emptyList()) { inner.recording.tagsOf(this) }
    }

    @Test
    fun aGroupConstraintReachesARawLayoutManager() = runComposeSwingTest {
        val layout = GridBagLayout()
        val constraints =
            GridBagConstraints().apply {
                gridx = 3
                gridy = 4
            }
        setContent {
            SwingNode(factory = { JPanel(layout) }) {
                VirtualSwingNode(SwingModifier.layoutConstraint(constraints)) {
                    Label("a")
                    Label("b")
                }
            }
        }

        for (text in listOf("a", "b")) {
            onNodeWithText(text).assertProperty(3 to 4, text) {
                layout.getConstraints(this).run { gridx to gridy }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeDeclarationCases#parentDataReplacements")
    fun parentDataKeysAreReplacedAcrossAllGroupsBeforeProtocolValidation(
        case: String,
        content: @Composable () -> Unit,
        expected: List<String>,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        setContent { SwingNode(factory = { host }, content = content) }

        assertEquals(listOf("a"), host.texts(), case)
        onNodeWithText("a").assertProperty(expected, case) { host.recording.parentDataOf(this) }
    }

    @ExclusiveWindowSystem
    @Test
    fun changingTheGroupModifierKeepsTheFocusedChildFocused() {
        assumeKeyboardFocusIsPossible()
        runComposeSwingTest {
            val host = MeasuringHost()
            var tag by mutableStateOf("A")
            lateinit var anchor: FocusRequester
            lateinit var requester: FocusRequester
            setContent {
                Window(onCloseRequest = {}, title = "virtual-node-focus-test") {
                    anchor = rememberFocusRequester()
                    requester = rememberFocusRequester()
                    SwingNode(factory = { host }) {
                        TextField("anchor", onValueChange = {}, modifier = SwingModifier.focusRequester(anchor))
                        VirtualSwingNode(SwingModifier.mark(tag)) {
                            TextField("field", onValueChange = {}, modifier = SwingModifier.focusRequester(requester))
                        }
                    }
                }
            }
            assumeWindowBecomesFocused(SwingUtilities.getWindowAncestor(host) as JFrame)
            val anchorField = host.components[0] as JTextField
            val field = host.components[1] as JTextField
            // A window gives its only focusable component the keyboard by itself, and Swing refuses a request for the
            // current focus owner, so the field takes the keyboard from another owner.
            anchor.requestFocus()
            waitUntil { anchorField.isFocusOwner }
            assertTrue(requester.requestFocus(), "the field takes the keyboard before the change")
            waitUntil { field.isFocusOwner }

            tag = "B"
            awaitIdle()

            assertEquals(listOf("B"), host.recording.tagsOf(field))
            assertTrue(field.isFocusOwner, "the field keeps the keyboard")
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeDeclarationCases#groupDeclarationChangeRows")
    internal fun aGroupChangeDeclaresEachStandingComponentOnce(
        case: String,
        content: @Composable (String) -> Unit,
        expected: List<List<String>>,
        current: List<List<String>>,
        change: GroupDeclarationChange,
    ) = runComposeSwingTest {
        val host = MeasuringHost()
        var suffix by mutableStateOf("0")
        setContent { SwingNode(factory = { host }) { content(suffix) } }
        assertEquals(change.beforeTexts, host.texts(), case)
        assertEquals(change.beforeTags, host.components.map { host.recording.tagsOf(it) }, case)
        assertEquals(change.data.before, host.components.map { host.recording.parentDataOf(it) }, case)
        host.recording.declarations.clear()
        host.recording.removals.clear()

        suffix = "1"
        awaitIdle()

        assertEquals(expected, host.recording.declarations.map { it.tags }, case)
        assertEquals(change.afterTexts, host.texts(), case)
        assertEquals(change.data.after, host.components.map { host.recording.parentDataOf(it) }, case)
        assertEquals(change.data.published, host.recording.declarations.map { it.parentData }, case)
        assertEquals(current, host.components.map { host.recording.tagsOf(it) }, case)
        assertEquals(
            change.removedTexts,
            host.recording.removals
                .toTypedArray()
                .texts(),
            case,
        )
    }

    @ParameterizedTest(name = "restore placement: {0}")
    @ValueSource(booleans = [true, false])
    fun reapplyingAStyledDeclarationMergesStyleOnlyForPublication(restore: Boolean) = runComposeSwingTest {
        val host = MeasuringHost()
        var keyReads = 0
        val counted =
            object : ParentLayoutElement {
                override val parentProtocol: ParentProtocol get() = MeasuringProtocol
                override val key: Any
                    get() {
                        keyReads++
                        return "counted"
                    }
            }
        var changed by mutableStateOf(false)
        setContent {
            StyledHost(host, "styled", MarkProperty) {
                SwingNode(
                    factory = { JLabel("a") },
                    modifier =
                        SwingModifier
                            .then(counted)
                            .then(Data("data", 1, MeasuringProtocol))
                            .then(NodeMark(if (changed && !restore) "after" else "before"))
                            .styleable(NestedProbe, SwingStyle),
                    update = {
                        if (changed && restore) reconcileWithChildren { restoreDeclaredPlacement() }
                    },
                )
            }
        }
        keyReads = 0
        host.recording.declarations.clear()

        changed = true
        awaitIdle()

        assertEquals(1, keyReads)
        assertEquals(1, host.recording.declarations.size)
    }
}

internal class GroupDeclarationChange(
    val beforeTexts: List<String>,
    val afterTexts: List<String>,
    val beforeTags: List<List<String>>,
    val data: GroupDeclarationData,
    val removedTexts: List<String>,
)

internal class GroupDeclarationData(
    val before: List<Any?>,
    val after: List<Any?>,
    val published: List<Any?>,
)

internal object VirtualSwingNodeDeclarationCases {
    @JvmStatic
    fun parentDataReplacements(): List<Arguments> {
        val protocol = parentProtocolOf("Q") { it is MeasuringHost }
        val childReplacement: @Composable () -> Unit = {
            VirtualSwingNode(
                SwingModifier then Data("group-P", 1, parentProtocolOf("P") { it is MeasuringHost }) then
                    Data("group-Q", 2, protocol),
            ) {
                Label("a", SwingModifier then Data("child-Q", 1, protocol))
            }
        }
        val innerReplacement: @Composable () -> Unit = {
            VirtualSwingNode(
                SwingModifier then Data("group-P", 1, parentProtocolOf("P") { it is MeasuringHost }) then
                    Data("group-Q", 2, protocol),
            ) {
                VirtualSwingNode(SwingModifier then Data("inner-Q", 1, protocol)) { Label("a") }
            }
        }
        return listOf(
            Arguments.of("replacement by the child", childReplacement, listOf("group-Q", "child-Q")),
            Arguments.of("replacement by an inner group", innerReplacement, listOf("group-Q", "inner-Q")),
        )
    }

    @JvmStatic
    fun groupDeclarationChangeRows(): List<Arguments> = groupDeclarationChanges() +
        inheritedGroupDeclarationChanges() +
        reorderedGroupDeclarationChanges() +
        childAndGroupDeclarationChanges()

    private fun groupDeclarationChanges(): List<Arguments> {
        val overridden: @Composable (String) -> Unit = { suffix ->
            VirtualSwingNode(SwingModifier.mark("group$suffix")) {
                Label("a", SwingModifier.mark("own"))
                Label("b")
            }
        }
        val nested: @Composable (String) -> Unit = { suffix ->
            VirtualSwingNode(SwingModifier.mark("outer$suffix")) {
                Label("a")
                VirtualSwingNode(SwingModifier.mark("inner$suffix", slot = 2)) { Label("b") }
            }
        }
        return listOf(
            Arguments.of(
                "one child replaces the changed group declaration and its sibling inherits it",
                overridden,
                listOf(listOf("own"), listOf("group1")),
                listOf(listOf("own"), listOf("group1")),
                GroupDeclarationChange(
                    listOf("a", "b"),
                    listOf("a", "b"),
                    listOf(listOf("own"), listOf("group0")),
                    GroupDeclarationData(
                        listOf(null, null),
                        listOf(null, null),
                        listOf(null, null),
                    ),
                    emptyList<String>(),
                ),
            ),
            Arguments.of(
                "outer and inner declarations change together",
                nested,
                listOf(listOf("outer1"), listOf("outer1", "inner1")),
                listOf(listOf("outer1"), listOf("outer1", "inner1")),
                GroupDeclarationChange(
                    listOf("a", "b"),
                    listOf("a", "b"),
                    listOf(listOf("outer0"), listOf("outer0", "inner0")),
                    GroupDeclarationData(
                        listOf(null, null),
                        listOf(null, null),
                        listOf(null, null),
                    ),
                    emptyList<String>(),
                ),
            ),
        )
    }

    private fun inheritedGroupDeclarationChanges(): List<Arguments> {
        val inherited: @Composable (String) -> Unit = { suffix ->
            VirtualSwingNode(SwingModifier.mark("group$suffix")) { Label("a") }
        }
        val extended: @Composable (String) -> Unit = { suffix ->
            VirtualSwingNode(SwingModifier.mark("group$suffix")) { Label("a", SwingModifier.mark("own", slot = 3)) }
        }
        return listOf(
            Arguments.of(
                "a child without declarations inherits the changed group declaration",
                inherited,
                listOf(listOf("group1")),
                listOf(listOf("group1")),
                GroupDeclarationChange(
                    listOf("a"),
                    listOf("a"),
                    listOf(listOf("group0")),
                    unchangedData(),
                    emptyList(),
                ),
            ),
            Arguments.of(
                "a child declaring another key keeps its declaration after the changed group declaration",
                extended,
                listOf(listOf("group1", "own")),
                listOf(listOf("group1", "own")),
                GroupDeclarationChange(
                    listOf("a"),
                    listOf("a"),
                    listOf(listOf("group0", "own")),
                    unchangedData(),
                    emptyList(),
                ),
            ),
        )
    }

    private fun reorderedGroupDeclarationChanges(): List<Arguments> {
        val reordered: @Composable (String) -> Unit = { suffix ->
            VirtualSwingNode(SwingModifier.mark(if (suffix == "0") "A" else "B")) {
                for (id in if (suffix == "0") listOf(1, 2) else listOf(2, 1)) {
                    key(id) { Label("g$id") }
                }
            }
        }
        val retainedKeys: @Composable (String) -> Unit = { suffix ->
            VirtualSwingNode(SwingModifier then Data("group", 1, MeasuringProtocol)) {
                Label(
                    "a",
                    SwingModifier then Data("child", if (suffix == "0") 1 else 3, MeasuringProtocol) then
                        Data("last", 2, MeasuringProtocol),
                )
            }
        }
        return listOf(
            Arguments.of(
                "reorder children while the group changes",
                reordered,
                listOf(listOf("B"), listOf("B")),
                listOf(listOf("B"), listOf("B")),
                GroupDeclarationChange(
                    listOf("g1", "g2"),
                    listOf("g2", "g1"),
                    listOf(listOf("A"), listOf("A")),
                    GroupDeclarationData(
                        listOf(null, null),
                        listOf(null, null),
                        listOf(null, null),
                    ),
                    listOf("g2"),
                ),
            ),
            Arguments.of(
                "retained keys change while own parent data stays equal",
                retainedKeys,
                listOf(emptyList<String>()),
                listOf(emptyList<String>()),
                GroupDeclarationChange(
                    listOf("a"),
                    listOf("a"),
                    listOf(emptyList<String>()),
                    GroupDeclarationData(
                        listOf(listOf("child", "last")),
                        listOf(listOf("group", "child", "last")),
                        listOf(listOf("group", "child", "last")),
                    ),
                    emptyList<String>(),
                ),
            ),
        )
    }

    private fun childAndGroupDeclarationChanges(): List<Arguments> {
        val replaced: @Composable (String) -> Unit = { suffix ->
            VirtualSwingNode(SwingModifier.mark("outer$suffix")) {
                VirtualSwingNode(SwingModifier.mark("inner$suffix")) {
                    Label("a", SwingModifier.mark("own$suffix"))
                }
            }
        }
        val combined: @Composable (String) -> Unit = { suffix ->
            VirtualSwingNode(SwingModifier.mark("outer$suffix")) {
                VirtualSwingNode(SwingModifier.mark("inner$suffix", slot = 2)) {
                    Label("a", SwingModifier.mark("own$suffix", slot = 3))
                }
            }
        }
        return listOf(
            Arguments.of(
                "the child replaces both groups",
                replaced,
                listOf(listOf("own1")),
                listOf(listOf("own1")),
                GroupDeclarationChange(listOf("a"), listOf("a"), listOf(listOf("own0")), unchangedData(), emptyList()),
            ),
            Arguments.of(
                "the child extends both groups",
                combined,
                listOf(listOf("outer1", "inner1", "own1")),
                listOf(listOf("outer1", "inner1", "own1")),
                GroupDeclarationChange(
                    listOf("a"),
                    listOf("a"),
                    listOf(listOf("outer0", "inner0", "own0")),
                    unchangedData(),
                    emptyList(),
                ),
            ),
        )
    }

    private fun unchangedData() = GroupDeclarationData(listOf(null), listOf(null), listOf(null))

    @JvmStatic
    fun declarationReaches(): List<Arguments> {
        val eachComponent: @Composable () -> Unit = {
            VirtualSwingNode(SwingModifier.mark("A")) {
                Label("a")
                Label("b")
            }
        }
        val nested: @Composable () -> Unit = {
            VirtualSwingNode(SwingModifier.mark("A", slot = 1)) {
                VirtualSwingNode(SwingModifier.mark("B", slot = 2)) {
                    Label("c", SwingModifier.mark("C", slot = 3))
                }
            }
        }
        val replaced: @Composable () -> Unit = {
            VirtualSwingNode(SwingModifier.mark("A")) {
                Label("c", SwingModifier.mark("B"))
                Label("d")
            }
        }
        return listOf(
            Arguments.of(
                "each component of the group",
                eachComponent,
                mapOf("a" to listOf("A"), "b" to listOf("A")),
            ),
            Arguments.of(
                "outermost group first, the component's own last",
                nested,
                mapOf("c" to listOf("A", "B", "C")),
            ),
            Arguments.of(
                "a component's own declaration of the key replaces the group's",
                replaced,
                mapOf("c" to listOf("B"), "d" to listOf("A")),
            ),
        )
    }
}
