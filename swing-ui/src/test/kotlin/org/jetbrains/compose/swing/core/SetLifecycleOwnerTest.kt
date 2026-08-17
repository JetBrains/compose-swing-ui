package org.jetbrains.compose.swing.core

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Behavioral tests for an owner a host sets on the Swing tree.
 *
 * A composition root resolves the owner the composition it joins carries, then one published at or above
 * its container, and only where neither answers does it mint one following that container. Setting one is
 * how a host that drives its content's lifecycle itself - a test harness among them - decides what its
 * content reads without the library minting a rival.
 */
class SetLifecycleOwnerTest {
    @Test
    fun aRootUnderAPublishedOwnerReadsItRatherThanMintingOne() = runSwingTest {
        val runtime = SwingRecomposer.create(JPanel())
        try {
            val host = JPanel()
            val published = HostOwner()
            host.setLifecycleOwner(published)
            val island = JPanel().also { host.add(it) }

            var read: LifecycleOwner? = null
            val handle =
                island.setContent(
                    parent = runtime.compositionContext,
                ) { read = LocalLifecycleOwner.current }

            assertSame(
                published,
                read,
                "a root standing under an owner a host set must read it rather than minting one of its own",
            )
            handle.dispose()
        } finally {
            runtime.dispose()
        }
    }

    @Test
    fun aRootUnderNoPublishedOwnerMintsOne() = runSwingTest {
        val runtime = SwingRecomposer.create(JPanel())
        try {
            val host = JPanel()
            val published = HostOwner()
            host.setLifecycleOwner(published)
            host.setLifecycleOwner(null)
            val island = JPanel().also { host.add(it) }

            var read: LifecycleOwner? = null
            val handle =
                island.setContent(
                    parent = runtime.compositionContext,
                ) { read = LocalLifecycleOwner.current }

            assertNotSame(
                published,
                read,
                "a cleared owner must leave a root minting one of its own, so a host's teardown gives the " +
                    "tree back the way it found it",
            )
            handle.dispose()
        } finally {
            runtime.dispose()
        }
    }

    /** A [LifecycleOwner] standing in for one a host drives itself. */
    private class HostOwner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.STARTED }

        override val lifecycle: Lifecycle get() = registry
    }
}
