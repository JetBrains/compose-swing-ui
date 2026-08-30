package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.tooling.findCompositionData
import org.jetbrains.compose.swing.tooling.isDebugInspectorInfoEnabled
import javax.swing.JComponent

/**
 * Composes [content] and asserts that it opened exactly [expected] restart scopes - the groups a
 * recomposition can restart at. A composable inlined into its caller opens none of its own, so the count
 * is what a call site pays to be recomposable.
 *
 * Turns inspection on, which is what publishes the composition data this reads; the caller restores it.
 */
internal fun ComposeSwingTest.assertRestartScopeCount(
    expected: Int,
    content: @Composable () -> Unit,
) {
    isDebugInspectorInfoEnabled = true
    val contentKey = Any()
    setContent { key(contentKey) { content() } }

    val data =
        checkNotNull((root as JComponent).findCompositionData()) {
            "the test root publishes no composition data"
        }
    val contentGroup =
        checkNotNull(data.allGroups().firstOrNull { it.key == contentKey }) {
            "the content the assertion composed is not in the slot table"
        }
    // The key group holds exactly the content lambda, whose own scope is not one the widget opened; the
    // count is taken from what that lambda declared.
    val declared = contentGroup.compositionGroups.toList()
    check(declared.size <= 1) {
        "the key group should hold only the content lambda, but held ${declared.size}"
    }
    val scopes =
        (declared.singleOrNull() ?: contentGroup)
            .compositionGroups
            .flatMap { sequenceOf(it) + it.allGroups() }
            // A restart scope is not a group property the tooling API answers, so it is read off the
            // slot the runtime stores its own scope object in.
            .filter { group -> group.data.any { it?.javaClass?.name?.contains("RecomposeScope") == true } }
            .toList()

    if (scopes.size != expected) {
        throw AssertionError(
            "expected $expected restart scope(s), found ${scopes.size}:\n" +
                scopes.joinToString("\n") { " - key=${it.key}, sourceInfo=${it.sourceInfo}" },
        )
    }
}

private fun CompositionData.allGroups(): Sequence<CompositionGroup> =
    compositionGroups.asSequence().flatMap { group -> sequenceOf(group) + group.allGroups() }

private fun CompositionGroup.allGroups(): Sequence<CompositionGroup> =
    compositionGroups.asSequence().flatMap { group -> sequenceOf(group) + group.allGroups() }
