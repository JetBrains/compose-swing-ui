package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.key
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup
import org.jetbrains.compose.swing.test.ComposeSwingTest

/**
 * Composes [content] and asserts that it opened exactly [expected] restart scopes - the groups a
 * recomposition can restart at. A composable inlined into its caller opens none of its own, so the count
 * is what a call site pays to be recomposable.
 */
public fun ComposeSwingTest.assertRestartScopeCount(
    expected: Int,
    content: @Composable () -> Unit,
) {
    val contentKey = Any()
    var data: CompositionData? = null
    setContent {
        data = currentComposer.compositionData
        key(contentKey) { content() }
    }

    val contentGroup =
        checkNotNull(data?.allGroups()?.firstOrNull { it.key == contentKey }) {
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
                scopes.joinToString("\n") { " - key=${it.key}" },
        )
    }
}

private fun CompositionData.allGroups(): Sequence<CompositionGroup> =
    compositionGroups.asSequence().flatMap { group -> sequenceOf(group) + group.allGroups() }

private fun CompositionGroup.allGroups(): Sequence<CompositionGroup> =
    compositionGroups.asSequence().flatMap { group -> sequenceOf(group) + group.allGroups() }
