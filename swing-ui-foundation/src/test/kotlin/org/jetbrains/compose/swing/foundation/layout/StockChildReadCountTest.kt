package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.assertEquals

/** How often a Foundation container reads an invalid stock child in one pass, and what that pass places it at. */
class StockChildReadCountTest {
    @ParameterizedTest(name = "{0}, text {1}")
    @CsvSource(
        "Column, 21, 4, 2",
        "Row, 21, 4, 1",
        "Box, 21, 3, 1",
        "nested, 21, 6, 1",
        "Column, 1234, 5, 3",
        "Row, 1234, 5, 2",
        "Box, 1234, 4, 2",
        "nested, 1234, 7, 2",
        "Column, 1, 5, 3",
        "Row, 1, 5, 2",
        "Box, 1, 4, 2",
        "nested, 1, 7, 2",
    )
    fun aTextChangeReadsAStockChildThisOften(
        container: String,
        lastText: String,
        preferredSizeReads: Int,
        baselineReads: Int,
    ) = runComposeSwingTest {
        var text by mutableStateOf("12")
        val label = CountingLabel()
        setContent {
            LabelIn(container, label, text)
        }
        awaitIdle()

        label.preferredSizeReads = 0
        label.baselineReads = 0
        text = lastText
        awaitIdle()

        assertEquals(
            preferredSizeReads to baselineReads,
            label.preferredSizeReads to label.baselineReads,
            "the getPreferredSize and getBaseline reads of one pass",
        )
    }

    @ParameterizedTest(name = "children={0}")
    @ValueSource(ints = [0, 1])
    fun aSizeReadLaysOutNoStockChild(children: Int) =
        runComposeSwingTest {
            val panel = LayoutCountingPanel().apply { repeat(children) { add(JLabel("Child")) } }
            setContent {
                Column(modifier = SwingModifier.testTag("column")) {
                    SwingNode(factory = { panel })
                }
            }
            awaitIdle()

            panel.invalidate()
            panel.layouts = 0
            onNodeWithTag("column").fetch<JComponent>().preferredSize

            assertEquals(0, panel.layouts, "the child's layouts during its parent's size read")
        }

    @ParameterizedTest(name = "{0}, then {1}")
    @CsvSource(
        "<html>One</html>, <html>One<br>Two<br>Three</html>",
        "<html>One<br>Two<br>Three</html>, <html>One</html>",
    )
    fun aTextSetAfterASizeReadPlacesTheChildAtTheHeightOfThatText(
        readText: String,
        lastText: String,
    ) = runComposeSwingTest {
        val label = JLabel("Start")
        setContent {
            Column(modifier = SwingModifier.testTag("column")) {
                SwingNode(factory = { label }, modifier = SwingModifier.testTag("label"))
            }
        }
        awaitIdle()

        label.text = readText
        onNodeWithTag("column").fetch<JComponent>().preferredSize
        label.text = lastText
        awaitIdle()

        onNodeWithTag("label").assertProperty(JLabel(lastText).preferredSize.height, "the label's height") { height }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contentTypes")
    fun aPaneResettingItsSizeOnATextChangeTakesItsWrappedHeightAtAHeldWidth(contentType: String) {
        runComposeSwingTest {
            var text by mutableStateOf("Short")
            setContent {
                Box {
                    Column(SwingModifier.width(200)) {
                        SwingNode(
                            factory = { SizeResettingPane(contentType) },
                            modifier = SwingModifier.fillMaxWidth().testTag("pane"),
                            update = { set(text) { this.text = it } },
                        )
                        Label("After", SwingModifier.testTag("after"))
                    }
                }
            }
            val wrapped =
                SizeResettingPane(contentType).run {
                    this.text = WRAPPING_TEXT
                    setSize(200, 1000)
                    preferredSize.height
                }

            text = WRAPPING_TEXT
            awaitIdle()

            onNodeWithTag("pane").assertProperty(wrapped, "the height of the wrapped text") { height }
            onNodeWithTag("after").assertProperty(wrapped, "the next child below the wrapped text") { y }
        }
    }

    @ParameterizedTest(name = "{0}, then {1}")
    @CsvSource(
        "<html>Short</html>, '<html>$WRAPPING_TEXT</html>'",
        "'<html>$WRAPPING_TEXT</html>', <html>Short</html>",
    )
    fun aRowAlignsAnHtmlLabelAtItsSettledBaselineAfterATextChangeAtItsHeldWidth(
        firstText: String,
        lastText: String,
    ) = runComposeSwingTest {
        var text by mutableStateOf(firstText)
        setContent {
            Row(modifier = SwingModifier.testTag("row")) {
                Label(text, modifier = SwingModifier.testTag("leaf").width(200).alignByBaseline())
                Label("<html>Side</html>", modifier = SwingModifier.testTag("side").padding(top = 10).alignByBaseline())
            }
        }
        awaitIdle()

        text = lastText
        awaitIdle()

        onNodeWithTag("leaf").assertProperty(10, "the label's top, where its settled baseline puts it") { y }
        onNodeWithTag("side").assertProperty(10, "the side label's top, below its padding and on that line") { y }
    }

    companion object {
        @JvmStatic
        fun contentTypes(): List<Arguments> =
            listOf(
                Arguments.of(Named.of("an HTML pane", "text/html")),
                Arguments.of(Named.of("a plain-text pane", "text/plain")),
            )
    }

    private class CountingLabel : JLabel() {
        var preferredSizeReads = 0
        var baselineReads = 0

        override fun getPreferredSize(): Dimension {
            preferredSizeReads++
            return super.getPreferredSize()
        }

        override fun getBaseline(
            width: Int,
            height: Int,
        ): Int {
            baselineReads++
            return super.getBaseline(width, height)
        }
    }

    private class LayoutCountingPanel : JPanel() {
        var layouts = 0

        override fun doLayout() {
            layouts++
            super.doLayout()
        }
    }
}

@Composable
private fun LabelIn(
    container: String,
    label: JLabel,
    text: String,
) {
    val child = @Composable { SwingNode(factory = { label }, update = { set(text) { this.text = it } }) }
    when (container) {
        "Column" -> Column { child() }
        "Row" -> Row { child() }
        "Box" -> Box { child() }
        "nested" -> Row { Column { Box { child() } } }
        else -> error("No container named $container")
    }
}

/** Sets its size to 0x0 on every text write, so its next preferred height depends on the width its parent grants. */
private class SizeResettingPane(
    contentType: String,
) : JEditorPane(contentType, "") {
    override fun setText(t: String?) {
        super.setText(t)
        size = Dimension(0, 0)
    }
}
