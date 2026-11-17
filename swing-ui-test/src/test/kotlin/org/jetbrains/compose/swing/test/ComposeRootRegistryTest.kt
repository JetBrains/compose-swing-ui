// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.test

import org.jetbrains.compose.swing.core.RootForTest
import java.awt.Component
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The registry the harness learns its roots through: announced once each, and used on the event dispatch thread. */
class ComposeRootRegistryTest {
    private class StubRoot(
        override val component: Component = JPanel(),
    ) : RootForTest

    @Test
    fun aRootAnnouncedTwiceFails() = runComposeSwingTest {
        val registry = ComposeRootRegistry()
        val root = StubRoot()
        registry.onRootForTestCreated(root)

        val failure = assertFailsWith<IllegalStateException> { registry.onRootForTestCreated(root) }

        assertTrue("announced twice" in failure.message.orEmpty(), "the failure: ${failure.message}")
        assertEquals(listOf(root.component), registry.rootComponents(), "the root is registered once")
    }

    @Test
    fun aWithdrawnRootCanBeAnnouncedAgain() = runComposeSwingTest {
        val registry = ComposeRootRegistry()
        val root = StubRoot()
        registry.onRootForTestCreated(root)
        registry.onRootForTestDisposed(root)

        registry.onRootForTestCreated(root)

        assertSame(root.component, registry.rootComponents().single())
    }

    @Test
    fun everyRegistryCallFromAnotherThreadFails() {
        val registry = ComposeRootRegistry()
        val root = StubRoot()
        val calls: Map<String, () -> Unit> =
            mapOf(
                "onRootForTestCreated" to { registry.onRootForTestCreated(root) },
                "onRootForTestDisposed" to { registry.onRootForTestDisposed(root) },
                "clear" to { registry.clear() },
                "rootComponents" to { registry.rootComponents() },
                "searchedRoots" to { registry.searchedRoots(JPanel()) },
            )

        calls.forEach { (name, call) ->
            val failure = assertFailsWith<IllegalStateException>(name) { call() }
            assertTrue(
                "event dispatch thread" in failure.message.orEmpty(),
                "$name should name the thread rule: ${failure.message}",
            )
        }
    }
}
