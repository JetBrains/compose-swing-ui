package org.jetbrains.compose.swing.passcost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Column
import java.awt.Container

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
    /** Builds the tree and its driver, fresh for every batch. [changing] false builds the null variant. */
    val build: (widgets: Int, changing: Boolean) -> Run,
)

/** A tree ready to be driven, the driver that changes it, and what has to hold once a batch is over. */
internal class Run(
    val content:
        @Composable @SwingComposable
        () -> Unit,
    /** Makes this pass's change and answers the series the pass belongs to. */
    val drive: (pass: Int) -> String,
    /** Checked on the composed tree once [passes] passes have been driven onto it. */
    val verify: (root: Container, passes: Int) -> Unit,
)

/** Every arm this module measures, in the order it reports them. */
internal fun arms(): List<Arm> =
    listOf(
        propertyArm(),
        scopeAboveArm(),
        structuralArm(),
        chainArm("modifier chain unchanged", freshCallback = false),
        chainArm("modifier chain rebuilt", freshCallback = true),
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
    ) + tableSelectionArms()

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
 */
private fun structuralArm(): Arm =
    Arm(listOf(INSERT_SERIES, REMOVE_SERIES)) { widgets, changing ->
        val present = mutableStateOf(false)
        val optionalRuns = IntArray(1)
        Run(
            content = {
                Column {
                    for (index in 0 until widgets) Label(FILLER_TEXTS[index])
                    OptionalLabel(present) { optionalRuns[0]++ }
                }
            },
            drive = { pass ->
                val inserting = pass % 2 == 0
                if (changing) present.value = inserting
                if (inserting) INSERT_SERIES else REMOVE_SERIES
            },
            verify = { root, passes ->
                val inserted = changing && (passes - 1) % 2 == 0
                checkWidgets("labels", labelCount(root), widgets + if (inserted) 1 else 0)
                checkScopeRuns("the optional label's scope", optionalRuns[0], if (changing) 1 + passes else 1)
            },
        )
    }

/** Every widget carrying the same four-element modifier chain, re-declared on every pass. */
private fun chainArm(
    name: String,
    freshCallback: Boolean,
): Arm =
    Arm(listOf(name)) { widgets, changing ->
        val tick = mutableStateOf(UNSET_TICK)
        val panelRuns = IntArray(1)
        Run(
            content = {
                Column {
                    repeat(widgets) { ChainPanel(tick, freshCallback) { panelRuns[0]++ } }
                }
            },
            drive = { pass ->
                if (changing) tick.value = pass % 2
                name
            },
            verify = { root, passes ->
                checkWidgets("panels", panelCount(root), widgets + 1)
                checkScopeRuns("the panel scopes", panelRuns[0], widgets * if (changing) 1 + passes else 1)
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
 * so a declaration that never reached one leaves it carrying no name at all.
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
                val expected = if (changing) (passes - 1) % 2 else SLIDER_MAXIMUM
                check(values.all { it == expected }) {
                    "a slider is left on ${values.first { it != expected }}, where $expected was declared last"
                }
            },
        )
    }

/**
 * The text written on pass [pass]. Two interned constants alternating, so every pass is a real change
 * and the driver itself allocates nothing.
 */
internal fun alternatingText(pass: Int): String = if (pass % 2 == 0) "A" else "B"

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
