package org.jetbrains.compose.swing.test

import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Color
import java.awt.Component
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
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
        val slot = slot()
        check.declaring(label, slot, element()) { label.name = "written" }
        check.restoring(label, slot) { }

        val failure = assertNotNull(reported.singleOrNull(), "one departing slot leaving one property wrong")
        assertContains(failure.message.orEmpty(), "name: found <null>, left <written>")
    }

    @Test
    fun aPropertyPutBackWhereTheChainFoundItIsNotReported() {
        val slot = slot()
        check.declaring(label, slot, element()) { label.name = "written" }
        check.restoring(label, slot) { label.name = null }

        assertTrue(reported.isEmpty(), "a restore that put the property back owes nothing: $reported")
    }

    @Test
    fun aLeftoverIsReportedByTheLastSlotToLeave() {
        val first = slot()
        val second = slot()
        check.declaring(label, first, element()) { label.name = "first" }
        check.declaring(label, second, element()) { label.name = "second" }

        check.restoring(label, first) { }
        assertTrue(reported.isEmpty(), "the property is still another slot's to declare: $reported")

        check.restoring(label, second) { }
        assertNotNull(reported.singleOrNull(), "only the last slot writing it reports the leftover")
        assertEquals("second", label.name)
    }

    @Test
    fun aNewDeclarationIsComparedWithTheValueFoundAfterThePreviousOneLeft() {
        // Another property remains declared, so the component stays watched while the name's
        // record is dropped and recreated by the second declaration.
        check.declaring(label, slot(), element()) { label.isEnabled = false }

        val first = slot()
        check.declaring(label, first, element()) { label.name = "first" }
        check.restoring(label, first) { label.name = null }
        // An external write after the first declaration leaves becomes the second declaration's
        // initial value.
        label.name = "elsewhere"

        val second = slot()
        check.declaring(label, second, element()) { label.name = "second" }
        check.restoring(label, second) { }

        assertNotNull(reported.singleOrNull(), "the second declaration takes a record of its own")
        assertEquals("second", label.name)
    }

    @Test
    fun everyPropertyLeftByOneWriteIsReported() {
        val slot = slot()
        check.declaring(label, slot, element()) {
            label.name = "written"
            label.isEnabled = false
        }
        check.restoring(label, slot) { }

        assertNotNull(reported.singleOrNull(), "one departing slot, one failure naming all it left")
        assertFalse(label.isEnabled)
        assertEquals("written", label.name)
    }

    @Test
    fun anAccessibleNameLeftWhereADeclarationWroteItIsReported() {
        val slot = slot()
        check.declaring(label, slot, element()) { label.accessibleContext.accessibleName = "written" }
        check.restoring(label, slot) { }

        assertNotNull(reported.singleOrNull(), "an accessible-context property is watched too")
        assertEquals("written", label.accessibleContext.accessibleName)
    }

    @Test
    fun aSlotDeclaresThePropertyItNamesEvenWhereItsWriteMovedNothing() {
        val leaving = slot()
        val standing = slot()
        check.declaring(label, leaving, element()) { label.name = "shared" }
        // The second declaration names the property even though the reads before and after its
        // write see the same value.
        check.declaring(label, standing, element(name = "name")) { label.name = "shared" }

        check.restoring(label, leaving) { }

        assertTrue(
            reported.isEmpty(),
            "the remaining slot still declares the name, so no leftover is reported: $reported",
        )
    }

    @Test
    fun aPropertyASlotHoldsWithoutMovingIsNotOwedByASlotLeavingBesideIt() {
        label.name = "found"
        val holding = slot()
        // The coarse declaration names the property even though its write leaves the value unchanged.
        check.declaring(label, holding, element(heldProperties = setOf("name"))) { label.name = "found" }
        // Something outside the modifier moves the name after the modifier has taken its record of it.
        label.name = "elsewhere"

        val moving = slot()
        check.declaring(label, moving, element(name = "name")) { label.name = "moved" }
        // Restoration uses the value found by the first declaration, including when that
        // declaration did not change it.
        check.restoring(label, moving) { label.name = "found" }

        assertTrue(
            reported.isEmpty(),
            "the coarse declaration still declares the name, so no leftover is reported: $reported",
        )
    }

    @Test
    fun aPropertyASlotThatPutsNothingBackStillDeclaresIsNotYetOwed() {
        val leaving = slot()
        val standing = slot()
        check.declaring(label, leaving, element()) { label.name = "leaving" }
        check.declaring(label, standing, element(restores = RestorePolicy.None)) { label.name = "standing" }

        // The remaining declaration is reapplied after restoration, so its value remains until
        // that declaration leaves too.
        check.restoring(label, leaving) { label.name = "standing" }

        assertTrue(reported.isEmpty(), "a property another slot still declares is not yet owed: $reported")
    }

    @Test
    fun anElementThatPutsBackNoDerivedPropertyIsHeldToTheOneItNames() {
        val slot = slot()
        check.declaring(label, slot, element(name = "name", restores = RestorePolicy.DeclaredPropertyOnly)) {
            label.name = "written"
            // DeclaredPropertyOnly excludes changes to properties other than the declared name.
            label.isEnabled = false
        }
        check.restoring(label, slot) { }

        val failure = assertNotNull(reported.singleOrNull(), "the property the element names is still owed")
        assertEquals("written", label.name)
        assertFalse("enabled" in failure.message.orEmpty(), "a derived property is not owed: ${failure.message}")
    }

    @Test
    fun aReadThatThrowsIsReportedOncePerDeclaration() {
        val throwing = ThrowingLabel()
        throwing.throwing = true

        check.declaring(throwing, slot(), element()) { }

        val failure = assertNotNull(reported.singleOrNull(), "one declaration, one report: $reported")
        assertContains(failure.message.orEmpty(), "threw")
    }

    @Test
    fun anElementThatPutsNothingBackIsNotHeldToIt() {
        val slot = slot()
        check.declaring(label, slot, element(restores = RestorePolicy.None)) { label.name = "installed" }
        check.restoring(label, slot) { }

        assertTrue(reported.isEmpty(), "an element that says it restores nothing is not held to it: $reported")
    }

    @Test
    fun aSlotOwesNothingForAPropertyItsWriteLeftAsItFoundIt() {
        label.name = "standing"
        val slot = slot()
        check.declaring(label, slot, element()) { label.name = "standing" }
        // Something outside the modifier moves the name the slot never wrote; its removal owes nothing for it.
        label.name = "elsewhere"
        check.restoring(label, slot) { }

        assertEquals(emptyList(), reported, "a write that changed nothing takes no record")
    }

    @Test
    fun aPropertyTheComponentsOwnStateMovedIsNotOwedByTheDepartingSlot() {
        val panel = OutsetPanel()
        val slot = slot()
        check.declaring(panel, slot, element()) { panel.border = EmptyBorder(2, 2, 2, 2) }
        panel.outset = 3
        check.restoring(panel, slot) { panel.border = null }

        assertTrue(reported.isEmpty(), "the insets the panel's own state moved are not the slot's: $reported")
    }

    @Test
    fun aPropertyMovedElsewhereBeforeAnotherSlotWritesIsNotOwedByTheDepartingSlot() {
        val panel = OutsetPanel()
        val slot = slot()
        check.declaring(panel, slot, element()) { panel.border = EmptyBorder(2, 2, 2, 2) }
        panel.outset = 3
        // The move is seen ahead of this write, not ahead of the restore that follows it.
        check.declaring(panel, slot(), element()) { panel.isEnabled = false }
        check.restoring(panel, slot) { panel.border = null }

        assertTrue(reported.isEmpty(), "the insets the panel's own state moved are not the slot's: $reported")
    }

    @Test
    fun aPropertyNothingElseMovedIsStillOwedByTheDepartingSlot() {
        val panel = OutsetPanel()
        val slot = slot()
        check.declaring(panel, slot, element()) { panel.border = EmptyBorder(2, 2, 2, 2) }
        check.restoring(panel, slot) { }

        val failure = assertNotNull(reported.singleOrNull(), "a slot that never restores is still caught")
        assertContains(failure.message.orEmpty(), "insets: found <${Insets(0, 0, 0, 0)}>")
    }

    @Test
    fun aMissedRestoreOfAPropertyMovedElsewhereGoesUnreported() {
        val panel = OutsetPanel()
        val slot = slot()
        check.declaring(panel, slot, element()) { panel.border = EmptyBorder(2, 2, 2, 2) }
        panel.outset = 3
        check.restoring(panel, slot) { }

        val failure = assertNotNull(reported.singleOrNull(), "the border the slot left is still reported")
        assertFalse("insets" in failure.message.orEmpty(), "the moved insets are no slot's: ${failure.message}")
    }

    @Test
    fun aPropertyMovedElsewhereIsOwedAgainOnceItsRecordIsDropped() {
        val panel = OutsetPanel()
        // Another property remains declared, so the panel stays watched between the two declarations.
        check.declaring(panel, slot(), element()) { panel.isEnabled = false }
        val first = slot()
        check.declaring(panel, first, element()) { panel.border = EmptyBorder(2, 2, 2, 2) }
        panel.outset = 3
        check.restoring(panel, first) { panel.border = null }

        val second = slot()
        check.declaring(panel, second, element()) { panel.border = EmptyBorder(2, 2, 2, 2) }
        check.restoring(panel, second) { }

        val failure = assertNotNull(reported.singleOrNull(), "a later declaration takes a record of its own")
        assertContains(failure.message.orEmpty(), "insets: found <${Insets(3, 3, 3, 3)}>")
    }

    @Test
    fun aPlacementAxisMovedElsewhereIsStillOwedByTheDepartingSlot() {
        val slot = slot()
        check.declaring(label, slot, element()) { label.setSize(10, 10) }
        label.setSize(20, 10)
        check.restoring(label, slot) { }

        val failure = assertNotNull(reported.singleOrNull(), "a layout pass moves the axes, so they stay owed")
        assertContains(failure.message.orEmpty(), "width: found <0>, left <20>")
    }

    @Test
    fun aPropertyOtherThanTheInsetsMovedElsewhereIsStillOwedByTheDepartingSlot() {
        val original = label.background
        val slot = slot()
        check.declaring(label, slot, element()) { label.background = Color.RED }
        label.background = Color.BLUE
        check.restoring(label, slot) { }

        val failure = assertNotNull(reported.singleOrNull(), "only the insets are exempted when moved elsewhere")
        assertContains(failure.message.orEmpty(), "background: found <$original>, left <${Color.BLUE}>")
    }

    private fun slot() = SwingModifier.ComponentNode<Component>()

    private fun element(
        restores: RestorePolicy = RestorePolicy.EverythingWritten,
        name: String = "standIn",
        heldProperties: Set<String> = setOf(name),
    ) = StandInElement(restores, name, heldProperties)
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
