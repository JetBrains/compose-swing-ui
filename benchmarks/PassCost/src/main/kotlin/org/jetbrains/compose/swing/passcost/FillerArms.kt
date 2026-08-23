package org.jetbrains.compose.swing.passcost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.mutableIntStateOf
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.constants.Orientation
import org.jetbrains.compose.swing.passcost.harness.drivePass
import java.awt.Dimension
import javax.swing.Box
import javax.swing.JProgressBar
import javax.swing.JSeparator
import javax.swing.JToolBar
import javax.swing.SwingConstants

/**
 * The feedback, drawing and filler components, each measured on one property a pass moves it by.
 *
 * None of them needs a structural arm: every one carries something a pass can declare differently - the
 * value a bar renders, the lambda a surface draws through, the axis a divider or a glue takes, the size a
 * strut, a spacer, a rigid area or a tool bar divider holds.
 */
internal fun fillerArms(): List<Arm> =
    listOf(
        progressBarValueArm(),
        canvasArm(CANVAS_STEADY_ARM, freshLambda = false),
        canvasArm(CANVAS_REBUILT_ARM, freshLambda = true),
        separatorOrientationArm(),
        toolBarSeparatorSizeArm(),
        steadyGlueArm(),
        fillerArm(
            name = GLUE_ARM,
            maximumAt = { tick ->
                if (alternatingOrientation(tick) == SwingConstants.HORIZONTAL) {
                    Dimension(UNBOUNDED, 0)
                } else {
                    Dimension(0, UNBOUNDED)
                }
            },
            filler = { tick, onCompose -> TurningGlue(tick, onCompose) },
        ),
        fillerArm(
            name = STRUT_ARM,
            maximumAt = { tick -> Dimension(alternatingSize(tick), UNBOUNDED) },
            filler = { tick, onCompose -> SizedStrut(tick, onCompose) },
        ),
        fillerArm(
            name = SPACER_ARM,
            maximumAt = { tick -> Dimension(alternatingSize(tick), alternatingSize(tick)) },
            filler = { tick, onCompose -> SizedSpacer(tick, onCompose) },
        ),
        fillerArm(
            name = RIGID_AREA_ARM,
            maximumAt = { tick -> Dimension(alternatingSize(tick), RIGID_AREA_HEIGHT) },
            filler = { tick, onCompose -> SizedRigidArea(tick, onCompose) },
        ),
    )

/**
 * Every widget a progress bar whose declared value moves on every pass: what a one-way declaration costs,
 * where the widget takes the write and answers nothing back.
 *
 * Read it against the "slider value changed" arm, which is this shape over a widget that declares its
 * value two-way: there the widget publishes the move back through the listener the wrapper installs, into
 * a mirror the declaring scope reads, and what that costs is the difference between the two arms. The
 * range is the one the declared slider spans, so the pair differs in the widget rather than in what it
 * holds.
 *
 * The value alternates between two the bar can hold, so every pass is a real change and the driver
 * allocates nothing; it starts on a third, so the first pass writes as every later one does.
 */
private fun progressBarValueArm(): Arm =
    Arm(listOf(PROGRESS_BAR_VALUE_ARM)) { widgets, changing ->
        val value = mutableIntStateOf(SLIDER_MAXIMUM)
        val barRuns = IntArray(1)
        Run(
            content = {
                Column {
                    repeat(widgets) { DeclaredProgressBar(value) { barRuns[0]++ } }
                }
            },
            drive = { pass ->
                if (changing) value.intValue = pass % 2
                PROGRESS_BAR_VALUE_ARM
            },
            verify = { root, passes ->
                val values = componentsOfType(root, JProgressBar::class.java).map { it.value }
                checkWidgets("progress bars", values.size, widgets)
                checkScopeRuns("the progress bar scopes", barRuns[0], widgets * if (changing) 1 + passes else 1)
                checkApplied("progress bar", values, if (changing) (passes - 1) % 2 else SLIDER_MAXIMUM)
            },
        )
    }

/**
 * Every widget a separator whose declared orientation turns on every pass: what re-declaring the one
 * property a divider carries costs, on a widget that holds nothing else.
 *
 * An orientation is one of two values, so no starting tick resolves to a third one and the orientation a
 * separator is left on cannot say a declaration ever reached it. [checkFollowsDeclaration] says it.
 */
private fun separatorOrientationArm(): Arm =
    Arm(listOf(SEPARATOR_ORIENTATION_ARM)) { widgets, changing ->
        val tick = mutableIntStateOf(STARTING_TICK)
        val separatorRuns = IntArray(1)
        Run(
            content = {
                Column {
                    repeat(widgets) { TurningSeparator(tick) { separatorRuns[0]++ } }
                }
            },
            drive = { pass ->
                if (changing) tick.intValue = pass % 2
                SEPARATOR_ORIENTATION_ARM
            },
            verify = { root, passes ->
                val orientations = { componentsOfType(root, JSeparator::class.java).map { it.orientation } }
                checkWidgets("separators", orientations().size, widgets)
                checkScopeRuns("the separator scopes", separatorRuns[0], widgets * if (changing) 1 + passes else 1)
                checkApplied("separator", orientations(), alternatingOrientation(lastTick(changing, passes)))
                checkFollowsDeclaration(
                    what = "separator",
                    declare = { declared -> tick.intValue = declared },
                    applied = orientations,
                    expectedAt = ::alternatingOrientation,
                )
            },
        )
    }

