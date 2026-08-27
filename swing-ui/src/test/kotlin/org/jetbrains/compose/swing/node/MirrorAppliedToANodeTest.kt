package org.jetbrains.compose.swing.node

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MirrorAppliedToANodeTest {
    @Test
    fun aMirrorNoNodeHasAppliedFailsAtTheFirstChange() {
        val mirror = MirrorState(false)

        val failure = assertFailsWith<IllegalStateException> { mirror.report(true) {} }

        assertTrue(
            "applyMirror()" in failure.message.orEmpty(),
            "the failure must name what applies the mirror to a node, and said: ${failure.message}",
        )
    }

    @Test
    fun aMirrorNoNodeHasAppliedStillMirrorsAnObservedChange() {
        val mirror = MirrorState(false)

        assertTrue(mirror.observed(true), "an observed move must be mirrored without a node having applied it")
        assertTrue(mirror.value, "an observed move must be mirrored without a node having applied it")
    }
}
