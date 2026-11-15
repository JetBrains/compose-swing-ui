// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.core.MotionDurationScale
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.fixtures.FixtureVisitor
import org.jetbrains.compose.swing.fixtures.WindowPropertiesFixture
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.DynamicTest
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.awt.Window
import java.lang.invoke.MethodHandles
import java.lang.reflect.Modifier
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.assertEquals

/**
 * The tests that compare a composable with the raw Swing it must match, one per alternative of the fixture.
 *
 * For each alternative the composed content and the raw component start from the fixture's initial values in
 * hosts of one size, switch to the alternative, and switch back. The two trees must match after each step. When the
 * composed content is gone, nothing may remain in its host.
 *
 * A fixture whose raw arm is a [java.awt.Window] is compared with the window its composable realizes, by the content
 * pane and menu bar, and by the properties of the windows when the fixture is a [WindowPropertiesFixture]. These need
 * a display.
 *
 * Return it from a `@TestFactory` function, so a fixture's test class adds the checks in one line.
 */
fun ComposableFixture<*, *>.rawSwingTests(): List<DynamicTest> {
    require(alternatives.isNotEmpty()) {
        "${this::class.simpleName} declares no alternatives, so no check compares its arms; add at least one"
    }
    return accept(RawSwingTests)
}

/** Runs the raw Swing comparison of [rawSwingTests] on the fixture it visits. */
private object RawSwingTests : FixtureVisitor<List<DynamicTest>> {
    override fun <P : Any, C : Component> visit(fixture: ComposableFixture<P, C>): List<DynamicTest> =
        fixture.alternatives.zip(alternativeNames(fixture)).map { (alternative, name) ->
            DynamicTest.dynamicTest(name) {
                runComposeSwingTest(effectContext = NoMotion) {
                    var mounted by mutableStateOf(true)
                    var parameters by mutableStateOf(fixture.initial)
                    val component = fixture.createRawSwingOrSkip()
                    val windowsBefore = Window.getWindows().toSet()
                    setContent { if (mounted) fixture.Content(parameters) }
                    RawHost(fixture, component, this, windowsBefore).use { raw ->
                        awaitIdle()
                        raw.settle()
                        raw.assertMatches("the initial values")

                        parameters = alternative
                        raw.apply(alternative)
                        awaitIdle()
                        raw.settle()
                        raw.assertMatches("the alternative")

                        parameters = fixture.initial
                        raw.apply(fixture.initial)
                        awaitIdle()
                        raw.settle()
                        raw.assertMatches("the initial values again")

                        mounted = false
                        awaitIdle()
                        raw.assertGone()
                    }
                }
            }
        }
}

/**
 * Builds the raw side, and skips the check when a headless JVM cannot: a raw window throws [HeadlessException] as it
 * is built, before [WindowPair] reaches its own assumption.
 */
internal fun <C : Component> ComposableFixture<*, C>.createRawSwingOrSkip(): C =
    try {
        createRawSwing()
    } catch (headless: HeadlessException) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        throw headless
    }

