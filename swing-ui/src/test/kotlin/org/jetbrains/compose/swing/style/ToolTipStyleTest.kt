package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.ToolTipManager
import kotlin.test.Test

class ToolTipStyleTest {
    @Test
    fun equalToolTipDeclarationDoesNotWrite() = runComposeSwingTest {
        var pass by mutableIntStateOf(0)
        val style =
            SwingStyle<SwingComponentStyleScope> {
                pass.let { }
                toolTip("styled")
            }
        setContent { Label("text", modifier = SwingModifier.styleable(TestNodeKind, style)) }
        onNodeOfType<JLabel>().assertProperty("styled") { toolTipText }
        onNodeOfType<JLabel>().fetch().toolTipText = "written outside"
        pass++
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(
            "written outside",
            "an equal tooltip declaration writes nothing",
        ) { toolTipText }
    }

    private fun JComponent.tooltipAt(
        x: Int,
        y: Int,
    ): String? {
        val moved = MouseEvent(this, MouseEvent.MOUSE_MOVED, 0L, 0, x, y, 0, false)
        dispatchEvent(moved)
        return getToolTipText(moved)
    }

    private fun JComponent.isUnderToolTipManager(): Boolean = ToolTipManager.sharedInstance() in mouseListeners

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("toolTipOverlaps")
    fun tooltipSharesModifierAndStyleBaseline(
        property: OverlapProperty,
        order: RemovalOrder,
    ) = runComposeSwingTest {
        assertCustomPropertyBaseline(property, order)
    }

    @ParameterizedTest
    @EnumSource(ToolTipSource::class)
    fun constantTooltipObservesStateAndRestoresOnRemoval(source: ToolTipSource) = runComposeSwingTest {
        var styled by mutableStateOf(true)
        var tip by mutableStateOf("first")
        val kind =
            SwingStyleKind<SwingComponentStyleScope, Unit>(
                { base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} },
            ) { if (styled) source.defaultStyle(this, tip) }
        setContent {
            SwingNode(
                factory = { JLabel("X").apply { toolTipText = "built-in" } },
                modifier =
                    SwingModifier.styleable(kind) {
                        if (styled) source.componentStyle(this, tip)
                    },
            )
        }
        onNodeOfType<JLabel>().assertProperty("first") { toolTipText }
        tip = "second"
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty("second") { toolTipText }
        styled = false
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty("built-in") { toolTipText }
    }

    @Test
    fun aConstantToolTipNeedsNothingFromThePointer() = runComposeSwingTest {
        setContent { Label("X", style = { toolTip("hint") }) }
        // A bare label given the same tooltip calibrates Swing's own registration.
        onNodeOfType<JLabel>().assertProperty(
            JLabel().apply { toolTipText = "hint" }.mouseMotionListeners.size,
            "a tooltip that is the same everywhere is read off the component, not followed",
        ) { mouseMotionListeners.size }
    }

    @Test
    fun thePerLocationFormAnswersForThePlaceUnderThePointer() = runComposeSwingTest {
        setContent {
            Label(
                "X",
                style = { toolTip { event -> if (event.x < 10) "left half" else "right half" } },
            )
        }
        onNodeOfType<JLabel>().apply {
            assertProperty("left half", "the place under the pointer decides the tooltip") { tooltipAt(2, 0) }
            assertProperty("right half", "and the next place decides it again") { tooltipAt(20, 0) }
        }
    }

    @Test
    fun aPlaceWithNoToolTipShowsNone() = runComposeSwingTest {
        setContent {
            Label("X", style = { toolTip { event -> "cell".takeIf { event.y < 5 } } })
        }
        onNodeOfType<JLabel>().assertProperty("cell", "the place that carries a tooltip reports it") { tooltipAt(0, 0) }

        onNodeOfType<JLabel>().apply {
            assertProperty(null, "and the place that carries none reports none") { tooltipAt(0, 9) }
            assertProperty(
                true,
                "a place with no tooltip must not take the component out of the tooltip manager",
            ) { isUnderToolTipManager() }
        }
    }

    @Test
    fun aComponentWithOnlyThePerLocationFormIsUnderTheToolTipManager() = runComposeSwingTest {
        setContent { Label("X", style = { toolTip { "cell" } }) }
        onNodeOfType<JLabel>().apply {
            assertProperty(
                null,
                "the declaration writes no tooltip of its own until the pointer arrives",
            ) { toolTipText }
            assertProperty(
                true,
                "yet the component is registered for tooltips, which is what lets one be shown at all",
            ) { isUnderToolTipManager() }
        }
    }

    @Test
    fun theLambdaOfTheLatestCompositionAnswers() = runComposeSwingTest {
        var declared by mutableIntStateOf(1)
        setContent {
            val generation = declared
            Label("X", style = { toolTip { "generation $generation" } })
        }
        declared = 2
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty("generation 2") { tooltipAt(0, 0) }
    }

    @Test
    fun theLastToolTipInTheChainOwnsTheComponentsToolTip() = runComposeSwingTest {
        setContent {
            Label("X", style = {
                toolTip("everywhere")
                toolTip { "here" }
            })
        }
        onNodeOfType<JLabel>().assertProperty(
            "here",
            "a component has one tooltip: the chain's last declaration owns it",
        ) { tooltipAt(0, 0) }
    }

    @Test
    fun switchingToThePerLocationFormLeavesNoConstantToolTipBehind() = runComposeSwingTest {
        var perLocation by mutableStateOf(false)
        setContent {
            Label(
                "X",
                style = {
                    if (perLocation) {
                        toolTip { event -> "cell".takeIf { event.x < 10 } }
                    } else {
                        toolTip("everywhere")
                    }
                },
            )
        }
        onNodeOfType<JLabel>().assertProperty(
            "everywhere",
            "the constant declaration shows its tooltip anywhere",
        ) { toolTipText }

        perLocation = true
        awaitIdle()
        onNodeOfType<JLabel>().apply {
            assertProperty(null, "the tooltip it left behind is not the new declaration's to show") { toolTipText }
            assertProperty("cell", "which answers for the place under the pointer instead") { tooltipAt(0, 0) }
            assertProperty(null, "including the places that carry none") { tooltipAt(20, 0) }
        }
    }

    @Test
    fun droppingThePerLocationFormRestoresTheComponent() = runComposeSwingTest {
        var hinted by mutableStateOf(true)
        setContent {
            Label("X", style = { if (hinted) toolTip { "cell" } })
        }
        onNodeOfType<JLabel>().assertProperty(
            "cell",
            "the tooltip is answered while the declaration is in the chain",
        ) {
            tooltipAt(0, 0)
        }

        hinted = false
        awaitIdle()
        onNodeOfType<JLabel>().apply {
            assertProperty(
                null,
                "the tooltip the component carried before the declaration comes back",
            ) {
                toolTipText
            }
            assertProperty(null, "and the pointer no longer publishes one") { tooltipAt(0, 0) }
            assertProperty(
                false,
                "a component the declaration put under the tooltip manager is taken back out with it",
            ) { isUnderToolTipManager() }
        }
    }

    companion object {
        @JvmStatic
        fun toolTipOverlaps(): List<Arguments> = OverlapProperty.toolTips.flatMap { property ->
            RemovalOrder.entries.map { order -> Arguments.of(property, order) }
        }
    }

    enum class ToolTipSource(
        val componentStyle: SwingComponentStyleScope.(String) -> Unit,
        val defaultStyle: SwingComponentStyleScope.(String) -> Unit,
    ) {
        Component({ toolTip(it) }, {}),
        KindDefault({}, { toolTip(it) }),
    }
}
