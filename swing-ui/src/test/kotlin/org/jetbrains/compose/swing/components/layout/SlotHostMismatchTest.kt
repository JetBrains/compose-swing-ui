// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.rememberMirrorState
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.hasTestTag
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTabbedPane
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A child carrying one scope's placement modifier composed under a host built by a different scope: both
 * hosts hold their children in named regions of their own, so the applier's generic region check does not
 * catch the mismatch, and it surfaces only where the builder installs the child through its own host's
 * Swing setter. It is refused there by name, naming the builder and the host it actually arrived at,
 * rather than as a bare `ClassCastException`.
 */
class SlotHostMismatchTest {
    @Test
    fun aScrollPanePlacementUnderASplitPaneIsRefusedByName() = runComposeSwingTest {
        val misplaced = with(ScrollPaneScopeImpl()) { SwingModifier.rowHeader() }

        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    SplitPane {
                        Label(text = "misplaced", modifier = misplaced)
                        Label(text = "trailing", modifier = SwingModifier.second())
                    }
                }
            }

        val message = failure.message.orEmpty()
        assertTrue(
            "SwingModifier.rowHeader()" in message,
            "the refusal should name the builder that would place the child: $message",
        )
        assertTrue("JScrollPane" in message, "the refusal should name the host type the builder expects: $message")
        assertTrue("JSplitPane" in message, "the refusal should name the host the child actually arrived at: $message")
    }

    @Test
    fun aSplitPanePlacementUnderAScrollPaneIsRefusedByName() = runComposeSwingTest {
        val misplaced = with(SplitPaneScopeImpl) { SwingModifier.first() }

        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    ScrollPane {
                        Label(text = "misplaced", modifier = misplaced)
                    }
                }
            }

        val message = failure.message.orEmpty()
        assertTrue(
            "SwingModifier.first()" in message,
            "the refusal should name the builder that would place the child: $message",
        )
        assertTrue("JSplitPane" in message, "the refusal should name the host type the builder expects: $message")
        assertTrue("JScrollPane" in message, "the refusal should name the host the child actually arrived at: $message")
    }

    @Test
    fun aTabPlacementUnderASplitPaneIsRefusedByName() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    // A tab's placement modifier needs no live composition to be built - it is a plain
                    // SwingModifier value - but TabbedPaneScopeImpl still takes a mirror, so this builds
                    // one just for the scope.
                    val scope = TabbedPaneScopeImpl(rememberMirrorState(0))
                    val misplaced = with(scope) { SwingModifier.tab("Tab") }
                    SplitPane {
                        Label(text = "misplaced", modifier = misplaced)
                        Label(text = "trailing", modifier = SwingModifier.second())
                    }
                }
            }

        val message = failure.message.orEmpty()
        assertTrue(TAB_SLOT_NAME in message, "the refusal should name the builder that would place the child: $message")
        assertTrue("JTabbedPane" in message, "the refusal should name the host type the builder expects: $message")
        assertTrue("JSplitPane" in message, "the refusal should name the host the child actually arrived at: $message")
    }

    @Test
    fun aTabDeclaredThroughAnotherPanesScopeIsInstalledInThePaneItIsComposedIn() = runComposeSwingTest {
        setContent {
            lateinit var first: TabbedPaneScope
            Panel {
                TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                    first = this
                    Label(text = "first page", modifier = SwingModifier.tab("First"))
                }
                TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}, modifier = SwingModifier.testTag("second")) {
                    Label(text = "stray page", modifier = with(first) { SwingModifier.tab("Stray") })
                }
            }
        }

        onAllNodesOfType<JTabbedPane>()
            .filterToOne(hasTestTag("second"))
            .assertProperty(listOf("Stray"), "the stray tab is installed in the pane it is composed in") {
                (0 until tabCount).map { getTitleAt(it) }
            }
    }
}
