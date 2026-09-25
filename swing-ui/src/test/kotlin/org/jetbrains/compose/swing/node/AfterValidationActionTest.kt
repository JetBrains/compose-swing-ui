package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.requestAfterValidation
import java.lang.reflect.InvocationTargetException
import javax.swing.CellRendererPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class AfterValidationActionTest {
    private class RequestNode : SwingModifier.Node()

    private class LayoutPanel : JPanel() {
        var invalidations = 0
        var revalidations = 0

        override fun invalidate() {
            invalidations++
            super.invalidate()
        }

        override fun revalidate() {
            revalidations++
            super.revalidate()
        }

        fun resetCounts() {
            invalidations = 0
            revalidations = 0
        }
    }

    private fun attachedNode(owner: TestCompositionOwner): Pair<JPanel, RequestNode> {
        val component = JPanel()
        val node = RequestNode()
        node.attach(CreatedNodeHolder(component).attachedTo(owner))
        return component to node
    }

    @Test
    fun rejectsANodeOwnedByAnotherCompositionBeforeRunningItsCallback() {
        val owner = TestCompositionOwner()
        val other = TestCompositionOwner()
        val (_, node) = attachedNode(owner)
        var calls = 0

        SwingUtilities.invokeAndWait {
            assertFailsWith<IllegalStateException> {
                other.requestAfterValidation(node) { calls++ }
            }
        }

        assertEquals(0, calls)
        node.detach()
        owner.dispose()
        other.dispose()
    }

    @Test
    fun rejectsLayoutInvalidationForAnotherCompositionsComponent() {
        val owner = TestCompositionOwner()
        val other = TestCompositionOwner()
        val component = LayoutPanel()
        val holder = CreatedNodeHolder(component).attachedTo(owner)

        SwingUtilities.invokeAndWait {
            component.resetCounts()
            assertFailsWith<IllegalStateException> { other.invalidateLayout(holder) }
        }

        assertEquals(0, component.invalidations)
        assertEquals(0, component.revalidations)
        holder.onRelease()
        owner.dispose()
        other.dispose()
    }

    @Test
    fun disposingAnOwnerPreventsImmediateRequestsFromRunning() {
        val posted = mutableListOf<() -> Unit>()
        val owner = TestCompositionOwner(AfterValidationCoordinator(posted::add))
        val (_, node) = attachedNode(owner)
        var calls = 0

        SwingUtilities.invokeAndWait {
            owner.dispose()
            owner.requestAfterValidation(node) { calls++ }
        }

        assertEquals(0, calls)
        assertEquals(0, posted.size)
        node.detach()
    }

    @Test
    fun runsImmediatelyWhenTheTreeLockIsNotHeld() {
        val posted = mutableListOf<() -> Unit>()
        val owner = TestCompositionOwner(AfterValidationCoordinator(posted::add))
        val (_, node) = attachedNode(owner)
        var calls = 0

        SwingUtilities.invokeAndWait {
            node.requestAfterValidation { calls++ }
        }

        assertEquals(1, calls)
        assertEquals(0, posted.size)
        node.detach()
        owner.dispose()
    }

    @Test
    fun anImmediateRequestCancelsTheSameQueuedCallback() {
        val posted = mutableListOf<() -> Unit>()
        val owner = TestCompositionOwner(AfterValidationCoordinator(posted::add))
        val (component, node) = attachedNode(owner)
        var calls = 0
        val action: (RequestNode) -> Unit = { calls++ }

        SwingUtilities.invokeAndWait {
            synchronized(component.treeLock) { node.requestAfterValidation(action) }
        }
        assertEquals(1, posted.size)

        SwingUtilities.invokeAndWait { node.requestAfterValidation(action) }
        assertEquals(1, calls)

        SwingUtilities.invokeAndWait { posted.removeAt(0)() }
        assertEquals(1, calls)
        node.detach()
        owner.dispose()
    }

    @Test
    fun defersAndDeduplicatesByNodeAndCallbackIdentity() {
        val posted = mutableListOf<() -> Unit>()
        val owner = TestCompositionOwner(AfterValidationCoordinator(posted::add))
        val (component, first) = attachedNode(owner)
        val (_, second) = attachedNode(owner)
        val called = mutableListOf<RequestNode>()
        val otherCalls = mutableListOf<RequestNode>()
        val action: (RequestNode) -> Unit = called::add
        val otherAction: (RequestNode) -> Unit = otherCalls::add

        SwingUtilities.invokeAndWait {
            synchronized(component.treeLock) {
                first.requestAfterValidation(action)
                first.requestAfterValidation(action)
                first.requestAfterValidation(otherAction)
                second.requestAfterValidation(action)
            }
        }

        assertEquals(0, called.size)
        assertEquals(1, posted.size)
        SwingUtilities.invokeAndWait { posted.removeAt(0)() }

        assertEquals(2, called.size)
        assertSame(first, called[0])
        assertSame(second, called[1])
        assertEquals(listOf(first), otherCalls)
        first.detach()
        second.detach()
        owner.dispose()
    }

    @Test
    fun twoOwnersShareOneEventQueuePost() {
        val posted = mutableListOf<() -> Unit>()
        val coordinator = AfterValidationCoordinator(posted::add)
        val firstOwner = TestCompositionOwner(coordinator)
        val secondOwner = TestCompositionOwner(coordinator)
        val (firstComponent, first) = attachedNode(firstOwner)
        val (secondComponent, second) = attachedNode(secondOwner)
        var calls = 0

        SwingUtilities.invokeAndWait {
            synchronized(firstComponent.treeLock) { first.requestAfterValidation { calls++ } }
            synchronized(secondComponent.treeLock) { second.requestAfterValidation { calls++ } }
        }

        assertEquals(1, posted.size)
        SwingUtilities.invokeAndWait { posted.removeAt(0)() }
        assertEquals(2, calls)
        first.detach()
        second.detach()
        firstOwner.dispose()
        secondOwner.dispose()
    }

    @Test
    fun requestsForAnotherOwnerAlreadyDrainingWaitForTheFollowingTurn() {
        val posted = mutableListOf<() -> Unit>()
        val coordinator = AfterValidationCoordinator(posted::add)
        val firstOwner = TestCompositionOwner(coordinator)
        val secondOwner = TestCompositionOwner(coordinator)
        val (firstComponent, first) = attachedNode(firstOwner)
        val (secondComponent, second) = attachedNode(secondOwner)
        val calls = mutableListOf<String>()
        val nextAction: (RequestNode) -> Unit = { calls += "next" }
        val firstAction: (RequestNode) -> Unit = {
            calls += "first"
            synchronized(secondComponent.treeLock) { second.requestAfterValidation(nextAction) }
        }
        val triggerAction: (RequestNode) -> Unit = {
            calls += "trigger"
            synchronized(secondComponent.treeLock) { second.requestAfterValidation(nextAction) }
        }

        SwingUtilities.invokeAndWait {
            synchronized(firstComponent.treeLock) { first.requestAfterValidation(triggerAction) }
            synchronized(secondComponent.treeLock) { second.requestAfterValidation(firstAction) }
        }
        SwingUtilities.invokeAndWait { posted.removeAt(0)() }

        assertEquals(listOf("trigger", "first"), calls)
        assertEquals(1, posted.size)
        SwingUtilities.invokeAndWait { posted.removeAt(0)() }

        assertEquals(listOf("trigger", "first", "next"), calls)
        first.detach()
        second.detach()
        firstOwner.dispose()
        secondOwner.dispose()
    }

    @Test
    fun detachReattachAndOwnerDisposalDropStaleActions() {
        val posted = mutableListOf<() -> Unit>()
        val coordinator = AfterValidationCoordinator(posted::add)
        val owner = TestCompositionOwner(coordinator)
        val (component, node) = attachedNode(owner)
        var calls = 0

        SwingUtilities.invokeAndWait {
            synchronized(component.treeLock) { node.requestAfterValidation { calls++ } }
            node.detach()
            node.attach(CreatedNodeHolder(component).attachedTo(owner))
        }
        SwingUtilities.invokeAndWait { posted.removeAt(0)() }
        assertEquals(0, calls)

        SwingUtilities.invokeAndWait {
            synchronized(component.treeLock) { node.requestAfterValidation { calls++ } }
            owner.dispose()
        }
        SwingUtilities.invokeAndWait { posted.removeAt(0)() }

        assertEquals(0, calls)
        node.detach()
    }

    @Test
    fun requestsMadeWhileDrainingRunOnTheFollowingTurn() {
        val posted = mutableListOf<() -> Unit>()
        val owner = TestCompositionOwner(AfterValidationCoordinator(posted::add))
        val (component, node) = attachedNode(owner)
        val calls = mutableListOf<String>()
        val secondAction: (RequestNode) -> Unit = { calls += "second" }
        val firstAction: (RequestNode) -> Unit = {
            calls += "first"
            synchronized(component.treeLock) { node.requestAfterValidation(secondAction) }
        }

        SwingUtilities.invokeAndWait {
            synchronized(component.treeLock) { node.requestAfterValidation(firstAction) }
        }
        SwingUtilities.invokeAndWait { posted.removeAt(0)() }

        assertEquals(listOf("first"), calls)
        assertEquals(1, posted.size)
        SwingUtilities.invokeAndWait { posted.removeAt(0)() }

        assertEquals(listOf("first", "second"), calls)
        node.detach()
        owner.dispose()
    }

    @Test
    fun aReentrantPostedTurnDoesNotSwapBuffersWhileTheCurrentTurnDrains() {
        val posted = mutableListOf<() -> Unit>()
        val owner = TestCompositionOwner(AfterValidationCoordinator(posted::add))
        val (component, node) = attachedNode(owner)
        val calls = mutableListOf<String>()
        val secondAction: (RequestNode) -> Unit = { calls += "nested" }
        val outerAction: (RequestNode) -> Unit = { calls += "outer" }
        val firstAction: (RequestNode) -> Unit = {
            calls += "first"
            synchronized(component.treeLock) { node.requestAfterValidation(secondAction) }
            posted.removeAt(0)()
        }

        SwingUtilities.invokeAndWait {
            synchronized(component.treeLock) {
                node.requestAfterValidation(firstAction)
                node.requestAfterValidation(outerAction)
            }
        }
        SwingUtilities.invokeAndWait { posted.removeAt(0)() }

        assertEquals(listOf("first", "nested", "outer"), calls)
        assertEquals(0, posted.size)
        node.detach()
        owner.dispose()
    }

    @Test
    fun detachAndReattachDuringDrainDoesNotResurrectTheOldRequest() {
        val posted = mutableListOf<() -> Unit>()
        val owner = TestCompositionOwner(AfterValidationCoordinator(posted::add))
        val (triggerComponent, trigger) = attachedNode(owner)
        val (targetComponent, target) = attachedNode(owner)
        var calls = 0
        val targetAction: (RequestNode) -> Unit = { calls++ }
        val triggerAction: (RequestNode) -> Unit = {
            target.detach()
            target.attach(CreatedNodeHolder(targetComponent).attachedTo(owner))
            synchronized(targetComponent.treeLock) { target.requestAfterValidation(targetAction) }
        }

        SwingUtilities.invokeAndWait {
            synchronized(triggerComponent.treeLock) { trigger.requestAfterValidation(triggerAction) }
            synchronized(targetComponent.treeLock) { target.requestAfterValidation(targetAction) }
        }
        SwingUtilities.invokeAndWait { posted.removeAt(0)() }

        assertEquals(0, calls)
        assertEquals(1, posted.size)
        SwingUtilities.invokeAndWait { posted.removeAt(0)() }

        assertEquals(1, calls)
        trigger.detach()
        target.detach()
        owner.dispose()
    }

    @Test
    fun defersInsideACellRendererPaneWhileItsTreeLockIsHeld() {
        val posted = mutableListOf<() -> Unit>()
        val owner = TestCompositionOwner(AfterValidationCoordinator(posted::add))
        val pane = CellRendererPane()
        val component = JPanel()
        pane.add(component)
        val node = RequestNode().also { it.attach(CreatedNodeHolder(component).attachedTo(owner)) }
        var calls = 0

        SwingUtilities.invokeAndWait {
            synchronized(component.treeLock) { node.requestAfterValidation { calls++ } }
        }

        assertEquals(0, calls)
        assertEquals(1, posted.size)
        SwingUtilities.invokeAndWait { posted.removeAt(0)() }

        assertEquals(1, calls)
        node.detach()
        owner.dispose()
    }

    @Test
    fun componentInvalidationIsImmediateAndRevalidationWaitsForTheLock() {
        val posted = mutableListOf<() -> Unit>()
        val owner = TestCompositionOwner(AfterValidationCoordinator(posted::add))
        val component = LayoutPanel()
        val holder = CreatedNodeHolder(component).attachedTo(owner)

        SwingUtilities.invokeAndWait {
            component.resetCounts()
            synchronized(component.treeLock) {
                holder.invalidateLayout()
                holder.invalidateLayout()
                assertEquals(2, component.invalidations)
                assertEquals(0, component.revalidations)
            }
        }

        assertEquals(1, posted.size)
        SwingUtilities.invokeAndWait { posted.removeAt(0)() }

        assertEquals(1, component.revalidations)
        owner.dispose()
    }

    @Test
    fun componentRevalidationSurvivesModifierDetachButReuseCancelsIt() {
        val posted = mutableListOf<() -> Unit>()
        val owner = TestCompositionOwner(AfterValidationCoordinator(posted::add))
        val component = LayoutPanel()
        val holder = CreatedNodeHolder(component).attachedTo(owner)
        val node = RequestNode().also { it.attach(holder) }

        SwingUtilities.invokeAndWait {
            component.resetCounts()
            synchronized(component.treeLock) { holder.invalidateLayout() }
            node.detach()
        }
        SwingUtilities.invokeAndWait { posted.removeAt(0)() }
        assertEquals(1, component.revalidations)

        SwingUtilities.invokeAndWait {
            component.resetCounts()
            synchronized(component.treeLock) { holder.invalidateLayout() }
            holder.onReuse()
        }
        SwingUtilities.invokeAndWait { posted.removeAt(0)() }

        assertEquals(0, component.revalidations)

        SwingUtilities.invokeAndWait {
            synchronized(component.treeLock) { holder.invalidateLayout() }
            holder.onDeactivate()
        }
        SwingUtilities.invokeAndWait { posted.removeAt(0)() }
        assertEquals(0, component.revalidations)

        SwingUtilities.invokeAndWait { holder.invalidateLayout() }
        assertEquals(0, posted.size)
        holder.onRelease()
        owner.dispose()
    }

    @Test
    fun releaseCancelsComponentRevalidationEvenWhenTeardownThrows() {
        val posted = mutableListOf<() -> Unit>()
        val owner = TestCompositionOwner(AfterValidationCoordinator(posted::add))
        val component = LayoutPanel()
        val holder = CreatedNodeHolder(component).attachedTo(owner)
        holder.releaseBlock = {
            synchronized(component.treeLock) { holder.invalidateLayout() }
            error("release failed")
        }

        SwingUtilities.invokeAndWait {
            component.resetCounts()
            synchronized(component.treeLock) { holder.invalidateLayout() }
        }
        SwingUtilities.invokeAndWait {
            val failure = assertFailsWith<IllegalStateException> { holder.onRelease() }
            assertEquals("release failed", failure.message)
        }
        SwingUtilities.invokeAndWait { posted.removeAt(0)() }

        assertEquals(0, component.revalidations)
        assertEquals(null, holder.owner)
        owner.dispose()
    }

    @Test
    fun callbackFailureDoesNotDropAnotherOwnersActions() {
        val posted = mutableListOf<() -> Unit>()
        val coordinator = AfterValidationCoordinator(posted::add)
        val throwingOwner = TestCompositionOwner(coordinator)
        val otherOwner = TestCompositionOwner(coordinator)
        val (throwingComponent, throwingNode) = attachedNode(throwingOwner)
        val (otherComponent, otherNode) = attachedNode(otherOwner)
        var otherCalls = 0

        SwingUtilities.invokeAndWait {
            synchronized(throwingComponent.treeLock) {
                throwingNode.requestAfterValidation { error("callback failed") }
            }
            synchronized(otherComponent.treeLock) { otherNode.requestAfterValidation { otherCalls++ } }
        }

        val failure =
            assertFailsWith<InvocationTargetException> {
                SwingUtilities.invokeAndWait { posted.removeAt(0)() }
            }

        assertEquals("callback failed", failure.targetException.message)
        assertEquals(1, otherCalls)
        throwingNode.detach()
        otherNode.detach()
        throwingOwner.dispose()
        otherOwner.dispose()
    }
}
