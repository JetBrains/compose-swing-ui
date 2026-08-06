package org.jetbrains.compose.swing.constants

import org.intellij.lang.annotations.MagicConstant
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.GridBagConstraints
import java.util.Calendar
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JFormattedTextField
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.TransferHandler
import javax.swing.text.DefaultCaret
import javax.swing.tree.TreeSelectionModel

/*
 * Typed views over closed sets of JDK/Swing integer (and one String) constants, expressed as
 * @MagicConstant-annotated typedefs rather than wrapper value classes.
 *
 * Each annotation below names exactly the JDK constants a parameter accepts, so an IDE flags any other
 * value at the call site while the value passed at runtime is the plain JDK constant the wrapped Swing
 * API already expects - no boxing, no accessor, no translation layer.
 *
 * Retention is BINARY (Java CLASS): the annotation survives into the compiled class files so the IDE's
 * MagicConstant inspection can read it across the published-jar boundary and warn consumers in their
 * own IDE, while org.jetbrains:annotations stays a compileOnly dependency that never reaches the
 * runtime classpath.
 */

/** A vertical-scrollbar policy for `JScrollPane` (`VERTICAL_SCROLLBAR_*`). */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED.toLong(),
        JScrollPane.VERTICAL_SCROLLBAR_ALWAYS.toLong(),
        JScrollPane.VERTICAL_SCROLLBAR_NEVER.toLong(),
    ],
)
public annotation class VerticalScrollbarPolicy

/** A horizontal-scrollbar policy for `JScrollPane` (`HORIZONTAL_SCROLLBAR_*`). */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED.toLong(),
        JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS.toLong(),
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER.toLong(),
    ],
)
public annotation class HorizontalScrollbarPolicy

/** A `JScrollPane` corner key (`*_CORNER`). */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    stringValues = [
        JScrollPane.UPPER_LEADING_CORNER,
        JScrollPane.UPPER_TRAILING_CORNER,
        JScrollPane.LOWER_LEADING_CORNER,
        JScrollPane.LOWER_TRAILING_CORNER,
        JScrollPane.UPPER_LEFT_CORNER,
        JScrollPane.UPPER_RIGHT_CORNER,
        JScrollPane.LOWER_LEFT_CORNER,
        JScrollPane.LOWER_RIGHT_CORNER,
    ],
)
public annotation class ScrollPaneCorner

/** A `ListSelectionModel` selection mode (`*_SELECTION`), for `ListBox`/`Table`. */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        ListSelectionModel.SINGLE_SELECTION.toLong(),
        ListSelectionModel.SINGLE_INTERVAL_SELECTION.toLong(),
        ListSelectionModel.MULTIPLE_INTERVAL_SELECTION.toLong(),
    ],
)
public annotation class SelectionMode

/**
 * A `JList` cell layout (`VERTICAL`/`VERTICAL_WRAP`/`HORIZONTAL_WRAP`), for `ListBox`: a single column
 * of cells, or cells wrapped into as many columns or rows as the space allows - filling column by
 * column for `VERTICAL_WRAP`, row by row for `HORIZONTAL_WRAP`.
 */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        JList.VERTICAL.toLong(),
        JList.VERTICAL_WRAP.toLong(),
        JList.HORIZONTAL_WRAP.toLong(),
    ],
)
public annotation class ListLayoutOrientation

/** A `JTable` auto-resize mode (`AUTO_RESIZE_*`). */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        JTable.AUTO_RESIZE_OFF.toLong(),
        JTable.AUTO_RESIZE_NEXT_COLUMN.toLong(),
        JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS.toLong(),
        JTable.AUTO_RESIZE_LAST_COLUMN.toLong(),
        JTable.AUTO_RESIZE_ALL_COLUMNS.toLong(),
    ],
)
public annotation class AutoResizeMode

/** A `TreeSelectionModel` selection mode (`*_TREE_SELECTION`), for `Tree`. */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        TreeSelectionModel.SINGLE_TREE_SELECTION.toLong(),
        TreeSelectionModel.CONTIGUOUS_TREE_SELECTION.toLong(),
        TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION.toLong(),
    ],
)
public annotation class TreeSelectionMode

/** A `JTabbedPane` tab placement (`TOP`/`BOTTOM`/`LEFT`/`RIGHT`). */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        JTabbedPane.TOP.toLong(),
        JTabbedPane.BOTTOM.toLong(),
        JTabbedPane.LEFT.toLong(),
        JTabbedPane.RIGHT.toLong(),
    ],
)
public annotation class TabPlacement

