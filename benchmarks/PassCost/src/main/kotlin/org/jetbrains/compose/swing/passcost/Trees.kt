package org.jetbrains.compose.swing.passcost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.declare
import java.awt.Color
import java.beans.PropertyChangeEvent
import javax.swing.JPanel

/**
 * The texts of the labels that never change, built once so a re-executed scope allocates none.
 *
 * Every widget below takes an `onCompose` callback it invokes as its body runs, so an arm can state how
 * many times a pass re-executed which scope - which is what separates a measured pass from a pass that
 * did nothing at all.
 */
internal val FILLER_TEXTS: List<String> = List(LARGE_TREE) { "row $it" }

/** Reads the text itself, so a write to it invalidates this scope and no other. */
@Composable
internal fun ReadingLabel(
    text: State<String>,
    onCompose: () -> Unit,
) {
    onCompose()
    Label(text.value)
}

/** Emits one label while [present] holds, so a write to it inserts or removes a widget. */
@Composable
internal fun OptionalLabel(
    present: State<Boolean>,
    onCompose: () -> Unit,
) {
    onCompose()
    if (present.value) Label(EXTRA_TEXT)
}

/**
 * A panel carrying a chain of four modifier elements, re-executed whenever [tick] changes.
 *
 * With [freshCallback] false the chain this pass declares equals the one applied last and is skipped
 * whole; with it true the listener element carries a lambda of a new identity, which no comparison can
 * match, so the chain is applied again. Nothing else separates the two.
 */
@Composable
internal fun ChainPanel(
    tick: State<Int>,
    freshCallback: Boolean,
    onCompose: () -> Unit,
) {
    onCompose()
    val onPropertyChange = if (freshCallback) forwardingPropertyChange { } else STABLE_PROPERTY_CHANGE
    SwingNode(
        factory = { JPanel() },
        update = {
            set(tick.value) { /* The key is the point; there is nothing to write. */ }
            applyModifier(
                SwingModifier
                    .background(Color.WHITE)
                    .foreground(Color.BLACK)
                    .toolTip(CHAIN_TOOL_TIP)
                    .propertyChangeListener(onPropertyChange),
            )
        },
    )
}

/** A panel whose update block holds one key and nothing else: what an invalidated node costs. */
@Composable
internal fun PlainPanel(
    tick: State<Int>,
    onCompose: () -> Unit,
) {
    onCompose()
    SwingNode(
        factory = { JPanel() },
        update = {
            set(tick.value) { /* The key is the point; there is nothing to write. */ }
        },
    )
}

/**
 * A panel keying [DECLARED_PROPERTIES] updates on the same two-part value a declaration is keyed on,
 * with nothing to apply them with: what a pass spends on the keys alone.
 */
@Composable
internal fun KeyedPanel(
    tick: State<Int>,
    onCompose: () -> Unit,
) {
    onCompose()
    SwingNode(
        factory = { JPanel() },
        update = {
            set(tick.value) { /* The key is the point; there is nothing to write. */ }
            repeat(DECLARED_PROPERTIES) {
                set(true to true) { /* The key is the point; there is nothing to write. */ }
            }
        },
    )
}

/**
 * A panel settling [DECLARED_PROPERTIES] two-way declarations, each on the value the widget already
 * holds, so no pass after the first writes anything: what is left is the price of declaring.
 */
@Composable
internal fun DeclaringPanel(
    tick: State<Int>,
    onCompose: () -> Unit,
) {
    onCompose()
    val applied = remember { List(DECLARED_PROPERTIES) { MirrorState(true) } }
    SwingNode(
        factory = { JPanel() },
        update = {
            set(tick.value) { /* The key is the point; there is nothing to write. */ }
            for (index in 0 until DECLARED_PROPERTIES) {
                declare(true, applied[index], { isEnabled }, { isEnabled = it })
            }
        },
    )
}

/**
 * One value of the data a [DeclaredTree] describes its structure with.
 *
 * Two values compare field by field in declaration order, so a pair differing in nothing but the label of
 * their last node is the pair a whole-tree comparison has to walk to the end.
 */
internal data class TreeValue(
    val label: String,
    val children: List<TreeValue>,
)

/** One row of the data a [DeclaredTable] shows. */
internal data class TableRow(
    val text: String,
)

/**
 * A root and [children] child values, the last of them labeled [lastLabel].
 *
 * Every call builds fresh values that share no node with any other tree: a value compares by identity
 * before it compares by field, so a shared node would be equal without ever being looked at.
 */
internal fun treeOf(
    children: Int,
    lastLabel: String,
): TreeValue =
    TreeValue(
        TREE_ROOT_LABEL,
        List(children) { index -> TreeValue(if (index == children - 1) lastLabel else "node $index", emptyList()) },
    )

/** [rows] rows, the last of them carrying [lastText], built fresh and sharing no row with any other list. */
internal fun rowsOf(
    rows: Int,
    lastText: String,
): List<TableRow> = List(rows) { index -> TableRow(if (index == rows - 1) lastText else "row $index") }

/**
 * A tree whose whole structure is declared as data: the value [root] holds, walked through the child and
 * label accessors. Both accessors are of one identity across passes, so what the tree compares pass to
 * pass is the root value alone.
 */
@Composable
internal fun DeclaredTree(
    root: State<TreeValue>,
    onCompose: () -> Unit,
) {
    onCompose()
    Tree(root = root.value, children = { it.children }, label = { it.label })
}

/** A table of one column whose rows are declared as data: the list [rows] holds. */
@Composable
internal fun DeclaredTable(
    rows: State<List<TableRow>>,
    onCompose: () -> Unit,
) {
    onCompose()
    Table(rows = rows.value) {
        column(TABLE_COLUMN_HEADER) { row -> row.text }
    }
}

/**
 * Adapts a plain callback into the listener lambda a builder takes, outside any composable - the shape a
 * component's own private helper has, and one that hands back a lambda of a new identity per call.
 */
private fun forwardingPropertyChange(onChange: () -> Unit): (PropertyChangeEvent) -> Unit = { onChange() }

/** A callback of one identity, so a chain carrying it equals the chain applied last. */
private val STABLE_PROPERTY_CHANGE: (PropertyChangeEvent) -> Unit = { }
