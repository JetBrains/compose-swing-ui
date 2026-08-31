package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The composable cell renderer reached the way an external caller reaches it: on a component of the
 * caller's own, built in a plain [SwingNode], rather than through a wrapper this library ships.
 *
 * [rememberListItemRenderer] builds the renderer as composition work and [listItemRenderer] installs it
 * as a modifier property, which is the pair `docs/CUSTOM-COMPONENTS.md` tells a caller to write. The
 * cell is asked for through the component itself - the renderer it carries, invoked for a row as the
 * widget invokes it when painting - so what these assert is what the caller's component would paint.
 */
class CallerBuiltRendererTest {
    @Test
    fun aCallerBuiltListRendersItsRowsThroughTheComposableCell() = runComposeSwingTest {
        setContent {
            WideItemList(items = listOf("alpha", "beta")) { item ->
                FlowPanel { Label(item) }
            }
        }

        val list = onNodeOfType<JList<*>>().fetch<JList<String>>()
        val cell = list.stampCell(index = 0)
        assertFalse(cell is JLabel, "a composable cell stamps what it composed, not the JList's own JLabel")
        assertEquals("alpha", cell.firstLabelText(), "the renderer the caller installed should render row 0")
        assertEquals("beta", list.stampCell(index = 1).firstLabelText(), "the reused cell should restamp row 1")
    }

    @Test
    fun theCellFollowsTheStateItsBodyReads() = runComposeSwingTest {
        var badge by mutableStateOf("draft")
        setContent {
            WideItemList(items = listOf("alpha")) { item ->
                FlowPanel { Label("$item ($badge)") }
            }
        }

        val list = onNodeOfType<JList<*>>().fetch<JList<String>>()
        assertEquals(
            "alpha (draft)",
            list.stampCell(index = 0).firstLabelText(),
            "the cell should render the state its body reads",
        )

        badge = "review"
        awaitIdle()

        assertEquals(
            "alpha (review)",
            list.stampCell(index = 0).firstLabelText(),
            "a live cell composition must restamp the state its body reads rather than freeze on the first value",
        )
    }

    @Test
    fun theRendererStopsStampingOnceItsCompositionIsDisposed() = runComposeSwingTest {
        var showList by mutableStateOf(true)
        setContent {
            if (showList) {
                WideItemList(items = listOf("alpha", "beta")) { item ->
                    FlowPanel { Label(item) }
                }
            }
        }

        // A renderer outlives the composition that remembered it: whoever captured it - the component
        // itself, a pane painting while the window is torn down - goes on invoking it afterwards.
        val renderer = onNodeOfType<JList<*>>().fetch<JList<String>>().cellRenderer

        showList = false
        awaitIdle()

        assertNull(
            renderer.stampCell(value = "beta", index = 1).firstLabelText(),
            "a stamp after the remembering composition is disposed must render an empty cell, not a stale row",
        )
    }

