package org.jetbrains.compose.swing.passcost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Alignment
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.layout.CardPanel
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.GridBagPanel
import org.jetbrains.compose.swing.components.layout.GridPanel
import org.jetbrains.compose.swing.components.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.passcost.harness.onEventDispatchThread
import java.awt.BorderLayout
import java.awt.Container
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JPanel

/*
 * What a layout panel costs a pass.
 *
 * A panel holds no value of its own, so what it is measured on is its children: every arm here composes
 * one panel per tree unit, each holding the same small fixed set of labels, and moves one
 * declaration on every pass. The panels sit inside a `Column`, the container every other arm in this
 * module is measured inside, so what an arm reports above that column is the panel's own reconciliation.
 *
 * The child count is fixed, so what scales with an arm's size is the number of panels rather than the
 * number of children: two sizes then separate what a panel costs from what one pass costs whatever the
 * tree holds.
 *
 * A panel taking per-child constraints through a scope is measured twice - once with a child's text
 * moving and once with the constraint itself moving, which re-registers the child with the parent's
 * layout manager rather than writing a property on it.
 */

/** The body of one measured panel, taking the value that this arm changes on every pass. */
private typealias PanelContent<T> = @Composable (declared: T) -> Unit

/** How many labels every measured panel holds. */
private const val PANEL_CHILDREN = 3

/** The texts of the two children that never change, so only the third separates two passes. */
private const val FIRST_STEADY_TEXT = "first"
private const val SECOND_STEADY_TEXT = "second"

/** The text of the child whose declared placement moves, by which a verify finds it among its siblings. */
private const val MOVED_TEXT = "moved"

/** A deck's third card, holding a third child so a deck holds as many children as a panel. */
private const val THIRD_CARD_KEY = "card three"

private const val BORDER_TEXT_ARM = "border panel text changed"
private const val BORDER_REGION_ARM = "border panel region moved"
private const val BOX_TEXT_ARM = "box panel text changed"
private const val CARD_TEXT_ARM = "card panel text changed"
private const val CARD_SHOWN_ARM = "card panel card shown"
private const val FLOW_TEXT_ARM = "flow panel text changed"
private const val GRID_TEXT_ARM = "grid panel text changed"
private const val GRID_BAG_TEXT_ARM = "grid bag panel text changed"
private const val GRID_BAG_CELL_ARM = "grid bag panel cell moved"
private const val ROW_TEXT_ARM = "row text changed"
private const val ROW_ALIGNMENT_ARM = "row alignment moved"

/** The extent a row is laid out at while its children's declared placements are read back. */
private const val ROW_WIDTH = 400
private const val ROW_HEIGHT = 200

/** How far down the free space a row's starting placement puts a child: one part in this many. */
private const val ROW_START_PARTS = 4

/** The two texts a changing child alternates between, built once so no pass allocates one. */
private val CHANGING_TEXTS: List<String> = listOf(alternatingText(0), alternatingText(1))

/**
 * The two regions a border panel's moving child alternates between. Both are free: the panel's other
 * children hold the center and the west, so neither pass leaves two children on one region.
 */
private val BORDER_REGIONS: List<String> = listOf(BorderLayout.NORTH, BorderLayout.SOUTH)

/** The region a border panel's moving child starts on, free of both its alternates and of its siblings'. */
private val BORDER_START_REGION: String = BorderLayout.EAST

/** The two cards a deck alternates between showing. Each is held by a child of its own. */
private val CARD_KEYS: List<String> = listOf("card one", "card two")

/** The two grid rows a grid bag panel's moving child alternates between. */
private val GRID_BAG_ROWS: List<Int> = listOf(0, 1)

/** The grid row a grid bag panel's moving child starts on, which is neither row it alternates between. */
private const val GRID_BAG_START_ROW = 2

/**
 * The two placements a row's moving child alternates between. Neither is the row's own `verticalAlignment`
 * - which is `Alignment.Top`, the default - so a child left where the row puts it is not where either of
 * these declares it, and the check cannot pass on a declaration that never reached the layout.
 */
private val ROW_ALIGNMENTS: List<Alignment.Vertical> = listOf(Alignment.CenterVertically, Alignment.Bottom)

/**
 * The placement a row's moving child starts on: a quarter of the way down the space it leaves free. That
 * is neither placement it alternates between, and not the row's own `verticalAlignment` either.
 */
