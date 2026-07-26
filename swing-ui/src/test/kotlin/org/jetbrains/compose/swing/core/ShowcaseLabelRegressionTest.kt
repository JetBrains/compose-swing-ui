package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * A [BorderPanel] with a stable NORTH title and a stable SOUTH status bar, and a CENTER body that
 * comes and goes. The panel composes its slots NORTH, CENTER, SOUTH, so the body occupies composition
 * index 1 - *between* its two stable siblings - and each toggle shifts the index the status bar sits
 * at. The applier addresses the AWT component array by that index, so this is the arrangement in
 * which an index the applier gets wrong takes out a sibling instead of the body.
 *
 * What the tests read back is therefore the siblings, not the body: same region, same instance, still
 * laid out, after the churn.
 */
class ShowcaseLabelRegressionTest {
    private companion object {
        const val TITLE = "Compose Swing UI - Component Showcase"
        const val STATUS = "Ready"
        const val BODY = "Run"
        const val TOGGLE_CYCLES = 5
    }

    @Test
    fun titleInNorthSurvivesStructuralChurnInCenterBody() = runComposeSwingTest {
        var showBody by mutableStateOf(false)
        var clicks by mutableIntStateOf(0)
        setContent {
            BorderPanel {
                north { Label(text = TITLE) }
                if (showBody) {
                    center { Button(text = BODY, onClick = { clicks++ }) }
                }
                south { Label(text = STATUS) }
            }
        }

        val title = onNodeWithText(TITLE)
        val status = onNodeWithText(STATUS)
        val body = onNodeWithText(BODY)

        // Baseline: title and status bar present in their regions; body absent.
        title.assertLayoutConstraint(BorderLayout.NORTH).assertIsDisplayed()
        status.assertLayoutConstraint(BorderLayout.SOUTH)
        body.assertDoesNotExist()

        // The live sibling instances, so the churn can be shown to preserve identity.
        val titleBefore = title.fetch()
        val statusBefore = status.fetch()

        showBody = true
        awaitIdle()
        body.assertLayoutConstraint(BorderLayout.CENTER)

        showBody = false
        awaitIdle()

        // The body is gone; the unrelated title and status bar keep their regions, their bounds and
        // their instances.
        body.assertDoesNotExist()
        title.assertLayoutConstraint(BorderLayout.NORTH).assertIsDisplayed()
        status.assertLayoutConstraint(BorderLayout.SOUTH)
        assertSame(titleBefore, title.fetch(), "NORTH title instance changed across churn")
        assertSame(statusBefore, status.fetch(), "SOUTH status instance changed across churn")
    }

    @Test
    fun repeatedBodyTogglesKeepTitleAndStatusStable() = runComposeSwingTest {
        var showBody by mutableStateOf(false)
        setContent {
            BorderPanel {
                north { Label(text = TITLE) }
                if (showBody) {
                    center { Label(text = BODY) }
                }
                south { Label(text = STATUS) }
            }
        }

        val title = onNodeWithText(TITLE)
        val status = onNodeWithText(STATUS)
        val body = onNodeWithText(BODY)

        val titleBefore = title.fetch()
        val statusBefore = status.fetch()

        repeat(TOGGLE_CYCLES) {
            showBody = true
            awaitIdle()
            body.assertLayoutConstraint(BorderLayout.CENTER)

            showBody = false
            awaitIdle()
            body.assertDoesNotExist()
        }

        title.assertLayoutConstraint(BorderLayout.NORTH).assertIsDisplayed()
        status.assertLayoutConstraint(BorderLayout.SOUTH)
        assertSame(titleBefore, title.fetch(), "NORTH title instance changed across churn")
        assertSame(statusBefore, status.fetch(), "SOUTH status instance changed across churn")
    }
}
