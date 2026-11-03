// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onChildAt
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Where a moved content starts and ends; a null [destinationHost] keeps it on the source host. */
class MoveRoute(
    val sourceProtocol: String,
    val destinationProtocol: String,
    val destinationHost: String?,
)

/** The content to move, the texts it adds, and the declarations its components carry before and after the move. */
class MovedContent(
    val content: @Composable () -> Unit,
    val texts: List<String>,
    val sourceTags: List<String>,
    val destinationTags: List<String>,
    val wrapped: Boolean = true,
)

/** Relocated groups attach their components at the destination in composition order. */
class VirtualSwingNodeRelocationTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("movableMoves")
    fun movableContentMovesBetweenGroupsAndHosts(
        case: String,
        route: MoveRoute,
        moved: MovedContent,
    ) = runComposeSwingTest {
        val shared = route.destinationHost == null
        var inSecond by mutableStateOf(false)
        setContent {
            val content = remember { movableContentOf(moved.content) }
            val slot: @Composable (String, String, Boolean) -> Unit = { tag, protocol, active ->
                if (moved.wrapped) {
                    VirtualSwingNode(SwingModifier.mark(tag, protocol = protocolOf(protocol))) { if (active) content() }
                } else if (active) {
                    content()
                }
            }
            val destination: @Composable () -> Unit = {
                Label("b0")
                slot("B", route.destinationProtocol, inSecond)
                Label("b1")
            }
            SwingNode(factory = { MeasuringHost(route.sourceProtocol) }) {
                Label("a0")
                slot("A", route.sourceProtocol, !inSecond)
                if (shared) destination()
            }
            if (!shared) {
                SwingNode(
                    factory = { MeasuringHost(checkNotNull(route.destinationHost)) },
                    content = destination,
                )
            }
        }
        val first = onAllNodesOfType<MeasuringHost>()[0].assertIsOfType<MeasuringHost>()
        val second = if (shared) first else onAllNodesOfType<MeasuringHost>()[1].assertIsOfType<MeasuringHost>()
        val sourceTexts = listOf("a0") + moved.texts + if (shared) listOf("b0", "b1") else emptyList<String>()
        first.assertProperty(sourceTexts, case) { texts() }
        first.assertProperty(sourceTexts.map { if (it in moved.texts) moved.sourceTags else emptyList() }, case) {
            components.map { recording.tagsOf(it) }
        }

        if (route.destinationProtocol == "R") {
            assertFailsWith<IllegalStateException> {
                inSecond = true
                awaitIdle()
            }
            return@runComposeSwingTest
        }
        inSecond = true
        awaitIdle()

        val arrived = listOf("b0") + moved.texts + listOf("b1")
        first.assertProperty(if (shared) listOf("a0") + arrived else listOf("a0"), case) { texts() }
        if (!shared) second.assertProperty(arrived, case) { texts() }
        val secondTexts = if (shared) listOf("a0") + arrived else arrived
        second.assertProperty(secondTexts.map { if (it in moved.texts) moved.destinationTags else emptyList() }, case) {
            components.map { recording.tagsOf(it) }
        }
        first.assertProperty(emptyList<String>(), case) { recording.tagsOf(components.first()) }
    }

    @ParameterizedTest(name = "two children: {0}")
    @ValueSource(booleans = [true, false])
    fun relocatingOneOrTwoChildrenKeepsTheirDestinationIndices(twoChildren: Boolean) = runComposeSwingTest {
        var moved by mutableStateOf(false)
        setContent {
            val a = remember { movableContentOf { Label("a") } }
            val b = remember { movableContentOf { Label("b") } }
            SwingNode(factory = { MeasuringHost() }) {
                if (!moved) {
                    a()
                    if (twoChildren) b()
                }
            }
            SwingNode(factory = { MeasuringHost() }) {
                Label("before")
                if (moved) a()
                Label("between")
                if (moved && twoChildren) b()
                Label("after")
            }
        }
        val first = onAllNodesOfType<MeasuringHost>()[0].fetch()
        val second = onAllNodesOfType<MeasuringHost>()[1].fetch()

        moved = true
        awaitIdle()

        assertEquals(emptyList(), first.texts())
        assertEquals(
            if (twoChildren) {
                listOf("before", "a", "between", "b", "after")
            } else {
                listOf("before", "a", "between", "after")
            },
            second.texts(),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("relocationOrders")
    fun movableContentMovesAGroupAndAComponentIntoItInOnePass(
        case: String,
        labelFirst: Boolean,
        destination: @Composable (@Composable () -> Unit) -> Unit,
        expected: List<String>,
    ) = runComposeSwingTest {
        var moved by mutableStateOf(false)
        setContent {
            val label = remember { movableContentOf { Label("m") } }
            val group =
                remember {
                    movableContentOf<@Composable () -> Unit> { inner ->
                        VirtualSwingNode(SwingModifier.mark("G")) {
                            destination(inner)
                        }
                    }
                }
            SwingNode(factory = { MeasuringHost() }) {
                if (!moved) {
                    if (labelFirst) label()
                    group {}
                    if (!labelFirst) label()
                }
            }
            SwingNode(factory = { MeasuringHost() }) {
                if (moved) group { label() }
            }
        }
        val first = onAllNodesOfType<MeasuringHost>()[0].fetch()
        val second = onAllNodesOfType<MeasuringHost>()[1].fetch()

        moved = true
        awaitIdle()

        first.assertLabels(emptyList(), "the old host")
        assertEquals(expected, second.texts(), case)
        assertEquals(listOf(listOf("G"), listOf("G")), second.components.map { second.recording.tagsOf(it) })
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("relocatedGroupChanges")
    fun changesInsideARelocatedGroupApplyWhenItIsPlaced(
        case: String,
        after: List<Int>,
        siblings: @Composable (Boolean) -> Unit,
        expected: List<String>,
    ) = runComposeSwingTest {
        var moved by mutableStateOf(false)
        var ids by mutableStateOf(listOf(1, 2, 3))
        setContent {
            val group =
                remember {
                    movableContentOf<List<Int>> { current ->
                        VirtualSwingNode(SwingModifier.mark("G")) {
                            for (id in current) key(id) { Label("g$id") }
                        }
                    }
                }
            SwingNode(factory = { MeasuringHost() }) {
                if (!moved) group(ids)
            }
            SwingNode(factory = { MeasuringHost() }) {
                if (moved) group(ids)
                siblings(moved)
            }
        }
        val first = onAllNodesOfType<MeasuringHost>()[0].fetch()
        val second = onAllNodesOfType<MeasuringHost>()[1].fetch()

        Snapshot.withMutableSnapshot {
            moved = true
            ids = after
        }
        awaitIdle()

        first.assertLabels(emptyList(), "the old host")
        second.assertLabels(expected, case)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("relocatedDescendantChanges")
    fun descendantsOfARelocatedGroupAreAttachedOnlyAtTheirDestination(
        case: String,
        content: @Composable (Boolean) -> Unit,
        before: List<String>,
        after: List<String>,
        parentData: List<Any?>,
    ) = runComposeSwingTest {
        var moved by mutableStateOf(false)
        setContent {
            val group = remember { movableContentOf<Boolean> { changed -> content(changed) } }
            SwingNode(factory = { MeasuringHost("P") }) {
                if (!moved) group(false)
            }
            SwingNode(factory = { MeasuringHost("Q") }) {
                if (moved) group(true)
            }
        }
        val departing = onAllNodesOfType<MeasuringHost>()[0].fetch()
        val arriving = onAllNodesOfType<MeasuringHost>()[1].fetch()
        assertEquals(before, departing.texts(), case)
        departing.recording.declarations.clear()
        arriving.recording.registrations.clear()

        moved = true
        awaitIdle()

        assertEquals(emptyList(), departing.texts(), case)
        assertEquals(after, arriving.texts(), case)
        assertEquals(emptyList(), departing.recording.declarations, case)
        assertEquals(after, arriving.recording.registrations.map { (it.component as JLabel).text }, case)
        assertEquals(parentData, arriving.components.map { arriving.recording.parentDataOf(it) }, case)
        assertEquals(emptyList(), arriving.recording.removals, case)
    }

    @ParameterizedTest(name = "nested source group: {0}")
    @ValueSource(booleans = [false, true])
    fun removingAChildFromAGroupUpdatesItsIndexInAnArrivingGroup(nested: Boolean) = runComposeSwingTest {
        var moved by mutableStateOf(false)
        setContent {
            val label = remember { movableContentOf { Label("m") } }
            val group =
                remember {
                    movableContentOf<@Composable () -> Unit> { content ->
                        VirtualSwingNode {
                            Label("prefix")
                            content()
                        }
                    }
                }
            Panel { if (!moved) group {} }
            Panel {
                if (moved) group { label() }
                if (moved) Label("new")
                VirtualSwingNode {
                    val content: @Composable () -> Unit = { if (!moved) label() }
                    if (nested) VirtualSwingNode(content = content) else content()
                    Label("tail")
                }
            }
        }
        onRoot()
            .onChildAt(0)
            .assertIsOfType<JPanel>()
            .assertProperty(listOf("prefix")) { components.texts() }
        onRoot()
            .onChildAt(1)
            .assertIsOfType<JPanel>()
            .assertProperty(listOf("m", "tail")) { components.texts() }

        moved = true
        awaitIdle()

        onRoot()
            .onChildAt(0)
            .assertIsOfType<JPanel>()
            .assertProperty(emptyList<String>()) { components.texts() }
        onRoot()
            .onChildAt(1)
            .assertIsOfType<JPanel>()
            .assertProperty(listOf("prefix", "m", "new", "tail")) { components.texts() }
    }

    @ParameterizedTest(name = "insert inside the component host during relocation: {0}")
    @ValueSource(booleans = [false, true])
    fun groupRelocationStopsAtTheComponentHost(insert: Boolean) = runComposeSwingTest {
        var moved by mutableStateOf(false)
        setContent {
            val group =
                remember {
                    movableContentOf<Boolean> { expanded ->
                        VirtualSwingNode {
                            VirtualSwingNode {
                                Panel {
                                    Label("inner-first")
                                    if (expanded) Label("inner-new")
                                    Label("inner-last")
                                }
                                Label("sibling")
                            }
                        }
                    }
                }
            Panel { if (!moved) group(false) }
            Panel { if (moved) group(insert) }
        }
        val departing = onRoot().onChildAt(0).assertIsOfType<JPanel>().fetch()
        val arriving = onRoot().onChildAt(1).assertIsOfType<JPanel>().fetch()
        val childHost = onNodeWithText("inner-first").onParent().assertIsOfType<JPanel>().fetch()

        val untouched = listOf("inner-first", "inner-last")
        val arrived = if (insert) listOf("inner-first", "inner-new", "inner-last") else untouched
        for ((atArriving, inner) in listOf(false to untouched, true to arrived, false to untouched)) {
            moved = atArriving
            awaitIdle()

            val host = if (atArriving) arriving else departing
            assertEquals(0, (if (atArriving) departing else arriving).componentCount)
            assertEquals(2, host.componentCount)
            assertEquals(childHost, host.getComponent(0))
            onNodeWithText("sibling").assertProperty(host) { parent }
            assertEquals(inner, childHost.components.texts())
        }
    }

    companion object {
        @JvmStatic
        fun movableMoves(): List<Arguments> {
            val label =
                MovedContent({ Label("m") }, listOf("m"), sourceTags = listOf("A"), destinationTags = listOf("B"))
            return listOf(
                Arguments.of("same host", MoveRoute("P", "P", null), label),
                Arguments.of("different accepted protocol", MoveRoute("P", "Q", "Q"), label),
                Arguments.of("refused protocol", MoveRoute("P", "R", "Q"), label),
                Arguments.of(
                    "a group between hosts",
                    MoveRoute("", "", ""),
                    MovedContent({ Group("G") }, listOf("g1", "g2"), listOf("G"), listOf("G"), wrapped = false),
                ),
            )
        }

        @JvmStatic
        fun relocationOrders(): List<Arguments> {
            val first: @Composable (@Composable () -> Unit) -> Unit = { inner ->
                inner()
                Label("g1")
            }
            val last: @Composable (@Composable () -> Unit) -> Unit = { inner ->
                Label("g1")
                inner()
            }
            return listOf(
                Arguments.of("label record first, destination first", true, first, listOf("m", "g1")),
                Arguments.of("label record first, destination last", true, last, listOf("g1", "m")),
                Arguments.of("group record first, destination first", false, first, listOf("m", "g1")),
                Arguments.of("group record first, destination last", false, last, listOf("g1", "m")),
            )
        }

        @JvmStatic
        fun relocatedGroupChanges(): List<Arguments> {
            val noSiblings: @Composable (Boolean) -> Unit = {}
            val insertSibling: @Composable (Boolean) -> Unit = { moved ->
                if (moved) Label("sibling")
                Label("tail")
            }
            val retainedSiblings: @Composable (Boolean) -> Unit = {
                Label("sibling")
                Label("tail")
            }
            val rebuildSibling: @Composable (Boolean) -> Unit = { moved ->
                key(moved) { Label("sibling") }
                Label("tail")
            }
            return listOf(
                Arguments.of(
                    "rebuild a sibling while relocating and changing inner keys",
                    listOf(3, 1, 4),
                    rebuildSibling,
                    listOf("g3", "g1", "g4", "sibling", "tail"),
                ),
                Arguments.of("change inner keys", listOf(3, 1, 4), noSiblings, listOf("g3", "g1", "g4")),
                Arguments.of(
                    "remove multiple relocated children with retained siblings",
                    listOf(3),
                    retainedSiblings,
                    listOf("g3", "sibling", "tail"),
                ),
                Arguments.of(
                    "insert a sibling and a group child",
                    listOf(1, 2, 3, 4),
                    insertSibling,
                    listOf("g1", "g2", "g3", "g4", "sibling", "tail"),
                ),
            )
        }

        @JvmStatic
        fun relocatedDescendantChanges(): List<Arguments> {
            val insert: @Composable (Boolean) -> Unit = { changed ->
                VirtualSwingNode {
                    VirtualSwingNode {
                        Label("g1")
                        if (changed) Label("g2")
                        Label("g3")
                    }
                }
            }
            val changeProtocol: @Composable (Boolean) -> Unit = { changed ->
                VirtualSwingNode {
                    VirtualSwingNode {
                        Label(
                            "g1",
                            SwingModifier then
                                Data(
                                    if (changed) "Q" else "P",
                                    1,
                                    protocolOf(if (changed) "Q" else "P"),
                                ),
                        )
                    }
                }
            }
            return listOf(
                Arguments.of(
                    "insert into a nested group during relocation",
                    insert,
                    listOf("g1", "g3"),
                    listOf("g1", "g2", "g3"),
                    listOf(null, null, null),
                ),
                Arguments.of(
                    "change a descendant protocol during relocation",
                    changeProtocol,
                    listOf("g1"),
                    listOf("g1"),
                    listOf(listOf("Q")),
                ),
            )
        }
    }
}
