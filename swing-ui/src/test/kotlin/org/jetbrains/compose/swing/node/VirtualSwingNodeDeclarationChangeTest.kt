// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.LayoutDeclaration
import org.jetbrains.compose.swing.NestedContentHost
import org.jetbrains.compose.swing.OnDemandComposition
import org.jetbrains.compose.swing.RecordingLayout
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.applyModifierDiff
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.style.NestedProbe
import org.jetbrains.compose.swing.style.PlainProbeContainer
import org.jetbrains.compose.swing.style.Probes
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.SwingStyleProperty
import org.jetbrains.compose.swing.style.stylePropertyOf
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.awt.LayoutManager
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Changes to groups, children, layout nodes, styles and protocols reach the standing components without superseded
 * or repeated publications, across removals, replacements, moves between compositions and deactivation.
 */
class VirtualSwingNodeDeclarationChangeTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeChangeCases#childrenChangedWithTheirGroup")
    internal fun aChildAddedOrReleasedWhileItsGroupChangesIsDeclaredUnderTheFinalGroup(
        case: String,
        newChange: () -> ChildChangeWithGroup,
    ) = runComposeSwingTest {
        val change = newChange()
        val hostTag = "changed-host"
        var changed by mutableStateOf(false)
        setContent {
            SwingNode(
                factory = { change.layout?.let { JPanel(it) } ?: MeasuringHost() },
                modifier = SwingModifier.testTag(hostTag),
            ) { change.content(changed) }
        }
        // The host's recording layout and the identity of the original children are not visible to the harness.
        val host = onNodeWithTag(hostTag).fetch<JPanel>()
        assertEquals(change.initialTexts, host.components.texts(), case)
        val original = host.components.toList()
        change.beforeChange(host)

        Snapshot.withMutableSnapshot { changed = true }
        awaitIdle()

        assertEquals(change.after.texts, host.components.texts(), case)
        change.after.verify(host, original)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeChangeCases#layoutNodeRemovals")
    fun removingALayoutNodeRedeclaresTheGroup(
        case: String,
        groupKey: Any,
        initialGroup: String,
        currentGroup: String,
        change: LayoutNodeRemovalChange,
    ) = runComposeSwingTest {
        var removed by mutableStateOf(false)
        setContent {
            SwingNode(factory = { MeasuringHost() }) {
                VirtualSwingNode(
                    SwingModifier then
                        Mark(if (removed) currentGroup else initialGroup, groupKey, false, MeasuringProtocol),
                ) {
                    Label("a", if (removed) SwingModifier else SwingModifier then NodeMark("own"))
                }
            }
        }
        val host = onNodeOfType<MeasuringHost>().fetch()
        val label = host.components.single()
        assertEquals(change.before, host.recording.tagsOf(label), case)
        host.recording.declarations.clear()
        host.recording.removals.clear()

        removed = true
        awaitIdle()

        assertEquals(listOf("a"), host.texts(), case)
        assertEquals(listOf(change.expected), host.components.map { host.recording.tagsOf(it) }, case)
        assertEquals(change.publications, host.recording.declarations.map { it.tags }, case)
        assertEquals(List(change.publications.size) { label }, host.recording.declarations.map { it.component }, case)
        assertEquals(emptyList(), host.recording.removals, case)
        host.doLayout()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeChangeCases#layoutNodeTypeReplacements")
    fun replacingALayoutNodeTypePublishesOnDetachAndAfterReplacement(
        case: String,
        groupKey: Any,
        before: List<String>,
        groupKeyReplaced: Boolean,
    ) = runComposeSwingTest {
        lateinit var replacementNode: RecordingLayoutNode
        val replacement =
            RecordingLayoutNodeElement("replacement", mutableListOf(), onAttach = { replacementNode = it })
        var changed by mutableStateOf(false)
        setContent {
            SwingNode(factory = { MeasuringHost() }) {
                VirtualSwingNode(SwingModifier then Mark("G", groupKey, false, MeasuringProtocol)) {
                    Label("a", SwingModifier then (if (changed) replacement else NodeMark("own")))
                }
            }
        }
        val host = onNodeOfType<MeasuringHost>().fetch()
        val label = host.components.single()
        assertEquals(before, host.recording.tagsOf(label), case)
        host.recording.declarations.clear()
        host.recording.removals.clear()

        changed = true
        awaitIdle()

        assertEquals(listOf("a"), host.texts(), case)
        assertEquals(
            listOf(
                if (groupKeyReplaced) emptyList() else listOf(Mark("G", groupKey, false, MeasuringProtocol)),
                listOf(Mark("G", groupKey, false, MeasuringProtocol), replacementNode),
            ),
            host.recording.declarations.map { it.elements },
            case,
        )
        assertEquals(listOf(label, label), host.recording.declarations.map { it.component }, case)
        assertEquals(
            listOf(Mark("G", groupKey, false, MeasuringProtocol), replacementNode),
            host.recording.elementsOf(label),
            case,
        )
        assertEquals(emptyList(), host.recording.removals, case)
    }

    @ParameterizedTest(name = "changed resolution property: {0}")
    @ValueSource(strings = ["key", "additive", "protocol", "inheritable"])
    fun updatingRetainedLayoutNodesResolvesTheirCurrentMetadata(property: String) = runComposeSwingTest {
        val alternate = parentProtocolOf("alternate measuring parent") { it is MeasuringHost }
        var version by mutableIntStateOf(0)
        var styled by mutableStateOf(false)
        var removed by mutableStateOf(false)
        setContent {
            StyledHost("styled", ReplacingMarkProperty) {
                VirtualSwingNode(SwingModifier.mark("group", slot = 2)) {
                    val middle = NodeMark("middle-$version", key = 2)
                    SwingNode(
                        factory = { JLabel("a") },
                        modifier =
                            SwingModifier then NodeMark("first-$version", key = 1) then
                                (
                                    if (removed) {
                                        SwingModifier
                                    } else if (version == 0) {
                                        middle
                                    } else {
                                        middle.withChangedResolution(property, alternate)
                                    }
                                ) then
                                NodeMark("last-$version", key = 3) then
                                (if (styled) SwingModifier.styleable(NestedProbe, SwingStyle) else SwingModifier),
                    )
                }
            }
        }
        val host = hostOf()
        val label = host.components.single()
        val nodes = host.recording.elementsOf(label)
        assertEquals(listOf("first-0", "middle-0", "last-0"), host.recording.tagsOf(label))
        host.recording.declarations.clear()

        version = 1
        awaitIdle()

        val grouped = property == "key" || property == "additive"
        val expected = (if (grouped) listOf("group") else emptyList()) + listOf("first-1", "middle-1", "last-1")
        val standing = listOfNotNull(Mark("group", 2, false, MeasuringProtocol).takeIf { grouped }) + nodes
        assertEquals(expected, host.recording.tagsOf(label))
        assertEquals(standing, host.recording.elementsOf(label))
        assertEquals(listOf(expected), host.recording.declarations.map { it.tags })
        assertEquals(emptyList(), host.recording.removals)

        version = 2
        awaitIdle()
        assertEquals(expected.map { it.replace("-1", "-2") }, host.recording.tagsOf(label))
        assertEquals(standing, host.recording.elementsOf(label))

        styled = true
        awaitIdle()
        assertEquals(expected.dropLast(3) + listOf("middle-2", "last-2", "styled"), host.recording.tagsOf(label))

        styled = false
        awaitIdle()
        assertEquals(expected.map { it.replace("-1", "-2") }, host.recording.tagsOf(label))
        assertEquals(standing, host.recording.elementsOf(label))

        removed = true
        awaitIdle()
        assertEquals(listOf("group", "first-2", "last-2"), host.recording.tagsOf(label))
    }

    @Test
    fun aFailedLayoutNodeReplacementKeepsTheStandingRecordsDeclarations() = runComposeSwingTest {
        val owner = TestCompositionOwner()
        val host = MeasuringHost()
        val root = CreatedNodeHolder(host).attachedTo(owner)
        val group =
            VirtualNodeHolder().apply {
                parent = root
                storeGroupDeclarations(groupDeclarationsOf(SwingModifier.mark("group", slot = 2)))
            }
        root.children += group
        val child = CreatedNodeHolder(JLabel("a")).apply { parent = group }
        group.children += child
        group.attachedTo(owner)
        host.add(child.component)
        child.declaration.attachedUnder(host, child.declaration.checkAttachableUnder(host))
        val initial =
            SwingModifier then NodeMark("first-0", key = 1) then
                NodeMark("middle", key = 2) then NodeMark("last", key = 3)
        try {
            child.applyModifierDiff(initial)
            val nodes = host.recording.elementsOf(child.component)
            host.recording.declarations.clear()
            val replacement =
                SwingModifier then NodeMark("first-1", key = 1) then
                    RecordingLayoutNodeElement("refused", mutableListOf(), key = 4) {
                        error("attachment refused")
                    } then NodeMark("last", key = 3)

            assertFailsWith<IllegalStateException> { child.applyModifierDiff(replacement) }

            assertEquals(nodes, host.recording.elementsOf(child.component))
            assertEquals(listOf("first-1", "middle", "last"), host.recording.tagsOf(child.component))
            assertEquals(listOf(LayoutDeclaration(child.component, null, nodes)), host.recording.declarations)

            child.applyModifierDiff(initial)
            assertEquals(listOf("first-0", "middle", "last"), host.recording.tagsOf(child.component))
        } finally {
            root.attachedTo(null)
            owner.dispose()
        }
    }

    @Test
    fun movingAChildAcrossCompositionsPublishesItsDestinationProtocolAfterLayoutNodeTeardown() = runComposeSwingTest {
        val p = parentProtocolOf("P") { it.layout is MeasurementLayoutManager }
        val q = parentProtocolOf("Q") { it.layout is MeasurementLayoutManager }
        var moved by mutableStateOf(false)
        setContent {
            val content =
                remember {
                    movableContentOf<ParentProtocol> { protocol ->
                        Label("a", SwingModifier then NodeMark("own") then Data(protocol.description, 2, protocol))
                    }
                }
            NestedContentHost {
                SwingNode(factory = { MeasuringHost() }, modifier = SwingModifier.testTag(DEPARTING_TAG)) {
                    VirtualSwingNode(SwingModifier then Data("group-P", 1, p)) { if (!moved) content(p) }
                }
            }
            NestedContentHost {
                SwingNode(factory = { MeasuringHost() }, modifier = SwingModifier.testTag("arriving")) {
                    VirtualSwingNode(SwingModifier then Data("group-Q", 1, q)) { if (moved) content(q) }
                }
            }
        }
        awaitIdle()
        // The recording layouts are not visible to the harness, and the moved label's identity is compared.
        val departing = onNodeWithTag(DEPARTING_TAG).fetch<MeasuringHost>()
        val arriving = onNodeWithTag("arriving").fetch<MeasuringHost>()
        val label = departing.components.single()
        assertEquals(listOf("group-P", "P"), departing.recording.parentDataOf(label))
        departing.recording.declarations.clear()
        arriving.recording.declarations.clear()

        moved = true
        awaitIdle()

        assertEquals(emptyList(), departing.components.toList())
        assertEquals(listOf(label), arriving.components.toList())
        assertEquals(emptyList(), departing.recording.declarations)
        assertEquals(listOf(listOf("group-Q", "Q")), arriving.recording.declarations.map { it.parentData })
        assertEquals(listOf(listOf("own")), arriving.recording.declarations.map { it.tags })
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeChangeCases#simultaneousChanges")
    fun aGroupChangePublishesNoSupersededGroup(
        case: String,
        content: @Composable (
            Boolean,
            String,
            String,
            String,
        ) -> Unit,
        change: SimultaneousChange,
    ) = runComposeSwingTest {
        var changed by mutableStateOf(false)
        var group by mutableStateOf("g0")
        var style by mutableStateOf("s0")
        var own by mutableStateOf("o0")
        setContent {
            content(changed, group, own, style)
        }
        // The recording layouts are not visible to the harness.
        val host = hostOf()
        val departing = onAllNodesWithTag(DEPARTING_TAG).fetchAll().map { it as MeasuringHost }
        host.recording.declarations.clear()
        departing.forEach { it.recording.declarations.clear() }

        Snapshot.withMutableSnapshot {
            changed = true
            group = change.group
            style = change.style
            own = change.own
        }
        awaitIdle()

        assertEquals(
            change.expected,
            host.recording.declarations
                .lastOrNull()
                ?.tags,
            case,
        )
        assertTrue(
            host.recording.declarations.none { declaration ->
                declaration.tags.any { it in change.forbidden }
            },
            case,
        )
        change.publications?.let { assertEquals(it, host.recording.declarations.size, case) }
        assertEquals(emptyList(), departing.flatMap { it.recording.declarations }, case)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeChangeCases#releasedLayoutNodeChildren")
    internal fun releasingAChildWithdrawsItsLayoutNodesLastPublishedDeclaration(
        case: String,
        content: @Composable () -> Unit,
        before: List<String>,
    ) = runComposeSwingTest {
        lateinit var context: CompositionContext
        setContent { context = rememberCompositionContext() }
        val composition = OnDemandComposition(context) { content() }
        val top = composition.recompose {}
        val host = top as? MeasuringHost ?: (top as Container).components.single() as MeasuringHost
        val child = host.components.single()
        try {
            assertEquals(before, host.recording.tagsOf(child), case)
            host.recording.declarations.clear()
        } finally {
            composition.dispose()
        }

        assertEquals(listOf(emptyList<String>()), host.recording.declarations.map { it.tags }, case)
        assertEquals(listOf(child), host.recording.declarations.map { it.component }, case)
        assertEquals(emptyList(), host.recording.elementsOf(child), case)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeChangeCases#nodeWritesWithGroupChanges")
    fun aLayoutNodeWrittenInThePassThatChangesItsGroupIsReceivedAfterTheWrite(
        case: String,
        groupKey: Any,
        styleOwned: Boolean,
        expected: List<String>,
    ) = runComposeSwingTest {
        val layout = SnapshottingLayout()
        var version by mutableIntStateOf(0)
        setContent {
            val group = SwingModifier then Mark("g$version", groupKey, false, MeasuringProtocol)
            if (styleOwned) {
                OwnedStyledHost(layout, "n$version", NodeProperty) { VirtualSwingNode(group) { ProbedLabel() } }
            } else {
                SwingNode(factory = { MeasuringHost() }, update = { set(layout) { this.layout = it } }) {
                    VirtualSwingNode(group) {
                        Label(
                            "a",
                            SwingModifier then NodeMark("n$version"),
                        )
                    }
                }
            }
        }
        layout.received.clear()

        version = 1
        awaitIdle()

        assertEquals(expected, layout.received.lastOrNull(), case)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeChangeCases#protocolDeactivationChildren")
    fun changingAGroupProtocolWhileDeactivatingAChildAllowsReactivation(
        case: String,
        child: @Composable (ParentProtocol) -> Unit,
    ) = runComposeSwingTest {
        val p = parentProtocolOf("P") { it is MeasuringHost }
        val q = parentProtocolOf("Q") { it is MeasuringHost }
        var protocol by mutableStateOf(p)
        var active by mutableStateOf(true)
        setContent {
            SwingNode(factory = { MeasuringHost() }) {
                VirtualSwingNode(SwingModifier then Data("group-${protocol.description}", 1, protocol)) {
                    ReusableContentHost(active) { child(protocol) }
                }
            }
        }
        val host = onNodeOfType<MeasuringHost>().fetch()
        assertEquals(listOf("a"), host.texts(), case)
        assertEquals(listOf("group-P", "child-P"), host.recording.parentDataOf(host.components.single()), case)

        Snapshot.withMutableSnapshot {
            protocol = q
            active = false
        }
        awaitIdle()

        assertEquals(emptyList(), host.components.toList(), case)

        active = true
        awaitIdle()

        assertEquals(listOf("a"), host.texts(), case)
        assertEquals(listOf("group-Q", "child-Q"), host.recording.parentDataOf(host.components.single()), case)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeChangeCases#refusedGroupHosts")
    fun groupChangesInOnePassEachReachTheirHostSettle(
        case: String,
        sharedHost: Boolean,
        expected: RefusedGroupSettles,
    ) = runComposeSwingTest {
        val firstTag = "A"
        val secondTag = "B"
        val tagB = if (sharedHost) firstTag else secondTag
        val p = parentProtocolOf("P") { it is MeasuringHost }
        val q = parentProtocolOf("Q") { it is MeasuringHost }
        var protocol by mutableStateOf(p)
        var active by mutableStateOf(true)
        var version by mutableIntStateOf(1)
        val groupA: @Composable () -> Unit = {
            VirtualSwingNode(SwingModifier then Data("A-${protocol.description}", 1, protocol)) {
                ReusableContentHost(active) {
                    Label("a", SwingModifier then Data("a-${protocol.description}", 2, protocol))
                }
            }
        }
        val groupB: @Composable () -> Unit = {
            VirtualSwingNode(SwingModifier then Data("B-$version", 1, MeasuringProtocol)) { Label("b") }
        }
        setContent {
            if (sharedHost) {
                SettlingHost(firstTag) {
                    groupA()
                    groupB()
                }
            } else {
                Panel {
                    SettlingHost(firstTag, groupA)
                    SettlingHost(secondTag, groupB)
                }
            }
        }
        onNodeWithTag(firstTag).assertProperty(expected.mountedA, case) { name }
        onNodeWithTag(tagB).assertProperty(expected.mountedB, case) { name }

        Snapshot.withMutableSnapshot {
            protocol = q
            active = false
            version = 2
        }
        awaitIdle()

        onNodeWithTag(tagB).assertProperty(expected.changedB, case) { name }
        onNodeWithTag(firstTag).assertProperty(expected.changedA, case) { name }

        active = true
        awaitIdle()

        onNodeWithTag(tagB).assertProperty(expected.reactivatedB, case) { name }
        onNodeWithTag(firstTag).assertProperty(expected.reactivatedA, case) { name }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeChangeCases#layoutNodeReplacements")
    fun groupAndChildProtocolChangesPublishTogether(
        case: String,
        childModifier: (ParentProtocol) -> SwingModifier,
        change: ProtocolDeclarationChange,
    ) = runComposeSwingTest {
        val initialProtocol = parentProtocolOf("P") { it is MeasuringHost }
        var protocol by mutableStateOf(initialProtocol)
        setContent {
            SwingNode(factory = { MeasuringHost() }) {
                VirtualSwingNode(SwingModifier then Data("group-${protocol.description}", 1, protocol)) {
                    SwingNode(
                        factory = { JLabel("a") },
                        childModifier(protocol) then Data("child-${protocol.description}", 2, protocol),
                    )
                }
            }
        }
        val host = onNodeOfType<MeasuringHost>().fetch()
        val label = host.components.single()
        assertEquals(listOf("group-P", "child-P"), host.recording.parentDataOf(label), case)
        assertEquals(change.initialTags, host.recording.tagsOf(label), case)
        host.recording.declarations.clear()
        host.recording.removals.clear()

        protocol = parentProtocolOf("Q") { it is MeasuringHost }
        awaitIdle()

        assertEquals(listOf("a"), host.texts(), case)
        assertEquals(listOf("group-Q", "child-Q"), host.recording.parentDataOf(label), case)
        assertEquals(change.expectedTags, host.recording.tagsOf(label), case)
        assertEquals(change.publications, host.recording.declarations.map { it.tags }, case)
        assertEquals(
            change.parentDataPublications,
            host.recording.declarations.map { it.parentData },
            case,
        )
        assertEquals(emptyList(), host.recording.removals, case)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.node.VirtualSwingNodeChangeCases#movedGroupsChangedWhileMoving")
    internal fun aGroupMovedBetweenCompositionsWhileItsDeclarationChangesGivesItsChildrenTheNewDeclaration(
        case: String,
        newMove: () -> MovedGroupChange,
    ) = runComposeSwingTest {
        val move = newMove()
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }
        var inSecond by mutableStateOf(false)
        var value by mutableStateOf(move.initial.value)
        var keepsB by mutableStateOf(true)
        val movable = movableContentOf<String, Boolean>(move.content)
        val firstComposition =
            OnDemandComposition(parentContext) {
                SwingNode(
                    factory = { MeasuringHost() },
                    update = { set(move.firstName) { protocolName = it } },
                ) { if (!inSecond) movable(value, keepsB) }
            }
        val secondComposition =
            OnDemandComposition(parentContext) {
                SwingNode(
                    factory = { MeasuringHost() },
                    update = { set(move.secondName) { protocolName = it } },
                ) { if (inSecond) movable(value, keepsB) }
            }
        val first = firstComposition.recompose {} as MeasuringHost
        val second = secondComposition.recompose {} as MeasuringHost
        try {
            assertEquals(move.initial.texts, first.texts(), case)
            assertEquals(move.initial.tags, first.components.map { first.recording.tagsOf(it) }, case)

            inSecond = true
            value = move.moved.value
            keepsB = false
            awaitIdle()

            assertEquals(emptyList(), first.texts(), case)
            assertEquals(move.moved.texts, second.texts(), case)
            assertEquals(move.moved.tags, second.components.map { second.recording.tagsOf(it) }, case)
        } finally {
            secondComposition.dispose()
            firstComposition.dispose()
        }
    }
}

/** [StyledHost] over a [MeasuringHost] that takes the [layout] the test supplies. */
@Composable
private fun OwnedStyledHost(
    layout: LayoutManager,
    style: String,
    property: SwingStyleProperty<String>,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMark provides style) {
        Panel(modifier = providedMarkStyle(property)) {
            SwingNode(factory = { MeasuringHost() }, update = { set(layout) { this.layout = it } }, content = content)
        }
    }
}

@Composable
private fun SettlingHost(
    tag: String,
    content: @Composable () -> Unit,
) {
    SwingNode(factory = { MeasuringHost() }, modifier = SwingModifier.testTag(tag), update = {
        reconcileWithChildren {
            component.name = component.components.joinToString(" ") { "${component.recording.parentDataOf(it)}" }
        }
    }) { content() }
}

private const val DEPARTING_TAG = "departing"

class ProtocolDeclarationChange(
    val initialTags: List<String>,
    val expectedTags: List<String>,
    val publications: List<List<String>>,
    val parentDataPublications: List<List<String>>,
)

class RefusedGroupSettles(
    val mountedA: String,
    val mountedB: String,
    val changedA: String,
    val changedB: String,
    val reactivatedA: String,
    val reactivatedB: String,
)

class LayoutNodeRemovalChange(
    val before: List<String>,
    val expected: List<String>,
    val publications: List<List<String>> = listOf(expected),
)

/**
 * A child added or released in the pass that changes its group: the [layout] the host takes in place of its own, or
 * `null`, the [content] under it given whether the change happened, and the [after] state it then reaches.
 */
internal class ChildChangeWithGroup(
    val layout: LayoutManager?,
    val content: @Composable (Boolean) -> Unit,
    val initialTexts: List<String>,
    val beforeChange: (JPanel) -> Unit,
    val after: ChildChangeOutcome,
)

/** The [texts] a host holds after a change, and [verify], which asserts its declarations given the prior components. */
internal class ChildChangeOutcome(
    val texts: List<String>,
    val verify: (host: JPanel, original: List<Component>) -> Unit,
)

/**
 * A group that moves from a host named [firstName] to one named [secondName] while its content changes from the
 * [initial] to the [moved] state.
 */
internal class MovedGroupChange(
    val firstName: String,
    val secondName: String,
    val content: @Composable (String, Boolean) -> Unit,
    val initial: MovedGroupState,
    val moved: MovedGroupState,
)

/** The [value] a moved group's content is given, with the [texts] and [tags] its host holds for it. */
internal class MovedGroupState(
    val value: String,
    val texts: List<String>,
    val tags: List<List<String>>,
)

/**
 * The values one batch sets, the tags the host last receives, or `null` where it receives nothing, and the number of
 * declarations it receives, or `null` where that is not asserted.
 */
class SimultaneousChange(
    val group: String,
    val style: String,
    val own: String,
    val expected: List<String>?,
    val forbidden: List<String>,
    val publications: Int? = null,
)

internal object VirtualSwingNodeChangeCases {
    @JvmStatic
    fun childrenChangedWithTheirGroup(): List<Arguments> = listOf(
        Arguments.of("added while the group protocol changes", { protocolAddition() }),
        Arguments.of("added while the group layout constraint changes", { constraintAddition() }),
        Arguments.of("released while the group protocol changes", { protocolRelease() }),
    )

    private fun protocolAddition(): ChildChangeWithGroup {
        val p = parentProtocolOf("P") { it is MeasuringHost }
        val q = parentProtocolOf("Q") { it is MeasuringHost }
        return ChildChangeWithGroup(
            null,
            content = { changed ->
                val protocol = if (changed) q else p
                VirtualSwingNode(SwingModifier then Data("group-${protocol.description}", 1, protocol)) {
                    if (changed) Label("a", SwingModifier then Data("child-${protocol.description}", 2, protocol))
                }
            },
            initialTexts = emptyList(),
            beforeChange = {},
            after =
                ChildChangeOutcome(
                    texts = listOf("a"),
                    verify = { panel, _ ->
                        val host = panel as MeasuringHost
                        val parentData = host.recording.parentDataOf(host.components.single())
                        assertEquals(listOf("group-Q", "child-Q"), parentData)
                    },
                ),
        )
    }

    private fun constraintAddition(): ChildChangeWithGroup {
        val layout = RecordingLayout(BorderLayout())
        return ChildChangeWithGroup(
            layout,
            content = { changed ->
                VirtualSwingNode(
                    SwingModifier.layoutConstraint(if (changed) BorderLayout.WEST else BorderLayout.EAST),
                ) {
                    if (changed) Label("a", SwingModifier.layoutConstraint(BorderLayout.NORTH))
                }
            },
            initialTexts = emptyList(),
            beforeChange = {},
            after =
                ChildChangeOutcome(
                    texts = listOf("a"),
                    verify = { _, _ ->
                        assertEquals(listOf<Any?>(BorderLayout.NORTH), layout.registrations.map { it.constraints })
                    },
                ),
        )
    }

    private fun protocolRelease(): ChildChangeWithGroup {
        val p = parentProtocolOf("P") { it is MeasuringHost }
        val q = parentProtocolOf("Q") { it is MeasuringHost }
        return ChildChangeWithGroup(
            null,
            content = { changed ->
                val protocol = if (changed) q else p
                VirtualSwingNode(SwingModifier then Data("group-${protocol.description}", 1, protocol)) {
                    Label("a", SwingModifier then Data("child-${protocol.description}", 2, protocol))
                    if (!changed) Label("b", SwingModifier then Data("child-${protocol.description}", 2, protocol))
                }
            },
            initialTexts = listOf("a", "b"),
            beforeChange = { (it as MeasuringHost).recording.declarations.clear() },
            after =
                ChildChangeOutcome(
                    texts = listOf("a"),
                    verify = { panel, original ->
                        val host = panel as MeasuringHost
                        val a = original.first()
                        assertSame(a, host.components.single())
                        assertEquals(listOf("group-Q", "child-Q"), host.recording.parentDataOf(a))
                        assertEquals(1, host.recording.declarations.count { it.component === a })
                    },
                ),
        )
    }

    @JvmStatic
    fun movedGroupsChangedWhileMoving(): List<Arguments> = listOf(
        Arguments.of(
            "the group modifier changes",
            {
                MovedGroupChange(
                    "",
                    "",
                    { tag, _ -> VirtualSwingNode(SwingModifier.mark(tag)) { Label("m") } },
                    initial = MovedGroupState("g0", listOf("m"), listOf(listOf("g0"))),
                    moved = MovedGroupState("g1", listOf("m"), listOf(listOf("g1"))),
                )
            },
        ),
        Arguments.of("the protocol changes and a child leaves", { protocolChangingMove() }),
    )

    private fun protocolChangingMove(): MovedGroupChange {
        val acceptedByFirst = protocolOf("P")
        val acceptedBySecond = protocolOf("Q")
        return MovedGroupChange(
            "P",
            "Q",
            { name, withB ->
                val declared = if (name == "Q") acceptedBySecond else acceptedByFirst
                VirtualSwingNode(SwingModifier.mark(name, protocol = declared)) {
                    Label("a")
                    ReusableContentHost(withB) {
                        Label("b", SwingModifier.mark("own", slot = 2, protocol = acceptedByFirst))
                    }
                }
            },
            initial = MovedGroupState("P", listOf("a", "b"), listOf(listOf("P"), listOf("P", "own"))),
            moved = MovedGroupState("Q", listOf("a"), listOf(listOf("Q"))),
        )
    }

    @JvmStatic
    fun protocolDeactivationChildren(): List<Arguments> {
        val parentData: @Composable (ParentProtocol) -> Unit = { protocol ->
            Label("a", SwingModifier then Data("child-${protocol.description}", 2, protocol))
        }
        val layoutNodeAndParentData: @Composable (ParentProtocol) -> Unit = { protocol ->
            Label(
                "a",
                SwingModifier then NodeMark("own") then Data("child-${protocol.description}", 2, protocol),
            )
        }
        return listOf(
            Arguments.of("parent data", parentData),
            Arguments.of("layout node and parent data", layoutNodeAndParentData),
        )
    }

    @JvmStatic
    fun refusedGroupHosts(): List<Arguments> = listOf(
        Arguments.of(
            "groups share a host",
            true,
            RefusedGroupSettles(
                mountedA = "[A-P, a-P] [B-1]",
                mountedB = "[A-P, a-P] [B-1]",
                changedA = "[B-2]",
                changedB = "[B-2]",
                reactivatedA = "[A-Q, a-Q] [B-2]",
                reactivatedB = "[A-Q, a-Q] [B-2]",
            ),
        ),
        Arguments.of(
            "groups have their own hosts",
            false,
            RefusedGroupSettles("[A-P, a-P]", "[B-1]", "", "[B-2]", "[A-Q, a-Q]", "[B-2]"),
        ),
    )

    @JvmStatic
    fun layoutNodeReplacements(): List<Arguments> {
        val data: (ParentProtocol) -> SwingModifier = { SwingModifier }
        val node: (ParentProtocol) -> SwingModifier = { SwingModifier then NodeMark(it.description) }
        val removed: (ParentProtocol) -> SwingModifier = {
            if (it.description == "P") SwingModifier then NodeMark("old") else SwingModifier
        }
        val replaced: (ParentProtocol) -> SwingModifier = {
            if (it.description == "P") SwingModifier then NodeMark("old") else SwingModifier.mark("new", slot = 3)
        }
        return listOf(
            Arguments.of(
                "group and parent data",
                data,
                ProtocolDeclarationChange(
                    emptyList<String>(),
                    emptyList<String>(),
                    listOf(emptyList<String>()),
                    listOf(listOf("group-Q", "child-Q")),
                ),
            ),
            Arguments.of(
                "group and layout node",
                node,
                ProtocolDeclarationChange(
                    listOf("P"),
                    listOf("Q"),
                    listOf(listOf("Q")),
                    listOf(listOf("group-Q", "child-Q")),
                ),
            ),
            Arguments.of(
                "remove the layout node",
                removed,
                ProtocolDeclarationChange(
                    listOf("old"),
                    emptyList<String>(),
                    listOf(emptyList<String>(), emptyList<String>()),
                    listOf(listOf("group-P", "child-P"), listOf("group-Q", "child-Q")),
                ),
            ),
            Arguments.of(
                "replace the layout node with an element",
                replaced,
                ProtocolDeclarationChange(
                    listOf("old"),
                    listOf("new"),
                    listOf(emptyList<String>(), listOf("new")),
                    listOf(listOf("group-P", "child-P"), listOf("group-Q", "child-Q")),
                ),
            ),
        )
    }

    @JvmStatic
    fun layoutNodeRemovals(): List<Arguments> = listOf(
        Arguments.of("different group key", 1, "G", "G", LayoutNodeRemovalChange(listOf("G", "own"), listOf("G"))),
        Arguments.of(
            "different group key changes while its child node leaves",
            1,
            "A",
            "B",
            LayoutNodeRemovalChange(listOf("A", "own"), listOf("B"), listOf(listOf("A"), listOf("B"))),
        ),
        Arguments.of(
            "same group key",
            NodeMark::class.java,
            "G",
            "G",
            LayoutNodeRemovalChange(listOf("own"), listOf("G"), listOf(emptyList(), listOf("G"))),
        ),
        Arguments.of(
            "group changes while its child node leaves",
            NodeMark::class.java,
            "old group",
            "new group",
            LayoutNodeRemovalChange(listOf("own"), listOf("new group"), listOf(emptyList(), listOf("new group"))),
        ),
    )

    @JvmStatic
    fun layoutNodeTypeReplacements(): List<Arguments> = listOf(
        Arguments.of("different group key", 1, listOf("G", "own"), false),
        Arguments.of("same group key", NodeMark::class.java, listOf("own"), true),
    )

    @JvmStatic
    fun nodeWritesWithGroupChanges(): List<Arguments> = listOf(true, false).flatMap { styleOwned ->
        val owner = if (styleOwned) "style-owned" else "modifier-owned"
        listOf(
            Arguments.of("$owner node, group key it replaces", NodeMark::class.java, styleOwned, listOf("n1")),
            Arguments.of("$owner node, group key it does not", 1, styleOwned, listOf("g1", "n1")),
        )
    }

    @JvmStatic
    fun releasedLayoutNodeChildren(): List<Arguments> {
        val group = SwingModifier then Mark("G", NodeMark::class.java, false, MeasuringProtocol)
        val inGroup: @Composable () -> Unit = {
            SwingNode(factory = { MeasuringHost() }) {
                VirtualSwingNode(group) { Label("a", SwingModifier then NodeMark("own")) }
            }
        }
        val styled: @Composable () -> Unit = {
            StyledHost("styled", NodeProperty) { ProbedLabel() }
        }
        return listOf(
            Arguments.of("a layout node of a group's child", inGroup, listOf("own")),
            Arguments.of("a style-owned layout node of a child without a group", styled, listOf("styled")),
        )
    }

    @JvmStatic
    fun simultaneousChanges(): List<Arguments> =
        ordinaryChanges() + styleLayoutChanges() + layoutNodeChanges() + relocatedChanges()

    private fun ordinaryChanges(): List<Arguments> {
        val ordinary = ordinaryContent(MarkProperty)
        return listOf(
            Arguments.of(
                "group",
                ordinary,
                SimultaneousChange("g1", "s0", "o0", listOf("g1", "o0", "s0"), listOf("g0")),
            ),
            Arguments.of(
                "style",
                ordinary,
                SimultaneousChange("g0", "s1", "o0", listOf("g0", "o0", "s1"), emptyList(), publications = 1),
            ),
            Arguments.of(
                "own",
                ordinary,
                SimultaneousChange("g0", "s0", "o1", listOf("g0", "o1", "s0"), emptyList(), publications = 1),
            ),
            Arguments.of(
                "group and style",
                ordinary,
                SimultaneousChange("g1", "s1", "o0", listOf("g1", "o0", "s1"), listOf("g0")),
            ),
            Arguments.of(
                "group and own",
                ordinary,
                SimultaneousChange("g1", "s0", "o1", listOf("g1", "o1", "s0"), listOf("g0")),
            ),
            Arguments.of(
                "style and own",
                ordinary,
                SimultaneousChange("g0", "s1", "o1", listOf("g0", "o1", "s1"), emptyList(), publications = 2),
            ),
            Arguments.of(
                "group, style and own",
                ordinary,
                SimultaneousChange("g1", "s1", "o1", listOf("g1", "o1", "s1"), listOf("g0")),
            ),
        )
    }

    private fun styleLayoutChanges(): List<Arguments> {
        val replacing = ordinaryContent(ReplacingMarkProperty)
        val styleAdded: @Composable (
            Boolean,
            String,
            String,
            String,
        ) -> Unit = { changed, group, own, style ->
            StyledHost(
                style,
                MarkProperty,
                modifier =
                    if (changed) {
                        SwingModifier.styleable(PlainProbeContainer) {
                            provideNestedStyle(Probes) { MarkProperty.provide(LocalMark.currentValue) }
                        }
                    } else {
                        SwingModifier
                    },
            ) {
                VirtualSwingNode(SwingModifier.mark(group)) { ProbedLabel(SwingModifier.mark(own, slot = 3)) }
            }
        }
        return listOf(
            Arguments.of(
                "group, with a style added",
                styleAdded,
                SimultaneousChange("g1", "s1", "o0", listOf("g1", "o0", "s1"), listOf("g0"), publications = 1),
            ),
            Arguments.of(
                "group and style of the group's key",
                replacing,
                SimultaneousChange("g1", "s1", "o0", listOf("o0", "s1"), listOf("g0", "g1")),
            ),
            Arguments.of(
                "own, with a style of the group's key",
                replacing,
                SimultaneousChange("g1", "s0", "o1", listOf("o1", "s0"), listOf("g0", "g1")),
            ),
            Arguments.of(
                "group only, with a style of the group's key",
                replacing,
                SimultaneousChange("g1", "s0", "o0", listOf("o0", "s0"), listOf("g0", "g1")),
            ),
        )
    }

    private fun layoutNodeChanges(): List<Arguments> {
        val layoutNode: @Composable (
            Boolean,
            String,
            String,
            String,
        ) -> Unit = { _, group, own, style ->
            StyledHost(style, MarkProperty) {
                VirtualSwingNode(SwingModifier.mark(group)) {
                    ProbedLabel(SwingModifier then NodeMark(own))
                }
            }
        }
        return listOf(
            Arguments.of(
                "layout node",
                layoutNode,
                SimultaneousChange("g1", "s0", "o1", listOf("g1", "o1", "s0"), listOf("g0")),
            ),
        )
    }

    private fun relocatedChanges(): List<Arguments> {
        val relocated: @Composable (
            Boolean,
            String,
            String,
            String,
        ) -> Unit = { changed, group, own, style ->
            val label =
                remember {
                    movableContentOf<String> { value -> ProbedLabel(SwingModifier.mark(value, slot = 3)) }
                }
            SwingNode(
                factory = { MeasuringHost() },
                modifier = SwingModifier.testTag(DEPARTING_TAG),
            ) { VirtualSwingNode(SwingModifier.mark("old")) { if (!changed) label(own) } }
            StyledHost(style, MarkProperty) {
                VirtualSwingNode(SwingModifier.mark(group)) { if (changed) label(own) }
            }
        }
        return listOf(
            Arguments.of(
                "relocated with own change",
                relocated,
                SimultaneousChange("g1", "s1", "o1", listOf("g1", "o1", "s1"), listOf("g0", "old")),
            ),
            Arguments.of(
                "relocated without own change",
                relocated,
                SimultaneousChange("g1", "s1", "o0", listOf("g1", "o0", "s1"), listOf("g0", "old")),
            ),
        )
    }

    private fun ordinaryContent(
        property: SwingStyleProperty<String>,
    ): @Composable (Boolean, String, String, String) -> Unit = { _, group, own, style ->
        StyledHost(style, property) {
            VirtualSwingNode(SwingModifier.mark(group)) { ProbedLabel(SwingModifier.mark(own, slot = 3)) }
        }
    }
}

private fun NodeMark.withChangedResolution(
    property: String,
    alternate: ParentProtocol,
): NodeMark = when (property) {
    "key" -> copy(key = 4)
    "additive" -> copy(additive = true)
    "protocol" -> copy(parentProtocol = alternate)
    else -> copy(inheritable = true)
}

/** Declares `NodeMark(value)`, a layout node. */
private val NodeProperty = stylePropertyOf<String> { NodeMark(it) }

/** A recording layout that also keeps the tags each declaration carried as it was received. */
private class SnapshottingLayout : RecordingMeasurementLayout(FlowLayout()) {
    val received: MutableList<List<String>> = mutableListOf()

    override fun declareComponentLayout(
        component: Component,
        parentData: Any?,
        elements: List<ParentLayoutElement>,
    ) {
        received += elements.map { (it as TaggedDeclaration).tag }
        super.declareComponentLayout(component, parentData, elements)
    }
}
