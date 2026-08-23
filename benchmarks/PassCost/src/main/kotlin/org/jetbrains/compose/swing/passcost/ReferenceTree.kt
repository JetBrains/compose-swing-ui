package org.jetbrains.compose.swing.passcost

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Composition
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.passcost.harness.Frames

/*
 * The denominator every other arm is missing: a tree of the barest node the Compose runtime can drive,
 * under an applier that does nothing but hold children. It carries no widget, no holder, no modifier
 * chain and no settle - so what it costs is what the runtime costs, and what a library arm costs above
 * it is what this library's node design asks for.
 */

/** The node a reference tree is made of. It holds its children and the key its update block took. */
internal class ReferenceNode {
    val children: MutableList<ReferenceNode> = ArrayList()
}

/**
 * The applier a reference tree is composed into: it holds the tree the composition declares and does
 * nothing else with it. No widget is built, attached, laid out or painted.
 */
internal class ReferenceApplier(
    root: ReferenceNode,
) : AbstractApplier<ReferenceNode>(root) {
    // Children are taken in on the way up, the way SwingApplier attaches them, so the two appliers are
    // handed the same tree in the same order and differ in what they do with it.
    override fun insertTopDown(
        index: Int,
        instance: ReferenceNode,
    ): Unit = Unit

    override fun insertBottomUp(
        index: Int,
        instance: ReferenceNode,
    ) {
        current.children.add(index, instance)
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        current.children.subList(index, index + count).clear()
    }

    override fun move(
        from: Int,
        to: Int,
        count: Int,
    ) {
        val children = current.children
        val moved = ArrayList(children.subList(from, from + count))
        children.subList(from, from + count).clear()
        children.addAll(if (from > to) to else to - count, moved)
    }

    override fun onClear() {
        root.children.clear()
    }
}

/**
 * The reference counterpart of a node whose update block holds one key and nothing else - the shape
 * `PlainPanel` declares over a `JPanel`.
 */
@Composable
internal fun ReferenceWidget(
    tick: State<Int>,
    onCompose: () -> Unit,
) {
    onCompose()
    ComposeNode<ReferenceNode, ReferenceApplier>(
        factory = { ReferenceNode() },
        update = {
            set(tick.value) { /* The key is the point; there is nothing to write. */ }
        },
    )
}

/** The reference counterpart of the container every arm's widgets are emitted into. */
@Composable
internal fun ReferenceGroup(content: @Composable () -> Unit) {
    ComposeNode<ReferenceNode, ReferenceApplier>(
        factory = { ReferenceNode() },
        update = { },
        content = content,
    )
}

/**
 * Composes [content] into [root] on the recomposer every other arm is driven by, so a reference pass and
 * a library pass are the same pass under the same frame protocol.
 *
 * Call on the event dispatch thread.
 */
internal fun mountReferenceTree(
    root: ReferenceNode,
    content: @Composable () -> Unit,
): DisposableHandle {
    val composition = Composition(ReferenceApplier(root), Frames.compositionContext)
    composition.setContent(content)
    return DisposableHandle { composition.dispose() }
}

/**
 * Every widget the barest node the Compose runtime can drive, under an applier that holds it and does
 * nothing else: what recomposing a tree of N invalidated nodes costs before any of this library is in it.
 *
 * This is the denominator the widget arms are read against. Its counterpart is "node key only", which
 * declares the same one key over the same tree shape through a `SwingNode` and a `JPanel`; the difference
 * between the two is what a Swing node costs above a bare one, and it is the only figure here that
 * separates what the runtime asks for from what this library's node design does.
 */
internal fun referenceNodeArm(): Arm =
    Arm(listOf(REFERENCE_NODE_ARM)) { widgets, changing ->
        val tick = mutableStateOf(UNSET_TICK)
        val nodeRuns = IntArray(1)
        val root = ReferenceNode()
        Run(
            mount = { _ ->
                mountReferenceTree(root) {
                    ReferenceGroup {
                        repeat(widgets) { ReferenceWidget(tick) { nodeRuns[0]++ } }
                    }
                }
            },
            drive = { pass ->
                if (changing) tick.value = pass % 2
                REFERENCE_NODE_ARM
            },
            verify = { _, passes ->
                checkWidgets("groups", root.children.size, 1)
                val group = root.children.single()
                checkWidgets("reference nodes", group.children.size, widgets)
                checkScopeRuns("the reference node scopes", nodeRuns[0], widgets * if (changing) 1 + passes else 1)
            },
        )
    }
