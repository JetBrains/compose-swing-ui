package org.jetbrains.compose.swing.passcost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.components.selection.RadioGroup
import javax.swing.AbstractButton
import javax.swing.JComboBox
import javax.swing.JRadioButton
import javax.swing.JSpinner

/** Every arm measuring an interactive control, in the order the report states them. */
internal fun controlArms(): List<Arm> =
    listOf(
        controlArm(BUTTON_TEXT_ARM, Control.BUTTON, ControlChange.TEXT),
        controlArm(CHECK_BOX_TEXT_ARM, Control.CHECK_BOX, ControlChange.TEXT),
        controlArm(CHECK_BOX_SELECTED_ARM, Control.CHECK_BOX, ControlChange.SELECTED),
        controlArm(RADIO_BUTTON_TEXT_ARM, Control.RADIO_BUTTON, ControlChange.TEXT),
        controlArm(TOGGLE_BUTTON_TEXT_ARM, Control.TOGGLE_BUTTON, ControlChange.TEXT),
        radioGroupSelectionArm(),
        comboBoxSelectionArm(),
        comboBoxItemsArm(),
        spinnerValueArm(),
    )

/** The four buttons a control arm measures, each named as the report and its checks name it. */
private enum class Control(
    val one: String,
    val many: String,
) {
    BUTTON("button", "buttons"),
    CHECK_BOX("check box", "check boxes"),
    RADIO_BUTTON("radio button", "radio buttons"),
    TOGGLE_BUTTON("toggle button", "toggle buttons"),
}

/** What a control arm's driver moves: the text a button shows, or the state a two-state button holds. */
private enum class ControlChange { TEXT, SELECTED }

/**
 * Every widget one button of the kind [control], with [changed] moving on every pass.
 *
 * The two changes are what separate a one-way declaration from a two-way one on the same widget. A text
 * is written whenever it differs from the one written last; a selected state is settled against what the
 * button holds, through the mirror the button's own toggle reporting feeds, so what the pass pays for
 * includes reading the widget back.
 *
 * The text alternates between two interned constants and starts on a third, so every pass is a real
 * change and the driver allocates nothing, and a button that took no declaration is left carrying a text
 * no check expects. A selected state has two values and no third to start on, so it starts on the one a
 * button is built holding and the state it is left in says nothing on its own;
 * [checkFollowsDeclaration] is what says a declared selected state reached the button.
 */
private fun controlArm(
    name: String,
    control: Control,
    changed: ControlChange,
): Arm =
    Arm(listOf(name)) { widgets, changing ->
        val text = mutableStateOf(INITIAL_TEXT)
        val selected = mutableStateOf(false)
        val controlRuns = IntArray(1)
        Run(
            content = {
                Column {
                    repeat(widgets) { DeclaredControl(control, text, selected) { controlRuns[0]++ } }
                }
            },
            drive = { pass ->
                if (changing) {
                    when (changed) {
                        ControlChange.TEXT -> text.value = alternatingText(pass)
                        ControlChange.SELECTED -> selected.value = pass % 2 == 0
                    }
                }
                name
            },
            verify = { root, passes ->
                val buttons = componentsOfType(root, AbstractButton::class.java)
                checkWidgets(control.many, buttons.size, widgets)
                val expectedRuns = widgets * if (changing) 1 + passes else 1
                checkScopeRuns("the ${control.many} scopes", controlRuns[0], expectedRuns)
                when (changed) {
                    ControlChange.TEXT -> {
                        val expected = if (changing) alternatingText(passes - 1) else INITIAL_TEXT
                        val wrong = buttons.firstOrNull { it.text != expected }
                        check(wrong == null) {
                            "a ${control.one} reads '${wrong?.text}', where '$expected' was declared last"
                        }
                    }

                    ControlChange.SELECTED -> {
                        val expected = if (changing && (passes - 1) % 2 == 0) widgets else 0
                        val counted = buttons.count { it.isSelected }
                        check(counted == expected) {
                            "the tree holds $counted selected ${control.many}, where $expected were declared so"
                        }
                        checkFollowsDeclaration(
                            what = control.one,
                            declare = { tick -> selected.value = tick == 0 },
                            applied = { buttons.map { it.isSelected } },
                            expectedAt = { tick -> tick == 0 },
                        )
                    }
                }
            },
        )
    }

/**
 * One button of the kind [control], reading the declared text and the declared selected state, so a write
 * to either invalidates this scope and no other. A plain button holds no selected state and reads none.
 */
@Composable
private fun DeclaredControl(
    control: Control,
    text: State<String>,
    selected: State<Boolean>,
    onCompose: () -> Unit,
) {
    onCompose()
    val label = text.value
    when (control) {
        Control.BUTTON -> Button(text = label, onClick = {})
        Control.CHECK_BOX -> CheckBox(text = label, checked = selected.value, onCheckedChange = {})
        Control.RADIO_BUTTON -> RadioButton(text = label, selected = selected.value, onSelectedChange = {})
        Control.TOGGLE_BUTTON -> ToggleButton(text = label, selected = selected.value, onSelectedChange = {})
    }
}

