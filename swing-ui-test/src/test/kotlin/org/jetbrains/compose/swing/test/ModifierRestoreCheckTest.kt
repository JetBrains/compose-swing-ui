// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.test

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.compose.swing.modifier.ModifierInfo
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Color
import java.awt.Component
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.border.EmptyBorder
import javax.swing.table.TableColumn
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What [ModifierRestoreCheck] holds a modifier to, driven through its own two calls rather than through a
 * composition. The library's own tests cover that a real composition reaches it.
 */
class ModifierRestoreCheckTest {
    private val reported = mutableListOf<Throwable>()
    private val check = ModifierRestoreCheck(reported::add)
    private val label = JLabel("target")

    @Test
    fun aPropertyLeftWhereADeclarationWroteItIsReported() {
        val slot = declaration(label, element())
        check.declaring(slot) { label.name = "written" }
        check.restoring(slot) { }

        val failure = assertNotNull(reported.singleOrNull(), "one departing slot leaving one property wrong")
        assertContains(failure.message.orEmpty(), "name: found <null>, left <written>")
    }

    @Test
    fun aPropertyPutBackWhereTheChainFoundItIsNotReported() {
        val slot = declaration(label, element())
        check.declaring(slot) { label.name = "written" }
        check.restoring(slot) { label.name = null }

        assertTrue(reported.isEmpty(), "a restore that put the property back owes nothing: $reported")
    }

    @Test
    fun aLeftoverIsReportedByTheLastSlotToLeave() {
        val first = declaration(label, element())
        check.declaring(first) { label.name = "first" }
        val second = declaration(label, element())
        check.declaring(second) { label.name = "second" }

        check.restoring(first) { }
        assertTrue(reported.isEmpty(), "the property is still another slot's to declare: $reported")

        check.restoring(second) { }
        assertNotNull(reported.singleOrNull(), "only the last slot writing it reports the leftover")
        assertEquals("second", label.name)
    }

    @Test
    fun aNewDeclarationIsComparedWithTheValueFoundAfterThePreviousOneLeft() {
        // Another property remains declared, so the component stays watched while the name's
        // record is dropped and recreated by the second declaration.
        check.declaring(declaration(label, element())) { label.isEnabled = false }

        val first = declaration(label, element())
        check.declaring(first) { label.name = "first" }
        check.restoring(first) { label.name = null }
        // An external write after the first declaration leaves becomes the second declaration's
        // initial value.
        label.name = "elsewhere"

        val second = declaration(label, element())
        check.declaring(second) { label.name = "second" }
        check.restoring(second) { }

        assertNotNull(reported.singleOrNull(), "the second declaration takes a record of its own")
        assertEquals("second", label.name)
    }

    @Test
    fun everyPropertyLeftByOneWriteIsReported() {
        val slot = declaration(label, element())
        check.declaring(slot) {
            label.name = "written"
            label.isEnabled = false
        }
        check.restoring(slot) { }

        assertNotNull(reported.singleOrNull(), "one departing slot, one failure naming all it left")
        assertFalse(label.isEnabled)
        assertEquals("written", label.name)
    }

    @Test
    fun anAccessibleNameLeftWhereADeclarationWroteItIsReported() {
        val slot = declaration(label, element())
        check.declaring(slot) { label.accessibleContext.accessibleName = "written" }
        check.restoring(slot) { }

        assertNotNull(reported.singleOrNull(), "an accessible-context property is watched too")
        assertEquals("written", label.accessibleContext.accessibleName)
    }

    @Test
    fun aSlotDeclaresThePropertyItNamesEvenWhereItsWriteMovedNothing() {
        val leaving = declaration(label, element())
        check.declaring(leaving) { label.name = "shared" }
        // The second declaration names the property even though the reads before and after its
        // write see the same value.
        val standing = declaration(label, element(name = "name"))
        check.declaring(standing) { label.name = "shared" }

        check.restoring(leaving) { }

        assertTrue(
            reported.isEmpty(),
            "the remaining slot still declares the name, so no leftover is reported: $reported",
        )
    }

    @Test
    fun aPropertyASlotHoldsWithoutMovingIsNotOwedByASlotLeavingBesideIt() {
        label.name = "found"
        // The coarse declaration names the property even though its write leaves the value unchanged.
        val holding = declaration(label, element(heldProperties = setOf("name")))
        check.declaring(holding) { label.name = "found" }
        // Something outside the modifier moves the name after the modifier has taken its record of it.
        label.name = "elsewhere"

        val moving = declaration(label, element(name = "name"))
        check.declaring(moving) { label.name = "moved" }
        // Restoration uses the value found by the first declaration, including when that
        // declaration did not change it.
        check.restoring(moving) { label.name = "found" }

        assertTrue(
            reported.isEmpty(),
            "the coarse declaration still declares the name, so no leftover is reported: $reported",
        )
    }