/** The raw side of a fixture, mounted in a host the size of the composed one; [close] unmounts it. */
internal class RawHost<P : Any, C : Component>(
    private val fixture: ComposableFixture<P, C>,
    private val component: C,
    private val test: ComposeSwingTest,
    windowsBefore: Set<Window>,
) : AutoCloseable {
    // As large as the host, so the host's visible rectangle is what it would be in a window and not clipped to nothing.
    private val hostParent = NeverShowingPanel().apply { size = test.root.size }

    /** Set when the raw side is a window, which no panel can hold. */
    val windows: WindowPair? = (component as? Window)?.let { WindowPair(test, it, windowsBefore) }

    val container: Container =
        windows?.rawRootPane
            ?: JPanel(test.root.layout).apply {
                size = test.root.size
                preferredSize = test.root.size
                add(component)
                // Without a window above it, the host bounds the focus cycle of the fixture's subtree.
                isFocusCycleRoot = true
                hostParent.add(this)
                hostParent.addNotify()
            }

    init {
        var applied = false
        try {
            fixture.updateRawSwing(component, fixture.initial)
            applied = true
        } finally {
            if (!applied) close()
        }
    }

    override fun close() = windows?.raw?.dispose() ?: hostParent.removeNotify()

    /** What the composed side holds: the test's root, or the root pane of the window it realizes. */
    val composedContainer: Container get() = windows?.composedRootPane ?: test.root

    /** Whether [component] is in what the composed side holds, including a window the composable replaced. */
    fun isComposed(component: Component): Boolean =
        windows?.isComposed(component) ?: SwingUtilities.isDescendingFrom(component, test.root)

    fun apply(parameters: P) = fixture.updateRawSwing(component, parameters)

    suspend fun settle() {
        test.awaitEventsDelivered()
        container.validate()
        // A layout pass posts component events (a viewport's resize) whose listeners repaint and invalidate; the
        // composed side's idle wait delivers them and lays out again, so the raw side must as well.
        test.awaitEventsDelivered()
        container.validate()
    }

    suspend fun assertMatches(stage: String) {
        if (windows == null) {
            try {
                test.onRoot().assertTreeMatches(container)
            } catch (failure: AssertionError) {
                throw AssertionError("The composed tree must match raw Swing for $stage. ${failure.message}", failure)
            }
        } else {
            windows.assertMatches(stage, fixture is WindowPropertiesFixture)
        }
    }

    fun assertGone() {
        windows?.let {
            it.assertGone(fixture.name)
            return
        }
        assertEquals(
            0,
            test.root.componentCount,
            "The host of ${fixture.name} must hold no components once its content is gone. " +
                "Found: ${test.root.components.map { it.javaClass.simpleName }}",
        )
    }
}

/**
 * Motion switched off, so an animation lands on its end state in the frame that starts it: the checks compare the
 * end states of the two sides, which raw Swing reaches without frames in between.
 */
internal object NoMotion : MotionDurationScale {
    override val scaleFactor: Float get() = 0f
}

/**
 * The parent of the raw host, which never shows. The host itself stays a plain
 * `JPanel`, the class the composed root extends. A text component that took the host for showing would ask for
 * its location on a screen it has none on.
 */
private class NeverShowingPanel : JPanel() {
    override fun isShowing(): Boolean = false
}

/**
 * The properties of [initial] whose value differs in [alternative], as `property=value` with the alternative's value,
 * sorted and separated by a comma and a space. Properties are the instance fields of [initial]'s own class.
 */
internal fun <P : Any> differingProperties(
    initial: P,
    alternative: P,
): String =
    initial.javaClass.declaredFields
        .asSequence()
        .filter { !Modifier.isStatic(it.modifiers) }
        .onEach { it.isAccessible = true }
        .filter { it.get(initial) != it.get(alternative) }
        .map { "${it.name}=${displayValue(it.get(alternative))}" }
        .sorted()
        .joinToString()

private fun displayValue(value: Any?): String =
    if (value is Function<*> || value?.javaClass?.name?.contains("\$\$Lambda") == true) "<lambda>" else value.toString()

/**
 * The display name of each of the [fixture]'s alternatives. Names that two alternatives share get the alternative's
 * index appended.
 */
internal fun <P : Any> alternativeNames(fixture: ComposableFixture<P, *>): List<String> {
    val names = fixture.alternatives.map { "${fixture.name}: ${differingProperties(fixture.initial, it)}" }
    return names.mapIndexed { index, name -> if (names.count { it == name } > 1) "$name #$index" else name }
}

/** A new instance equal to [value], built through the constructor of the fixture's own data class. */
internal fun <P : Any> equalCopy(value: P): P {
    val fields = value.javaClass.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
    val constructor = value.javaClass.declaredConstructors.single { it.parameterCount == fields.size }
    val copy =
        MethodHandles
            .lookup()
            .unreflectConstructor(constructor)
            .invokeWithArguments(fields.map { it.apply { isAccessible = true }.get(value) })
    return value.javaClass.cast(copy)
}
