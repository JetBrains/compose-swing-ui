@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import org.jetbrains.compose.swing.constants.BoxAxis
import org.jetbrains.compose.swing.constants.FlowAlignment
import org.jetbrains.compose.swing.constants.GridBagAnchor
import org.jetbrains.compose.swing.constants.GridBagFill
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Insets
import java.util.Objects
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * A `JPanel` under the Swing layout manager [layout] names, holding [content] as its children.
 *
 * Children are written plainly; what a child declares to [layout]'s scope places it under that manager.
 *
 * A change of layout kind rebuilds the panel and its children, because the constraints those children
 * carry belong to the manager that was there; a change of a layout's parameters applies to the panel
 * already standing.
 *
 * @param layout the layout the panel is built under; see [PanelLayout]
 * @param modifier the [SwingModifier] applied to the panel
 * @param content the composable content of the panel, with [layout]'s scope as its receiver
 */
@Composable
public fun <S : PanelScope> Panel(
    layout: PanelLayout<S>,
    modifier: SwingModifier = SwingModifier,
    content: @Composable S.() -> Unit,
) {
    key(layout.javaClass) {
        SwingNode<JPanel>(
            factory = { ScrollablePanel() },
            modifier = modifier,
            update = { layout.installOn(this) },
            content = { layout.contentScope.content() },
        )
    }
}

/**
 * A `JPanel` under the layout a `JPanel` builds itself with: a [PanelLayout.Flow] laying [content] out
 * in a centered row that wraps, five pixels apart.
 *
 * @param modifier the [SwingModifier] applied to the panel
 * @param content the composable content of the panel
 */
@Composable
public fun Panel(
    modifier: SwingModifier = SwingModifier,
    content: @Composable PanelScope.() -> Unit,
): Unit = Panel(PanelLayout.Flow(), modifier, content)

/**
 * The layout a [Panel] is built under: the Swing layout manager the panel holds, the parameters that
 * manager takes, and the scope [S] through which the panel's children declare their placement under it.
 *
 * A layout not modeled here is declared with a `SwingNode` of your own that builds the container, its
 * children naming their constraints with `SwingModifier.layoutConstraint` - see
 * `docs/CUSTOM-CONTAINERS.md`.
 */
