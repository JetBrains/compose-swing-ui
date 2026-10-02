package org.jetbrains.compose.swing.core

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class LifecyclePublicationOwnershipTest {
    @TestFactory
    fun aHostsOwnerOutlivesTheContentItHosted(): List<DynamicTest> = listOf(
        survives(
            "an unchanged owner across two mounts",
            mounts = 2,
            afterMount = { _, _ -> },
            expected = { original, _ -> original },
        ),
        survives(
            "an owner the caller replaced",
            afterMount = { host, replacement -> host.setLifecycleOwner(replacement) },
            expected = { _, replacement -> replacement },
        ),
        // Guards that a clear the caller made while mounted is not undone by the content's disposal.
        survives(
            "an owner the caller cleared",
            afterMount = { host, _ -> host.setLifecycleOwner(null) },
            expected = { _, _ -> null },
        ),
    )

    /** Guards that disposing a child's content leaves the owner an ancestor carries in place. */
    @Test
    fun ancestorOwnerSurvivesAndChildPublicationIsRemoved() = runSwingTest {
        val parent = JPanel()
        val host = JPanel().also(parent::add)
        val owner = HostOwner()
        parent.setLifecycleOwner(owner)
        mountAndDispose(host, owner)
        assertSame(owner, parent.findLifecycleOwner())
        parent.remove(host)
        assertNull(host.findLifecycleOwner())
    }

    private fun survives(
        name: String,
        mounts: Int = 1,
        afterMount: (host: JPanel, replacement: LifecycleOwner) -> Unit,
        expected: (original: LifecycleOwner, replacement: LifecycleOwner) -> LifecycleOwner?,
    ) = DynamicTest.dynamicTest(name) {
        runSwingTest {
            val host = JPanel()
            val original = HostOwner()
            val replacement = HostOwner()
            host.setLifecycleOwner(original)
            repeat(mounts) { mountAndDispose(host, original) { afterMount(host, replacement) } }
            assertSame(expected(original, replacement), host.findLifecycleOwner())
        }
    }

    private fun mountAndDispose(
        host: JPanel,
        expected: LifecycleOwner,
        mounted: () -> Unit = {},
    ) {
        val listeners = host.hierarchyListeners.toList()
        val recomposer = SwingRecomposer.create(JPanel())
        try {
            var read: LifecycleOwner? = null
            val handle = host.setContent(parent = recomposer.compositionContext) { read = LocalLifecycleOwner.current }
            assertSame(expected, read)
            mounted()
            handle.dispose()
            assertEquals(listeners, host.hierarchyListeners.toList())
        } finally {
            recomposer.dispose()
        }
    }

    private class HostOwner : LifecycleOwner {
        override val lifecycle: Lifecycle =
            LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.STARTED }
    }
}
