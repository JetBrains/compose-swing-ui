package org.jetbrains.compose.swing.passcost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.passcost.harness.Frames
import org.jetbrains.compose.swing.setContent
import java.awt.Container
import javax.swing.JPanel

/**
 * One measured arm: a tree, and the change its driver makes to that tree on every pass.
 *
 * An arm is measured twice, and the two measurements differ in nothing but the driver: once changing
 * what the arm names, and once - its null variant - changing nothing at all. The null is the floor of
 * that exact tree under that exact frame protocol, so what the change costs is the arm minus its own
 * null. A null that is not near zero means the figures above it belong to the driver rather than to the
 * runtime, and no arm can then be attributed to anything.
 */
internal class Arm(
    /** The names this arm's passes are reported under; more than one where a pass alternates. */
    val series: List<String>,
    /**
     * The tree sizes this arm is measured at, each of them twice. Two sizes are what separate what a
     * change costs per widget from what it costs whatever the tree holds: the slope between them is the
     * per-widget cost, and an arm flat across them carries none.
     */
    val sizes: List<Int> = TREE_SIZES,
    /** Builds the tree and its driver, fresh for every batch. [changing] false builds the null variant. */
    val build: (widgets: Int, changing: Boolean) -> Run,
)

/** A tree ready to be driven, the driver that changes it, and what has to hold once a batch is over. */
internal class Run(
    /**
     * Composes this arm's tree under the panel a batch is given, and answers the handle that disposes
     * it. An arm states this itself rather than handing over content because the applier a tree is
     * composed into is part of what an arm measures - see [mountReferenceTree].
     */
    val mount: (root: JPanel) -> DisposableHandle,
    /** Makes this pass's change and answers the series the pass belongs to. */
    val drive: (pass: Int) -> String,
    /** Checked on the composed tree once [passes] passes have been driven onto it. */
    val verify: (root: Container, passes: Int) -> Unit,
) {
    /** An arm whose tree is the Swing one every widget arm composes, mounted under this module's runtime. */
    constructor(
        content:
            @Composable @SwingComposable
            () -> Unit,
        drive: (pass: Int) -> String,
        verify: (root: Container, passes: Int) -> Unit,
    ) : this({ root -> root.setContent(parent = Frames.compositionContext, content = content) }, drive, verify)
}

/** Every arm this module measures, in the order it reports them. */
internal fun arms(): List<Arm> =
    listOf(
        propertyArm(),
        scopeAboveArm(),
        structuralArm(),
        chainArm("modifier chain unchanged", freshCallback = false),
        chainArm("modifier chain rebuilt", freshCallback = true),
        referenceNodeArm(),
        nodeArm("node key only", NodeShape.PLAIN),
        nodeArm("value keys only", NodeShape.KEYED),
        nodeArm("declared two-way", NodeShape.DECLARING),
        nodeArm("declared two-way moved", NodeShape.MOVING),
        sliderValueArm(),
        treeValueArm(),
        treeSizeArm(),
        tableValueArm(),
        tableSizeArm(),
        listItemsArm(),
        listSelectionArm(),
        treeSelectionArm(),
    ) + tableSelectionArms() + textArms() + controlArms() + panelArms() + containerArms() + fillerArms()

/**
 * A label reading the changing text itself, so a write invalidates that label's scope and no other:
 * what one changed property costs.
 */
private fun propertyArm(): Arm =
    Arm(listOf(PROPERTY_ARM)) { widgets, changing ->
        val text = mutableStateOf(INITIAL_TEXT)
        val contentRuns = IntArray(1)
        val labelRuns = IntArray(1)
        Run(
            content = {
                Column {
                    contentRuns[0]++
                    ReadingLabel(text) { labelRuns[0]++ }
                    for (index in 0 until widgets - 1) Label(FILLER_TEXTS[index])
                }
            },
            drive = { pass ->
                if (changing) text.value = alternatingText(pass)
                PROPERTY_ARM
            },
            verify = { root, passes ->
                checkWidgets("labels", labelCount(root), widgets)
                checkScopeRuns("the column's content scope", contentRuns[0], 1)
                checkScopeRuns("the label's scope", labelRuns[0], if (changing) 1 + passes else 1)
                val expected = if (changing) alternatingText(passes - 1) else INITIAL_TEXT
                check(labelTexts(root).contains(expected)) {
                    "no label carries '$expected', the text written last"
                }
            },
        )
    }