    @Test
    fun aPropertyASlotThatPutsNothingBackStillDeclaresIsNotYetOwed() {
        val leaving = declaration(label, element())
        check.declaring(leaving) { label.name = "leaving" }
        val standing = declaration(label, element(restores = RestorePolicy.None))
        check.declaring(standing) { label.name = "standing" }

        // The remaining declaration is reapplied after restoration, so its value remains until
        // that declaration leaves too.
        check.restoring(leaving) { label.name = "standing" }

        assertTrue(reported.isEmpty(), "a property another slot still declares is not yet owed: $reported")
    }

    @Test
    fun anElementThatPutsBackNoDerivedPropertyIsHeldToTheOneItNames() {
        val report = mockk<(Throwable) -> Unit>(relaxed = true)
        val restoreCheck = ModifierRestoreCheck(report)
        val slot = declaration(label, element(name = "name", restores = RestorePolicy.DeclaredPropertyOnly))
        restoreCheck.declaring(slot) {
            label.name = "written"
            // DeclaredPropertyOnly excludes changes to properties other than the declared name.
            label.isEnabled = false
        }
        restoreCheck.restoring(slot) { }

        val failures = mutableListOf<Throwable>()
        verify(exactly = 1) { report(capture(failures)) }
        assertEquals("written", label.name)
        assertFalse("enabled" in failures.single().message.orEmpty(), "a derived property is not owed")
    }

    @Test
    fun aReadThatThrowsIsReportedOncePerDeclaration() {
        val throwing = ThrowingLabel()
        throwing.throwing = true

        check.declaring(declaration(throwing, element())) { }

        val failure = assertNotNull(reported.singleOrNull(), "one declaration, one report: $reported")
        assertContains(failure.message.orEmpty(), "threw")
    }

    @Test
    fun anElementThatPutsNothingBackIsNotHeldToIt() {
        val slot = declaration(label, element(restores = RestorePolicy.None))
        check.declaring(slot) { label.name = "installed" }
        check.restoring(slot) { }

        assertTrue(reported.isEmpty(), "an element that says it restores nothing is not held to it: $reported")
    }

    @Test
    fun aSlotOwesNothingForAPropertyItsWriteLeftAsItFoundIt() {
        label.name = "standing"
        val slot = declaration(label, element())
        check.declaring(slot) { label.name = "standing" }
        // Something outside the modifier moves the name the slot never wrote; its removal owes nothing for it.
        label.name = "elsewhere"
        check.restoring(slot) { }

        assertEquals(emptyList(), reported, "a write that changed nothing takes no record")
    }

    @Test
    fun aPropertyTheComponentsOwnStateMovedIsNotOwedByTheDepartingSlot() {
        val panel = OutsetPanel()
        val slot = declaration(panel, element())
        check.declaring(slot) { panel.border = EmptyBorder(2, 2, 2, 2) }
        panel.outset = 3
        check.restoring(slot) { panel.border = null }

        assertTrue(reported.isEmpty(), "the insets the panel's own state moved are not the slot's: $reported")
    }

    @Test
    fun aPropertyMovedElsewhereBeforeAnotherSlotWritesIsNotOwedByTheDepartingSlot() {
        val panel = OutsetPanel()
        val slot = declaration(panel, element())
        check.declaring(slot) { panel.border = EmptyBorder(2, 2, 2, 2) }
        panel.outset = 3
        // The move is seen ahead of this write, not ahead of the restore that follows it.
        check.declaring(declaration(panel, element())) { panel.isEnabled = false }
        check.restoring(slot) { panel.border = null }

        assertTrue(reported.isEmpty(), "the insets the panel's own state moved are not the slot's: $reported")
    }

    @Test
    fun aPropertyNothingElseMovedIsReportedWhenTheDepartingSlotLeavesIt() {
        val report = mockk<(Throwable) -> Unit>(relaxed = true)
        val restoreCheck = ModifierRestoreCheck(report)
        val panel = OutsetPanel()
        val slot = declaration(panel, element())
        restoreCheck.declaring(slot) { panel.border = EmptyBorder(2, 2, 2, 2) }
        restoreCheck.restoring(slot) { }

        val failures = mutableListOf<Throwable>()
        verify(exactly = 1) { report(capture(failures)) }
        assertContains(failures.single().message.orEmpty(), "insets: found <${Insets(0, 0, 0, 0)}>")
    }

