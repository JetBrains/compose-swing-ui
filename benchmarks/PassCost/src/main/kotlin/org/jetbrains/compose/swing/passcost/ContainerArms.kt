package org.jetbrains.compose.swing.passcost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.DesktopPane
import org.jetbrains.compose.swing.components.desktop.InternalFrameState
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.components.desktop.rememberInternalFrameState
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.ScrollState
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.components.layout.ToolBar
import org.jetbrains.compose.swing.components.layout.rememberScrollState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import java.awt.Container
import java.awt.Rectangle
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import javax.swing.JLayeredPane
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JToolBar

/**
 * What one pass costs the composite containers - the ones that hold their children in regions of their
 * own rather than as indexed children of a layout.
 *
 * Each container is measured twice where its signature offers a two-way declaration: once for the plain
 * content change every container answers, and once for the value the caller declares and the widget also
 * moves, which is the expensive path.
 */
internal fun containerArms(): List<Arm> =
    listOf(
        contentArm(ContainerKind.SCROLL_PANE),
        scrollPositionArm(),
        contentArm(ContainerKind.SPLIT_PANE),
        movedValueArm(MovedValue.SPLIT_DIVIDER),
        contentArm(ContainerKind.TABBED_PANE),
        movedValueArm(MovedValue.TAB_SELECTION),
        contentArm(ContainerKind.TOOL_BAR),
        contentArm(ContainerKind.DESKTOP_PANE),
        desktopFrameArm(),
        contentArm(ContainerKind.LAYERED_PANE),
    )

/**
 * One composite container, holding two children of which the first carries a text that changes on every
 * pass: what a pass costs a container that re-declares everything it holds.
 *
 * [arm] is the series the container is reported under, [counted] names its copies in a failure, and
 * [type] is the widget one copy is realized as.
 */
private enum class ContainerKind(
    val arm: String,
    val counted: String,
    val type: Class<out Container>,
) {
    SCROLL_PANE(SCROLL_CONTENT_ARM, "scroll panes", JScrollPane::class.java),
    SPLIT_PANE(SPLIT_CONTENT_ARM, "split panes", JSplitPane::class.java),
    TABBED_PANE(TABBED_CONTENT_ARM, "tabbed panes", JTabbedPane::class.java),
    TOOL_BAR(TOOL_BAR_CONTENT_ARM, "tool bars", JToolBar::class.java),
    DESKTOP_PANE(DESKTOP_CONTENT_ARM, "desktop panes", JDesktopPane::class.java),
    LAYERED_PANE(LAYERED_CONTENT_ARM, "layered panes", JLayeredPane::class.java),
}

/**
 * Every widget a [kind] container whose first child carries the text that changes on every pass.
 *
 * The text is read in the scope that declares the container, so a pass re-declares the container whole -
 * its regions, the children in them, and the chain around each - which is what a caller pays for when
 * anything inside a container moves. The second child never changes, so a pass that reached only one of
 * the two is told apart from one that reached both.
 */
private fun contentArm(kind: ContainerKind): Arm =
    Arm(listOf(kind.arm), HEAVY_TREE_SIZES) { widgets, changing ->
        val text = mutableStateOf(INITIAL_TEXT)
        val containerRuns = IntArray(1)
        Run(
            content = {
                Column {
                    repeat(widgets) { DeclaredContainer(kind, text) { containerRuns[0]++ } }
                }
            },
            drive = { pass ->
                if (changing) text.value = alternatingText(pass)
                kind.arm
            },
            verify = { root, passes ->
                checkWidgets(kind.counted, componentsOfType(root, kind.type).size, widgets)
                checkScopeRuns(
                    "the ${kind.counted}' scopes",
                    containerRuns[0],
                    widgets * if (changing) 1 + passes else 1,
                )
                val expected = if (changing) alternatingText(passes - 1) else INITIAL_TEXT
                val carrying = labelTexts(root).count { it == expected }
                check(carrying == widgets) {
                    "$carrying children read '$expected', where $widgets were declared with it"
                }
            },
        )
    }

/**
 * A [kind] container holding two children, the first of them carrying [text].
 *
 * The text is read here rather than inside a child, so this scope - the one that declares the container -
 * is the scope a write invalidates.
 */