/** A `JTabbedPane` tab-layout policy (`WRAP_TAB_LAYOUT`/`SCROLL_TAB_LAYOUT`). */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        JTabbedPane.WRAP_TAB_LAYOUT.toLong(),
        JTabbedPane.SCROLL_TAB_LAYOUT.toLong(),
    ],
)
public annotation class TabLayoutPolicy

/**
 * A horizontal alignment from `SwingConstants` (`LEFT`/`CENTER`/`RIGHT`/`LEADING`/`TRAILING`), as used
 * by `JLabel` and similar components.
 */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        SwingConstants.LEFT.toLong(),
        SwingConstants.CENTER.toLong(),
        SwingConstants.RIGHT.toLong(),
        SwingConstants.LEADING.toLong(),
        SwingConstants.TRAILING.toLong(),
    ],
)
public annotation class HorizontalAlignment

/**
 * A vertical alignment from `SwingConstants` (`TOP`/`CENTER`/`BOTTOM`), as used by `JLabel` and
 * button-like components.
 */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        SwingConstants.TOP.toLong(),
        SwingConstants.CENTER.toLong(),
        SwingConstants.BOTTOM.toLong(),
    ],
)
public annotation class VerticalAlignment

/**
 * A `FlowLayout` alignment (`LEFT`/`CENTER`/`RIGHT`/`LEADING`/`TRAILING`), as used by `FlowPanel`.
 * `FlowLayout`'s constants are a separate numbering from `SwingConstants`, so this is distinct from
 * [HorizontalAlignment].
 */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        FlowLayout.LEFT.toLong(),
        FlowLayout.CENTER.toLong(),
        FlowLayout.RIGHT.toLong(),
        FlowLayout.LEADING.toLong(),
        FlowLayout.TRAILING.toLong(),
    ],
)
public annotation class FlowAlignment

/** A one-dimensional `SwingConstants` orientation: `HORIZONTAL` or `VERTICAL`. */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        SwingConstants.HORIZONTAL.toLong(),
        SwingConstants.VERTICAL.toLong(),
    ],
)
public annotation class Orientation

/** A `BoxLayout` axis (`X_AXIS`/`Y_AXIS`/`LINE_AXIS`/`PAGE_AXIS`). */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        BoxLayout.X_AXIS.toLong(),
        BoxLayout.Y_AXIS.toLong(),
        BoxLayout.LINE_AXIS.toLong(),
        BoxLayout.PAGE_AXIS.toLong(),
    ],
)
public annotation class BoxAxis

/** A `JSplitPane` orientation (`HORIZONTAL_SPLIT`/`VERTICAL_SPLIT`). */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        JSplitPane.HORIZONTAL_SPLIT.toLong(),
        JSplitPane.VERTICAL_SPLIT.toLong(),
    ],
)
public annotation class SplitOrientation

/**
 * A `GridBagConstraints` anchor: where a child sits inside its display area when the area is larger
 * than the child.
 */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        GridBagConstraints.CENTER.toLong(),
        GridBagConstraints.NORTH.toLong(),
        GridBagConstraints.NORTHEAST.toLong(),
        GridBagConstraints.EAST.toLong(),
        GridBagConstraints.SOUTHEAST.toLong(),
        GridBagConstraints.SOUTH.toLong(),
        GridBagConstraints.SOUTHWEST.toLong(),
        GridBagConstraints.WEST.toLong(),
        GridBagConstraints.NORTHWEST.toLong(),
        GridBagConstraints.PAGE_START.toLong(),
        GridBagConstraints.PAGE_END.toLong(),
        GridBagConstraints.LINE_START.toLong(),
        GridBagConstraints.LINE_END.toLong(),
        GridBagConstraints.FIRST_LINE_START.toLong(),
        GridBagConstraints.FIRST_LINE_END.toLong(),
        GridBagConstraints.LAST_LINE_START.toLong(),
        GridBagConstraints.LAST_LINE_END.toLong(),
        GridBagConstraints.BASELINE.toLong(),
        GridBagConstraints.BASELINE_LEADING.toLong(),
        GridBagConstraints.BASELINE_TRAILING.toLong(),
        GridBagConstraints.ABOVE_BASELINE.toLong(),
        GridBagConstraints.ABOVE_BASELINE_LEADING.toLong(),
        GridBagConstraints.ABOVE_BASELINE_TRAILING.toLong(),
        GridBagConstraints.BELOW_BASELINE.toLong(),
        GridBagConstraints.BELOW_BASELINE_LEADING.toLong(),
        GridBagConstraints.BELOW_BASELINE_TRAILING.toLong(),
    ],
)
public annotation class GridBagAnchor

