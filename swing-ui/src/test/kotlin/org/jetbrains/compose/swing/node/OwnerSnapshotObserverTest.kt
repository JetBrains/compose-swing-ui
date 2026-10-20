package org.jetbrains.compose.swing.node

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals

class OwnerSnapshotObserverTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("observations")
    fun aNestedObservationRecordsReadsOnlyForTheInnerNode(observedBefore: Boolean) = runComposeSwingTest {
        val earlierState = mutableStateOf("earlier")
        val watched = mutableStateOf("before")
        val changed = mutableListOf<String>()
        lateinit var outer: RenderingNode
        lateinit var earlier: RenderingNode
        lateinit var inner: RenderingNode
        setContent {
            SwingNode(factory = { JLabel() }, modifier = SwingModifier then RenderingElement { outer = it })
            SwingNode(factory = { JLabel() }, modifier = SwingModifier then RenderingElement { earlier = it })
            SwingNode(factory = { JLabel() }, modifier = SwingModifier then RenderingElement { inner = it })
        }
        outer.observeReads(onChanged = { changed += "outer" }) {
            if (observedBefore) {
                earlier.observeReads(onChanged = { changed += "earlier" }) { earlierState.value }
            }
            inner.observeReads(onChanged = { changed += "inner" }) { watched.value }
        }

        watched.value = "after"
        awaitIdle()

        assertEquals(listOf("inner"), changed)
    }

    @Test
    fun aChangeAppliedDuringTheFirstObservedBlockUpdatesTheRenderedValue() = runComposeSwingTest {
        val watched = mutableIntStateOf(0)
        var firstRead = true
        lateinit var node: RenderingNode
        setContent {
            SwingNode(
                factory = { JLabel() },
                modifier =
                    SwingModifier then
                        RenderingElement { created ->
                            node = created
                        },
            )
        }
        node.render = {
            val value = watched.intValue
            if (firstRead) {
                firstRead = false
                CompletableFuture
                    .runAsync {
                        Snapshot.withMutableSnapshot { watched.intValue = 1 }
                    }.get(10, TimeUnit.SECONDS)
            }
            value.toString()
        }
        node.refresh()
        awaitIdle()

        onNodeWithText("1").assertExists()
    }

    @Test
    fun aNodeDetachedWhileAnotherIsNotifiedStopsHearingChangesAndCanObserveAgain() = runComposeSwingTest {
        val watched = mutableIntStateOf(0)
        val heard = mutableListOf<String>()
        lateinit var first: RenderingNode
        lateinit var second: RenderingNode
        setContent {
            SwingNode(factory = { JLabel() }, modifier = SwingModifier then RenderingElement { first = it })
            SwingNode(factory = { JLabel() }, modifier = SwingModifier then RenderingElement { second = it })
        }
        val owner = first.requireOwner()
        lateinit var detached: RenderingNode
        lateinit var onFirst: (RenderingNode) -> Unit
        lateinit var onSecond: (RenderingNode) -> Unit
        onFirst = {
            heard += "first"
            detached = second
            owner.snapshotObserver.clear(second)
        }
        onSecond = {
            heard += "second"
            detached = first
            owner.snapshotObserver.clear(first)
        }
        first.observeReads(onFirst) { watched.intValue }
        second.observeReads(onSecond) { watched.intValue }

        watched.intValue = 1
        awaitIdle()

        assertEquals(1, heard.size)
        heard.clear()
        owner.snapshotObserver.clear(first)
        owner.snapshotObserver.clear(second)
        detached.observeReads(onChanged = { heard += "reattached" }) { watched.intValue }
        watched.intValue = 2
        awaitIdle()

        assertEquals(listOf("reattached"), heard)
    }

    companion object {
        @JvmStatic
        fun observations(): List<Arguments> = listOf(
            Arguments.of(Named.of("as the first observation", false)),
            Arguments.of(Named.of("after another observation", true)),
        )
    }
}

private class RenderingElement(
    private val created: (RenderingNode) -> Unit,
) : SwingModifier.NodeElement<JLabel, RenderingNode>() {
    override val targetType: Class<JLabel> get() = JLabel::class.java

    override fun create(): RenderingNode = RenderingNode().also(created)

    override fun update(node: RenderingNode) = Unit

    override fun equals(other: Any?): Boolean = other is RenderingElement

    override fun hashCode(): Int = RenderingElement::class.hashCode()
}

private class RenderingNode :
    SwingModifier.ComponentNode<JLabel>(),
    ObserverModifierNode {
    lateinit var render: () -> String

    fun refresh() = observeReads { component.text = render() }

    override fun onObservedReadsChanged() = refresh()
}