@Immutable
public sealed class PanelLayout<S : PanelScope> private constructor(
    internal val contentScope: S,
) {
    /**
     * Installs this layout's manager on the panel and declares this layout's parameters on [updater],
     * each written to that manager on the pass that builds the panel and on every pass that changes the
     * parameter.
     */
    internal abstract fun installOn(updater: SwingNodeUpdater<JPanel>)

    /**
     * Lays the children out one after another along a single axis, in declaration order.
     *
     * A `BoxLayout` puts no space between children on its own. Declare gaps as content: [RigidArea] and
     * [Strut] for a fixed gap, [Glue] for empty space that takes the largest share of what is left over.
     *
     * @property axis the axis along which children are arranged (a [BoxAxis] `BoxLayout` value); the default
     *   `Y_AXIS` stacks them top to bottom
     * @see javax.swing.BoxLayout
     */
    public class Box(
        @param:BoxAxis public val axis: Int = BoxLayout.Y_AXIS,
    ) : PanelLayout<PanelScope>(PanelScopeImpl) {
        override fun installOn(updater: SwingNodeUpdater<JPanel>) {
            // A BoxLayout fixes its axis at construction and serves only the container it was built
            // for, so a new axis means a new instance for this panel. It holds no per-child data, so
            // recreating it loses nothing.
            updater.set(axis) {
                layout = BoxLayout(this, it)
                revalidate()
            }
        }

        override fun equals(other: Any?): Boolean = other is Box && axis == other.axis

        override fun hashCode(): Int = axis
    }

    /**
     * Lays the children out in a row, each at the size it prefers, and starts a new row with the child
     * that no longer fits the panel's width.
     *
     * @property alignment the horizontal alignment of components within each row (a [FlowAlignment]
     *   `FlowLayout` value); the default `CENTER` centers each row across the panel's width
     * @property hgap the horizontal gap held between two adjacent components and at the panel's left and
     *   right edges; `5`, `FlowLayout`'s own default
     * @property vgap the vertical gap held between two rows and at the panel's top and bottom edges; `5`,
     *   `FlowLayout`'s own default
     * @see java.awt.FlowLayout
     */
    public class Flow(
        @param:FlowAlignment public val alignment: Int = FlowLayout.CENTER,
        public val hgap: Int = 5,
        public val vgap: Int = 5,
    ) : PanelLayout<PanelScope>(PanelScopeImpl) {
        override fun installOn(updater: SwingNodeUpdater<JPanel>) {
            updater.init { layout = FlowLayout() }
            updater.setOnLayout<FlowLayout, _>(alignment) { this.alignment = it }
            updater.setOnLayout<FlowLayout, _>(hgap) { this.hgap = it }
            updater.setOnLayout<FlowLayout, _>(vgap) { this.vgap = it }
        }

        override fun equals(other: Any?): Boolean =
            other is Flow && alignment == other.alignment && hgap == other.hgap && vgap == other.vgap

        override fun hashCode(): Int = 31 * (31 * alignment + hgap) + vgap
    }

    /**
     * Divides the panel into equally sized cells and fills them with the children, one per cell, row by
     * row in the panel's reading order.
     *
     * A zero [rows] means as many rows as the children need, and a zero [cols] as many columns; one of
     * the two may be zero, never both.
     *
     * @property rows the number of rows, or 0 for as many as the children need; the default `1` puts every
     *   child in one row
     * @property cols the number of columns, or 0 for as many as the children need; a non-zero [rows] takes
     *   precedence, and the column count then follows from the row count and the number of children
     * @property hgap the horizontal gap between components; `0` by default, so columns touch
     * @property vgap the vertical gap between components; `0` by default, so rows touch
     * @throws IllegalArgumentException if both [rows] and [cols] are zero
     * @see java.awt.GridLayout
     */
    public class Grid(
        public val rows: Int = 1,
        public val cols: Int = 0,
        public val hgap: Int = 0,
        public val vgap: Int = 0,
    ) : PanelLayout<PanelScope>(PanelScopeImpl) {
        override fun installOn(updater: SwingNodeUpdater<JPanel>) {
            updater.init { layout = GridLayout() }
            updater.setOnLayout<GridLayout, _>(rows) { applyDimensions(it, this@Grid.cols) }
            updater.setOnLayout<GridLayout, _>(cols) { applyDimensions(this@Grid.rows, it) }
            updater.setOnLayout<GridLayout, _>(hgap) { this.hgap = it }
            updater.setOnLayout<GridLayout, _>(vgap) { this.vgap = it }
        }

        override fun equals(other: Any?): Boolean =
            other is Grid && rows == other.rows && cols == other.cols && hgap == other.hgap && vgap == other.vgap

        override fun hashCode(): Int = 31 * (31 * (31 * rows + cols) + hgap) + vgap
    }

    /**
     * Places each child in the region that child names: the four edges and the center. An edge child
     * keeps the thickness it prefers and spans its edge, and the center child takes whatever the edges
     * leave.
     *
     * A child names its region on its own modifier, through [BorderPanelScope]:
     * ```
     * Panel(PanelLayout.Border()) {
     *     Toolbar(modifier = SwingModifier.north())
     *     Editor()
     *     StatusBar(modifier = SwingModifier.south())
     * }
     * ```
     * A child that names no region occupies the center, so the panel's main content is written plainly.
     *
     * A region hosts one child: dropping a child (e.g. behind an `if`) empties the region it occupied,
     * and an edge no child names holds nothing.
     *
     * @property hgap the horizontal gap between regions; `0` by default, so they touch
     * @property vgap the vertical gap between regions; `0` by default, so they touch
     * @see java.awt.BorderLayout
     */
    public class Border(
        public val hgap: Int = 0,
        public val vgap: Int = 0,
    ) : PanelLayout<BorderPanelScope>(BorderPanelScopeImpl) {
        override fun installOn(updater: SwingNodeUpdater<JPanel>) {
            updater.init { layout = BorderLayout() }
            updater.setOnLayout<BorderLayout, _>(hgap) { this.hgap = it }
            updater.setOnLayout<BorderLayout, _>(vgap) { this.vgap = it }
        }

        override fun equals(other: Any?): Boolean = other is Border && hgap == other.hgap && vgap == other.vgap

        override fun hashCode(): Int = 31 * hgap + vgap
    }

    /**
     * Places each child in the grid cell that child's own constraints describe - the row and column it
     * starts at, the cells it spans, its share of the leftover extent, and how it fills the cell it is
     * given.
     *
     * A child names its cell with `item`, through [GridBagPanelScope]:
     *
     * ```
     * Panel(PanelLayout.GridBag) {
     *     Label(text = "Name", modifier = SwingModifier.item(gridx = 0, gridy = 0))
     *     Button(
     *         text = "Pick",
     *         onClick = ::pick,
     *         modifier = SwingModifier.item(gridx = 1, gridy = 0, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL),
     *     )
     * }
     * ```
     *
     * A child that declares no `item` is laid out under `GridBagConstraints`' own defaults.
     *
     * Constraints are reactive: changing a child's arguments re-places it, and dropping the child (e.g.
     * behind an `if`) removes it.
     *
     * @see java.awt.GridBagLayout
     */
    public object GridBag : PanelLayout<GridBagPanelScope>(GridBagPanelScopeImpl) {
        override fun installOn(updater: SwingNodeUpdater<JPanel>) {
            updater.init { layout = GridBagLayout() }
        }
    }

    /**
     * Holds the children as a deck of cards of which exactly one is shown. The deck asks for room enough
     * for its largest card, so showing another card does not resize it.
     *
     * Each child names the card it is placed on through [CardPanelScope], and [selectedCard] picks the
     * card the deck shows:
     * ```
     * Panel(PanelLayout.Card(selectedCard = page)) {
     *     Label(text = "Welcome", modifier = SwingModifier.card("welcome"))
     *     Label(text = "Details", modifier = SwingModifier.card("details"))
     * }
     * ```
     * A card holds a single child; two children naming the same card are reported as an error on the
     * event dispatch thread once the change pass that caused it has settled, so a pass that replaces a
     * card's occupant need not take the outgoing child out before the incoming one arrives. The report
     * reaches whatever handles an uncaught exception on that thread - by default the JDK's, which prints
     * it and moves on, so an application that installs none of its own keeps running with both children
     * on the card.
     *
     * Dropping a child (e.g. behind an `if`) takes its card with it, and a [selectedCard] matching no
     * card leaves the card currently on top showing. A child that names no card is placed on the deck's
     * empty-named card, which an empty [selectedCard] shows; that card holds a single child like any
     * other, so two children naming none are refused the way a card named twice is.
     *
     * @property selectedCard the key of the card to show
     * @property hgap the horizontal gap between the panel's left/right edges and the shown card; `0` by
     *   default, so the card reaches those edges
     * @property vgap the vertical gap between the panel's top/bottom edges and the shown card; `0` by
     *   default, so the card reaches those edges
     * @see java.awt.CardLayout
     */
    public class Card(
        public val selectedCard: String,
        public val hgap: Int = 0,
        public val vgap: Int = 0,
    ) : PanelLayout<CardPanelScope>(CardPanelScopeImpl) {
        override fun installOn(updater: SwingNodeUpdater<JPanel>) {
            updater.init { layout = CardDeckLayout() }
            updater.set(selectedCard) { (layout as CardDeckLayout).showCard(this, it) }
            updater.setOnLayout<CardDeckLayout, _>(hgap) { this.hgap = it }
            updater.setOnLayout<CardDeckLayout, _>(vgap) { this.vgap = it }
        }

        override fun equals(other: Any?): Boolean =
            other is Card && selectedCard == other.selectedCard && hgap == other.hgap && vgap == other.vgap

        override fun hashCode(): Int = 31 * (31 * selectedCard.hashCode() + hgap) + vgap
    }
}