    @Test
    fun aMissedRestoreOfAPropertyMovedElsewhereGoesUnreported() {
        val panel = OutsetPanel()
        val slot = declaration(panel, element())
        check.declaring(slot) { panel.border = EmptyBorder(2, 2, 2, 2) }
        panel.outset = 3
        check.restoring(slot) { }

        val failure = assertNotNull(reported.singleOrNull(), "the border the slot left is still reported")
        assertFalse("insets" in failure.message.orEmpty(), "the moved insets are no slot's: ${failure.message}")
    }

    @Test
    fun aPropertyMovedElsewhereIsOwedAgainOnceItsRecordIsDropped() {
        val panel = OutsetPanel()
        // Another property remains declared, so the panel stays watched between the two declarations.
        check.declaring(declaration(panel, element())) { panel.isEnabled = false }
        val first = declaration(panel, element())
        check.declaring(first) { panel.border = EmptyBorder(2, 2, 2, 2) }
        panel.outset = 3
        check.restoring(first) { panel.border = null }

        val second = declaration(panel, element())
        check.declaring(second) { panel.border = EmptyBorder(2, 2, 2, 2) }
        check.restoring(second) { }

        val failure = assertNotNull(reported.singleOrNull(), "a later declaration takes a record of its own")
        assertContains(failure.message.orEmpty(), "insets: found <${Insets(3, 3, 3, 3)}>")
    }

    @Test
    fun aPropertyALaterWriteOfASlotIsTheFirstToLandOnIsStillOwed() {
        val report = mockk<(Throwable) -> Unit>(relaxed = true)
        val restoreCheck = ModifierRestoreCheck(report)
        val slot = declaration(label, element())
        restoreCheck.declaring(slot) { label.isEnabled = false }
        restoreCheck.declaring(slot) { label.name = "written" }
        restoreCheck.restoring(slot) { label.isEnabled = true }

        val failures = mutableListOf<Throwable>()
        verify(exactly = 1) { report(capture(failures)) }
        assertContains(failures.single().message.orEmpty(), "name: found <null>, left <written>")
    }

    @Test
    fun aPropertyALaterWriteLandsOnAfterAFirstReadThatThrewIsStillOwed() {
        val report = mockk<(Throwable) -> Unit>(relaxed = true)
        val restoreCheck = ModifierRestoreCheck(report)
        val throwing = ThrowingLabel()
        throwing.name = "standing"
        throwing.throwing = true
        val slot = declaration(throwing, element())
        restoreCheck.declaring(slot) { }
        throwing.throwing = false

        restoreCheck.declaring(slot) { throwing.name = "written" }
        restoreCheck.restoring(slot) { }

        val failures = mutableListOf<Throwable>()
        verify(exactly = 2) { report(capture(failures)) }
        assertContains(failures.last().message.orEmpty(), "name: found <standing>, left <written>")
    }

    @Test
    fun aPropertyDerivedFromASlotsOwnDeclarationBeforeItsWriteLandsOnItIsNotOwed() {
        val report = mockk<(Throwable) -> Unit>(relaxed = true)
        val restoreCheck = ModifierRestoreCheck(report)
        val pane = JTabbedPane()
        val original = pane.background
        val slot = declaration(pane, element())
        restoreCheck.declaring(slot) { pane.background = Color.MAGENTA }
        // The tab declares no color, so the pane answers its color with the one the slot declared.
        pane.addTab("tab", JLabel())

        restoreCheck.declaring(slot) { pane.background = Color.RED }
        restoreCheck.restoring(slot) { pane.background = original }

        verify(exactly = 0) { report(any()) }
    }

