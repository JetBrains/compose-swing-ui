package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.TestRecomposer
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import java.awt.Container
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests for `setContent` composing under a caller-named parent, on the half that needs no
 * window.
 *
 * A named parent is the whole answer to what the content joins, so every case here runs on a container
 * that hangs off no window at all - the shape of a container built to be read rather than shown. The
 * parent is a [org.jetbrains.compose.swing.TestRecomposer] the test owns and drives on a controllable
 * frame clock (no sleeps, bounded frames), which is exactly what a caller passing a context of their own
 * has.
 */
class SetContentParentTest {
    @Test
    fun namedParentContentComposesOnTheCall() = runSwingTest {
        val test = TestRecomposer(this)
        val handles = mutableListOf<DisposableHandle>()
        try {
            // This container hangs off no window and no published ancestor, so nothing but the named
            // parent answers what it composes under; the content is there the moment the call returns.
            val detached = JPanel().apply { size = Dimension(SIZE, SIZE) }

            handles += detached.setContent(parent = test.recomposer) { Label(text = "on-call") }
            // Read right after the call, before any event-queue turn or frame.
            val textsOnReturn = labelTexts(detached)

            assertEquals(
                listOf("on-call"),
                textsOnReturn,
                "content under a named parent must be composed by the time setContent returns",
            )
        } finally {
            handles.forEach { it.dispose() }
            test.cancel()
        }
    }

    @Test
    fun disposingACompositionLeavesItsParentDrivingTheNextOne() = runSwingTest {
        val test = TestRecomposer(this)
        val handles = mutableListOf<DisposableHandle>()
        try {
            val first = JPanel().apply { size = Dimension(SIZE, SIZE) }
            val second = JPanel().apply { size = Dimension(SIZE, SIZE) }
            var shared by mutableStateOf("v0")

            val firstHandle = first.setContent(parent = test.recomposer) { Label(text = "first=$shared") }
            handles += firstHandle
            test.awaitIdle()
            assertEquals(
                listOf("first=v0"),
                labelTexts(first),
                "the first content composition should render its initial state",
            )

            // The handle owns this content composition and nothing else, so disposing it takes only
            // that composition's content down and leaves the parent the caller owns running.
            firstHandle.dispose()
            assertEquals(
                emptyList(),
                labelTexts(first),
                "disposing the handle must take the composed content with it",
            )

            handles += second.setContent(parent = test.recomposer) { Label(text = "second=$shared") }
            assertEquals(
                listOf("second=v0"),
                labelTexts(second),
                "a second content composition must compose under a parent that outlived the first",
            )

            shared = "v1"
            test.awaitIdle()
            assertEquals(
                listOf("second=v1"),
                labelTexts(second),
                "the parent must keep driving recomposition after one of its content compositions was disposed",
            )
            assertEquals(
                emptyList(),
                labelTexts(first),
                "a disposed content composition must not compose again when the state it read changes",
            )
        } finally {
            handles.forEach { it.dispose() }
            test.cancel()
        }
    }

    @Test
    fun aContentCompositionUnderANamedParentWatchesTheContainersPlaceAndGivesItUpOnDispose() = runSwingTest {
        val test = TestRecomposer(this)
        val handles = mutableListOf<DisposableHandle>()
        try {
            // Two things watch the container's place in the Swing tree, each with its own
            // HierarchyListener: mounting (so a container that ends up in a window joins that
            // window's composition) and the content's lifecycle owner (which reads whether the
            // content is shown off that same place). Disposing the handle gives both listeners back.
            val detached = JPanel().apply { size = Dimension(SIZE, SIZE) }
            val before = detached.hierarchyListeners.size

            val handle = detached.setContent(parent = test.recomposer) { Label(text = "watched") }
            assertEquals(
                before + 2,
                detached.hierarchyListeners.size,
                "content mounted under a named parent must watch the container's place with one " +
                    "HierarchyListener for mounting and one for the content's lifecycle",
            )

            handle.dispose()
            assertEquals(
                before,
                detached.hierarchyListeners.size,
                "disposing the handle must unregister every HierarchyListener setContent added",
            )
            assertEquals(emptyList(), labelTexts(detached), "disposing the handle must take the content down")

            handle.dispose()
            assertEquals(
                before,
                detached.hierarchyListeners.size,
                "a double-dispose must leave no listener behind",
            )
        } finally {
            handles.forEach { it.dispose() }
            test.cancel()
        }
    }

    @Test
    fun aMoveThatResolvesToNoParentLeavesTheCompositionComposingAndDisposableCleanly() = runSwingTest {
        val test = TestRecomposer(this)
        val handles = mutableListOf<DisposableHandle>()
        try {
            // Moved into another container that is itself in no window, the container's place still
            // resolves to no parent of its own, so the content composition keeps composing under the
            // parent it was given.
            val detached = JPanel().apply { size = Dimension(SIZE, SIZE) }
            val newHost = JPanel().apply { size = Dimension(SIZE, SIZE) }
            var shared by mutableStateOf("v0")

            val handle = detached.setContent(parent = test.recomposer) { Label(text = "moved=$shared") }
            handles += handle
            test.awaitIdle()

            newHost.add(detached)
            shared = "v1"
            test.awaitIdle()
            assertEquals(
                listOf("moved=v1"),
                labelTexts(detached),
                "a container moved where no parent resolves must keep composing under the parent it was given",
            )
            assertEquals(
                2,
                detached.hierarchyListeners.size,
                "a live content composition and the lifecycle of its content must both keep watching the " +
                    "container's place after a move",
            )

            handle.dispose()
            assertEquals(
                0,
                detached.hierarchyListeners.size,
                "disposing after a move must unregister every HierarchyListener setContent added",
            )
            assertEquals(emptyList(), labelTexts(detached), "disposing after a move must take the content down")
        } finally {
            handles.forEach { it.dispose() }
            test.cancel()
        }
    }

    /** The text of every [JLabel] in [container]'s subtree, in tree order. */
    private fun labelTexts(container: Container): List<String> {
        val texts = mutableListOf<String>()

        fun visit(c: Container) {
            for (child in c.components) {
                if (child is JLabel) texts += child.text
                if (child is Container) visit(child)
            }
        }
        visit(container)
        return texts
    }

    private companion object {
        const val SIZE: Int = 200
    }
}