/**
 * The receiver of a [Panel]'s content. Each [PanelLayout] names the scope it hands its children, through
 * which they declare their own placement under that layout; a layout that places children by declaration
 * order alone hands this one, which offers nothing.
 */
public sealed interface PanelScope

/** The [PanelScope] handed to the content of a layout that places children by declaration order alone. */
internal object PanelScopeImpl : PanelScope

/**
 * The receiver of the content of a [Panel] under [PanelLayout.Border], through which a child declares
 * which region of that panel it occupies.
 *
 * Children are written plainly; the region a child names here rides along on its `modifier`:
 *
 * ```
 * Panel(PanelLayout.Border()) {
 *     Toolbar(modifier = SwingModifier.north())
 *     Editor()
 *     StatusBar(modifier = SwingModifier.south())
 * }
 * ```
 *
 * Two families of region are available:
 *  - absolute compass: [north], [south], [east], [west], [center];
 *  - orientation-aware: [pageStart], [pageEnd], [lineStart], [lineEnd], resolved against the panel's
 *    `ComponentOrientation` (leading is the left edge under left-to-right, the right edge under
 *    right-to-left).
 *
 * Prefer one family for a given edge: pairing, e.g., [north] with [pageStart] attaches two children and
 * the orientation-aware one is laid out at the top. [center] is shared by both families.
 *
 * A region holds one child. The last region named in a chain is the one that child occupies. Where two
 * children name the same region, the second to be registered takes it and the panel lays nothing out
 * for the first.
 *
 * @see java.awt.BorderLayout
 */
