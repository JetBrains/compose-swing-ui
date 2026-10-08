package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BorderPanelScope
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.onDescendants
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.test.assertEquals
import kotlin.time.Duration

/** A Foundation container, placed under a modifier, holding the stock component it is handed. */
public typealias ContainerContent = @Composable (SwingModifier, @Composable (SwingModifier) -> Unit) -> Unit

internal const val NARROWING_STEP = 60

/**
 * The [PreferenceSlots] whose parent takes the width of the container it holds from the width the container
 * prefers.
 */
private val PreferredWidthSlots: Map<String, Slot> =
    widthTakingSlots(listOf(400)) +
        mapOf<String, BorderPanelScope.() -> SwingModifier>(
            "BorderLayout EAST" to { SwingModifier.east() },
            "BorderLayout WEST" to { SwingModifier.west() },
        ).mapValues<String, BorderPanelScope.() -> SwingModifier, Slot> { (_, placement) ->
            { width: Int, subject: @Composable (SwingModifier) -> Unit ->
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, SLOT_HEIGHT)) {
                    subject(placement())
                }
            }
        } +
        mapOf<String, Slot>(
            "BoxLayout X_AXIS" to { width, subject ->
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, SLOT_HEIGHT)) {
                    Panel(PanelLayout.Box(BoxLayout.X_AXIS), modifier = SwingModifier.north()) {
                        subject(SwingModifier)
                    }
                }
            },
            "JSplitPane first side" to { width, subject ->
                SplitPane(modifier = SwingModifier.preferredSize(width, SLOT_HEIGHT)) {
                    subject(SwingModifier.first())
                    Label("Second", modifier = SwingModifier.second())
                }
            },
        )

/** The slots of Swing layout managers whose parent reads a container's size, and lays it out at the size it grants. */
public val PreferenceSlots: Map<String, Slot> = WidthSettingSlots + PreferredWidthSlots

/** The name of the [DefaultLeaves] leaf holding a [ResizeCountingTextArea] in a `JPanel`. */
public const val WRAPPING_TEXT_AREA_PANEL: String = "JPanel holding a wrapping JTextArea"

/**
 * The stock components a layout resizes with the width it grants: those whose height follows their width, and
 * containers that lay their children out again at a new width, as a scroll pane revalidates when its view is resized.
 */
public val DefaultLeaves: Map<String, @Composable (SwingModifier) -> Unit> =
    mapOf<String, @Composable (SwingModifier) -> Unit>(
        "JScrollPane holding a JList" to { modifier ->
            SwingNode(factory = { JScrollPane(JList(arrayOf("One", "Two", "Three"))) }, modifier = modifier)
        },
        WRAPPING_TEXT_AREA_PANEL to { modifier ->
            SwingNode(factory = { JPanel(BorderLayout()).apply { add(ResizeCountingTextArea()) } }, modifier = modifier)
        },
    ) + WrappingTexts

/**
 * Exercises every compatible container under each of [slots], named as in [PreferenceSlots], with the leaves given for
 * that slot. A name that is not one of [PreferenceSlots] fails. Narrows the slot twice and asserts
 * that a preference read at a width the layout does not grant lays nothing out there, so one validation settles:
 * - the composition becomes idle, and a paint requests no validation within a few of them;
 * - no component is left invalid, and none requests a validation once idle;
 * - a component the stock child holds is resized at most once by the validation a narrower slot starts, and by none
 *   that revalidates the unchanged tree;
 * - [sizeProblems], given the container and stock child, names nothing wrong once each narrowing has settled.
 *
 * A wrapping text area in the stock child may be resized once more, at the width it was given, for the height it takes
 * there. With a [transitionTime] each narrowing is checked that far into the animation; without one, after it finishes.
 *
 * [widthFromLeafHeight] are containers whose preferred width depends on a leaf's height at an unbounded width, as a
 * `Column` does for an aspect-ratio leaf. Under these slots, a stock leaf answers at the width it holds, so each width
 * change can change the result and keep the container from settling. The test's `Constrainable` answers that query
 * independently of its held bounds.
 */
