// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.onChild
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** What a component holds before a declaration writes it, and the check that withdrawing it gave that back. */
class Restoration<S>(
    /** Reads what the component holds before any declaration writes it. */
    val snapshot: (JComponent) -> S,
    /** Checks the component against what [snapshot] read once the declaration is withdrawn. */
    val assertRestored: (JComponent, S) -> Unit,
)

/** Where a [DirectWrite] case finds its component and the bean whose listener throws. */
class Target(
    /** Finds the mounted component through the harness once it is composed. */
    val find: ComposeSwingTest.() -> JComponent = { onRoot().onChild().fetch<JComponent>() },
    /** The bean whose property listener throws, derived from the mounted component. */
    val listenerOn: (JComponent) -> JComponent = { it },
)

/** One declaration site that writes its component during the apply, named [label] in the test report. */
class DirectWrite<S>(
    private val label: String,
    /** The bean property whose listener throws. */
    val failingProperty: String,
    /** Shows the component, carrying the given modifier. The component is built by the node's own factory. */
    val mount: @Composable (SwingModifier) -> Unit,
    /**
     * The declarations the component carries in turn, each with the check of what it wrote. A site that
     * writes again on an update has three; a site that writes once on install has one.
     */
    val writes: List<Pair<SwingModifier, (JComponent) -> Unit>>,
    /** What withdrawing the declarations has to give back. */
    val restoration: Restoration<S>,
    /** Where the component and the throwing bean are found. */
    val target: Target = Target(),
) {
    override fun toString(): String = label
}

/**
 * Mounts [case], has the listener on its failing property throw during each write, and asserts the throw is
 * reported by the apply, every write still lands, and withdrawing the declarations restores the component.
 */
suspend fun <S> ComposeSwingTest.assertAPropertyListenerThatThrowsIsReportedAndALaterChangeStillApplies(
    case: DirectWrite<S>,
) {
    val failure = IllegalStateException("the listener throws")
    val listener = mockk<PropertyChangeListener>(relaxed = true)
    var declaration by mutableStateOf<SwingModifier>(SwingModifier)
    setContent { case.mount(declaration) }
    awaitIdle()
    val component = case.target.find(this)
    val original = case.restoration.snapshot(component)
    case.target.listenerOn(component).addPropertyChangeListener(case.failingProperty, listener)

    every { listener.propertyChange(any()) } throws failure
    val (declared, assertDeclared) = case.writes.first()
    declaration = declared
    assertSame(failure, assertFailsWith<IllegalStateException> { awaitIdle() })
    verify(exactly = 1) { listener.propertyChange(any()) }
    assertDeclared(component)

    case.writes.getOrNull(1)?.let { (changed, assertChanged) ->
        declaration = changed
        assertSame(failure, assertFailsWith<IllegalStateException> { awaitIdle() })
        verify(exactly = 2) { listener.propertyChange(any()) }
        assertChanged(component)
    }

    every { listener.propertyChange(any()) } just Runs
    case.writes.getOrNull(2)?.let { (changedAgain, assertChangedAgain) ->
        declaration = changedAgain
        awaitIdle()
        assertChangedAgain(component)
    }

    declaration = SwingModifier
    awaitIdle()
    case.restoration.assertRestored(component, original)
}