/**
 * The same changing text read one scope above the label, in the column's content scope, so every call
 * that scope makes re-executes on every pass.
 */
private fun scopeAboveArm(): Arm =
    Arm(listOf(SCOPE_ABOVE_ARM)) { widgets, changing ->
        val text = mutableStateOf(INITIAL_TEXT)
        val contentRuns = IntArray(1)
        Run(
            content = {
                Column {
                    contentRuns[0]++
                    Label(text.value)
                    for (index in 0 until widgets - 1) Label(FILLER_TEXTS[index])
                }
            },
            drive = { pass ->
                if (changing) text.value = alternatingText(pass)
                SCOPE_ABOVE_ARM
            },
            verify = { root, passes ->
                checkWidgets("labels", labelCount(root), widgets)
                checkScopeRuns("the column's content scope", contentRuns[0], if (changing) 1 + passes else 1)
                val expected = if (changing) alternatingText(passes - 1) else INITIAL_TEXT
                check(labelTexts(root).contains(expected)) {
                    "no label carries '$expected', the text written last"
                }
            },
        )
    }

/**
 * One widget appearing and disappearing at the end of the tree, reported as two series: the passes that
 * insert it and the passes that remove it. One driver produces both, so the two are measured on one
 * tree that never drifts in size.
 *
 * A label is emitted while the state holds a text, and the tree is composed holding [INITIAL_TEXT] - a
 * text no pass writes. A tree that was never inserted into nor removed from therefore holds one label
 * too many, reading a text neither series declares.
 *
 * The optional slot is a composable lambda rather than a call, so it carries a scope of its own that a
 * write invalidates without re-executing the labels around it.
 */
private fun structuralArm(): Arm =
    Arm(listOf(INSERT_SERIES, REMOVE_SERIES)) { widgets, changing ->
        val extra = mutableStateOf<String?>(INITIAL_TEXT)
        val optionalRuns = IntArray(1)
        val optional: @Composable () -> Unit = {
            optionalRuns[0]++
            val text = extra.value
            if (text != null) Label(text)
        }
        Run(
            content = {
                Column {
                    for (index in 0 until widgets) Label(FILLER_TEXTS[index])
                    optional()
                }
            },
            drive = { pass ->
                val inserting = pass % 2 == 0
                if (changing) extra.value = if (inserting) EXTRA_TEXT else null
                if (inserting) INSERT_SERIES else REMOVE_SERIES
            },
            verify = { root, passes ->
                val standing =
                    when {
                        !changing -> INITIAL_TEXT
                        (passes - 1) % 2 == 0 -> EXTRA_TEXT
                        else -> null
                    }
                checkWidgets("labels", labelCount(root), widgets + if (standing != null) 1 else 0)
                checkScopeRuns("the optional label's scope", optionalRuns[0], if (changing) 1 + passes else 1)
                check(standing == null || labelTexts(root).contains(standing)) {
                    "no label carries '$standing', the text the last pass emitted"
                }
            },
        )
    }

/** What an invalidated node's update block costs, by the shape of what it holds. */
private enum class NodeShape { PLAIN, KEYED, DECLARING, MOVING }

/**
 * Every widget an invalidated node whose update block holds one key, [DECLARED_PROPERTIES] two-part
 * keys, or [DECLARED_PROPERTIES] two-way declarations - settled on what the widget already holds, or
 * moved onto a value it does not.
 */
