package org.jetbrains.compose.swing.style

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.style.Buttons
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.components.style.Panels
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.plaf.ColorUIResource
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class NestedStyleTest {
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun nestedBlocksCannotProvideStyles(sameKey: Boolean) = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    Panel(style = {
                        provideNestedStyle(Panels) {
                            if (sameKey) {
                                provideNestedStyle(Panels) { background(Color.RED) }
                            } else {
                                provideNestedStyle(Labels) { foreground(Color.RED) }
                            }
                        }
                    }) { Panel { Label("nested") } }
                }
            }
        val key = if (sameKey) Panels else Labels
        assertContains(failure.message.orEmpty(), "provideNestedStyle($key)")
        assertContains(failure.message.orEmpty(), "component's own style instead")
    }

    @Test
    fun aStyleProvidedForAllComponentsReachesTheDescendantsButNotTheProvider() = runComposeSwingTest {
        setContent {
            Panel(
                modifier = SwingModifier.testTag("provider"),
                style = {
                    provideNestedStyle(AllComponents) { enabled(false) }
                    provideNestedStyle(Panels) { background(Color.BLUE) }
                },
            ) {
                Label("label")
                Label("own", style = { enabled(true) })
                Button("button", onClick = {})
                Panel(
                    SwingModifier.testTag("nested"),
                    style = { provideNestedStyle(Labels) { foreground(Color.RED) } },
                ) { Label("deep") }
            }
        }

        onNodeWithText("label").assertProperty(false, "a Label applies AllComponents") { isEnabled }
        onNodeWithText("own").assertProperty(true) { isEnabled }
        onNodeWithTag("nested").assertProperty(Color.BLUE) { background }
        onNodeWithText("deep").assertProperty(Color.RED) { foreground }
        onNodeWithText("button").assertProperty(false, "and so does a Button") { isEnabled }
        onNodeWithTag("nested").assertProperty(false, "and a Panel below the provider") { isEnabled }
        onNodeWithText("deep").assertProperty(false, "the style reaches every depth") { isEnabled }
        onNodeWithTag("provider").assertProperty(true, "the provider does not apply its own nested style") { isEnabled }
    }

    @Test
    fun forTheSameKeyTheNearerProviderWins() = runComposeSwingTest {
        setContent {
            Panel(style = { provideNestedStyle(Labels) { enabled(false) } }) {
                Panel(style = { provideNestedStyle(Labels) { enabled(true) } }) { Label("enabled nearer") }
            }
            Panel(style = { provideNestedStyle(Labels) { enabled(true) } }) {
                Panel(style = { provideNestedStyle(Labels) { enabled(false) } }) { Label("disabled nearer") }
            }
        }

        onNodeWithText("enabled nearer").assertProperty(true) { isEnabled }
        onNodeWithText("disabled nearer").assertProperty(false, "the order of the values plays no part") { isEnabled }
    }

    @Test
    fun aFartherKindStyleWinsOverANearerAllComponentsStyle() = runComposeSwingTest {
        setContent {
            Panel(style = { provideNestedStyle(Buttons) { enabled(true) } }) {
                Panel(style = { provideNestedStyle(AllComponents) { enabled(false) } }) {
                    Button("button", onClick = {})
                    Label("label")
                }
            }
        }

        onNodeWithText("button").assertProperty(true, "a Button applies its kind key after AllComponents") { isEnabled }
        onNodeWithText("label").assertProperty(false, "a component the kind style misses stays disabled") { isEnabled }
    }

    @Test
    fun aProvidedStyleStaysInItsSubtreeAndAChangeReachesUnderANearerProvider() = runComposeSwingTest {
        var outer by mutableStateOf(Color.RED)
        val own = JLabel()
        setContent {
            Panel(style = { provideNestedStyle(AllComponents) { background(outer) } }) {
                Label("outer")
                Panel(style = { provideNestedStyle(Labels) { enabled(false) } }) { Label("inner") }
            }
            Panel(style = { provideNestedStyle(AllComponents) { enabled(false) } }) { Label("sibling") }
            Label("outside")
        }
        val inner = onNodeWithText("inner")
        val sibling = onNodeWithText("sibling")
        val outside = onNodeWithText("outside")

        for (expected in listOf(Color.RED, Color.GREEN)) {
            outer = expected
            awaitIdle()

            onNodeWithText("outer").assertProperty(expected, "$expected: the provider's subtree") { background }
            inner.assertProperty(expected, "$expected: the value reaches under a nearer provider") { background }
            inner.assertProperty(false, "$expected: beside what the nearer provider sets") { isEnabled }
            sibling.assertProperty(own.background, "$expected: the style stays out of a sibling subtree") { background }
            sibling.assertProperty(false, "$expected: the sibling keeps its own provider's style") { isEnabled }
            outside.assertProperty(own.background, "$expected: a component under no provider") { background }
            outside.assertProperty(true, "$expected: a component under no provider") { isEnabled }
        }
    }

    @Test
    fun aComponentsOwnValueAndAProvidedOneTradePlacesInBothDirections() = runComposeSwingTest {
        var provided by mutableStateOf<Color?>(Color.RED)
        var own by mutableStateOf<Color?>(Color.BLUE)
        setContent {
            Panel(style = { provided?.let { provideNestedStyle(AllComponents) { background(it) } } }) {
                SwingNode(
                    factory = { JLabel("styled") },
                    modifier = SwingModifier.styleable({ own?.let { background(it) } }, ProvidedLeafKind),
                    update = { set(Color.YELLOW) { background = it } },
                )
            }
        }
        val label = onNodeWithText("styled")
        label.assertProperty(Color.BLUE, "the own value wins") { background }

        own = null
        awaitIdle()
        label.assertProperty(Color.RED, "the own value leaving shows the provided one") { background }

        own = Color.BLUE
        awaitIdle()
        label.assertProperty(Color.BLUE, "the own value returning wins again") { background }

        provided = Color.GREEN
        awaitIdle()
        label.assertProperty(Color.BLUE, "a changed provided value leaves the own value standing") { background }

        own = null
        awaitIdle()
        label.assertProperty(Color.GREEN, "the provided value overwrites what the update block set") { background }

        provided = null
        awaitIdle()
        label.assertProperty(Color.YELLOW, "both leaving give back what the update block set") { background }
    }

    @Test
    fun aProvidedValueThatLeavesGivesBackTheComponentsOwnUIResource() = runComposeSwingTest {
        var provided by mutableStateOf<Color?>(Color.RED)
        val resource = ColorUIResource(Color.ORANGE)
        setContent {
            Panel(style = { provided?.let { provideNestedStyle(AllComponents) { background(it) } } }) {
                SwingNode(
                    factory = { JLabel("styled").apply { background = resource } },
                    modifier = SwingModifier.styleable(SwingStyle, ProvidedLeafKind),
                )
            }
        }
        val label = onNodeWithText("styled")
        label.assertProperty(Color.RED) { background }

        provided = null
        awaitIdle()
        label.assertProperty(true, "the label holds its own look-and-feel color again") { background === resource }
    }

    @Test
    fun aProvidedStyleReachesAnInsertedAReorderedAndAReactivatedDescendant() = runComposeSwingTest {
        var showInserted by mutableStateOf(false)
        var items by mutableStateOf(listOf("A", "B"))
        var active by mutableStateOf(true)
        setContent {
            Panel(style = { provideNestedStyle(AllComponents) { background(Color.MAGENTA) } }) {
                Label("first")
                if (showInserted) Label("inserted")
                for (item in items) key(item) { Label(item) }
                ReusableContentHost(active = active) { Label("parkable") }
            }
        }

        fun assertAllProvided(
            count: Int,
            reason: String,
        ) {
            val labels = onAllNodesOfType<JLabel>()
            labels.assertCountEquals(count)
            repeat(count) { labels[it].assertProperty(Color.MAGENTA, reason) { background } }
        }
        assertAllProvided(4, "every descendant takes the provided value")

        showInserted = true
        awaitIdle()
        onAllNodesOfType<JLabel>().assertCountEquals(5)
        listOf("first", "inserted", "A", "B", "parkable").forEachIndexed { index, expected ->
            onAllNodesOfType<JLabel>()[index].assertProperty(expected) { text }
        }
        onNodeWithText("inserted").assertProperty(Color.MAGENTA, "an inserted descendant") { background }
        val labelA = onNodeWithText("A").fetch<JLabel>()
        val labelB = onNodeWithText("B").fetch<JLabel>()

        items = listOf("B", "A")
        awaitIdle()
        onAllNodesOfType<JLabel>().assertCountEquals(5)
        listOf("first", "inserted", "B", "A", "parkable").forEachIndexed { index, expected ->
            onAllNodesOfType<JLabel>()[index].assertProperty(expected) { text }
        }
        onAllNodesOfType<JLabel>()[2].assertProperty(true, "the keyed reorder moves B first") { this === labelB }
        onAllNodesOfType<JLabel>()[3].assertProperty(true, "the keyed reorder moves A second") { this === labelA }
        assertAllProvided(5, "a keyed reorder keeps the provided value")

        active = false
        awaitIdle()
        onAllNodesOfType<JLabel>().assertCountEquals(4)
        onNodeWithText("parkable").assertDoesNotExist()
        active = true
        awaitIdle()
        onNodeWithText("parkable").assertProperty(Color.MAGENTA, "a reactivated descendant") { background }
    }

    @Test
    fun aChangedProvidedStyleKeepsTheComponentsModifierAndOwnStyle() = runComposeSwingTest {
        var providesBackground by mutableStateOf(false)
        setContent {
            Panel(
                style = {
                    if (providesBackground) {
                        provideNestedStyle(AllComponents) { background(Color.BLUE) }
                    } else {
                        provideNestedStyle(Labels) { enabled(false) }
                    }
                },
            ) {
                Button(
                    "button",
                    onClick = {},
                    modifier = SwingModifier.name("tip"),
                    style = { foreground(Color.RED) },
                )
            }
        }
        providesBackground = true
        awaitIdle()

        val button = onNodeOfType<JButton>()
        button.assertProperty(Color.BLUE, "the style the provider now provides reaches the button") { background }
        button.assertProperty(Color.RED, "the button's own style stands") { foreground }
        button.assertProperty("tip", "the button's modifier stands") { name }
    }

    @Test
    fun aProvidedPropertyWithARewriteOnIsWrittenAgainWhenTheComponentOverwritesIt() = runComposeSwingTest {
        setContent {
            Panel(style = { provideNestedStyle(Labels) { OpaqueProperty.provide(true) } }) { Label("opaque") }
        }
        onNodeWithText("opaque").assertProperty(true) { isOpaque }

        val label = onNodeWithText("opaque").fetch<JLabel>()
        label.isOpaque = false
        awaitIdle()
        onNodeWithText("opaque")
            .assertProperty(true, "the property announces the overwrite, so the provided value is written again") {
                isOpaque
            }
    }

    @Test
    fun aProvidersOwnWriteIsNotCheckedAsPartOfAConsumersNestingDefault() = runComposeSwingTest {
        setContent {
            Panel(
                modifier = SwingModifier.testTag("provider"),
                style = {
                    background(Color.RED)
                    provideNestedStyle(AllComponents) {
                        foreground(Color.BLUE)
                        background(Color.BLUE)
                    }
                },
            ) {
                SwingNode(
                    factory = { JLabel("third party") },
                    modifier = SwingModifier.styleable(SwingStyle, ThirdPartyNestingKind),
                )
            }
        }

        onNodeWithText("third party")
            .assertProperty(Color.BLUE) { foreground }
            .assertProperty(Color.BLUE) { background }
        onNodeWithTag("provider").assertProperty(Color.RED) { background }
    }

    @Test
    fun theDefaultOfANestingKindCannotWriteAPropertyOutsideANestedStyle() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    Panel(style = { provideNestedStyle(AllComponents) { background(Color.BLUE) } }) {
                        SwingNode(
                            factory = { JLabel("writing") },
                            modifier = SwingModifier.styleable(SwingStyle, WritingNestingKind),
                        )
                    }
                }
            }

        assertContains(failure.message.orEmpty(), "default of a nesting kind may only apply nested styles")
        assertContains(failure.message.orEmpty(), "SwingStyleKind constructor")
    }
}

/** A kind a third party builds, whose default applies the styles provided for every component. */
private val ThirdPartyNestingKind =
    nestingStyleKind<SwingComponentStyleScope, Unit>(
        { base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} },
    ) {
        applyNestedStyle(AllComponents)
    }

/** A kind whose default writes a property of its own, which a nesting kind cannot do. */
private val WritingNestingKind =
    nestingStyleKind<SwingComponentStyleScope, Unit>(
        { base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} },
    ) {
        OpaqueProperty.provide(true)
    }

/** A raw node's kind that applies the styles provided for every component. */
private val ProvidedLeafKind =
    SwingStyleKind<SwingComponentStyleScope, Unit>(
        { base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} },
    ) {
        applyNestedStyle(AllComponents)
    }

private val OpaqueProperty =
    stylePropertyOf(
        ComponentPropertyDescriptor<JComponent, Boolean>(
            name = "opaque",
            read = { it.isOpaque },
            write = { component, value -> component.isOpaque = value },
        ),
        rewriteOn = "opaque",
    )
