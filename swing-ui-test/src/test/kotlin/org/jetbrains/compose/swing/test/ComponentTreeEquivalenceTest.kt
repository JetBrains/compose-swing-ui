package org.jetbrains.compose.swing.test

import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.LayoutManager
import java.awt.Point
import java.util.Vector
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JProgressBar
import javax.swing.JSlider
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import org.jetbrains.compose.swing.test.lookalike.first.Widget as FirstWidget
import org.jetbrains.compose.swing.test.lookalike.second.Widget as SecondWidget

class ComponentTreeEquivalenceTest {
    @Test
    fun treesBuiltTheSameWayAreEquivalent() = runComposeSwingTest {
        assertComponentTreesEquivalent(screen(), screen())
    }

    @Test
    fun aSubclassMatchesTheWidgetItExtendsByDefault() = runComposeSwingTest {
        val reference = JPanel().apply { add(JButton("OK")) }
        val counting = JPanel().apply { add(CountingButton("OK")) }

        assertComponentTreesEquivalent(reference, counting)
    }

    @Test
    fun aSubclassIsReportedWhereSubclassesAreNotAllowed() = runComposeSwingTest {
        val reference = JPanel().apply { add(JButton("OK")) }
        val counting = JPanel().apply { add(CountingButton("OK")) }

        val failure =
            assertFailsWith<AssertionError> {
                assertComponentTreesEquivalent(reference, counting, allowSubclasses = false)
            }

        assertContains(failure.message.orEmpty(), "type: expected <JButton>, actual <CountingButton>")
    }

    /** Two classes named alike are two classes: the comparison reads what they are, not what they are called. */
    @Test
    fun twoClassesSharingASimpleNameAreReported() = runComposeSwingTest {
        val failure =
            assertFailsWith<AssertionError> { assertComponentTreesEquivalent(FirstWidget(), SecondWidget()) }

        assertContains(failure.message.orEmpty(), "type: expected <Widget>, actual <Widget>")
    }