private val ROW_START_ALIGNMENT: Alignment.Vertical =
    Alignment.Vertical { size, space -> (space - size) / ROW_START_PARTS }

/** The child labels a panel holds, the first of them carrying the text an arm changes. */
private val CHILD_LABELS: PanelContent<String> = { text ->
    Label(text)
    Label(FIRST_STEADY_TEXT)
    Label(SECOND_STEADY_TEXT)
}

/** A border panel whose children hold three regions, of which the north one's text changes. */
private val BORDER_TEXT_PANEL: PanelContent<String> = { text ->
    BorderPanel {
        Label(text, modifier = SwingModifier.north())
        Label(FIRST_STEADY_TEXT, modifier = SwingModifier.center())
        Label(SECOND_STEADY_TEXT, modifier = SwingModifier.south())
    }
}

/**
 * A border panel whose first child moves between the north and south regions while its siblings hold the
 * center and the west: what re-registering a child with its parent's layout manager costs. The child
 * starts on the east, the one remaining free region, so it holds one no pass ever declares onto it.
 */
private val BORDER_REGION_PANEL: PanelContent<String> = { region ->
    BorderPanel {
        Label(
            MOVED_TEXT,
            modifier =
                when (region) {
                    BorderLayout.NORTH -> SwingModifier.north()
                    BorderLayout.SOUTH -> SwingModifier.south()
                    else -> SwingModifier.east()
                },
        )
        Label(FIRST_STEADY_TEXT, modifier = SwingModifier.center())
        Label(SECOND_STEADY_TEXT, modifier = SwingModifier.west())
    }
}

/** A box panel of three labels, of which the first one's text changes. */
private val BOX_TEXT_PANEL: PanelContent<String> = { text -> BoxPanel { CHILD_LABELS(text) } }

/** A card deck showing one card throughout, whose text is what changes. */
private val CARD_TEXT_PANEL: PanelContent<String> = { text ->
    CardPanel(selectedCard = CARD_KEYS[0]) {
        Label(text, modifier = SwingModifier.card(CARD_KEYS[0]))
        Label(FIRST_STEADY_TEXT, modifier = SwingModifier.card(CARD_KEYS[1]))
        Label(SECOND_STEADY_TEXT, modifier = SwingModifier.card(THIRD_CARD_KEY))
    }
}

/**
 * A card deck of three cards whose shown card changes, each card holding a label that reads the card's own
 * name - which is what lets a verify say from the one visible child which card the deck settled on.
 */
private val CARD_SHOWN_PANEL: PanelContent<String> = { shown ->
    CardPanel(selectedCard = shown) {
        Label(CARD_KEYS[0], modifier = SwingModifier.card(CARD_KEYS[0]))
        Label(CARD_KEYS[1], modifier = SwingModifier.card(CARD_KEYS[1]))
        Label(THIRD_CARD_KEY, modifier = SwingModifier.card(THIRD_CARD_KEY))
    }
}

/** A flow panel of three labels, of which the first one's text changes. */
private val FLOW_TEXT_PANEL: PanelContent<String> = { text -> FlowPanel { CHILD_LABELS(text) } }

/** A grid panel of three labels, of which the first one's text changes. */
private val GRID_TEXT_PANEL: PanelContent<String> = { text -> GridPanel { CHILD_LABELS(text) } }

/** A grid bag panel of three placed children, of which the first one's text changes. */
private val GRID_BAG_TEXT_PANEL: PanelContent<String> = { text ->
    GridBagPanel {
        Label(text, modifier = SwingModifier.item(gridx = 0, gridy = 0))
        Label(FIRST_STEADY_TEXT, modifier = SwingModifier.item(gridx = 1, gridy = 0))
        Label(SECOND_STEADY_TEXT, modifier = SwingModifier.item(gridx = 2, gridy = 0))
    }
}

/**
 * A grid bag panel whose first child moves between two grid rows. The constraints a pass builds compare by
 * value, so the two steady children declare the cell they already hold and only the moving one is placed
 * again.
 */
private val GRID_BAG_CELL_PANEL: PanelContent<Int> = { row ->
    GridBagPanel {
        Label(MOVED_TEXT, modifier = SwingModifier.item(gridx = 0, gridy = row))
        Label(FIRST_STEADY_TEXT, modifier = SwingModifier.item(gridx = 1, gridy = 0))
        Label(SECOND_STEADY_TEXT, modifier = SwingModifier.item(gridx = 2, gridy = 0))
    }
}