/**
 * Every widget a tool bar separator whose declared size moves on every pass: what a declaration that
 * skips the first composition costs, since the size a tool bar separator arrives on is the one its
 * factory took.
 *
 * The size declared is never null. A null one hands the separator back to the look and feel, which is a
 * different declaration from the one this arm names, and the sizes are built ahead of the batch, so
 * neither the driver nor a re-executed scope allocates one. The separators are composed on a third size
 * no pass declares, so one that took no declaration is left on a size no check expects.
 */
private fun toolBarSeparatorSizeArm(): Arm =
    Arm(listOf(TOOL_BAR_SEPARATOR_SIZE_ARM)) { widgets, changing ->
        val tick = mutableIntStateOf(STARTING_TICK)
        val separatorRuns = IntArray(1)
        Run(
            content = {
                Column {
                    repeat(widgets) { SizedToolBarSeparator(tick) { separatorRuns[0]++ } }
                }
            },
            drive = { pass ->
                if (changing) tick.intValue = pass % 2
                TOOL_BAR_SEPARATOR_SIZE_ARM
            },
            verify = { root, passes ->
                val sizes = componentsOfType(root, JToolBar.Separator::class.java).map { it.separatorSize }
                checkWidgets("tool bar separators", sizes.size, widgets)
                checkScopeRuns("the separator scopes", separatorRuns[0], widgets * if (changing) 1 + passes else 1)
                checkApplied("tool bar separator", sizes, alternatingSeparatorSize(lastTick(changing, passes)))
            },
        )
    }

/**
 * Every widget the empty space [filler] declares, re-shaped on every pass: what re-declaring a
 * `Box.Filler` costs, where the extent it asks for is the only thing that moved.
 *
 * [maximumAt] is the largest extent the filler asks for at a tick. Every filler declares that extent
 * differently - it is the whole of a rigid area's shape, and the axis a glue absorbs along - so it is
 * what says the declaration reached the widget rather than stopping at the composition.
 *
 * A glue absorbs along one of two axes, so its extent is one of two and the one it is left on cannot say
 * a declaration ever reached it. [checkFollowsDeclaration] says it, and is run for every filler alike.
 */
private fun fillerArm(
    name: String,
    maximumAt: (tick: Int) -> Dimension,
    filler:
        @Composable @SwingComposable
        (tick: IntState, onCompose: () -> Unit) -> Unit,
): Arm =
    Arm(listOf(name)) { widgets, changing ->
        val tick = mutableIntStateOf(STARTING_TICK)
        val fillerRuns = IntArray(1)
        Run(
            content = {
                Column {
                    repeat(widgets) { filler(tick) { fillerRuns[0]++ } }
                }
            },
            drive = { pass ->
                if (changing) tick.intValue = pass % 2
                name
            },
            verify = { root, passes ->
                val maximums = { componentsOfType(root, Box.Filler::class.java).map { it.maximumSize } }
                checkWidgets("fillers", maximums().size, widgets)
                checkScopeRuns("the filler scopes", fillerRuns[0], widgets * if (changing) 1 + passes else 1)
                checkApplied("filler", maximums(), maximumAt(lastTick(changing, passes)))
                checkFollowsDeclaration(
                    what = "filler",
                    declare = { declared -> tick.intValue = declared },
                    applied = maximums,
                    expectedAt = maximumAt,
                )
            },
        )
    }

/**
 * Every widget a glue whose declared axis never moves, in a scope that re-executes on every pass: what a
 * filler costs a container that recomposes around it.
 *
 * Read it against the "glue orientation" arm, which declares a new axis every pass. Here every pass
 * declares the axis the widget already holds, so what the arm reports is the price of arriving at that
 * declaration and finding nothing to do.
 *
 * The fillers are built holding no size at all - a `Box.Filler` takes its sizes from its constructor, and
 * the arm's own is empty - so a widget the declaration never reached carries a size the check names
 * nowhere.
 */
private fun steadyGlueArm(): Arm =
    Arm(listOf(STEADY_GLUE_ARM)) { widgets, changing ->
        val tick = mutableIntStateOf(STARTING_TICK)
        val fillerRuns = IntArray(1)
        Run(
            content = {
                Column {
                    repeat(widgets) { SteadyGlue(tick) { fillerRuns[0]++ } }
                }
            },
            drive = { pass ->
                if (changing) tick.intValue = pass % 2
                STEADY_GLUE_ARM
            },
            verify = { root, passes ->
                val fillers = componentsOfType(root, Box.Filler::class.java)
                checkWidgets("steady fillers", fillers.size, widgets)
                checkScopeRuns("the steady filler scopes", fillerRuns[0], widgets * if (changing) 1 + passes else 1)
                checkApplied("steady filler", fillers.map { it.maximumSize }, Dimension(UNBOUNDED, 0))
            },
        )
    }