/**
 * One radio group whose first option is selected and cleared again, reported as two series the way the
 * structural arm reports its own: selecting an option is a write to that option's button, while clearing
 * it goes through the group the options share.
 *
 * The selection alternates between the first option and none at all rather than between two options: a
 * group of one option has no second option to move to, so a move between options would be a different
 * change at each of the two sizes, and the slope between them would carry both. That leaves a selection
 * of two values and no third to start on, so what the group is left holding says nothing on its own and
 * [checkFollowsDeclaration] is what says a declared selection reached the buttons.
 */
private fun radioGroupSelectionArm(): Arm =
    Arm(listOf(RADIO_GROUP_SELECT_SERIES, RADIO_GROUP_CLEAR_SERIES)) { options, changing ->
        val selected = mutableIntStateOf(NO_OPTION)
        val groupRuns = IntArray(1)
        val optionRuns = IntArray(1)
        Run(
            content = {
                DeclaredRadioGroup(
                    options = options,
                    selectedIndex = selected,
                    onCompose = { groupRuns[0]++ },
                    onDeclareOption = { optionRuns[0]++ },
                )
            },
            drive = { pass ->
                val selecting = pass % 2 == 0
                if (changing) selected.intValue = if (selecting) FIRST_OPTION else NO_OPTION
                if (selecting) RADIO_GROUP_SELECT_SERIES else RADIO_GROUP_CLEAR_SERIES
            },
            verify = { root, passes ->
                val buttons = componentsOfType(root, JRadioButton::class.java)
                checkWidgets("radio buttons", buttons.size, options)
                checkScopeRuns("the group's scope", groupRuns[0], if (changing) 1 + passes else 1)
                val expectedOptionRuns = options * if (changing) 1 + passes else 1
                checkScopeRuns("the option declarations", optionRuns[0], expectedOptionRuns)
                val expected = if (changing && (passes - 1) % 2 == 0) FIRST_OPTION else NO_OPTION
                val shown = buttons.indexOfFirst { it.isSelected }
                check(shown == expected) {
                    "the group has option $shown selected, where $expected was declared last"
                }
                val held = buttons.count { it.isSelected }
                check(held == if (expected == NO_OPTION) 0 else 1) {
                    "the group holds $held selected options, where a group holds at most one"
                }
                checkFollowsDeclaration(
                    what = "radio group",
                    declare = { tick -> selected.intValue = if (tick == 0) FIRST_OPTION else NO_OPTION },
                    applied = { listOf(buttons.indexOfFirst { it.isSelected }) },
                    expectedAt = { tick -> if (tick == 0) FIRST_OPTION else NO_OPTION },
                )
            },
        )
    }

/**
 * A radio group of [options] choices, selected on the option [selectedIndex] names. The labels are the
 * texts built once for the whole run, so collecting the choices allocates no text of its own.
 */
@Composable
private fun DeclaredRadioGroup(
    options: Int,
    selectedIndex: IntState,
    onCompose: () -> Unit,
    onDeclareOption: () -> Unit,
) {
    onCompose()
    RadioGroup(selectedIndex = selectedIndex.intValue, onSelectionChange = {}) {
        repeat(options) { index ->
            onDeclareOption()
            option(FILLER_TEXTS[index])
        }
    }
}

/**
 * Every widget a combo box over two items that never change, whose declared selection moves to the other
 * item on every pass: what a two-way selection costs where the widget answers the write by publishing it
 * straight back through the action channel the wrapper listens on.
 *
 * The selection starts on no item at all, so the first pass moves it as every later one does. A combo box
 * carries a popup, an editor and a renderer of its own, so the larger tree here holds [HEAVY_TREE] of
 * them rather than the [LARGE_TREE] a light control is measured at.
 */
private fun comboBoxSelectionArm(): Arm =
    Arm(listOf(COMBO_BOX_SELECTION_ARM), HEAVY_TREE_SIZES) { widgets, changing ->
        val steadyItems = mutableStateOf(List(2) { alternatingText(it) })
        val selected = mutableStateOf<String?>(null)
        val comboRuns = IntArray(1)
        Run(
            content = {
                Column {
                    repeat(widgets) { DeclaredComboBox(steadyItems, selected) { comboRuns[0]++ } }
                }
            },
            drive = { pass ->
                if (changing) selected.value = alternatingText(pass)
                COMBO_BOX_SELECTION_ARM
            },
            verify = { root, passes ->
                val boxes = componentsOfType(root, JComboBox::class.java)
                checkWidgets("combo boxes", boxes.size, widgets)
                val expectedRuns = widgets * if (changing) 1 + passes else 1
                checkScopeRuns("the combo box scopes", comboRuns[0], expectedRuns)
                val expected = if (changing) alternatingText(passes - 1) else null
                val wrong = boxes.firstOrNull { it.selectedItem != expected }
                check(wrong == null) {
                    "a combo box holds '${wrong?.selectedItem}', where '$expected' was declared last"
                }
            },
        )
    }

