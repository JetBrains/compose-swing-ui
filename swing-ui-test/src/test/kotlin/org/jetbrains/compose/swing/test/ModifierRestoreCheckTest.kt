package org.jetbrains.compose.swing.test

import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import javax.swing.JLabel
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
    fun aPropertyAnotherSlotStillWritesIsAnsweredForByTheLastToLeave() {
        val first = slot()
        val second = slot()
        check.declaring(label, first, element()) { label.name = "first" }
        check.declaring(label, second, element()) { label.name = "second" }

        check.restoring(label, first) { }
        assertTrue(reported.isEmpty(), "the property is still another slot's to declare: $reported")

        check.restoring(label, second) { }
        val failure = assertNotNull(reported.singleOrNull(), "the last slot writing it answers for it")
        assertContains(failure.message.orEmpty(), "name: found <null>, left <second>")
    }

    @Test
    fun aDeclarationMadeAgainIsAnsweredAgainstWhatTheChainFindsByThen() {
        // A slot writing something else stands throughout, so the component goes on being watched and
        // what answers the second declaration is the record the first left rather than a fresh watch.
        check.declaring(label, slot(), element()) { label.isEnabled = false }

        val first = slot()
        check.declaring(label, first, element()) { label.name = "first" }
        check.restoring(label, first) { label.name = null }
        // Something outside the modifier moves the name once the first declaration has gone, so the value
        // the second is answered against is not the one the first was.
        label.name = "elsewhere"

        val second = slot()
        check.declaring(label, second, element()) { label.name = "second" }
        check.restoring(label, second) { }

        val failure = assertNotNull(reported.singleOrNull(), "the second declaration takes a record of its own")
        assertContains(failure.message.orEmpty(), "name: found <elsewhere>, left <second>")
    }

    @Test
    fun everyPropertyOneWriteLandsOnIsAnsweredFor() {
        val slot = slot()
        check.declaring(label, slot, element()) {
            label.name = "written"
            label.isEnabled = false
        }
        check.restoring(label, slot) { }

        val failure = assertNotNull(reported.singleOrNull(), "one departing slot, one failure naming all it left")
        assertContains(failure.message.orEmpty(), "enabled: found <true>, left <false>")
        assertContains(failure.message.orEmpty(), "name: found <null>, left <written>")
    }

    @Test
    fun anAccessibleNameLeftWhereADeclarationWroteItIsReported() {
        val slot = slot()
        check.declaring(label, slot, element()) { label.accessibleContext.accessibleName = "written" }
        check.restoring(label, slot) { }

        val failure = assertNotNull(reported.singleOrNull(), "what a context answers with is watched too")
        assertContains(failure.message.orEmpty(), "accessibleName: found <target>, left <written>")
    }

    @Test
    fun aSlotDeclaresThePropertyItNamesEvenWhereItsWriteMovedNothing() {
        val leaving = slot()
        val standing = slot()
        check.declaring(label, leaving, element()) { label.name = "shared" }
        // The second declaration writes the value the first left standing, so there is nothing around
        // it for a read to see - and it is what declares the name until it leaves.
        check.declaring(label, standing, element(name = "name")) { label.name = "shared" }

        check.restoring(label, leaving) { }

        assertTrue(reported.isEmpty(), "the name is still the standing declaration's to answer for: $reported")
    }

    @Test
    fun aPropertyASlotHoldsWithoutMovingIsNotOwedByASlotLeavingBesideIt() {
        label.name = "found"
        val holding = slot()
        // A coarse declaration whose write covers the name as well, and finds it already standing there.
        check.declaring(label, holding, element(heldProperties = setOf("name"))) { label.name = "found" }
        // Something outside the modifier moves the name after the modifier has taken its record of it.
        label.name = "elsewhere"

        val moving = slot()
        check.declaring(label, moving, element(name = "name")) { label.name = "moved" }
        // The modifier hands the name back to where the coarse declaration found it, not to where this
        // slot did, because that declaration holds it too.
        check.restoring(label, moving) { label.name = "found" }

        assertTrue(reported.isEmpty(), "the name is still the coarse declaration's to answer for: $reported")
    }

    @Test
    fun aPropertyASlotThatPutsNothingBackStillDeclaresIsNotYetOwed() {
        val leaving = slot()
        val standing = slot()
        check.declaring(label, leaving, element()) { label.name = "leaving" }
        check.declaring(label, standing, element(restores = RestorePolicy.None)) { label.name = "standing" }

        // The restore hands the name back and the standing declaration is written over it again, which
        // is the modifier's answer for the name until that declaration leaves as well.
        check.restoring(label, leaving) { label.name = "standing" }

        assertTrue(reported.isEmpty(), "a property another slot still declares is not yet owed: $reported")
    }

    @Test
    fun anElementThatPutsBackNoDerivedPropertyIsHeldToTheOneItNames() {
        val slot = slot()
        check.declaring(label, slot, element(name = "name", restores = RestorePolicy.DeclaredPropertyOnly)) {
            label.name = "written"
            // What a look and feel works out from the write, which the departing slot does not answer for.
            label.isEnabled = false
        }
        check.restoring(label, slot) { }

        val failure = assertNotNull(reported.singleOrNull(), "the property the element names is still owed")
        assertContains(failure.message.orEmpty(), "name: found <null>, left <written>")
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
    fun aSlotAnswersForNoPropertyItsOwnWriteLeftAsItFoundIt() {
        label.name = "standing"
        val slot = slot()
        check.declaring(label, slot, element()) { label.name = "standing" }
        // Something outside the modifier moves the name the slot never wrote; its removal owes nothing for it.
        label.name = "elsewhere"
        check.restoring(label, slot) { }

        assertEquals(emptyList(), reported, "a write that changed nothing takes no record")
    }

    private fun slot() = SwingModifier.ComponentNode<Component>()

    private fun element(
        restores: RestorePolicy = RestorePolicy.EverythingWritten,
        name: String = "standIn",
        heldProperties: Set<String> = setOf(name),
    ) = StandInElement(restores, name, heldProperties)
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