/**
 * Raises unless every widget follows both ticks a driver alternates between: [declare] writes a tick,
 * [applied] reads back what the widgets hold and [expectedAt] is what that tick asks them to hold.
 *
 * Both ticks are written here, after the batch has been measured, each settled through the protocol a
 * pass uses. They cost the batch nothing and they are what a property of two values needs: whatever such
 * a property was declared last, a widget that took no declaration after the first is left holding one of
 * the same two values, so the value it is left on says nothing. A widget that follows a tick it was not
 * composed on took a declaration it did not already hold.
 *
 * Call after every check that counts scope runs or reads what the batch itself declared.
 */
internal fun <T> checkFollowsDeclaration(
    what: String,
    declare: (tick: Int) -> Unit,
    applied: () -> List<T>,
    expectedAt: (tick: Int) -> T,
) {
    for (tick in 0..1) {
        drivePass { declare(tick) }
        checkApplied(what, applied(), expectedAt(tick))
    }
}

/**
 * The axis pass [tick] declares. Two orientations alternating, so every pass is a real change: a
 * separator and a glue both hold their axis, and a declaration equal to what the widget holds is dropped
 * before it reaches it.
 */
@Orientation
internal fun alternatingOrientation(tick: Int): Int =
    if (tick == 0) SwingConstants.HORIZONTAL else SwingConstants.VERTICAL

/**
 * The extent pass [tick] declares onto a filler: two constants alternating, so no pass allocates one, and
 * a third at [STARTING_TICK] that no pass declares.
 */
internal fun alternatingSize(tick: Int): Int =
    when (tick) {
        0 -> NARROW_FILLER
        1 -> WIDE_FILLER
        else -> UNDECLARED_FILLER
    }

/**
 * The size pass [tick] declares onto a tool bar separator, built ahead of the batch and handed back. A
 * third size stands at [STARTING_TICK], which no pass declares.
 */
internal fun alternatingSeparatorSize(tick: Int): Dimension =
    when (tick) {
        0 -> NARROW_SEPARATOR_SIZE
        1 -> WIDE_SEPARATOR_SIZE
        else -> UNDECLARED_SEPARATOR_SIZE
    }

/**
 * The tick declared last: the one the final pass wrote for an arm that changes, and the one the widgets
 * were composed on for its null variant.
 */
internal fun lastTick(
    changing: Boolean,
    passes: Int,
): Int = if (changing) (passes - 1) % 2 else STARTING_TICK

private const val PROGRESS_BAR_VALUE_ARM = "progress bar value"
private const val CANVAS_STEADY_ARM = "canvas draw unchanged"
private const val CANVAS_REBUILT_ARM = "canvas draw rebuilt"
private const val SEPARATOR_ORIENTATION_ARM = "separator orientation"
private const val TOOL_BAR_SEPARATOR_SIZE_ARM = "tool bar separator size"
private const val STEADY_GLUE_ARM = "glue scope re-run"
private const val GLUE_ARM = "glue orientation"
private const val STRUT_ARM = "strut size"
private const val SPACER_ARM = "spacer size"
private const val RIGID_AREA_ARM = "rigid area size"

/**
 * The tick every arm here starts on: one no pass writes, so the first pass is a real change like every
 * later one, and a widget that took no declaration after the first is left on a value no check expects.
 * A property of two values - an orientation - resolves this tick to one of the two anyway, and is checked
 * by [checkFollowsDeclaration] instead.
 */
internal const val STARTING_TICK = 2

/** The two extents a filler alternates between, and the height a rigid area holds while its width moves. */
private const val NARROW_FILLER = 4
private const val WIDE_FILLER = 12
internal const val RIGID_AREA_HEIGHT = 2

/** The extent a filler is composed on, which no pass declares. */
private const val UNDECLARED_FILLER = 8

/**
 * The extent a filler asks for on an axis it absorbs - the value `javax.swing.Box`'s own glue and strut
 * factories use, and the one a declared glue or strut is left carrying.
 */
private const val UNBOUNDED: Int = Short.MAX_VALUE.toInt()

/** The two sizes a tool bar separator alternates between, built once so no pass allocates one. */
private val NARROW_SEPARATOR_SIZE = Dimension(NARROW_FILLER, NARROW_FILLER)
private val WIDE_SEPARATOR_SIZE = Dimension(WIDE_FILLER, WIDE_FILLER)

/** The size a tool bar separator is composed on, which no pass declares. */
private val UNDECLARED_SEPARATOR_SIZE = Dimension(UNDECLARED_FILLER, UNDECLARED_FILLER)