public fun assertPreferencesSettle(
    containers: Map<String, ContainerContent>,
    slots: Map<String, Map<String, @Composable (SwingModifier) -> Unit>> = PreferenceSlots.mapValues { DefaultLeaves },
    transitionTime: Duration? = null,
    sizeProblems: (container: JComponent, leaf: JComponent) -> List<String> = { _, _ -> emptyList() },
    widthFromLeafHeight: Map<String, ContainerContent> = emptyMap(),
) {
    require(transitionTime == null || transitionTime > Duration.ZERO) {
        "transitionTime must be positive, or null to run the animation to the end: $transitionTime"
    }
    val unknownSlots = slots.keys - PreferenceSlots.keys
    require(unknownSlots.isEmpty()) { "$unknownSlots are not among the PreferenceSlots ${PreferenceSlots.keys}" }
    val unsettled = mutableListOf<String>()
    for ((slotName, slotLeaves) in slots) {
        val slot = PreferenceSlots.getValue(slotName)
        for ((containerName, containerContent) in containers + widthFromLeafHeight) {
            val needsConstrainable = containerName in widthFromLeafHeight && slotName in PreferredWidthSlots
            if (needsConstrainable) {
                unsettled +=
                    unsettledAfterNarrowing(
                        slot,
                        containerContent,
                        leaf = { modifier ->
                            SwingNode(
                                factory = {
                                    object : JComponent(), Constrainable {
                                        override var constrainedWidth: Int = 0
                                            private set

                                        override var constrainedHeight: Int = 0
                                            private set

                                        override fun measure(constraints: Constraints) {
                                            constrainedWidth =
                                                constraints.constrainWidth(maxIntrinsicWidth(Constraints.Infinity))
                                            constrainedHeight =
                                                constraints.constrainHeight(maxIntrinsicHeight(constrainedWidth))
                                        }

                                        override fun minIntrinsicWidth(height: Int): Int = 80

                                        override fun maxIntrinsicWidth(height: Int): Int = 320

                                        override fun minIntrinsicHeight(width: Int): Int = maxIntrinsicHeight(width)

                                        // Intrinsic answers vary with width without changing this component's bounds.
                                        override fun maxIntrinsicHeight(width: Int): Int {
                                            val lineHeight = 20
                                            if (width == Constraints.Infinity) return lineHeight
                                            val laidOutAt = maxOf(width, minIntrinsicWidth(Constraints.Infinity))
                                            val naturalWidth = maxIntrinsicWidth(Constraints.Infinity)
                                            val remainder = if (naturalWidth % laidOutAt == 0) 0 else 1
                                            return lineHeight * (naturalWidth / laidOutAt + remainder)
                                        }
                                    }
                                },
                                modifier = modifier,
                            )
                        },
                        transitionTime,
                        sizeProblems,
                    ).map { "$slotName, $containerName, width-dependent Constrainable: $it" }
            }
            val selectedLeaves =
                if (needsConstrainable) {
                    slotLeaves - WRAPPING_TEXT_AREA_PANEL - WrappingTexts.keys
                } else {
                    slotLeaves
                }
            for ((leafName, leaf) in selectedLeaves) {
                unsettled +=
                    unsettledAfterNarrowing(
                        slot,
                        containerContent,
                        leaf,
                        transitionTime,
                        sizeProblems,
                    ).map { "$slotName, $containerName, $leafName: $it" }
            }
        }
    }
    assertEquals(emptyList(), unsettled, "the preference reads that leave the layout unsettled")
}

private fun unsettledAfterNarrowing(
    slot: Slot,
    containerContent: ContainerContent,
    leaf: @Composable (SwingModifier) -> Unit,
    transitionTime: Duration?,
    sizeProblems: (container: JComponent, leaf: JComponent) -> List<String>,
): List<String> {
    val problems = mutableListOf<String>()
    runComposeSwingTest {
        var width by mutableIntStateOf(320)
        runCatching {
            mainClock.autoAdvance = transitionTime == null
            setContent {
                slot(width) { modifier -> containerContent(modifier.testTag("container")) { leaf(it.testTag("leaf")) } }
            }
            if (transitionTime != null) {
                mainClock.advanceTimeBy(transitionTime)
                awaitIdle()
            }
            val container = onNodeWithTag("container").fetch<JComponent>()
            val leafComponent = onNodeWithTag("leaf").fetch<JComponent>()
            val held = onNodeWithTag("leaf").onDescendants().fetchAll()
            val textAreas = held.filterIsInstance<ResizeCountingTextArea>()
            val resized = mutableListOf<Component>()
            val listener =
                object : ComponentAdapter() {
                    override fun componentResized(event: ComponentEvent) {
                        resized += event.component
                    }
                }
            held.forEach { it.addComponentListener(listener) }
            cyclesUntilStable(container)
            repeat(2) { _ ->
                width -= NARROWING_STEP
                resized.clear()
                textAreas.forEach { it.resetCounts() }
                // A held clock needs a frame to apply the new width, and the animation must not move with it.
                if (transitionTime != null) mainClock.advanceTimeBy(Duration.ZERO, ignoreFrameDuration = true)
                awaitIdle()
                problems += repeatedResizes(held, resized, width)
                cyclesUntilStable(container)
                problems += sizeProblems(container, leafComponent).map { "$it at $width" }
            }
            resized.clear()
            container.revalidate()
            awaitIdle()
            problems +=
                held
                    .filter { component -> resized.any { it === component } }
                    .map { "${it.javaClass.simpleName} is resized by an unchanged tree" }
            problems += unsettledWhenIdle()
        }.exceptionOrNull()?.let {
            problems += "${it.javaClass.simpleName}: ${it.message.orEmpty().lines().first()}"
        }
    }
    return problems
}

/**
 * Names each of [held] resized more than once at [width]; a [ResizeCountingTextArea] may be resized once more at the
 * width it was given.
 */
private fun repeatedResizes(
    held: List<Component>,
    resized: List<Component>,
    width: Int,
): List<String> =
    buildList {
        held
            .filter { component -> component !is ResizeCountingTextArea && resized.count { it === component } > 1 }
            .mapTo(this) { "${it.javaClass.simpleName} is resized again at $width" }
        for (area in held.filterIsInstance<ResizeCountingTextArea>()) {
            if (area.widthChanges > 1) add("JTextArea is given another width at $width")
            if (area.resizes > 2) add("JTextArea is resized again at $width")
        }
    }

/** Names each component left invalid after an unchanged tree is validated, and each requesting validation when idle. */
private suspend fun ComposeSwingTest.unsettledWhenIdle(): List<String> =
    buildList {
        val root = onRoot()
        (listOf(root.fetch()) + root.onDescendants().fetchAll()).filterNot { it.isValid }.mapTo(this) {
            "${it.javaClass.simpleName} is invalid after an unchanged tree"
        }
        withRecordedRepaints { recorder ->
            awaitIdle()
            recorder.relayouts.mapTo(this) { "${it.javaClass.simpleName} requests a validation when idle" }
        }
    }