/**
 * One combo box whose declared items change on every pass: the last item carries a different text, so a
 * comparison tells the two lists apart only at the end. The arm scales the items rather than the widgets,
 * as the list box's own items arm does, so the slope between its sizes is what one item costs.
 *
 * No item is selected, so what a pass pays for is the items and the model built around them alone.
 *
 * The box is composed over a third list, whose last item carries a text no pass declares, so a box that
 * took no declaration after the first is left showing a text no check expects. All three are built ahead
 * of the batch, so no pass allocates a list.
 */
private fun comboBoxItemsArm(): Arm =
    Arm(listOf(COMBO_BOX_ITEMS_ARM)) { items, changing ->
        val itemSets = List(2) { index -> itemsOf(items, alternatingText(index)) }
        val declared = mutableStateOf(itemsOf(items, INITIAL_TEXT), referentialEqualityPolicy())
        val noSelection = mutableStateOf<String?>(null)
        val comboRuns = IntArray(1)
        Run(
            content = { DeclaredComboBox(declared, noSelection) { comboRuns[0]++ } },
            drive = { pass ->
                if (changing) declared.value = itemSets[pass % 2]
                COMBO_BOX_ITEMS_ARM
            },
            verify = { root, passes ->
                val model = singleOfType(root, JComboBox::class.java).model
                checkWidgets("combo box items", model.size, items)
                checkScopeRuns("the combo box's scope", comboRuns[0], if (changing) 1 + passes else 1)
                val expected = if (changing) alternatingText(passes - 1) else INITIAL_TEXT
                val shown = model.getElementAt(model.size - 1)
                check(shown == expected) { "the last item reads '$shown', where '$expected' was declared last" }
            },
        )
    }

/**
 * A combo box over the items [items] holds, selected on the item [selected] holds. Both are read here, so
 * a write to either invalidates this scope and no other.
 */
@Composable
private fun DeclaredComboBox(
    items: State<List<String>>,
    selected: State<String?>,
    onCompose: () -> Unit,
) {
    onCompose()
    ComboBox(items = items.value, selectedItem = selected.value, onSelectionChange = {})
}

/**
 * Every widget a spinner whose declared value moves on every pass: what a two-way declaration costs a
 * widget that reformats the editor it shows the value through for every value it takes.
 *
 * The two values are boxed ahead of the batch and alternated, so the driver allocates nothing, and the
 * value starts on a third the first pass does not write. The model is unbounded, so both values are ones
 * the spinner holds whole and no pass settles on a value of the widget's own. A spinner builds an editor
 * of its own around its model, so the larger tree here holds [HEAVY_TREE] of them rather than the
 * [LARGE_TREE] a light control is measured at.
 */
private fun spinnerValueArm(): Arm =
    Arm(listOf(SPINNER_VALUE_ARM), HEAVY_TREE_SIZES) { widgets, changing ->
        val value = mutableStateOf<Number>(SPINNER_START)
        val spinnerRuns = IntArray(1)
        Run(
            content = {
                Column {
                    repeat(widgets) { DeclaredSpinner(value) { spinnerRuns[0]++ } }
                }
            },
            drive = { pass ->
                if (changing) value.value = SPINNER_VALUES[pass % 2]
                SPINNER_VALUE_ARM
            },
            verify = { root, passes ->
                val spinners = componentsOfType(root, JSpinner::class.java)
                checkWidgets("spinners", spinners.size, widgets)
                val expectedRuns = widgets * if (changing) 1 + passes else 1
                checkScopeRuns("the spinner scopes", spinnerRuns[0], expectedRuns)
                val expected = if (changing) SPINNER_VALUES[(passes - 1) % 2] else SPINNER_START
                val wrong = spinners.firstOrNull { it.value != expected }
                check(wrong == null) {
                    "a spinner is left on ${wrong?.value}, where $expected was declared last"
                }
            },
        )
    }

/** A spinner stepping through numbers, settled on the value [value] holds, bounded at neither end. */
@Composable
private fun DeclaredSpinner(
    value: State<Number>,
    onCompose: () -> Unit,
) {
    onCompose()
    Spinner(value = value.value, onValueChange = {})
}

private const val BUTTON_TEXT_ARM = "button text changed"
private const val CHECK_BOX_TEXT_ARM = "check box text changed"
private const val CHECK_BOX_SELECTED_ARM = "check box selected changed"
private const val RADIO_BUTTON_TEXT_ARM = "radio button text changed"
private const val TOGGLE_BUTTON_TEXT_ARM = "toggle button text changed"
private const val RADIO_GROUP_SELECT_SERIES = "radio group option selected"
private const val RADIO_GROUP_CLEAR_SERIES = "radio group option cleared"
private const val COMBO_BOX_SELECTION_ARM = "combo box selection changed"
private const val COMBO_BOX_ITEMS_ARM = "combo box items changed"
private const val SPINNER_VALUE_ARM = "spinner value changed"

/** The option a radio group arm selects. */
private const val FIRST_OPTION = 0

/** The index that leaves every option of a radio group unselected. */
private const val NO_OPTION = -1

/** The value a declared spinner starts on, so the first value written over it is a real change. */
private const val SPINNER_START: Int = 2

/** The two values a spinner arm alternates between, boxed once so the driver allocates none. */
private val SPINNER_VALUES: List<Number> = listOf(0, 1)
