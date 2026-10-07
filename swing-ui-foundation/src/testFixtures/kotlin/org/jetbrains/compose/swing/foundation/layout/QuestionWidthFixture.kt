package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
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
public typealias Asker = @Composable (SwingModifier, @Composable (SwingModifier) -> Unit) -> Unit

private const val INITIAL_WIDTH = 320
private const val NARROWING_STEP = 60
private const val ASKED_NARROWER_BY = 30

/** This width less a fixed amount, or unbounded where it is: the width these tests' policies ask at. */
public fun Int.narrower(): Int = if (this == Constraints.Infinity) this else (this - ASKED_NARROWER_BY).coerceAtLeast(0)

/** The [QuestionSlots] whose parent takes the width of the container it holds from the width the container prefers. */
private val WidthTakingQuestionSlots: Map<String, Slot> =
    widthTakingSlots(listOf(400)) +
        mapOf<String, Slot>(
            "BorderLayout EAST" to { width, subject ->
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, SLOT_HEIGHT)) {
                    subject(SwingModifier.east())
                }
            },
            "BorderLayout WEST" to { width, subject ->
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(width, SLOT_HEIGHT)) {
                    subject(SwingModifier.west())
                }
            },
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

/** The slots of Swing layout managers whose parent asks a container's size, and lays it out at the size it grants. */
public val QuestionSlots: Map<String, Slot> = WidthSettingSlots + WidthTakingQuestionSlots

/** Stock components whose height follows their width: a wrapping text, alone or in a panel. */
private val HeightFollowingLeaves: Map<String, @Composable (SwingModifier) -> Unit> =
    mapOf<String, @Composable (SwingModifier) -> Unit>(
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
    ) + WrappingTexts

/**
 * Stock components a layout resizes with the width it grants: those whose height follows their width, and containers
 * that lay their children out again at a new width, as a scroll pane revalidates when its view is resized.
 */
public val QuestionLeaves: Map<String, @Composable (SwingModifier) -> Unit> =
    mapOf<String, @Composable (SwingModifier) -> Unit>(
        "JScrollPane holding a JList" to { modifier ->
            SwingNode(factory = { JScrollPane(JList(arrayOf("One", "Two", "Three"))) }, modifier = modifier)
        },
    ) + HeightFollowingLeaves

/** The Foundation containers a stock parent asks their size, each holding the stock component handed to it. */
public val QuestionContainers: Map<String, Asker> =
    mapOf<String, Asker>(
        "Box" to { modifier, leaf -> Box(modifier = modifier) { leaf(SwingModifier.fillMaxWidth()) } },
        "Column" to { modifier, leaf -> Column(modifier = modifier) { leaf(SwingModifier.fillMaxWidth()) } },
        "Row with a weighted child" to { modifier, leaf ->
            Row(modifier = modifier) {
                leaf(SwingModifier.weight(1f))
                Label("|")
            }
        },
        "Box with widthIn" to { modifier, leaf -> Box(modifier = modifier) { leaf(SwingModifier.widthIn(max = 200)) } },
        "Box with requiredWidthIn" to { modifier, leaf ->
            Box(modifier = modifier) { leaf(SwingModifier.requiredWidthIn(min = 200, max = 400)) }
        },
        "Box with padding" to { modifier, leaf ->
            Box(modifier = modifier) { leaf(SwingModifier.padding(8).fillMaxWidth()) }
        },
        "Box with aspectRatio" to { modifier, leaf ->
            Box(modifier = modifier) { leaf(SwingModifier.fillMaxWidth().aspectRatio(2f)) }
        },
    )

/**
 * Foundation containers a stock parent asks their size that answer their width from their child's height at an
 * unbounded width: a `Column` asks a child at an `aspectRatio` its height, and answers that height times the ratio.
 */
public val WidthFromLeafHeightContainers: Map<String, Asker> =
    mapOf<String, Asker>(
        "Column with an aspect-ratio leaf" to { modifier, leaf ->
            Column(modifier = modifier) { leaf(SwingModifier.fillMaxWidth().aspectRatio(2f)) }
        },
    )