    @TestFactory
    fun aPropertyMovedElsewhereIsReportedWhenTheDepartingSlotLeavesIt(): List<DynamicTest> = listOf(
        unrestoredIsReported(
            "a placement axis, which a layout pass moves",
            JLabel("target"),
            "width: found <0>, left <20>",
        ) { write ->
            write { setSize(10, 10) }
            setSize(20, 10)
        },
        unrestoredIsReported(
            "a placement axis a later write is the first to land on",
            JLabel("target"),
            "x: found <5>, left <20>",
        ) { write ->
            write { setLocation(0, 0) }
            setLocation(5, 5)
            write { setLocation(20, 20) }
        },
        unrestoredIsReported(
            "a color",
            JLabel("target").apply { background = Color.GREEN },
            "background: found <${Color.GREEN}>, left <${Color.BLUE}>",
        ) { write ->
            write { background = Color.RED }
            background = Color.BLUE
        },
        unrestoredIsReported(
            "a value of a tab that still stands",
            JTabbedPane().apply { addTab("tab", JLabel()) },
            "tabTitles: found <[tab]>, left <[elsewhere]>",
        ) { write ->
            write { setTitleAt(0, "written") }
            setTitleAt(0, "elsewhere")
        },
    )

    @TestFactory
    fun aSelectionGrownByTheComponentIsReportedWhenTheDepartingSlotLeavesIt(): List<DynamicTest> = listOf(
        unrestoredIsReported(
            "a selection a write landed on, grown with every item still standing",
            JList(arrayOf("a", "b", "c")).apply { selectedIndex = 0 },
            "selectedIndices: found <[0]>, left <[1, 2]>",
        ) { write ->
            write { selectedIndex = 1 }
            addSelectionInterval(2, 2)
        },
        unrestoredIsReported(
            "a selection a declaration holds, grown with every item still standing",
            JList(arrayOf("a", "b", "c")).apply { selectedIndex = 0 },
            "selectedIndices: found <[0]>, left <[1, 2]>",
            heldProperties = setOf("selectedIndices"),
        ) { write ->
            write { selectedIndex = 1 }
            addSelectionInterval(2, 2)
        },
    )

    @TestFactory
    fun aPropertyWhoseElementsChangedSinceIsReportedWhenTheDepartingSlotLeavesIt(): List<DynamicTest> = listOf(
        unrestoredIsReported(
            "a tab's color, with a tab arrived since",
            JTabbedPane().apply { addTab("tab", JLabel()) },
            "tabBackgrounds: found <[null]>, left <[${Color.RED}, null]>",
        ) { write ->
            write { setBackgroundAt(0, Color.RED) }
            addTab("second", JLabel())
        },
        unrestoredIsReported(
            "a tab's title, with a tab gone since",
            JTabbedPane().apply {
                addTab("tab", JLabel())
                addTab("second", JLabel())
            },
            "tabTitles: found <[tab, second]>, left <[written]>",
        ) { write ->
            write { setTitleAt(0, "written") }
            removeTabAt(1)
        },
        unrestoredIsReported(
            "a tab's title a declaration holds, with a tab arrived since",
            JTabbedPane().apply { addTab("tab", JLabel()) },
            "tabTitles: found <[tab]>, left <[written, second]>",
            heldProperties = setOf("tabTitles"),
        ) { write ->
            write { setTitleAt(0, "written") }
            addTab("second", JLabel())
        },
        unrestoredIsReported(
            "a tab's tooltip a later write is the first to land on, with a tab arrived before it",
            JTabbedPane().apply { addTab("tab", JLabel()) },
            "tabToolTips: found <[null, null]>, left <[written, null]>",
        ) { write ->
            write { setTitleAt(0, "tab") }
            addTab("second", JLabel())
            write { setToolTipTextAt(0, "written") }
        },
        unrestoredIsReported(
            "a column's header, with a column arrived since",
            JTable(1, 1),
            "columnHeaders: found <[A]>, left <[written, null]>",
        ) { write ->
            write { columnModel.getColumn(0).headerValue = "written" }
            columnModel.addColumn(TableColumn(0))
        },
    )

    @Test
    fun aTabColorWrittenEqualToThePanesIsNotSeenUntilThePaneMoves() {
        val report = mockk<(Throwable) -> Unit>(relaxed = true)
        val restoreCheck = ModifierRestoreCheck(report)
        val pane = JTabbedPane().apply { addTab("tab", JLabel()) }
        val slot = declaration(pane, element())
        restoreCheck.declaring(slot) { pane.setBackgroundAt(0, pane.background) }
        restoreCheck.restoring(slot) { }

        verify(exactly = 0) { report(any()) }
    }

    @Test
    fun aPropertyWhoseElementsAWriteAddedIsReportedWhenTheDepartingSlotLeavesIt() {
        val report = mockk<(Throwable) -> Unit>(relaxed = true)
        val restoreCheck = ModifierRestoreCheck(report)
        val pane = JTabbedPane()
        val slot = declaration(pane, element())
        restoreCheck.declaring(slot) { pane.addTab("written", JLabel()) }
        restoreCheck.restoring(slot) { }

        val failures = mutableListOf<Throwable>()
        verify(exactly = 1) { report(capture(failures)) }
        assertContains(failures.single().message.orEmpty(), "tabTitles: found <[]>, left <[written]>")
    }

