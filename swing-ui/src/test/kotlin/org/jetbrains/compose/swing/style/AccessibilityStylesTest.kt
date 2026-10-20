package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test

/** The style setters of what assistive technologies announce for a component. */
class AccessibilityStylesTest {
    @Test
    fun eachSetterWritesRewritesAndRestores() = runComposeSwingTest {
        assertEachSetterWritesRewritesAndRestores(
            listOf(
                setterCase("accessibleName", "first", "second", { it.accessibleContext.accessibleName }) {
                    accessibleName(it)
                },
                setterCase("accessibleDescription", "first", "second", { it.accessibleContext.accessibleDescription }) {
                    accessibleDescription(it)
                },
            ),
        )
    }
}