/** The components this one holds, at any depth. */
private fun Component.descendants(): List<Component> =
    (this as? Container)?.components.orEmpty().flatMap { listOf(it) + it.descendants() }

/**
 * Shows each of [askers] and [widthFromLeafHeight] holding each of [leaves] in each of [QuestionSlots], narrows the
 * slot twice, and asserts that a question asked at a width the layout does not grant lays nothing out at that width, so
 * one validation settles:
 * - the composition becomes idle, and a paint asks for no validation within a few of them;
 * - no component is left invalid, and none asks for a validation once idle;
 * - a component the stock child holds is resized at most once by the validation a narrower slot starts, and by none
 *   that revalidates the unchanged tree.
 *
 * [widthFromLeafHeight] are askers whose container answers its width from the leaf's height at an unbounded width, as
 * a `Column` does for a leaf at an `aspectRatio`. A stock component answers that height at the width it holds, as
 * Swing has no other answer. Under a slot whose parent takes the container's width from that answer, each width
 * granted changes the answer, so the container never settles, as the same layout in plain Swing does not. There these
 * askers hold the [ConstrainableTextAreaLeaf], which answers that height on one line, in place of the stock components
 * whose height follows their width.
 */
public fun assertQuestionsSettle(
    askers: Map<String, Asker>,
    leaves: Map<String, @Composable (SwingModifier) -> Unit> = QuestionLeaves,
    widthFromLeafHeight: Map<String, Asker> = emptyMap(),
) {
    val unsettled = mutableListOf<String>()
    for ((slotName, slot) in QuestionSlots) {
        for ((askerName, asker) in askers + widthFromLeafHeight) {
            val askedLeaves =
                if (askerName in widthFromLeafHeight && slotName in WidthTakingQuestionSlots) {
                    leaves - HeightFollowingLeaves.keys + ConstrainableTextAreaLeaf
                } else {
                    leaves
                }
            for ((leafName, leaf) in askedLeaves) {
                unsettled +=
                    unsettledAfterNarrowing(slot, asker, leaf).map {
                        "$slotName, $askerName, $leafName: $it"
                    }
            }
        }
    }
    assertEquals(emptyList(), unsettled, "the questions that leave the layout unsettled")
}

/**
 * What [slot] holding [asker] holding [leaf] leaves unsettled as the slot narrows. A composition that never becomes
 * idle, a container a paint keeps asking to validate and a failure thrown on the way are such problems, so the others
 * still show.
 */
private fun unsettledAfterNarrowing(
    slot: Slot,
    asker: Asker,
    leaf: @Composable (SwingModifier) -> Unit,
): List<String> {
    val problems = mutableListOf<String>()
    runComposeSwingTest {
        var width by mutableIntStateOf(INITIAL_WIDTH)
        runCatching {
            setContent { slot(width) { modifier -> asker(modifier.testTag("asker")) { leaf(it.testTag("leaf")) } } }
            val container = onNodeWithTag("asker").fetch<JComponent>()
            val held = onNodeWithTag("leaf").fetch<JComponent>().descendants()
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

            fun resizedMoreThan(times: Int) = held.filter { component -> resized.count { it === component } > times }

            cyclesUntilStable(container)
            for (step in 1..2) {
                width = INITIAL_WIDTH - NARROWING_STEP * step
                resized.clear()
                awaitIdle()
                problems += resizedMoreThan(1).map { "${it.javaClass.simpleName} is resized again at $width" }
                cyclesUntilStable(container)
            }
            resized.clear()
            container.revalidate()
            awaitIdle()
            problems += resizedMoreThan(0).map { "${it.javaClass.simpleName} is resized by an unchanged tree" }
            problems += root.descendants().filterNot { it.isValid }.map { "${it.javaClass.simpleName} is left invalid" }
            withRecordedRepaints { recorder ->
                awaitIdle()
                problems += recorder.relayouts.map { "${it.javaClass.simpleName} asks for a validation when idle" }
            }
        }.exceptionOrNull()?.let { problems += "${it.javaClass.simpleName}: ${it.message.orEmpty().lines().first()}" }
    }
    return problems
}
