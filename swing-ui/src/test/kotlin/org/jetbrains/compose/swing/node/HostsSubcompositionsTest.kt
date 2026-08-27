package org.jetbrains.compose.swing.node

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.core.COMPOSITION_KEY
import org.jetbrains.compose.swing.core.get
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Behavioral coverage for the `hostsSubcompositions = true` opt-in on [SwingNode].
 *
 * The scenario mirrors the real use case: a custom component built via [SwingNode] returns a
 * [java.awt.Container] whose OWN internal logic calls `setContent` on one of its children. That child
 * `setContent` carries no injected recomposer, so it must discover the surrounding composition by
 * walking up the Swing tree to the [SwingNode] component that opted in and stamped its composition
 * context there. We prove the nesting by reading a [androidx.compose.runtime.CompositionLocal] provided
 * at the top of the surrounding composition from inside the child's content, and prove the stamp is
 * cleared once the host node leaves the composition.
 */
class HostsSubcompositionsTest {
    @Test
    fun childSetContentNestsIntoStampedHostComposition() {
        lateinit var hostPanel: JPanel

        runComposeSwingTest {
            setContent {
                CompositionLocalProvider(LocalGreeting provides PROVIDED) {
                    SwingNode(
                        factory = { JPanel().also { hostPanel = it } },
                        hostsSubcompositions = true,
                    ) {}
                }
            }

            val child = JPanel().also { hostPanel.add(it) }

            var observed: String? = null
            val handle =
                child.setContent {
                    val greeting by remember { mutableStateOf("") }
                    observed = LocalGreeting.current + greeting
                }
            awaitIdle()

            assertEquals(
                PROVIDED,
                observed,
                "the nested child content must observe the parent-provided CompositionLocal, proving it " +
                    "nested into the surrounding composition through the stamped COMPOSITION_KEY.",
            )

            handle.dispose()
        }
    }

    @Test
    fun stampIsClearedWhenHostNodeLeavesComposition() {
        lateinit var hostPanel: JPanel

        runComposeSwingTest {
            var present by mutableStateOf(true)
            setContent {
                if (present) {
                    SwingNode(
                        factory = { JPanel().also { hostPanel = it } },
                        hostsSubcompositions = true,
                    ) {}
                }
            }

            assertEquals(
                true,
                hostPanel[COMPOSITION_KEY] != null,
                "an opted-in host node must publish the COMPOSITION_KEY stamp while in the composition.",
            )

            // Remove the host node from the composition: its release must clear the stamp so a recycled
            // component cannot leak a stale parent context to a later setContent walk.
            present = false
            awaitIdle()

            assertNull(
                hostPanel[COMPOSITION_KEY],
                "releasing the host node must clear the COMPOSITION_KEY stamp.",
            )
        }
    }

    @Test
    fun stampFollowsTheOptInStateDrivingIt() {
        lateinit var hostPanel: JPanel

        runComposeSwingTest {
            var hosting by mutableStateOf(false)
            setContent {
                SwingNode(
                    factory = { JPanel().also { hostPanel = it } },
                    hostsSubcompositions = hosting,
                ) {}
            }

            assertNull(
                hostPanel[COMPOSITION_KEY],
                "a node that does not opt in must publish no COMPOSITION_KEY stamp.",
            )

            hosting = true
            awaitIdle()
            assertNotNull(
                hostPanel[COMPOSITION_KEY],
                "opting in must publish the stamp so descendants can discover the composition.",
            )

            hosting = false
            awaitIdle()
            assertNull(
                hostPanel[COMPOSITION_KEY],
                "opting back out must clear the stamp so a later setContent walk finds no host here.",
            )
        }
    }

    @Test
    fun theLeafOverloadStampFollowsTheOptInStateDrivingIt() {
        lateinit var hostPanel: JPanel

        runComposeSwingTest {
            var hosting by mutableStateOf(false)
            setContent {
                SwingNode(
                    factory = { JPanel().also { hostPanel = it } },
                    hostsSubcompositions = hosting,
                )
            }

            assertNull(
                hostPanel[COMPOSITION_KEY],
                "a node that does not opt in must publish no COMPOSITION_KEY stamp.",
            )

            hosting = true
            awaitIdle()
            assertNotNull(
                hostPanel[COMPOSITION_KEY],
                "opting in must publish the stamp so descendants can discover the composition.",
            )

            hosting = false
            awaitIdle()
            assertNull(
                hostPanel[COMPOSITION_KEY],
                "opting back out must clear the stamp so a later setContent walk finds no host here.",
            )
        }
    }

    private companion object {
        const val PROVIDED: String = "from-parent"
        const val UNPROVIDED: String = "<unprovided>"

        // Provided at the top of the surrounding composition; the nested child reads it to prove it
        // joined that composition rather than starting a detached one (which would see UNPROVIDED).
        val LocalGreeting = compositionLocalOf { UNPROVIDED }
    }
}