    @Test
    fun anItemTheCellBodyWasNotWrittenOverIsNamedRatherThanCastBlindly() = runComposeSwingTest {
        // The widget stamps a row to measure itself, so the mismatch surfaces where the component is first
        // laid out rather than at some later paint.
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    // Nothing ties the cell body's item type to what the component's model holds, so the
                    // two can disagree: a cell written over String on a list of numbers.
                    val cells = rememberListItemRenderer<String> { item -> Label(item) }
                    SwingNode(
                        factory = { WideList<Int>() },
                        modifier = SwingModifier.listItemRenderer(cells),
                        update = {
                            set(listOf(1, 2)) { setListData(it.toTypedArray()) }
                        },
                    )
                }
                awaitIdle()
            }

        assertTrue(
            failure.message.orEmpty().contains("java.lang.String") &&
                failure.message.orEmpty().contains("java.lang.Integer"),
            "the refusal must name both the type the cell was written over and the one it was handed: " +
                "${failure.message}",
        )
    }

    @Test
    fun aGenericWrapperNamesTheItemTypeItCannotReify() = runComposeSwingTest {
        // Inside a generic function the cell body's type argument is erased, so the call site cannot
        // supply it: the wrapper names it instead, and the stamp is checked against what it named.
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent { TypedItemList(items = listOf(1, 2), itemType = String::class) }
                awaitIdle()
            }

        assertTrue(
            failure.message.orEmpty().contains("java.lang.String") &&
                failure.message.orEmpty().contains("java.lang.Integer"),
            "a wrapper that names its item type must get the same check a direct call gets: " +
                "${failure.message}",
        )
    }

    @Test
    fun anItemTypeNamedByAWrapperIsTheBoxedOne() = runComposeSwingTest {
        // A model holds boxed items whatever the item type is written as, so a primitive one has to be
        // checked against the class its values actually are.
        setContent { TypedItemList(items = listOf(1, 2), itemType = Int::class) }

        val list = onNodeOfType<JList<*>>().fetch<JList<Int>>()
        assertEquals(
            "1",
            list.stampCell(index = 0).firstLabelText(),
            "a wrapper naming a primitive item type must render its rows rather than refuse them",
        )
    }

    @Test
    fun aNullRowUnderANamedItemTypeIsRefused() = runComposeSwingTest {
        // A stated item type is not nullable, so a model that holds null disagrees with the cell body
        // just as one holding the wrong class does.
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    val cells = rememberListItemRenderer<String> { item -> Label(item) }
                    SwingNode(
                        factory = { WideList<String?>() },
                        modifier = SwingModifier.listItemRenderer(cells),
                        update = {
                            set(listOf("alpha", null)) { setListData(it.toTypedArray()) }
                        },
                    )
                }
                awaitIdle()
            }

        assertTrue(
            failure.message.orEmpty().contains("was handed null"),
            "the refusal must say the model handed a null: ${failure.message}",
        )
    }

    @Test
    fun aRendererOfTheCallersOwnIsInstalledAsItIsWritten() = runComposeSwingTest {
        // A renderer already written against the Swing interface, over its own item type: adopting this
        // library must not oblige anyone to widen it to Any?.
        val own =
            ListCellRenderer<Person> { _, value, _, _, _ -> JLabel(value?.name) }
        setContent {
            SwingNode(
                factory = { WideList<Person>() },
                modifier = SwingModifier.listItemRenderer(own),
                update = {
                    set(listOf(Person("Ada", 36))) { setListData(it.toTypedArray()) }
                },
            )
        }

        val list = onNodeOfType<JList<*>>().fetch<JList<Person>>()
        assertEquals(
            "Ada",
            list.stampCell(index = 0).firstLabelText(),
            "the caller's own renderer must be the one the component stamps its items through",
        )
    }
}

/**
 * A list of the caller's own: one that never shrinks below its preferred width, so a long item is not
 * clipped. This library wraps no such component - only the caller's composable below builds it.
 */
private class WideList<T> : JList<T>() {
    override fun getMinimumSize(): Dimension = preferredSize
}

/**
 * A generic wrapper of the caller's own: its item type is a type parameter, so the call site cannot
 * reify it and the wrapper names it through the overload that takes one. The model is separate from the
 * cell body's type, as a caller-built component's model is.
 */
@Composable
private fun <T : Any> TypedItemList(
    items: List<Any>,
    itemType: KClass<T>,
    modifier: SwingModifier = SwingModifier,
) {
    val cells = rememberListItemRenderer(itemType) { item -> Label(item.toString()) }
    SwingNode(
        factory = { WideList<Any>() },
        modifier = modifier.listItemRenderer(cells),
        update = {
            set(items) { setListData(it.toTypedArray()) }
        },
    )
}

/**
 * The caller's composable over [WideList], in the shape `docs/CUSTOM-COMPONENTS.md` shows: the renderer
 * is built in the composable body, and the node's update block installs it on the component.
 */
@Composable
private fun WideItemList(
    items: List<String>,
    modifier: SwingModifier = SwingModifier,
    itemContent: @Composable ListItemScope.(item: String) -> Unit,
) {
    val cells = rememberListItemRenderer(itemContent)
    SwingNode(
        factory = { WideList<String>() },
        modifier = modifier.listItemRenderer(cells),
        update = {
            set(items) { setListData(it.toTypedArray()) }
        },
    )
}