/** A row of three labels, of which the first one's text changes. */
private val ROW_TEXT_PANEL: PanelContent<String> = { text -> Row { CHILD_LABELS(text) } }

/**
 * A row whose first child names a new placement across the row on every pass. A row's per-child
 * declarations reach its layout manager rather than the widget, so this is the scope declaration that
 * moves without any property of the child being written.
 */
private val ROW_ALIGNED_PANEL: PanelContent<Alignment.Vertical> = { alignment ->
    Row {
        Label(MOVED_TEXT, modifier = SwingModifier.align(alignment))
        Label(FIRST_STEADY_TEXT)
        Label(SECOND_STEADY_TEXT)
    }
}

/** Every arm this file measures, one panel after another and the text arm of each before its scope arm. */
internal fun panelArms(): List<Arm> =
    listOf(
        panelArm(BORDER_TEXT_ARM, INITIAL_TEXT, CHANGING_TEXTS, BORDER_TEXT_PANEL, ::checkDeclaredText),
        panelArm(BORDER_REGION_ARM, BORDER_START_REGION, BORDER_REGIONS, BORDER_REGION_PANEL, ::checkDeclaredRegion),
        panelArm(BOX_TEXT_ARM, INITIAL_TEXT, CHANGING_TEXTS, BOX_TEXT_PANEL, ::checkDeclaredText),
        panelArm(CARD_TEXT_ARM, INITIAL_TEXT, CHANGING_TEXTS, CARD_TEXT_PANEL, ::checkDeclaredText),
        panelArm(CARD_SHOWN_ARM, THIRD_CARD_KEY, CARD_KEYS, CARD_SHOWN_PANEL, ::checkShownCard),
        panelArm(FLOW_TEXT_ARM, INITIAL_TEXT, CHANGING_TEXTS, FLOW_TEXT_PANEL, ::checkDeclaredText),
        panelArm(GRID_TEXT_ARM, INITIAL_TEXT, CHANGING_TEXTS, GRID_TEXT_PANEL, ::checkDeclaredText),
        panelArm(GRID_BAG_TEXT_ARM, INITIAL_TEXT, CHANGING_TEXTS, GRID_BAG_TEXT_PANEL, ::checkDeclaredText),
        panelArm(GRID_BAG_CELL_ARM, GRID_BAG_START_ROW, GRID_BAG_ROWS, GRID_BAG_CELL_PANEL, ::checkDeclaredCell),
        panelArm(ROW_TEXT_ARM, INITIAL_TEXT, CHANGING_TEXTS, ROW_TEXT_PANEL, ::checkDeclaredText),
        panelArm(ROW_ALIGNMENT_ARM, ROW_START_ALIGNMENT, ROW_ALIGNMENTS, ROW_ALIGNED_PANEL, ::checkDeclaredAlignment),
    )

/**
 * One panel per tree unit, each built by [panel] from a value that alternates between the two [values]
 * holds, with [checkDeclared] reading back what the last pass declared onto every panel.
 *
 * Both values are built before the batch, so every pass is a real change and the driver allocates
 * nothing. The state starts on [start], which is neither of them: a panel a change never reached still
 * holds [start], which no expectation of a changing batch names, so [checkDeclared] raises on it whatever
 * parity the batch ends on. The state compares by identity, so what a pass pays for is the panel rather
 * than the comparison that reached it.
 */
private fun <T> panelArm(
    name: String,
    start: T,
    values: List<T>,
    panel: PanelContent<T>,
    checkDeclared: (panels: List<JPanel>, declared: T) -> Unit,
): Arm =
    Arm(listOf(name)) { panels, changing ->
        val declared = mutableStateOf(start, referentialEqualityPolicy())
        val panelRuns = IntArray(1)
        Run(
            content = {
                Column {
                    repeat(panels) { ChangingPanel(declared, panel) { panelRuns[0]++ } }
                }
            },
            drive = { pass ->
                if (changing) declared.value = values[pass % 2]
                name
            },
            verify = { root, passes ->
                val composed = armPanels(root)
                checkWidgets("panels", composed.size, panels)
                checkWidgets("labels", labelCount(root), panels * PANEL_CHILDREN)
                checkScopeRuns("the panel scopes", panelRuns[0], panels * if (changing) 1 + passes else 1)
                checkDeclared(composed, if (changing) values[(passes - 1) % 2] else start)
            },
        )
    }