@Composable
private fun DeclaredContainer(
    kind: ContainerKind,
    text: State<String>,
    onCompose: () -> Unit,
) {
    onCompose()
    val changing = text.value
    when (kind) {
        ContainerKind.SCROLL_PANE -> {
            ScrollPane {
                Label(text = changing, modifier = SwingModifier.viewport())
                Label(text = STEADY_TEXT, modifier = SwingModifier.columnHeader())
            }
        }

        ContainerKind.SPLIT_PANE -> {
            SplitPane {
                Label(text = changing, modifier = SwingModifier.first())
                Label(text = STEADY_TEXT, modifier = SwingModifier.second())
            }
        }

        ContainerKind.TABBED_PANE -> {
            TabbedPane(selectedIndex = FIRST_TAB, onSelectedIndexChange = {}) {
                Label(text = changing, modifier = SwingModifier.tab(FIRST_TAB_TITLE))
                Label(text = STEADY_TEXT, modifier = SwingModifier.tab(SECOND_TAB_TITLE))
            }
        }

        ContainerKind.TOOL_BAR -> {
            ToolBar {
                Label(text = changing)
                Label(text = STEADY_TEXT)
            }
        }

        ContainerKind.DESKTOP_PANE -> {
            DesktopPane {
                InternalFrame(title = STEADY_TEXT, bounds = FIRST_FRAME_BOUNDS, onClose = {}) {
                    Label(text = changing)
                }
                InternalFrame(title = STEADY_TEXT, bounds = SECOND_FRAME_BOUNDS, onClose = {}) {
                    Label(text = STEADY_TEXT)
                }
            }
        }

        // Neither child declares bounds. Nothing here is ever laid out, so a placement would add a
        // geometry element rebuilt on every pass to the figure without changing what the pane does.
        ContainerKind.LAYERED_PANE -> {
            LayeredPane {
                Label(text = changing)
                Label(text = STEADY_TEXT, modifier = SwingModifier.layer(JLayeredPane.PALETTE_LAYER))
            }
        }
    }
}

/**
 * A pane whose declared two-way integer moves between [near] and [far] on every pass, reported under
 * [arm], named [one] and counted as [counted] in a failure. It is declared on [start] first, a value
 * neither of the two names, so the first measured pass moves the pane exactly as every later one does.
 */
private enum class MovedValue(
    val arm: String,
    val one: String,
    val counted: String,
    val start: Int,
    val near: Int,
    val far: Int,
) {
    /**
     * A split pane's divider offset. The pane takes the offset within the write that declares it and
     * publishes nothing the composition reads back, so a move settles in the pass that made it.
     */
    SPLIT_DIVIDER(SPLIT_DIVIDER_ARM, "split pane", "split panes", DIVIDER_START, DIVIDER_NEAR, DIVIDER_FAR),

    /**
     * A tabbed pane's selected tab. The pane republishes the selection the write moved it to, and the
     * wrapper records that answer as one its own settlement made rather than reading it back as news, so
     * a move settles in the pass that made it. What this arm measures is what that republishing costs on
     * top of the write itself.
     */
    TAB_SELECTION(TAB_SELECTION_ARM, "tabbed pane", "tabbed panes", NO_TAB_SELECTED, FIRST_TAB, SECOND_TAB),
}

/**
 * Every widget a pane whose declared [MovedValue] moves on every pass: what a two-way declaration costs
 * where the widget answers the write by publishing the move straight back through the listener the
 * wrapper installs.
 *
 * The two values are held in an array built ahead of the batch and indexed by the pass, so every pass is
 * a real change and the driver allocates nothing. One state is shared by every pane, and it is read in
 * the scope that declares the pane, so a pass re-declares each of them.
 */
private fun movedValueArm(moved: MovedValue): Arm =
    Arm(listOf(moved.arm), HEAVY_TREE_SIZES) { widgets, changing ->
        val declared = mutableIntStateOf(moved.start)
        val values = intArrayOf(moved.near, moved.far)
        val paneRuns = IntArray(1)
        Run(
            content = {
                Column {
                    repeat(widgets) { SettlingPane(moved, declared) { paneRuns[0]++ } }
                }
            },
            drive = { pass ->
                if (changing) declared.intValue = values[pass % 2]
                moved.arm
            },
            verify = { root, passes ->
                val standing =
                    when (moved) {
                        MovedValue.SPLIT_DIVIDER -> {
                            componentsOfType(root, JSplitPane::class.java).map { it.dividerLocation }
                        }

                        MovedValue.TAB_SELECTION -> {
                            componentsOfType(root, JTabbedPane::class.java).map { it.selectedIndex }
                        }
                    }
                checkWidgets(moved.counted, standing.size, widgets)
                checkScopeRuns(
                    "the ${moved.counted}' scopes",
                    paneRuns[0],
                    widgets * if (changing) 1 + passes else 1,
                )
                checkApplied(moved.one, standing, if (changing) values[(passes - 1) % 2] else moved.start)
            },
        )
    }

