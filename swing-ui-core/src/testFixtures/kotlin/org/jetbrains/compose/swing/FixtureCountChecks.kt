// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.fixtures.FixtureVisitor
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import java.awt.Component
import java.awt.Container
import java.awt.Window
import javax.swing.JComponent
import javax.swing.RepaintManager
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tests that hold a composable's Swing work to what raw Swing does, one per alternative of the fixture.
 *
 * For each alternative the repaints and revalidations of each host and its descendants, and the layout passes of the
 * two host layouts, the composed root's and the raw host's, that the composed side asks for while it switches to the
 * alternative must not exceed what the raw side asks for while it applies the same change. Layout passes inside the
 * hosted component are not counted, nor are requests from components outside the hosts. Switching to a distinct
 * instance equal to the values already shown must ask for none.
 *
 * A fixture whose raw arm is a [java.awt.Window] is held against the window its composable realizes: the requests of
 * the raw root pane, every window the composable realizes while it switches, replaced ones included, and their
 * descendants are counted, and layout passes are not. These need a display.
 *
 * A non-null [maxRepaints], [maxRevalidations] or [maxLayouts] replaces what raw Swing asks for as the ceiling of that
 * count for every alternative; a null one keeps raw Swing as the ceiling.
 *
 * Return it from a `@TestFactory` function, so a fixture's test class adds the checks in one line.
 */
fun ComposableFixture<*, *>.countTests(
    maxRepaints: Int? = null,
    maxRevalidations: Int? = null,
    maxLayouts: Int? = null,
): List<DynamicTest> {
    require(alternatives.isNotEmpty()) {
        "${this::class.simpleName} declares no alternatives, so no check compares its arms; add at least one"
    }
    return accept(CountTests(Pins(maxRepaints, maxRevalidations, maxLayouts)))
}

/** The ceilings [countTests] was given in place of raw Swing's counts; null keeps raw Swing's. */
private class Pins(
    val repaints: Int?,
    val revalidations: Int?,
    val layouts: Int?,
)

/** Runs the request counting of [countTests] on the fixture it visits. */
private class CountTests(
    private val pins: Pins,
) : FixtureVisitor<List<DynamicTest>> {
    override fun <P : Any, C : Component> visit(fixture: ComposableFixture<P, C>): List<DynamicTest> =
        fixture.alternatives.zip(alternativeNames(fixture)).map { (alternative, name) ->
            DynamicTest.dynamicTest(name) {
                runComposeSwingTest(effectContext = NoMotion) {
                    var mounted by mutableStateOf(true)
                    var parameters by mutableStateOf(fixture.initial, referentialEqualityPolicy())
                    val component = fixture.createRawSwingOrSkip()
                    val windowsBefore = Window.getWindows().toSet()
                    setContent { if (mounted) fixture.Content(parameters) }
                    RawHost(fixture, component, this, windowsBefore).use { raw ->
                        awaitIdle()
                        raw.settle()
                        // A window lays its content out on turns of its own, so only a panel host's layout is counted.
                        val rawLayouts = if (raw.windows == null) recordLayoutOf(raw.container) else null
                        val composedLayouts = if (raw.windows == null) recordLayoutOf(raw.composedContainer) else null
                        // Replacing a layout manager invalidates its container, so let that pass run unrecorded.
                        awaitIdle()
                        raw.settle()

                        val composed =
                            Counts.record(raw::isComposed, composedLayouts) {
                                parameters = alternative
                                awaitIdle()
                            }
                        val plain =
                            Counts.record({ SwingUtilities.isDescendingFrom(it, raw.container) }, rawLayouts) {
                                raw.apply(alternative)
                                raw.settle()
                            }
                        composed.assertNoMoreThan(plain, pins, fixture.name)

                        val unchanged =
                            Counts.record(raw::isComposed, composedLayouts) {
                                parameters = equalCopy(alternative)
                                awaitIdle()
                            }
                        assertEquals(
                            Triple(0, 0, 0),
                            Triple(unchanged.repaints, unchanged.revalidations, unchanged.layouts),
                            "${fixture.name} must do no Swing work when it is given values equal to " +
                                "the ones it shows. Asked for repaints, revalidations and host layout passes: " +
                                "${unchanged.repaints}, ${unchanged.revalidations}, ${unchanged.layouts}.",
                        )

                        mounted = false
                        awaitIdle()
                    }
                }
            }
        }
}

/** Replaces the layout of [container] with a recording one that delegates to it, and returns it. */
private fun recordLayoutOf(container: Container): RecordingLayout =
    RecordingLayout(container.layout).also { recording -> container.layout = recording }

/** What one change asked of Swing; [layouts] counts the layout passes of the host layout only. */
private class Counts(
    val repaints: Int,
    val revalidations: Int,
    val layouts: Int,
) {
    fun assertNoMoreThan(
        raw: Counts,
        pins: Pins,
        fixture: String,
    ) {
        assertTrue(
            repaints <= (pins.repaints ?: raw.repaints),
            "$fixture asked for $repaints repaints; ${ceiling(pins.repaints, raw.repaints)}.",
        )
        assertTrue(
            revalidations <= (pins.revalidations ?: raw.revalidations),
            "$fixture asked for $revalidations revalidations; ${ceiling(pins.revalidations, raw.revalidations)}.",
        )
        assertTrue(
            layouts <= (pins.layouts ?: raw.layouts),
            "$fixture asked for $layouts host layout passes; ${ceiling(pins.layouts, raw.layouts)}.",
        )
    }

    private fun ceiling(
        pinned: Int?,
        raw: Int,
    ): String = if (pinned == null) "raw Swing asked for $raw" else "the pinned maximum is $pinned"

    companion object {
        /**
         * Runs [change] and reports what it asked for of the components that [isHost] accepts, the host layout passes
         * through [layout], when there is one. A request from any other component is not the change's own.
         */
        suspend fun record(
            isHost: (Component) -> Boolean,
            layout: RecordingLayout?,
            change: suspend () -> Unit,
        ): Counts {
            layout?.layouts?.clear()
            val requests = HostRequests(isHost)
            val standing = RepaintManager.currentManager(null)
            RepaintManager.setCurrentManager(requests)
            try {
                change()
            } finally {
                RepaintManager.setCurrentManager(standing)
            }
            return Counts(requests.repaints, requests.revalidations, layout?.layouts?.size ?: 0)
        }
    }
}

/** Counts the requests that come from components [isHost] accepts, deciding as each is made whether it does. */
private class HostRequests(
    private val isHost: (Component) -> Boolean,
) : RepaintManager() {
    var repaints = 0
        private set
    var revalidations = 0
        private set

    override fun addInvalidComponent(component: JComponent) {
        if (isHost(component)) revalidations++
        super.addInvalidComponent(component)
    }

    override fun addDirtyRegion(
        component: JComponent,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        if (isHost(component)) repaints++
        super.addDirtyRegion(component, x, y, width, height)
    }
}
