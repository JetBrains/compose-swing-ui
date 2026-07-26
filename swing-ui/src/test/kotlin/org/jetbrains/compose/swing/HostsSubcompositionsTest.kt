package org.jetbrains.compose.swing

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

            // A descendant component whose own setContent (no injected recomposer) must resolve the
            // surrounding composition through the COMPOSITION_KEY stamp left on the host panel by the
            // opt-in.
            val child = JPanel().also { hostPanel.add(it) }

            var observed: String? = null
            val handle =
                child.setContent {
                    // Read the parent-provided local; if the child nested into the surrounding
                    // composition it sees PROVIDED, otherwise the local's UNPROVIDED default.
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

            // While present, the stamp is published so descendants can discover it.
            assertEquals(
                true,
                hostPanel.getClientProperty(COMPOSITION_KEY_FOR_TEST) != null,
                "an opted-in host node must publish the COMPOSITION_KEY stamp while in the composition.",
            )

            // Remove the host node from the composition: its release must clear the stamp so a recycled
            // component cannot leak a stale parent context to a later setContent walk.
            present = false
            awaitIdle()

            assertNull(
                hostPanel.getClientProperty(COMPOSITION_KEY_FOR_TEST),
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
                hostPanel.getClientProperty(COMPOSITION_KEY_FOR_TEST),
                "a node that does not opt in must publish no COMPOSITION_KEY stamp.",
            )

            hosting = true
            awaitIdle()
            assertNotNull(
                hostPanel.getClientProperty(COMPOSITION_KEY_FOR_TEST),
                "opting in must publish the stamp so descendants can discover the composition.",
            )

            hosting = false
            awaitIdle()
            assertNull(
                hostPanel.getClientProperty(COMPOSITION_KEY_FOR_TEST),
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
                hostPanel.getClientProperty(COMPOSITION_KEY_FOR_TEST),
                "a node that does not opt in must publish no COMPOSITION_KEY stamp.",
            )

            hosting = true
            awaitIdle()
            assertNotNull(
                hostPanel.getClientProperty(COMPOSITION_KEY_FOR_TEST),
                "opting in must publish the stamp so descendants can discover the composition.",
            )

            hosting = false
            awaitIdle()
            assertNull(
                hostPanel.getClientProperty(COMPOSITION_KEY_FOR_TEST),
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

        // The client-property key the library publishes for the descendant tree-walk. Kept internal in
        // production; the literal is duplicated here only to assert observable Swing state (the stamp
        // being present then cleared), never a private field of the library.
        const val COMPOSITION_KEY_FOR_TEST: String = "org.jetbrains.compose.swing.composition"
    }
}
