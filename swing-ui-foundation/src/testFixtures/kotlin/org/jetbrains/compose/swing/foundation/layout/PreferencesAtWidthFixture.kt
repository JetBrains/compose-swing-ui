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
import org.jetbrains.compose.swing.test.interaction.assertProperty
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
import javax.swing.JTextArea
import kotlin.test.assertEquals

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

/**
 * Exercises every compatible container, leaf and slot combination in [PreferenceSlots].
 * Narrows the slot twice and asserts
 * that a preference read at a width the layout does not grant lays nothing out there, so one validation settles:
 * - the composition becomes idle, and a paint requests no validation within a few of them;
 * - no component is left invalid, and none requests a validation once idle;
 * - a component the stock child holds is resized at most once by the validation a narrower slot starts, and by none
 *   that revalidates the unchanged tree.
 *
 * [widthFromLeafHeight] are containers whose preferred width depends on a leaf's height at an unbounded width, as a
 * `Column` does for an aspect-ratio leaf. Under these slots, a stock leaf answers at the width it holds, so each width
 * change can change the result and keep the container from settling. The test's `Constrainable` answers that query
 * independently of its held bounds.
 *
 * The default [leaves] are stock components a layout resizes with the width it grants: those whose height follows their
 * width, and containers that lay their children out again at a new width, as a scroll pane revalidates when its view is
 * resized.
 */
public fun assertPreferencesSettle(
    containers: Map<String, ContainerContent>,
    leaves: Map<String, @Composable (SwingModifier) -> Unit> =
        mapOf<String, @Composable (SwingModifier) -> Unit>(
            "JScrollPane holding a JList" to { modifier ->
                SwingNode(factory = { JScrollPane(JList(arrayOf("One", "Two", "Three"))) }, modifier = modifier)
            },
            "JPanel holding a wrapping JTextArea" to { modifier ->
                SwingNode(
                    factory = {
                        JPanel(BorderLayout()).apply {
                            add(
                                JTextArea(WRAPPING_TEXT).apply {
                                    lineWrap = true
                                    wrapStyleWord = true
                                },
                            )
                        }
                    },
                    modifier = modifier,
                )
            },
        ) + WrappingTexts,
    widthFromLeafHeight: Map<String, ContainerContent> = emptyMap(),
) {
    val unsettled = mutableListOf<String>()
    for ((slotName, slot) in PreferenceSlots) {
        for ((containerName, containerContent) in containers + widthFromLeafHeight) {
            val needsConstrainable = containerName in widthFromLeafHeight && slotName in PreferredWidthSlots
            if (needsConstrainable) {
                unsettled +=
                    unsettledAfterNarrowing(slot, containerContent) { modifier ->
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
                    }.map { "$slotName, $containerName, width-dependent Constrainable: $it" }
            }
            val selectedLeaves =
                if (needsConstrainable) leaves - "JPanel holding a wrapping JTextArea" - WrappingTexts.keys else leaves
            for ((leafName, leaf) in selectedLeaves) {
                unsettled +=
                    unsettledAfterNarrowing(
                        slot,
                        containerContent,
                        leaf,
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
): List<String> {
    val problems = mutableListOf<String>()
    runComposeSwingTest {
        var width by mutableIntStateOf(320)
        runCatching {
            setContent {
                slot(width) { modifier -> containerContent(modifier.testTag("container")) { leaf(it.testTag("leaf")) } }
            }
            val container = onNodeWithTag("container").fetch<JComponent>()
            val held = onNodeWithTag("leaf").onDescendants().fetchAll()
            val resized = mutableListOf<Component>()
            for (component in held) {
                component.addComponentListener(
                    object : ComponentAdapter() {
                        override fun componentResized(event: ComponentEvent) {
                            resized += event.component
                        }
                    },
                )
            }

            fun resizedMoreThan(times: Int) =
                held.filter { component ->
                    resized.count { it === component } > times
                }

            cyclesUntilStable(container)
            repeat(2) { _ ->
                width -= NARROWING_STEP
                resized.clear()
                awaitIdle()
                problems += resizedMoreThan(1).map { "${it.javaClass.simpleName} is resized again at $width" }
                cyclesUntilStable(container)
            }
            resized.clear()
            container.revalidate()
            awaitIdle()
            problems += resizedMoreThan(0).map { "${it.javaClass.simpleName} is resized by an unchanged tree" }
            val descendants = onRoot().onDescendants()
            val invalid = mutableListOf<String>()
            repeat(descendants.fetchSize()) { index ->
                try {
                    descendants[index].assertProperty(true) { isValid }
                } catch (failure: AssertionError) {
                    invalid +=
                        failure.message
                            .orEmpty()
                            .lines()
                            .first()
                }
            }
            problems += invalid
            withRecordedRepaints { recorder ->
                awaitIdle()
                problems +=
                    recorder.relayouts.map { "${it.javaClass.simpleName} requests a validation when idle" }
            }
        }.exceptionOrNull()?.let {
            problems += "${it.javaClass.simpleName}: ${it.message.orEmpty().lines().first()}"
        }
    }
    return problems
}