public sealed interface BorderPanelScope : PanelScope {
    /**
     * Places the child across the top of the panel, at the height it prefers and the panel's full
     * width ([BorderLayout.NORTH]).
     *
     * @see java.awt.BorderLayout.NORTH
     */
    public fun SwingModifier.north(): SwingModifier

    /**
     * Places the child across the bottom of the panel, at the height it prefers and the panel's full
     * width ([BorderLayout.SOUTH]).
     *
     * @see java.awt.BorderLayout.SOUTH
     */
    public fun SwingModifier.south(): SwingModifier

    /**
     * Places the child down the right side of the panel, at the width it prefers and the height the
     * top and bottom regions leave ([BorderLayout.EAST]).
     *
     * @see java.awt.BorderLayout.EAST
     */
    public fun SwingModifier.east(): SwingModifier

    /**
     * Places the child down the left side of the panel, at the width it prefers and the height the top
     * and bottom regions leave ([BorderLayout.WEST]).
     *
     * @see java.awt.BorderLayout.WEST
     */
    public fun SwingModifier.west(): SwingModifier

    /**
     * Places the child in the middle of the panel, filling everything the edge regions leave
     * ([BorderLayout.CENTER]).
     *
     * @see java.awt.BorderLayout.CENTER
     */
    public fun SwingModifier.center(): SwingModifier

    /**
     * Places the child across the top of the panel, orientation-aware; wins the top edge over [north]
     * ([BorderLayout.PAGE_START]).
     *
     * @see java.awt.BorderLayout.PAGE_START
     */
    public fun SwingModifier.pageStart(): SwingModifier

    /**
     * Places the child across the bottom of the panel, orientation-aware; wins the bottom edge over
     * [south] ([BorderLayout.PAGE_END]).
     *
     * @see java.awt.BorderLayout.PAGE_END
     */
    public fun SwingModifier.pageEnd(): SwingModifier

    /**
     * Places the child down the leading side of the panel (left in LTR, right in RTL); wins that side
     * over [west]/[east] ([BorderLayout.LINE_START]).
     *
     * @see java.awt.BorderLayout.LINE_START
     */
    public fun SwingModifier.lineStart(): SwingModifier

    /**
     * Places the child down the trailing side of the panel (right in LTR, left in RTL); wins that side
     * over [east]/[west] ([BorderLayout.LINE_END]).
     *
     * @see java.awt.BorderLayout.LINE_END
     */
    public fun SwingModifier.lineEnd(): SwingModifier
}

/**
 * The [BorderPanelScope] every [PanelLayout.Border] panel hands its content. A region builder appends the
 * matching [BorderLayout] constraint to the child's own chain and holds nothing of the panel it was called
 * under, so one instance serves them all.
 */
internal object BorderPanelScopeImpl : BorderPanelScope {
    override fun SwingModifier.north(): SwingModifier = this.layoutConstraint(BorderLayout.NORTH)

    override fun SwingModifier.south(): SwingModifier = this.layoutConstraint(BorderLayout.SOUTH)

    override fun SwingModifier.east(): SwingModifier = this.layoutConstraint(BorderLayout.EAST)

    override fun SwingModifier.west(): SwingModifier = this.layoutConstraint(BorderLayout.WEST)

