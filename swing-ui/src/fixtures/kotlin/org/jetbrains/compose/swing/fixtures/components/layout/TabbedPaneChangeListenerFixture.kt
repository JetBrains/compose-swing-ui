// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.fixtures.components.DelegatingChangeListener
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import javax.swing.JTabbedPane
import javax.swing.event.ChangeListener

/** The parameters [TabbedPaneChangeListenerFixture] varies: the change listener only. */
public data class TabbedPaneChangeListenerParameters(
    val changeListener: ChangeListener,
)

/**
 * A [TabbedPane] taking a [ChangeListener] instead of an `onSelectedIndexChange` callback, against a raw
 * [JTabbedPane] in the same state as [TabbedPaneFixture.initial] notifying the same listener.
 */
public object TabbedPaneChangeListenerFixture :
    ComposableFixture<TabbedPaneChangeListenerParameters, JTabbedPane>(
        initial = TabbedPaneChangeListenerParameters(changeListener = {}),
    ) {
    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<TabbedPaneChangeListenerParameters> =
        listOf(
            initial.copy(changeListener = {}),
        )

    @Composable
    override fun Content(parameters: TabbedPaneChangeListenerParameters): Unit = Content(parameters.changeListener)

    /** Composes a [TabbedPane] notifying [changeListener], with the shared values of [TabbedPaneFixture]. */
    @Composable
    public fun Content(changeListener: ChangeListener) {
        val shared = TabbedPaneFixture.initial
        TabbedPane(
            selectedIndex = shared.selectedIndex,
            changeListener = changeListener,
            style =
                SwingStyle {
                    enabled(shared.enabled)
                    foreground(shared.color)
                },
            tabPlacement = shared.tabPlacement,
            tabLayoutPolicy = shared.tabLayoutPolicy,
        ) {
            Label(shared.firstPageText, modifier = SwingModifier.tab("First"))
            Label("Second", modifier = SwingModifier.tab("Second"))
        }
    }

    /** Builds a [JTabbedPane] in the shared initial state of [TabbedPaneFixture]. */
    override fun createRawSwing(): JTabbedPane =
        TabbedPaneFixture.createRawSwing().also {
            TabbedPaneFixture.applyShared(it, TabbedPaneFixture.initial)
        }

    /** Keeps the one listener this fixture installed on [component] and has it notify the listener of [parameters]. */
    override fun updateRawSwing(
        component: JTabbedPane,
        parameters: TabbedPaneChangeListenerParameters,
    ) {
        val adapter =
            component.changeListeners.firstNotNullOfOrNull { it as? DelegatingChangeListener }
                ?: DelegatingChangeListener(parameters.changeListener).also { component.addChangeListener(it) }
        adapter.delegate = parameters.changeListener
    }
}
