package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.core.TracedTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JMenuBar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins what the appliers report about a change pass: the pass is named as a whole, from the moment the
 * runtime starts driving it to the moment the last container has been brought up to date, so the node
 * update blocks it runs - where a widget is written and read back - are named as part of it.
 */
class ApplySectionTest : TracedTest() {
    @Test
    fun theChurnAPassDrivesIsReportedInsideIt() = runComposeSwingTest {
        setContent { Label("only child") }
        awaitIdle()

        // A node reaches the tree through the top-down pass and is given its place on the bottom-up one,
        // so mounting any content at all drives both kinds of churn.
        val churn = tracer.sections.filter { it.name == "insert" || it.name == "attach" }
        val names = churn.map { it.name }.toSet()
        assertTrue(
            "insert" in names,
            "mounting a component reaches the tree through the top-down pass, so the pass should name " +
                "that: ${tracer.sections}",
        )
        assertTrue(
            "attach" in names,
            "mounting a component is given its place on the bottom-up pass, so the pass should name " +
                "that: ${tracer.sections}",
        )
        for (section in churn) {
            assertTrue(
                "apply" in section.enclosing,
                "churn is part of the change pass and should be reported inside it, but got $section",
            )
        }
    }

    @Test
    fun aMenuChangePassIsReported() {
        val applier = MenuApplier(JMenuBar())

        applier.onBeginChanges()
        applier.onEndChanges()

        assertEquals(
            listOf("apply"),
            tracer.sections.map { it.name },
            "the menu applier should name the change pass it runs",
        )
    }

    @Test
    fun aChangePassThatThrowsStillClosesItsSection() {
        // revalidate() is what the end-of-pass refresh calls on every container the pass dirtied, so a
        // menu bar that throws there is a pass that fails after its section is open.
        val failing =
            object : JMenuBar() {
                var failing: Boolean = true

                override fun revalidate() {
                    if (failing) error("refresh failed")
                }
            }
        val applier = MenuApplier(failing)
        applier.onBeginChanges()
        // Removing nothing still marks the bar as a container the pass touched, which is all the
        // end-of-pass refresh needs to reach it.
        applier.remove(index = 0, count = 0)

        assertFailsWith<IllegalStateException> { applier.onEndChanges() }
        tracer.clear()

        // A section left open would still be on the thread's stack, and the next pass would be recorded
        // inside it. Reporting the later pass on its own is what says the failed one was closed.
        failing.failing = false
        applier.onBeginChanges()
        applier.onEndChanges()
        assertEquals(
            listOf(emptyList()),
            tracer.sections.map { it.enclosing },
            "a pass that ended by throwing should leave no section open, but the next pass was reported " +
                "inside one: ${tracer.sections}",
        )
    }

    @Test
    fun aChangePassThatNeverEndsDoesNotEncloseTheNextOne() {
        // The runtime tells the applier changes ended only where the pass ran to completion: a change
        // that throws - a node update block written by a caller - unwinds past that call, so the applier
        // is left with a pass it is never told about again.
        val applier = MenuApplier(JMenuBar())
        applier.onBeginChanges()
        tracer.clear()

        applier.onBeginChanges()
        applier.onEndChanges()

        assertEquals(
            listOf(emptyList()),
            tracer.sections.map { it.enclosing },
            "a pass abandoned before it ended should leave no section open, but the next pass was " +
                "reported inside one: ${tracer.sections}",
        )
    }
}
