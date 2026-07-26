package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.beans.PropertyChangeListener
import javax.swing.JSplitPane
import javax.swing.LookAndFeel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Every declared aspect of a [SplitPane] keeps following the composition: a value changed after the
 * first composition reaches the live [JSplitPane], a value changed back is applied again, a value
 * withdrawn hands the property back to the look and feel and settles there, one never declared is left
 * for the look and feel to install, and the callback that hears a divider move is the one the latest
 * composition declared.
 */
class SplitPaneReactivityTest {
    /** A listener that records the divider offset the pane reports on every move. */
    private fun recordingListener(into: MutableList<Int>): PropertyChangeListener =
        PropertyChangeListener { event -> into += (event.source as JSplitPane).dividerLocation }

    @Test
    fun theResizeWeightFollowsEveryDeclaredValue() = runComposeSwingTest {
        var weight by mutableStateOf(0.0)
        setContent {
            SplitPane(resizeWeight = weight) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        assertEquals(0.0, pane.resizeWeight, "the pane should start at the declared weight")

        weight = 0.75
        awaitIdle()
        assertEquals(0.75, pane.resizeWeight, "the pane should adopt the new weight")

        weight = 0.0
        awaitIdle()
        assertEquals(0.0, pane.resizeWeight, "the pane should return to the first weight")
    }

    @Test
    fun theOrientationFollowsEveryDeclaredValue() = runComposeSwingTest {
        var orientation by mutableIntStateOf(JSplitPane.HORIZONTAL_SPLIT)
        setContent {
            SplitPane(orientation = orientation) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        assertEquals(JSplitPane.HORIZONTAL_SPLIT, pane.orientation, "the pane should start on the declared axis")

        orientation = JSplitPane.VERTICAL_SPLIT
        awaitIdle()
        assertEquals(JSplitPane.VERTICAL_SPLIT, pane.orientation, "the pane should turn to the new axis")

        orientation = JSplitPane.HORIZONTAL_SPLIT
        awaitIdle()
        assertEquals(JSplitPane.HORIZONTAL_SPLIT, pane.orientation, "the pane should turn back to the first axis")
    }

    @Test
    fun theDividerSizeFollowsEveryDeclaredValue() = runComposeSwingTest {
        var size by mutableStateOf<Int?>(null)
        setContent {
            SplitPane(dividerSize = size) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        assertEquals(JSplitPane().dividerSize, pane.dividerSize, "an undeclared size should be the look-and-feel size")

        size = 14
        awaitIdle()
        assertEquals(14, pane.dividerSize, "the declared size should reach the divider")

        size = 22
        awaitIdle()
        assertEquals(22, pane.dividerSize, "a new declared size should reach the divider")
    }

    @Test
    fun theOneTouchFlagFollowsEveryDeclaredValue() = runComposeSwingTest {
        var expandable by mutableStateOf<Boolean?>(null)
        setContent {
            SplitPane(oneTouchExpandable = expandable) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        assertEquals(
            JSplitPane().isOneTouchExpandable,
            pane.isOneTouchExpandable,
            "an undeclared flag should be the look-and-feel value",
        )

        expandable = true
        awaitIdle()
        assertEquals(true, pane.isOneTouchExpandable, "the declared flag should reach the divider")

        expandable = false
        awaitIdle()
        assertEquals(false, pane.isOneTouchExpandable, "a new declared flag should reach the divider")
    }

    @Test
    fun withdrawingTheDividerSizeGivesTheSizeBackToTheLookAndFeel() = runComposeSwingTest {
        // A pane that was never given a size draws its divider at the size its look and feel asks for,
        // so the oracle is a pane a hand-written form was handed the plain Swing way, and the declared
        // size is chosen away from it so that a withdrawal that did nothing could not pass for one that
        // gave the size back.
        val handWrittenSize = JSplitPane().dividerSize
        var size by mutableStateOf<Int?>(handWrittenSize + 13)
        setContent {
            SplitPane(dividerSize = size) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        assertEquals(handWrittenSize + 13, pane.dividerSize, "the declared size should reach the divider")

        size = null
        awaitIdle()

        assertEquals(
            handWrittenSize,
            pane.dividerSize,
            "withdrawing the size should leave the divider sized as its look and feel sizes one",
        )
    }

    @Test
    fun withdrawingTheOneTouchFlagGivesTheChoiceBackToTheLookAndFeel() = runComposeSwingTest {
        var expandable by mutableStateOf<Boolean?>(true)
        setContent {
            SplitPane(oneTouchExpandable = expandable) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        assertEquals(true, pane.isOneTouchExpandable, "the declared flag should reach the divider")

        expandable = null
        awaitIdle()

        // A pane that was never given a choice carries the one its look and feel leaves it with, so the
        // oracle is a pane a hand-written form was handed the plain Swing way.
        val handWrittenFlag = JSplitPane().isOneTouchExpandable
        assertNotEquals(
            true,
            handWrittenFlag,
            "the look and feel under test should leave a pane unexpanded, so that a withdrawal that did " +
                "nothing could not pass for one that gave the choice back",
        )
        assertEquals(
            handWrittenFlag,
            pane.isOneTouchExpandable,
            "withdrawing the flag should leave the divider as its look and feel leaves one",
        )
    }

    @Test
    fun aDividerSizeGivenBackIsNoLongerTheLookAndFeelsToInstall() = runComposeSwingTest {
        val handWrittenSize = JSplitPane().dividerSize
        var size by mutableStateOf<Int?>(handWrittenSize + 13)
        setContent {
            SplitPane(dividerSize = size) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        size = null
        awaitIdle()
        assertEquals(
            handWrittenSize,
            pane.dividerSize,
            "withdrawing the size should leave the divider sized as its look and feel sizes one",
        )

        // Handing the size back writes it onto the pane, and a look and feel sizes a divider only while
        // the pane carries no size of its own, so the size handed back is where the divider stays. The
        // oracle is a pane a hand-written form was handed the plain Swing way, which still takes what a
        // look and feel installs.
        val installedSize = handWrittenSize + 21
        val handWrittenPane = JSplitPane()
        LookAndFeel.installProperty(handWrittenPane, "dividerSize", installedSize)
        LookAndFeel.installProperty(pane, "dividerSize", installedSize)

        assertEquals(
            installedSize,
            handWrittenPane.dividerSize,
            "a look and feel should reach a pane that carries no size of its own, so that a pane which " +
                "never stopped taking one could not pass for one that did",
        )
        assertEquals(
            handWrittenSize,
            pane.dividerSize,
            "a divider sized by a withdrawal should keep that size where a look and feel installs another",
        )
    }

    @Test
    fun aOneTouchFlagGivenBackIsNoLongerTheLookAndFeelsToInstall() = runComposeSwingTest {
        var expandable by mutableStateOf<Boolean?>(true)
        setContent {
            SplitPane(oneTouchExpandable = expandable) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        val handWrittenFlag = JSplitPane().isOneTouchExpandable
        expandable = null
        awaitIdle()
        assertEquals(
            handWrittenFlag,
            pane.isOneTouchExpandable,
            "withdrawing the flag should leave the divider as its look and feel leaves one",
        )

        // Handing the choice back writes it onto the pane, and a look and feel decides the flag only
        // while the pane carries no choice of its own, so the choice handed back is where the pane
        // stays. The oracle is a pane a hand-written form was handed the plain Swing way, which still
        // takes what a look and feel installs.
        val handWrittenPane = JSplitPane()
        LookAndFeel.installProperty(handWrittenPane, "oneTouchExpandable", !handWrittenFlag)
        LookAndFeel.installProperty(pane, "oneTouchExpandable", !handWrittenFlag)

        assertEquals(
            !handWrittenFlag,
            handWrittenPane.isOneTouchExpandable,
            "a look and feel should reach a pane that carries no choice of its own, so that a pane which " +
                "never stopped taking one could not pass for one that did",
        )
        assertEquals(
            handWrittenFlag,
            pane.isOneTouchExpandable,
            "a divider left by a withdrawal should keep that choice where a look and feel installs another",
        )
    }

    @Test
    fun anUndeclaredDividerSizeIsLeftForTheLookAndFeelToInstall() = runComposeSwingTest {
        setContent {
            SplitPane {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        // A look and feel sizes a divider only while the pane carries no size of its own, so installing
        // a size is how a pane that records no choice is told from one that does.
        val installedSize = pane.dividerSize + 13
        LookAndFeel.installProperty(pane, "dividerSize", installedSize)

        assertEquals(
            installedSize,
            pane.dividerSize,
            "a pane declared without a size should still let its look and feel size the divider",
        )
    }

    @Test
    fun anUndeclaredOneTouchFlagIsLeftForTheLookAndFeelToInstall() = runComposeSwingTest {
        setContent {
            SplitPane {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        // A look and feel decides the flag only while the pane carries no choice of its own, so
        // installing a choice is how a pane that records none is told from one that does.
        val installedFlag = !pane.isOneTouchExpandable
        LookAndFeel.installProperty(pane, "oneTouchExpandable", installedFlag)

        assertEquals(
            installedFlag,
            pane.isOneTouchExpandable,
            "a pane declared without a flag should still let its look and feel decide it",
        )
    }

    @Test
    fun aDividerMoveReachesTheLatestDeclaredCallbackExactlyOnce() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        var origin by mutableStateOf("first")
        var weight by mutableStateOf(0.0)
        setContent {
            // The marker is read while composing, so declaring a new one recomposes the pane with a
            // callback built around it. A callback captured at the first composition keeps reporting
            // the marker that composition saw.
            val declaredBy = origin
            SplitPane(
                dividerLocation = 100,
                onDividerLocationChange = { reported += "$declaredBy:$it" },
                resizeWeight = weight,
            ) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        reported.clear()

        // The callback the latest composition declared is the one that fires, and it fires once - a
        // recomposition neither leaves the previous lambda attached nor adds a second registration.
        // The weight is redeclared alongside the marker so the recomposition reaches the live pane
        // and re-applies the whole declaration, the divider listener included.
        origin = "second"
        weight = 0.5
        awaitIdle()
        pane.dividerLocation = 150
        awaitIdle()

        assertEquals(listOf("second:150"), reported, "the recomposed callback should report the move once")
    }

    @Test
    fun aDividerMoveReachesTheLatestDeclaredPropertyChangeListener() = runComposeSwingTest {
        val firstMoves = mutableListOf<Int>()
        val secondMoves = mutableListOf<Int>()
        var useSecond by mutableStateOf(false)
        setContent {
            val firstListener = remember { recordingListener(firstMoves) }
            val secondListener = remember { recordingListener(secondMoves) }
            SplitPane(dividerLocationListener = if (useSecond) secondListener else firstListener) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        firstMoves.clear()

        pane.dividerLocation = 150
        awaitIdle()
        assertEquals(listOf(150), firstMoves, "the declared listener should be notified once per move")

        useSecond = true
        awaitIdle()
        pane.dividerLocation = 175
        awaitIdle()

        assertEquals(listOf(175), secondMoves, "the newly declared listener should take over")
        assertEquals(listOf(150), firstMoves, "the replaced listener should no longer be notified")
    }

    @Test
    fun thePropertyChangeListenerOverloadFollowsItsDeclaredDividerValues() = runComposeSwingTest {
        var orientation by mutableIntStateOf(JSplitPane.HORIZONTAL_SPLIT)
        var location by mutableIntStateOf(120)
        var weight by mutableStateOf(0.25)
        var expandable by mutableStateOf<Boolean?>(null)
        var size by mutableStateOf<Int?>(null)
        var showSecond by mutableStateOf(true)
        setContent {
            val listener = remember { PropertyChangeListener { } }
            SplitPane(
                dividerLocationListener = listener,
                orientation = orientation,
                dividerLocation = location,
                resizeWeight = weight,
                oneTouchExpandable = expandable,
                dividerSize = size,
            ) {
                first { Label(text = "Leading") }
                if (showSecond) {
                    second { Label(text = "Trailing") }
                }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        assertEquals(JSplitPane.HORIZONTAL_SPLIT, pane.orientation, "the pane should start on the declared axis")
        assertEquals(120, pane.dividerLocation, "the divider should start at the declared offset")
        assertEquals(0.25, pane.resizeWeight, "the pane should start at the declared weight")
        assertEquals(
            JSplitPane().isOneTouchExpandable,
            pane.isOneTouchExpandable,
            "an undeclared flag should be the look-and-feel value",
        )
        assertEquals(JSplitPane().dividerSize, pane.dividerSize, "an undeclared size should be the look-and-feel size")
        assertSame(onNodeWithText("Leading").fetch(), pane.leftComponent, "the first side should be the left component")
        assertSame(
            onNodeWithText("Trailing").fetch(),
            pane.rightComponent,
            "the second side should be the right component",
        )

        orientation = JSplitPane.VERTICAL_SPLIT
        location = 60
        weight = 0.5
        expandable = true
        size = 14
        showSecond = false
        awaitIdle()

        assertEquals(JSplitPane.VERTICAL_SPLIT, pane.orientation, "the pane should turn to the new axis")
        assertEquals(60, pane.dividerLocation, "the divider should follow the controlled offset")
        assertEquals(0.5, pane.resizeWeight, "the pane should adopt the new weight")
        assertEquals(true, pane.isOneTouchExpandable, "the declared flag should reach the divider")
        assertEquals(14, pane.dividerSize, "the declared size should reach the divider")
        assertNull(pane.rightComponent, "dropping the second side should clear it")

        expandable = false
        size = 22
        awaitIdle()

        assertEquals(false, pane.isOneTouchExpandable, "a new declared flag should reach the divider")
        assertEquals(22, pane.dividerSize, "a new declared size should reach the divider")
    }

    @Test
    fun theControlledDividerLocationIsAppliedAgainAfterReturningToItsFirstOffset() = runComposeSwingTest {
        var location by mutableIntStateOf(100)
        setContent {
            SplitPane(dividerLocation = location) {
                first { Label(text = "A") }
                second { Label(text = "B") }
            }
        }

        val pane = onNodeOfType<JSplitPane>().fetch()
        assertEquals(100, pane.dividerLocation, "the divider should start at the controlled offset")

        location = 180
        awaitIdle()
        assertEquals(180, pane.dividerLocation, "the divider should follow the controlled offset")

        location = 100
        awaitIdle()
        assertEquals(100, pane.dividerLocation, "the divider should follow back to the first offset")
    }
}