    override fun SwingModifier.center(): SwingModifier = this.layoutConstraint(BorderLayout.CENTER)

    override fun SwingModifier.pageStart(): SwingModifier = this.layoutConstraint(BorderLayout.PAGE_START)

    override fun SwingModifier.pageEnd(): SwingModifier = this.layoutConstraint(BorderLayout.PAGE_END)

    override fun SwingModifier.lineStart(): SwingModifier = this.layoutConstraint(BorderLayout.LINE_START)

    override fun SwingModifier.lineEnd(): SwingModifier = this.layoutConstraint(BorderLayout.LINE_END)
}

/**
 * The receiver of the content of a [Panel] under [PanelLayout.GridBag], through which a child declares
 * the cell it occupies.
 *
 * Children are written plainly; what a child declares here rides along on its `modifier`:
 *
 * ```
 * Panel(PanelLayout.GridBag) {
 *     Label(text = "Name", modifier = SwingModifier.item(gridx = 0, gridy = 0))
 *     Button(text = "Pick", onClick = ::pick, modifier = SwingModifier.item(gridx = 1, gridy = 0, weightx = 1.0))
 * }
 * ```
 *
 * @see java.awt.GridBagLayout
 */
public sealed interface GridBagPanelScope : PanelScope {
    /**
     * Places the child in the cell these constraints describe. The parameters carry
     * `GridBagConstraints`' own field names and defaults, so a grid-bag layout written against Swing
     * transfers field for field.
     *
     * @param gridx the cell holding the leading edge of the child's display area, the first cell in a
     *   row being `0`; `GridBagConstraints.RELATIVE` places the child immediately after the previously
     *   declared one
     * @param gridy the cell at the top of the child's display area, the topmost cell being `0`;
     *   `GridBagConstraints.RELATIVE` places the child just below the previously declared one
     * @param gridwidth the number of cells the display area spans in its row;
     *   `GridBagConstraints.REMAINDER` spans to the last cell in the row, `GridBagConstraints.RELATIVE`
     *   to the next to last
     * @param gridheight the number of cells the display area spans in its column;
     *   `GridBagConstraints.REMAINDER` spans to the last cell in the column,
     *   `GridBagConstraints.RELATIVE` to the next to last
     * @param weightx the share of extra horizontal space this child's column claims; a column of
     *   weight `0.0` receives none
     * @param weighty the share of extra vertical space this child's row claims; a row of weight `0.0`
     *   receives none
     * @param anchor where the child sits within its display area when the area is larger
     * @param fill whether and along which axes the child is resized to fill its display area
     * @param insets the external padding - the room held clear around the child inside its display
     *   area
     * @param ipadx the internal horizontal padding: the child is at least its minimum width plus this
     *   many pixels wide
     * @param ipady the internal vertical padding: the child is at least its minimum height plus this
     *   many pixels tall
     * @return this chain with the placement declared on it.
     * @see java.awt.GridBagConstraints
     */
    @Suppress("LongParameterList")
    // One parameter per GridBagConstraints field, under the field's own name and at the field's own
    // default, so a declaration names only the constraints it sets and a grid-bag layout written against
    // Swing carries over unchanged.
    public fun SwingModifier.item(
        gridx: Int = GridBagConstraints.RELATIVE,
        gridy: Int = GridBagConstraints.RELATIVE,
        gridwidth: Int = 1,
        gridheight: Int = 1,
        weightx: Double = 0.0,
        weighty: Double = 0.0,
        @GridBagAnchor anchor: Int = GridBagConstraints.CENTER,
        @GridBagFill fill: Int = GridBagConstraints.NONE,
        insets: Insets = DefaultInsets,
        ipadx: Int = 0,
        ipady: Int = 0,
    ): SwingModifier
}