private fun nodeArm(
    name: String,
    shape: NodeShape,
): Arm =
    Arm(listOf(name)) { widgets, changing ->
        val tick = mutableStateOf(UNSET_TICK)
        val panelRuns = IntArray(1)
        Run(
            content = {
                Column {
                    repeat(widgets) {
                        when (shape) {
                            NodeShape.PLAIN -> PlainPanel(tick) { panelRuns[0]++ }
                            NodeShape.KEYED -> KeyedPanel(tick) { panelRuns[0]++ }
                            NodeShape.DECLARING -> DeclaringPanel(tick) { panelRuns[0]++ }
                            NodeShape.MOVING -> MovingPanel(tick) { panelRuns[0]++ }
                        }
                    }
                }
            },
            drive = { pass ->
                if (changing) tick.value = pass % 2
                name
            },
            verify = { root, passes ->
                checkWidgets("panels", panelCount(root), widgets + 1)
                checkScopeRuns("the panel scopes", panelRuns[0], widgets * if (changing) 1 + passes else 1)
                if (shape == NodeShape.MOVING) checkMovedPanels(root, widgets, changing, passes)
            },
        )
    }

/**
 * Raises unless every declared panel carries the name the last pass declared onto it - which is what
 * says that a moved declaration reached the widget and not only the mirror. A panel arrives unnamed,
 * so a declaration that never reached one leaves it carrying no name at all; the tick it is composed
 * on names a third text, so a panel left on the first declaration it was given fails just as loudly.
 */
private fun checkMovedPanels(
    root: Container,
    widgets: Int,
    changing: Boolean,
    passes: Int,
) {
    val declared = alternatingText(if (changing) passes - 1 else UNSET_TICK)
    val named = panelsNamed(root, declared)
    check(named == widgets) {
        "the tree holds $named panels named '$declared', where $widgets were declared onto"
    }
}

/**
 * Every widget a slider whose declared value moves on every pass: what a two-way declaration costs where
 * the widget answers the write by publishing the move straight back through the listener the wrapper
 * installs, and the mirror that listener feeds is read by the scope that declared the value.
 *
 * The value alternates between two the slider can hold, so every pass is a real change and the driver
 * allocates nothing; it starts on a third, so the first pass writes as every later one does. The ticks
 * and labels are the ones SwingMark's slider screen declares.
 */
private fun sliderValueArm(): Arm =
    Arm(listOf(SLIDER_VALUE_ARM)) { widgets, changing ->
        val value = mutableIntStateOf(SLIDER_MAXIMUM)
        val sliderRuns = IntArray(1)
        Run(
            content = {
                Column {
                    repeat(widgets) { DeclaredSlider(value) { sliderRuns[0]++ } }
                }
            },
            drive = { pass ->
                if (changing) value.intValue = pass % 2
                SLIDER_VALUE_ARM
            },
            verify = { root, passes ->
                val values = sliderValues(root)
                checkWidgets("sliders", values.size, widgets)
                checkScopeRuns("the slider scopes", sliderRuns[0], widgets * if (changing) 1 + passes else 1)
                checkApplied("slider", values, if (changing) (passes - 1) % 2 else SLIDER_MAXIMUM)
            },
        )
    }

internal fun checkWidgets(
    what: String,
    counted: Int,
    expected: Int,
) {
    check(counted == expected) { "the tree holds $counted $what where $expected were composed" }
}

internal fun checkScopeRuns(
    what: String,
    counted: Int,
    expected: Int,
) {
    check(counted == expected) { "$what ran $counted times where $expected were expected" }
}

/**
 * Raises unless every widget carries [expected], the value the last pass declared onto all of them -
 * which is what says the declaration reached the widgets and not only the mirror they settle through.
 */
internal fun <T> checkApplied(
    what: String,
    applied: List<T>,
    expected: T,
) {
    check(applied.all { it == expected }) {
        "a $what is left on ${applied.first { it != expected }}, where $expected was declared last"
    }
}