    @Test
    fun aDifferentWidgetTypeIsReported() = runComposeSwingTest {
        val reference = JPanel().apply { add(JButton("OK")) }
        val actual = JPanel().apply { add(JTextField()) }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "type: expected <JButton>, actual <JTextField>")
        assertContains(failure.message.orEmpty(), "root(JPanel) > [0]JButton")
    }

    @Test
    fun aDifferentChildCountIsReported() = runComposeSwingTest {
        val reference = JPanel().apply { add(JLabel("one")) }
        val actual =
            JPanel().apply {
                add(JLabel("one"))
                add(JLabel("two"))
            }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "children: expected <1>, actual <2>")
        assertContains(failure.message.orEmpty(), "root(JPanel)")
    }

    @Test
    fun theFirstDifferenceInPreOrderIsTheOneReported() = runComposeSwingTest {
        val reference =
            JPanel().apply {
                add(JLabel("first"))
                add(JLabel("second"))
            }
        val actual =
            JPanel().apply {
                add(JLabel("changed"))
                add(JLabel("also changed"))
            }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "text: expected <first>, actual <changed>")
    }

    @Test
    fun aDifferentLayoutIsReported() = runComposeSwingTest {
        val reference = JPanel(FlowLayout())
        val actual = JPanel(BorderLayout())

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "layout: expected <FlowLayout>, actual <BorderLayout>")
    }

    @Test
    fun aLayoutOfTheCallersOwnIsNamedByItsOwnType() = runComposeSwingTest {
        val reference = JPanel(FixedLayout())
        val actual = JPanel(FlowLayout())

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "layout: expected <FixedLayout>, actual <FlowLayout>")
    }

    @Test
    fun differentBoundsAreReported() = runComposeSwingTest {
        val reference = JPanel().apply { add(JLabel("hi").apply { preferredSize = Dimension(40, 20) }) }
        val actual = JPanel().apply { add(JLabel("hi").apply { preferredSize = Dimension(80, 20) }) }
        reference.layoutOffscreen(Dimension(200, 100))
        actual.layoutOffscreen(Dimension(200, 100))

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "bounds: expected <")
        assertContains(failure.message.orEmpty(), "width=40")
    }

    /**
     * The two roots stand in trees of their own, so where each of them was placed says nothing about the
     * other. Everything below them is placed by the tree under comparison and compares as it stands.
     */
    @Test
    fun theRootsAreComparedOnTheirSizeAndNotTheirPosition() = runComposeSwingTest {
        val reference = JPanel().apply { add(JLabel("hi")) }
        val actual = JPanel().apply { add(JLabel("hi")) }
        reference.layoutOffscreen(Dimension(200, 100))
        actual.layoutOffscreen(Dimension(200, 100))
        actual.location = Point(30, 40)

        assertComponentTreesEquivalent(reference, actual)

        actual.layoutOffscreen(Dimension(300, 100))
        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "size: expected <")
        assertContains(failure.message.orEmpty(), "width=200")
    }

    @Test
    fun aDifferentFontIsReported() = runComposeSwingTest {
        val reference = JLabel("hi")
        val actual = JLabel("hi").apply { font = Font("Serif", Font.BOLD, FONT_SIZE) }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "font: expected <")
    }

    @Test
    fun differentColorsAreReported() = runComposeSwingTest {
        val reference = JPanel()
        val actual = JPanel().apply { background = Color.RED }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "background: expected <")
    }

    @Test
    fun aDifferentForegroundIsReported() = runComposeSwingTest {
        val reference = JLabel("hi")
        val actual = JLabel("hi").apply { foreground = Color.RED }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "foreground: expected <")
    }

    @Test
    fun aDifferentBorderIsReported() = runComposeSwingTest {
        val reference = JPanel()
        val actual = JPanel().apply { border = BorderFactory.createLineBorder(Color.BLACK) }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        // The border decides both the insets a child is laid out in and what is painted around it, so
        // the insets are the first of the two to be reported.
        assertContains(failure.message.orEmpty(), "insets: expected <")
    }

    @Test
    fun aBorderThatChangesNoInsetsIsStillReported() = runComposeSwingTest {
        val reference = JPanel()
        val actual = JPanel().apply { border = BorderFactory.createEmptyBorder() }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "border: expected <null>, actual <EmptyBorder>")
    }

    @Test
    fun aDisabledWidgetIsReported() = runComposeSwingTest {
        val reference = JButton("OK")
        val actual = JButton("OK").apply { isEnabled = false }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "enabled: expected <true>, actual <false>")
    }

    @Test
    fun aHiddenWidgetIsReported() = runComposeSwingTest {
        val reference = JPanel().apply { add(JLabel("hi")) }
        val actual = JPanel().apply { add(JLabel("hi").apply { isVisible = false }) }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "visible: expected <true>, actual <false>")
    }

    @Test
    fun aTransparentWidgetIsReported() = runComposeSwingTest {
        val reference = JPanel()
        val actual = JPanel().apply { isOpaque = false }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "opaque: expected <true>, actual <false>")
    }

    @Test
    fun theFailureCarriesBothTrees() = runComposeSwingTest {
        val reference = JPanel().apply { add(JLabel("expected")) }
        val actual = JPanel().apply { add(JLabel("actual")) }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "Expected tree:")
        assertContains(failure.message.orEmpty(), "JLabel text=\"expected\"")
        assertContains(failure.message.orEmpty(), "Actual tree:")
        assertContains(failure.message.orEmpty(), "JLabel text=\"actual\"")
    }

    @Test
    fun aWidgetHoldingNoChildrenIsCompared() = runComposeSwingTest {
        val reference = canvas().apply { background = Color.BLUE }
        val actual = canvas().apply { background = Color.GREEN }

        assertComponentTreesEquivalent(canvas(), canvas())
        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "root(Canvas)")
        assertContains(failure.message.orEmpty(), "Expected tree:\nCanvas")
    }

    @Test
    fun differencesDeepInTheTreeAreFoundAndNamedByTheirPath() = runComposeSwingTest {
        val reference = screen()
        val actual = screen()
        ((actual.getComponent(1) as JPanel).getComponent(0) as JButton).text = "Cancel"

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "root(JPanel) > [1]JPanel > [0]JButton")
        assertContains(failure.message.orEmpty(), "text: expected <OK>, actual <Cancel>")
    }

    @Test
    fun menuBarsHoldingTheSameMenusAreEquivalent() = runComposeSwingTest {
        assertComponentTreesEquivalent(menuBar(itemsPerMenu = 4), menuBar(itemsPerMenu = 4))
    }

    @Test
    fun aMissingMenuItemIsReported() = runComposeSwingTest {
        val reference = menuBar(itemsPerMenu = 4)
        val actual = menuBar(itemsPerMenu = 3)

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "root(JMenuBar) > [0]JMenu > [0]JPopupMenu")
        assertContains(failure.message.orEmpty(), "children: expected <4>, actual <3>")
    }

    @Test
    fun aMenuThatBuiltNoItemsAtAllIsReported() = runComposeSwingTest {
        val reference = menuBar(itemsPerMenu = 4)
        val actual = menuBar(itemsPerMenu = 0)

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        // Swing builds a menu's popup when the first item is added, so a menu that got none holds
        // nothing at all where the reference holds a popup.
        assertContains(failure.message.orEmpty(), "root(JMenuBar) > [0]JMenu")
        assertContains(failure.message.orEmpty(), "children: expected <1>, actual <0>")
    }

    @Test
    fun aDifferentMenuItemLabelIsReported() = runComposeSwingTest {
        val reference = menuBar(itemsPerMenu = 2)
        val actual = menuBar(itemsPerMenu = 2)
        (actual.getMenu(0).getItem(1) as JMenuItem).text = "Save As"

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "root(JMenuBar) > [0]JMenu > [0]JPopupMenu > [1]JMenuItem")
        assertContains(failure.message.orEmpty(), "text: expected <Menu0 Item1>, actual <Save As>")
    }

    /**
     * Laying two trees out is what makes the comparison read their layout, and a menu's items are part
     * of it: a longer item widens the popup holding it, which is a difference in bounds rather than one
     * only the text would have shown.
     */
    @Test
    fun menuContentLaidOutOffscreenIsComparedOnItsBounds() = runComposeSwingTest {
        val reference = menuBar(itemsPerMenu = 2)
        val actual = menuBar(itemsPerMenu = 2)
        (actual.getMenu(0).getItem(1) as JMenuItem).text = "Save every open document as a copy"
        reference.layoutOffscreen(Dimension(MENU_BAR_WIDTH, MENU_BAR_HEIGHT))
        actual.layoutOffscreen(Dimension(MENU_BAR_WIDTH, MENU_BAR_HEIGHT))

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "root(JMenuBar) > [0]JMenu > [0]JPopupMenu")
        assertContains(failure.message.orEmpty(), "bounds:")
    }

    @Test
    fun aMenuAlreadyHeldAsAComponentIsWalkedOnce() = runComposeSwingTest {
        val reference = menuBar(itemsPerMenu = 1)
        val actual = menuBar(itemsPerMenu = 1)
        actual.getMenu(0).text = "Edit"

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        // A JMenuBar reports its menus both as components and as menu sub-elements, and a JPopupMenu
        // reports its items the same way, so each has to appear in the tree exactly once.
        val actualTree = failure.message.orEmpty().substringAfter("Actual tree:")
        assertEquals(1, actualTree.occurrencesOf("JMenu text=\"Edit\""))
        assertEquals(1, actualTree.occurrencesOf("JMenuItem text=\"Menu1 Item0\""))
    }

    @Test
    fun aCheckedBoxIsReported() = runComposeSwingTest {
        val reference = JPanel().apply { add(JCheckBox("agree").apply { isSelected = false }) }
        val actual = JPanel().apply { add(JCheckBox("agree").apply { isSelected = true }) }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "selected: expected <false>, actual <true>")
    }

    @Test
    fun aDifferentEchoCharacterIsReported() = runComposeSwingTest {
        val reference = JPanel().apply { add(JPasswordField().apply { echoChar = '*' }) }
        val actual = JPanel().apply { add(JPasswordField().apply { echoChar = '#' }) }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "echoChar: expected <*>, actual <#>")
    }

    @Test
    fun aSliderStandingSomewhereElseIsReported() = runComposeSwingTest {
        val reference = JPanel().apply { add(JSlider(0, 10, 3)) }
        val actual = JPanel().apply { add(JSlider(0, 10, 7)) }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "value: expected <3>, actual <7>")
    }

    @Test
    fun aProgressBarFilledDifferentlyIsReported() = runComposeSwingTest {
        val reference = JPanel().apply { add(JProgressBar(0, 100).apply { value = 20 }) }
        val actual = JPanel().apply { add(JProgressBar(0, 100).apply { value = 80 }) }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "value: expected <20>, actual <80>")
    }

    @Test
    fun listsHoldingTheSameItemsMatchThroughModelsOfDifferentClasses() = runComposeSwingTest {
        val reference = JPanel().apply { add(JList(arrayOf("one", "two"))) }
        val actual = JPanel().apply { add(JList(Vector(listOf("one", "two")))) }

        assertComponentTreesEquivalent(reference, actual)
    }

    @Test
    fun aListHoldingDifferentItemsIsReported() = runComposeSwingTest {
        val reference = JPanel().apply { add(JList(arrayOf("one", "two"))) }
        val actual = JPanel().apply { add(JList(arrayOf("one", "three"))) }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "items: expected <[one, two]>, actual <[one, three]>")
    }

    @Test
    fun aDifferentSelectionIsReported() = runComposeSwingTest {
        val items = arrayOf("one", "two", "three")
        val reference = JPanel().apply { add(JList(items).apply { selectedIndex = 0 }) }
        val actual = JPanel().apply { add(JList(items).apply { selectedIndex = 2 }) }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "selectedIndices: expected <[0]>, actual <[2]>")
    }

    @Test
    fun aDifferentSelectionModeIsReported() = runComposeSwingTest {
        val reference = JPanel().apply { add(JList(arrayOf("one")).apply { selectionMode = 0 }) }
        val actual = JPanel().apply { add(JList(arrayOf("one")).apply { selectionMode = 2 }) }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "selectionMode: expected <0>, actual <2>")
    }

    @Test
    fun aTextFieldSizedForADifferentNumberOfColumnsIsReported() = runComposeSwingTest {
        val reference = JPanel().apply { add(JTextField("hi", 4)) }
        val actual = JPanel().apply { add(JTextField("hi", 12)) }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "columns: expected <4>, actual <12>")
    }

    @Test
    fun aLayoutManagerSpacingItsChildrenDifferentlyIsReported() = runComposeSwingTest {
        val reference = JPanel(BorderLayout(4, 4))
        val actual = JPanel(BorderLayout(12, 4))

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(failure.message.orEmpty(), "borderLayout.hgap: expected <4>, actual <12>")
    }

    @Test
    fun aDifferentAccessibleDescriptionIsReported() = runComposeSwingTest {
        val reference = JPanel()
        val actual = JPanel().apply { accessibleContext.accessibleDescription = "Save the document" }

        val failure = assertFailsWith<AssertionError> { assertComponentTreesEquivalent(reference, actual) }

        assertContains(
            failure.message.orEmpty(),
            "accessibleDescription: expected <null>, actual <Save the document>",
        )
    }

    /** A composed widget against the plain JDK widget it is built over, which is what the extension is for. */
    @Test
    fun aComposedWidgetComparesEqualToTheOneItIsBuiltOver() = runComposeSwingTest {
        setContent { Button("Save", onClick = {}) }

        onNodeOfType<JButton>().assertTreeMatches(JButton("Save"))
    }

    /** The reference needs no layout pass of its own: the assertion gives it the node's size. */
    @Test
    fun theReferenceIsLaidOutAtTheNodesSize() = runComposeSwingTest {
        setContent { Button("Save", onClick = {}) }
        val reference = JButton("Save")

        val node = onNodeOfType<JButton>()
        node.assertTreeMatches(reference)

        assertEquals(node.fetch().size, reference.size)
    }

    @Test
    fun theAssertedInteractionIsReturnedForChaining() = runComposeSwingTest {
        setContent { Button("Save", onClick = {}) }

        val node = onNodeOfType<JButton>()

        assertSame(node, node.assertTreeMatches(JButton("Save")))
    }

    /** A bar of two menus, each holding [itemsPerMenu] items in the popup a menu keeps its content in. */
    private fun menuBar(itemsPerMenu: Int): JMenuBar = JMenuBar().apply {
        for (menu in 0 until MENU_COUNT) {
            add(
                JMenu("Menu$menu").apply {
                    for (item in 0 until itemsPerMenu) add(JMenuItem("Menu$menu Item$item"))
                },
            )
        }
    }

    private fun String.occurrencesOf(text: String): Int = split(text).size - 1

    /** An AWT widget names itself off a counter, so two of them are named alike here to compare on the rest. */
    private fun canvas(): Canvas = Canvas().apply { name = "canvas" }

    /** A small nested tree, built the same way every time it is called. */
    private fun screen(): JPanel = JPanel(BorderLayout()).apply {
        add(JLabel("Title"), BorderLayout.NORTH)
        add(
            JPanel(FlowLayout()).apply {
                add(JButton("OK"))
                add(JButton("Cancel"))
            },
            BorderLayout.CENTER,
        )
    }

    /** A layout of the caller's own, standing for no JDK layout manager at all. */
    private class FixedLayout : LayoutManager {
        override fun addLayoutComponent(
            name: String?,
            component: Component?,
        ) = Unit

        override fun removeLayoutComponent(component: Component?) = Unit

        override fun preferredLayoutSize(parent: Container?): Dimension = Dimension()

        override fun minimumLayoutSize(parent: Container?): Dimension = Dimension()

        override fun layoutContainer(parent: Container?) = Unit
    }

    /** A widget subclassed for bookkeeping of its own, as a benchmark counting paints subclasses one. */
    private class CountingButton(
        text: String,
    ) : JButton(text) {
        var paints: Int = 0
    }

    private companion object {
        const val FONT_SIZE = 20
        const val MENU_COUNT = 2
        const val MENU_BAR_WIDTH = 400
        const val MENU_BAR_HEIGHT = 24
    }
}