/** A pane declaring [declared] as the two-way value [moved] names, over two children that never change. */
@Composable
private fun SettlingPane(
    moved: MovedValue,
    declared: State<Int>,
    onCompose: () -> Unit,
) {
    onCompose()
    val value = declared.value
    when (moved) {
        MovedValue.SPLIT_DIVIDER -> {
            SplitPane(dividerLocation = value) {
                Label(text = STEADY_TEXT, modifier = SwingModifier.first())
                Label(text = STEADY_TEXT, modifier = SwingModifier.second())
            }
        }

        MovedValue.TAB_SELECTION -> {
            TabbedPane(selectedIndex = value, onSelectedIndexChange = {}) {
                Label(text = STEADY_TEXT, modifier = SwingModifier.tab(FIRST_TAB_TITLE))
                Label(text = STEADY_TEXT, modifier = SwingModifier.tab(SECOND_TAB_TITLE))
            }
        }
    }
}

/**
 * Every widget a scroll pane whose hoisted scroll position moves on every pass: what the two-way channel
 * between a [ScrollState] and its viewport costs.
 *
 * The move reaches the widget through the state itself rather than through a declaration, so no scope of
 * the composition is invalidated and the pass carries the channel alone - the scope count below is flat
 * for that reason, and what says the move landed is the position read off each viewport.
 *
 * A state renders one pane, so each pane hoists its own and the driver writes to all of them. The
 * content states its own size: a pane that is never laid out has an extent of zero, and a position past
 * the content it has to show is one the pane's scroll bars would clamp straight back to the origin.
 */
private fun scrollPositionArm(): Arm =
    Arm(listOf(SCROLL_POSITION_ARM), HEAVY_TREE_SIZES) { widgets, changing ->
        val states = arrayOfNulls<ScrollState>(widgets)
        val positions = intArrayOf(SCROLLED_NEAR, SCROLLED_FAR)
        val paneRuns = IntArray(1)
        Run(
            content = {
                Column {
                    repeat(widgets) { index -> ScrollingPane({ states[index] = it }) { paneRuns[0]++ } }
                }
            },
            drive = { pass ->
                if (changing) {
                    val position = positions[pass % 2]
                    for (state in states) state?.y = position
                }
                SCROLL_POSITION_ARM
            },
            verify = { root, passes ->
                val panes = componentsOfType(root, JScrollPane::class.java)
                checkWidgets("scroll panes", panes.size, widgets)
                checkScopeRuns("the scroll panes' scopes", paneRuns[0], widgets)
                val standing = panes.map { it.viewport.viewPosition.y }
                checkApplied("scroll pane", standing, if (changing) positions[(passes - 1) % 2] else SCROLL_ORIGIN)
            },
        )
    }

/**
 * A scroll pane over content of a stated size, driven by a scroll state of its own which is handed to
 * [onState] as the pane is composed - which is how the driver reaches the state a pane remembers.
 */
@Composable
private fun ScrollingPane(
    onState: (ScrollState) -> Unit,
    onCompose: () -> Unit,
) {
    onCompose()
    val state = rememberScrollState()
    onState(state)
    ScrollPane(state = state) {
        Label(text = STEADY_TEXT, modifier = SwingModifier.viewport().preferredSize(VIEW_WIDTH, VIEW_HEIGHT))
    }
}

/**
 * Every widget a desktop holding one frame whose hoisted geometry moves on every pass: what the two-way
 * channel between an [InternalFrameState] and its `JInternalFrame` costs.
 *
 * The move invalidates the library's own frame node rather than the scope that declared the frame, so
 * the scope count below is flat and what says the move landed is the position read off each frame. The
 * frame reports its own move back asynchronously, and the pass pays for that echo being recognized as
 * the apply's own and dropped.
 *
 * A state drives one frame, so each desktop hoists its own and the driver writes to all of them. Only
 * the x coordinate moves, so the frame is resized by nothing and what a pass carries is one move.
 */