    @TestFactory
    fun aDeclarationIsHeldToTheElementItHasWhenItLeaves(): List<DynamicTest> = listOf(
        Triple("an element updated to restore nothing", RestorePolicy.EverythingWritten to RestorePolicy.None, 0),
        Triple("an element updated to restore everything", RestorePolicy.None to RestorePolicy.EverythingWritten, 1),
    ).map { (name, policies, reports) ->
        dynamicTest(name) {
            val label = JLabel("target")
            val report = mockk<(Throwable) -> Unit>(relaxed = true)
            val restoreCheck = ModifierRestoreCheck(report)
            var current = element(policies.first)
            val slot = declaration(label, element()).also { every { it.modifier } answers { current } }
            restoreCheck.declaring(slot) { label.name = "written" }
            current = element(policies.second)
            restoreCheck.declaring(slot) { label.name = "written" }
            restoreCheck.restoring(slot) { }

            verify(exactly = reports) { report(any()) }
        }
    }

    @Test
    fun aDeclarationIsHeldToTheElementItHadBeforeItsRestoreRan() {
        val report = mockk<(Throwable) -> Unit>(relaxed = true)
        val restoreCheck = ModifierRestoreCheck(report)
        var current = element(RestorePolicy.EverythingWritten)
        val slot = declaration(label, element()).also { every { it.modifier } answers { current } }
        restoreCheck.declaring(slot) { label.name = "written" }
        restoreCheck.restoring(slot) { current = element(RestorePolicy.None) }

        val failures = mutableListOf<Throwable>()
        verify(exactly = 1) { report(capture(failures)) }
        assertContains(failures.single().message.orEmpty(), "name: found <null>, left <written>")
    }

    /** A case where a slot whose [changes] write on [component] and put nothing back leaves [expected] standing. */
    private fun <T : Component> unrestoredIsReported(
        name: String,
        component: T,
        expected: String,
        heldProperties: Set<String> = emptySet(),
        changes: T.(write: (T.() -> Unit) -> Unit) -> Unit,
    ) = dynamicTest(name) {
        val report = mockk<(Throwable) -> Unit>(relaxed = true)
        val restoreCheck = ModifierRestoreCheck(report)
        val slot = declaration(component, element(heldProperties = heldProperties))
        component.changes { write ->
            restoreCheck.declaring(slot) { component.write() }
        }
        restoreCheck.restoring(slot) { }

        val failures = mutableListOf<Throwable>()
        verify(exactly = 1) { report(capture(failures)) }
        assertContains(failures.single().message.orEmpty(), expected)
    }

    private fun element(
        restores: RestorePolicy = RestorePolicy.EverythingWritten,
        name: String = "standIn",
        heldProperties: Set<String> = setOf(name),
    ) = StandInElement(restores, name, heldProperties)
}

/**
 * The installed declaration a case drives the check with: a mock whose identity is its own, over a replaceable
 * element.
 */
private fun declaration(
    component: Component,
    modifier: SwingModifier.NodeElement<*, *>,
): ModifierInfo {
    val info = mockk<ModifierInfo>()
    every { info.component } returns component
    every { info.modifier } returns modifier
    return info
}

/** A panel whose insets follow its own [outset] as well as its border, with no modifier writing them. */
private class OutsetPanel : JPanel() {
    var outset: Int = 0

    override fun getInsets(): Insets =
        super.getInsets().apply { set(top + outset, left + outset, bottom + outset, right + outset) }
}

/** A component whose own accessor throws, standing for the caller code a watched read runs. */
private class ThrowingLabel : JLabel("target") {
    var throwing: Boolean = false

    override fun getText(): String = if (throwing) error("the caller's own read threw") else super.getText()
}

/** An element standing for whichever declaration a case drives the check with. */
private class StandInElement(
    override val restores: RestorePolicy,
    override val name: String,
    override val heldProperties: Set<String>,
) : SwingModifier.NodeElement<Component, SwingModifier.ComponentNode<Component>>() {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): SwingModifier.ComponentNode<Component> = SwingModifier.ComponentNode()

    override fun update(node: SwingModifier.ComponentNode<Component>): Unit = Unit

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}
