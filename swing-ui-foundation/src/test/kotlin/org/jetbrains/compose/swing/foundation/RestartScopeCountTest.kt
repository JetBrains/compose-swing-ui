package org.jetbrains.compose.swing.foundation

import org.jetbrains.compose.swing.assertRestartScopeCount
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.EmptyBoxMeasurePolicy
import org.jetbrains.compose.swing.foundation.layout.Layout
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test

/**
 * A Foundation composable with no content declares one restart scope: its own. The node it renders is
 * inlined into it, so no composable opens a second.
 */
class RestartScopeCountTest {
    @Test
    fun aCanvasOpensOneRestartScope() =
        runComposeSwingTest {
            assertRestartScopeCount(1) { Canvas { } }
        }

    @Test
    fun aLayoutWithoutContentOpensOneRestartScope() =
        runComposeSwingTest {
            assertRestartScopeCount(1) { Layout(measurePolicy = EmptyBoxMeasurePolicy) }
        }

    // Layout is not inline, so a Box without content pays Layout's scope on top of its own.
    @Test
    fun aBoxWithoutContentOpensItsScopeAndLayouts() =
        runComposeSwingTest {
            assertRestartScopeCount(2) { Box() }
        }

    // A container's content is a composable lambda the caller wrote, and a lambda of its own is a scope of
    // its own. The container adds none on top of it.
    @Test
    fun aLayoutOpensNoScopeBeyondItsContent() =
        runComposeSwingTest {
            assertRestartScopeCount(2) { Layout(measurePolicy = EmptyBoxMeasurePolicy) { } }
        }

    @Test
    fun aBoxOpensNoScopeBeyondItsContent() =
        runComposeSwingTest {
            assertRestartScopeCount(2) { Box { } }
        }

    @Test
    fun aRowOpensNoScopeBeyondItsContent() =
        runComposeSwingTest {
            assertRestartScopeCount(2) { Row { } }
        }

    @Test
    fun aColumnOpensNoScopeBeyondItsContent() =
        runComposeSwingTest {
            assertRestartScopeCount(2) { Column { } }
        }
}