/**
 * One panel, built by [panel] from what [declared] holds. The value is read here, so a write invalidates
 * this scope and the panel below it is what a pass reconciles.
 */
@Composable
private fun <T> ChangingPanel(
    declared: State<T>,
    panel: PanelContent<T>,
    onCompose: () -> Unit,
) {
    onCompose()
    panel(declared.value)
}

/** The panels the composed [root] holds, one per tree unit, in the order the column arranges them. */
private fun armPanels(root: Container): List<JPanel> {
    val columns = root.components.filterIsInstance<JPanel>()
    check(columns.size == 1) { "the tree holds ${columns.size} columns where one was composed" }
    return columns.first().components.filterIsInstance<JPanel>()
}

/** The child of [panel] whose declared placement moves, found by the text only that child carries. */
private fun movedLabel(panel: JPanel): JLabel {
    val moved = panel.components.filterIsInstance<JLabel>().filter { it.text == MOVED_TEXT }
    check(moved.size == 1) { "the panel holds ${moved.size} moving children where one was composed" }
    return moved.first()
}

/** Raises unless every panel holds a label reading [text], the text the last pass declared onto it. */
private fun checkDeclaredText(
    panels: List<JPanel>,
    text: String,
) {
    val carrying =
        panels.count { panel ->
            panel.components.filterIsInstance<JLabel>().any { it.text == text }
        }
    check(carrying == panels.size) {
        "$carrying of ${panels.size} panels hold a label reading '$text', the text declared last"
    }
}

/**
 * Raises unless every panel's moving child occupies [region], the region the last pass declared onto it.
 * A child whose constraint never reached the layout manager is held by the center instead, which is the
 * region a border layout registers an unconstrained child under.
 */
private fun checkDeclaredRegion(
    panels: List<JPanel>,
    region: String,
) {
    for (panel in panels) {
        val layout = panel.layout as BorderLayout
        val placed = layout.getLayoutComponent(region)
        check(placed === movedLabel(panel)) {
            "the region '$region' holds $placed, where the moving child was declared onto it last"
        }
    }
}

/**
 * Raises unless every deck shows the card [key] names. A deck leaves one child visible - the one on the
 * card it shows - and every child reads the name of its own card, so the visible one says which card that
 * is.
 */
private fun checkShownCard(
    panels: List<JPanel>,
    key: String,
) {
    for (panel in panels) {
        val visible = panel.components.filter { it.isVisible }
        check(visible.size == 1) { "a card deck leaves ${visible.size} children visible, where it leaves one" }
        val shown = visible.first()
        check(shown is JLabel && shown.text == key) {
            "the deck shows $shown, where the card '$key' was declared shown last"
        }
    }
}

/**
 * Raises unless every panel's moving child sits in grid row [row], the row the last pass declared onto it.
 * An item that never reached the layout manager leaves the child on `GridBagConstraints`' own default row,
 * which is neither of the two an arm declares.
 */
private fun checkDeclaredCell(
    panels: List<JPanel>,
    row: Int,
) {
    for (panel in panels) {
        val layout = panel.layout as GridBagLayout
        val placed = layout.getConstraints(movedLabel(panel)).gridy
        check(placed == row) {
            "the moving child sits in grid row $placed, where row $row was declared onto it last"
        }
    }
}

/**
 * Raises unless every row places its moving child where [declared] puts it.
 *
 * A row's per-child placements reach its layout manager and no widget, so a laid-out row is what says one
 * arrived. Each row is sized and laid out first, on the thread the composition ran on; the expectation is
 * then computed from the extent the child was given, so it holds whatever extent this runtime measures a
 * label at.
 */
private fun checkDeclaredAlignment(
    panels: List<JPanel>,
    declared: Alignment.Vertical,
) {
    onEventDispatchThread {
        for (panel in panels) {
            panel.setSize(ROW_WIDTH, ROW_HEIGHT)
            panel.doLayout()
        }
    }
    for (panel in panels) {
        val moved = movedLabel(panel)
        val expected = declared.align(moved.height, ROW_HEIGHT)
        check(moved.y == expected) {
            "the moving child sits ${moved.y} pixels down the row, where what was declared last puts it at $expected"
        }
    }
}