private fun desktopFrameArm(): Arm =
    Arm(listOf(DESKTOP_FRAME_ARM), HEAVY_TREE_SIZES) { widgets, changing ->
        val states = arrayOfNulls<InternalFrameState>(widgets)
        val places = intArrayOf(FRAME_NEAR_X, FRAME_FAR_X)
        val frameRuns = IntArray(1)
        Run(
            content = {
                Column {
                    repeat(widgets) { index -> MovingFrame({ states[index] = it }) { frameRuns[0]++ } }
                }
            },
            drive = { pass ->
                if (changing) {
                    val place = places[pass % 2]
                    for (state in states) state?.x = place
                }
                DESKTOP_FRAME_ARM
            },
            verify = { root, passes ->
                val frames = componentsOfType(root, JInternalFrame::class.java)
                checkWidgets("internal frames", frames.size, widgets)
                checkScopeRuns("the desktops' scopes", frameRuns[0], widgets)
                val standing = frames.map { it.x }
                checkApplied("internal frame", standing, if (changing) places[(passes - 1) % 2] else FRAME_START_X)
            },
        )
    }

/**
 * A desktop holding one frame driven by a frame state of its own, which is handed to [onState] as the
 * desktop is composed - which is how the driver reaches the state a desktop remembers.
 */
@Composable
private fun MovingFrame(
    onState: (InternalFrameState) -> Unit,
    onCompose: () -> Unit,
) {
    onCompose()
    val state = rememberInternalFrameState(FIRST_FRAME_BOUNDS)
    onState(state)
    DesktopPane {
        InternalFrame(title = STEADY_TEXT, state = state, onClose = {}) { Label(text = STEADY_TEXT) }
    }
}

private const val SCROLL_CONTENT_ARM = "scroll pane content changed"
private const val SCROLL_POSITION_ARM = "scroll position moved"
private const val SPLIT_CONTENT_ARM = "split pane content changed"
private const val SPLIT_DIVIDER_ARM = "split divider moved"
private const val TABBED_CONTENT_ARM = "tabbed pane content changed"
private const val TAB_SELECTION_ARM = "tab selection moved"
private const val TOOL_BAR_CONTENT_ARM = "tool bar content changed"
private const val DESKTOP_CONTENT_ARM = "desktop pane content changed"
private const val DESKTOP_FRAME_ARM = "desktop frame moved"
private const val LAYERED_CONTENT_ARM = "layered pane content changed"

/** The titles of the two tabs every measured tabbed pane holds. */
private const val FIRST_TAB_TITLE = "first"
private const val SECOND_TAB_TITLE = "second"

/** The two tabs a moved selection alternates between. */
private const val FIRST_TAB = 0
private const val SECOND_TAB = 1

/** The index a `JTabbedPane` holds while no tab of it is selected. */
private const val NO_TAB_SELECTED = -1

/**
 * The divider offsets a split pane is declared on, in pixels. All three are non-negative: a negative
 * offset asks the pane to derive the divider position from its sides instead of holding one.
 */
private const val DIVIDER_START = 120
private const val DIVIDER_NEAR = 60
private const val DIVIDER_FAR = 90

/** The size the scrolled content states for itself, so a declared position has content to reach. */
private const val VIEW_WIDTH = 400
private const val VIEW_HEIGHT = 400

/** The scroll positions a pane is declared on; both are within [VIEW_HEIGHT] of the origin. */
private const val SCROLL_ORIGIN = 0
private const val SCROLLED_NEAR = 1
private const val SCROLLED_FAR = 2

/** The positions and size an internal frame stands on within its desktop, in pixels. */
private const val FRAME_START_X = 0
private const val FRAME_NEAR_X = 20
private const val FRAME_FAR_X = 40
private const val FRAME_WIDTH = 240
private const val FRAME_HEIGHT = 160

private val FIRST_FRAME_BOUNDS = Rectangle(FRAME_START_X, FRAME_START_X, FRAME_WIDTH, FRAME_HEIGHT)
private val SECOND_FRAME_BOUNDS = Rectangle(FRAME_FAR_X, FRAME_FAR_X, FRAME_WIDTH, FRAME_HEIGHT)