/** The [GridBagPanelScope] every [PanelLayout.GridBag] panel hands its content. It holds nothing of its own. */
internal object GridBagPanelScopeImpl : GridBagPanelScope {
    override fun SwingModifier.item(
        gridx: Int,
        gridy: Int,
        gridwidth: Int,
        gridheight: Int,
        weightx: Double,
        weighty: Double,
        @GridBagAnchor anchor: Int,
        @GridBagFill fill: Int,
        insets: Insets,
        ipadx: Int,
        ipady: Int,
    ): SwingModifier =
        layoutConstraint(
            ItemConstraints().apply {
                this.gridx = gridx
                this.gridy = gridy
                this.gridwidth = gridwidth
                this.gridheight = gridheight
                this.weightx = weightx
                this.weighty = weighty
                this.anchor = anchor
                this.fill = fill
                // Insets is mutable, so retain the values this declaration carried rather than a caller's
                // object that could change after the layout's equality gate has recorded it.
                this.insets = Insets(insets.top, insets.left, insets.bottom, insets.right)
                this.ipadx = ipadx
                this.ipady = ipady
            },
        )
}

/**
 * The receiver of the content of a [Panel] under [PanelLayout.Card], through which a child names the card
 * it is placed on.
 *
 * Children are written plainly; the card a child names rides along on its `modifier`:
 *
 * ```
 * Panel(PanelLayout.Card(selectedCard = page)) {
 *     Label(text = "Welcome", modifier = SwingModifier.card("welcome"))
 *     Label(text = "Details", modifier = SwingModifier.card("details"))
 * }
 * ```
 *
 * @see java.awt.CardLayout
 */
public sealed interface CardPanelScope : PanelScope {
    /**
     * Places the child on the card named [key], the card the panel shows while its `selectedCard` equals
     * that key. The name follows the value: a child declaring a new key moves to that card, keeping its
     * position among its siblings.
     *
     * [key] cannot be empty, and two children of one panel cannot name the same card; either is refused,
     * and a card named twice is reported by its name.
     *
     * @param key names the card this child is placed on, non-empty
     * @return this chain with the card declared on it.
     * @see java.awt.CardLayout.show
     */
    public fun SwingModifier.card(key: String): SwingModifier
}

/**
 * The [CardPanelScope] every [PanelLayout.Card] panel hands its content; it holds nothing of the panel it
 * serves.
 */
internal object CardPanelScopeImpl : CardPanelScope {
    override fun SwingModifier.card(key: String): SwingModifier {
        // A card is addressed by its name, and the empty name is the one `CardLayout` gives a child added
        // with no card at all - so an empty key would declare a card that cannot be told from no card.
        require(key.isNotEmpty()) { "A PanelLayout.Card card key must not be empty." }
        return layoutConstraint(key)
    }
}

/**
 * Writes both dimensions, the non-zero one first: `GridLayout` refuses a zero row count while its
 * column count is also zero, and vice versa.
 */
private fun GridLayout.applyDimensions(
    rows: Int,
    cols: Int,
) {
    if (rows != 0) {
        this.rows = rows
        this.columns = cols
    } else {
        this.columns = cols
        this.rows = rows
    }
}

/**
 * The placement one [GridBagPanelScope.item] declares, comparing equal to the placement an identical
 * declaration produces. `GridBagConstraints` compares by identity, so value equality here is what lets a
 * chain rebuilt from the same arguments reach the node as the placement it already holds.
 *
 * Passing it wherever a `GridBagConstraints` is expected is safe: `GridBagLayout` stores a deep copy of
 * what it is handed.
 */
private class ItemConstraints : GridBagConstraints() {
    override fun equals(other: Any?): Boolean =
        other is ItemConstraints && sameCell(other) && sameSpace(other) && samePadding(other)

    override fun hashCode(): Int =
        Objects.hash(gridx, gridy, gridwidth, gridheight, weightx, weighty, anchor, fill, insets, ipadx, ipady)

    private fun sameCell(other: GridBagConstraints): Boolean =
        gridx == other.gridx && gridy == other.gridy && gridwidth == other.gridwidth &&
            gridheight == other.gridheight

    private fun sameSpace(other: GridBagConstraints): Boolean =
        weightx == other.weightx && weighty == other.weighty && anchor == other.anchor &&
            fill == other.fill

    private fun samePadding(other: GridBagConstraints): Boolean =
        insets == other.insets && ipadx == other.ipadx && ipady == other.ipady
}

// GridBagConstraints' own default external padding, shared by every item that leaves `insets` unset.
// Sharing one instance is safe because GridBagLayout stores a deep copy of the constraints it is
// handed - insets included - so no item can reach, let alone mutate, the instance another item used.
private val DefaultInsets: Insets = Insets(0, 0, 0, 0)