/**
 * A `GridBagConstraints` fill (`NONE`/`HORIZONTAL`/`VERTICAL`/`BOTH`): whether and along which axes a
 * child is resized to fill a display area larger than its requested size.
 */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        GridBagConstraints.NONE.toLong(),
        GridBagConstraints.HORIZONTAL.toLong(),
        GridBagConstraints.VERTICAL.toLong(),
        GridBagConstraints.BOTH.toLong(),
    ],
)
public annotation class GridBagFill

/** A `JFormattedTextField` focus-lost behavior (`COMMIT_OR_REVERT`/`COMMIT`/`REVERT`/`PERSIST`). */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        JFormattedTextField.COMMIT_OR_REVERT.toLong(),
        JFormattedTextField.COMMIT.toLong(),
        JFormattedTextField.REVERT.toLong(),
        JFormattedTextField.PERSIST.toLong(),
    ],
)
public annotation class FocusLostBehavior

/**
 * A `DefaultCaret` update policy (`ALWAYS_UPDATE`/`NEVER_UPDATE`/`UPDATE_WHEN_ON_EDT`): whether the
 * caret is carried along by an edit made elsewhere in the document, and from which thread.
 */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        DefaultCaret.ALWAYS_UPDATE.toLong(),
        DefaultCaret.NEVER_UPDATE.toLong(),
        DefaultCaret.UPDATE_WHEN_ON_EDT.toLong(),
    ],
)
public annotation class CaretUpdatePolicy

/**
 * A `JComponent` input-map focus condition (`WHEN_FOCUSED`, `WHEN_ANCESTOR_OF_FOCUSED_COMPONENT`,
 * `WHEN_IN_FOCUSED_WINDOW`), passed to `getInputMap(int)`.
 */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        JComponent.WHEN_FOCUSED.toLong(),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT.toLong(),
        JComponent.WHEN_IN_FOCUSED_WINDOW.toLong(),
    ],
)
public annotation class FocusCondition

/**
 * A data-transfer action bit-mask from `TransferHandler` (`NONE`/`COPY`/`MOVE`/`LINK`/`COPY_OR_MOVE`).
 * Combine `COPY` and `MOVE` with the bitwise `or` to express both.
 */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        TransferHandler.NONE.toLong(),
        TransferHandler.COPY.toLong(),
        TransferHandler.MOVE.toLong(),
        TransferHandler.LINK.toLong(),
        TransferHandler.COPY_OR_MOVE.toLong(),
    ],
)
public annotation class TransferAction

/** A `JEditorPane` MIME content type string (`text/plain`, `text/html`, `text/rtf`). */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(stringValues = ["text/plain", "text/html", "text/rtf"])
public annotation class ContentType

/**
 * A `Frame` extended state (`NORMAL`/`ICONIFIED`/`MAXIMIZED_HORIZ`/`MAXIMIZED_VERT`/`MAXIMIZED_BOTH`).
 * The state is a bit mask: combine `ICONIFIED` with a maximized bit using the bitwise `or` to express
 * both.
 */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        Frame.NORMAL.toLong(),
        Frame.ICONIFIED.toLong(),
        Frame.MAXIMIZED_HORIZ.toLong(),
        Frame.MAXIMIZED_VERT.toLong(),
        Frame.MAXIMIZED_BOTH.toLong(),
    ],
)
public annotation class WindowExtendedState

/**
 * A `Calendar` field a date sequence advances by one unit of, as `SpinnerDateModel` steps its value
 * (`ERA`, `YEAR`, `MONTH`, `WEEK_OF_YEAR`, `WEEK_OF_MONTH`, `DAY_OF_MONTH`, `DAY_OF_YEAR`,
 * `DAY_OF_WEEK`, `DAY_OF_WEEK_IN_MONTH`, `AM_PM`, `HOUR`, `HOUR_OF_DAY`, `MINUTE`, `SECOND`,
 * `MILLISECOND`).
 */
@Retention(AnnotationRetention.BINARY)
@MagicConstant(
    intValues = [
        Calendar.ERA.toLong(),
        Calendar.YEAR.toLong(),
        Calendar.MONTH.toLong(),
        Calendar.WEEK_OF_YEAR.toLong(),
        Calendar.WEEK_OF_MONTH.toLong(),
        Calendar.DAY_OF_MONTH.toLong(),
        Calendar.DAY_OF_YEAR.toLong(),
        Calendar.DAY_OF_WEEK.toLong(),
        Calendar.DAY_OF_WEEK_IN_MONTH.toLong(),
        Calendar.AM_PM.toLong(),
        Calendar.HOUR.toLong(),
        Calendar.HOUR_OF_DAY.toLong(),
        Calendar.MINUTE.toLong(),
        Calendar.SECOND.toLong(),
        Calendar.MILLISECOND.toLong(),
    ],
)
public annotation class CalendarField
