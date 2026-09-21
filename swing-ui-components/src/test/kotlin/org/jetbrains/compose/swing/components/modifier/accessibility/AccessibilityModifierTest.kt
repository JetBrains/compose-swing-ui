// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.modifier.accessibility

import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.countTests
import org.jetbrains.compose.swing.fixtures.components.modifier.accessibility.LabelForFixture
import org.jetbrains.compose.swing.fixtures.components.modifier.accessibility.LabelTargetFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.rawSwingTests
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.TestFactory
import javax.swing.JLabel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertSame

/** Behavioral and chain coverage for the accessibility builders the component catalog adds on top of the core ones. */
class AccessibilityModifierTest {
    @Test
    fun labelForAssociatesLabelWithItsTarget() = runComposeSwingTest {
        setContent {
            val usernameField = rememberLabelTarget()
            Label("Name", modifier = SwingModifier.labelFor(usernameField))
            TextField("", onValueChange = {}, modifier = SwingModifier.labelTarget(usernameField))
        }
        awaitIdle()
        assertSame(
            onNodeOfType<JTextField>().fetch(),
            onNodeWithText("Name").fetch<JLabel>().labelFor,
            "the caption should be associated with the field the reference names",
        )
    }

    @Test
    fun labelForResolvesRegardlessOfDeclarationOrder() = runComposeSwingTest {
        setContent {
            val usernameField = rememberLabelTarget()
            // The target is declared before its label; the pairing still resolves once both attach.
            TextField("", onValueChange = {}, modifier = SwingModifier.labelTarget(usernameField))
            Label("Name", modifier = SwingModifier.labelFor(usernameField))
        }
        awaitIdle()
        assertSame(
            onNodeOfType<JTextField>().fetch(),
            onNodeWithText("Name").fetch<JLabel>().labelFor,
            "the caption should be associated with the field the reference names",
        )
    }

    @Test
    fun aCaptionCanItselfBeCaptioned() = runComposeSwingTest {
        setContent {
            val field = rememberLabelTarget()
            val caption = rememberLabelTarget()
            // The middle label captions the field and is itself the outer label's captioned target.
            Label("Section", modifier = SwingModifier.labelFor(caption))
            Label("Name", modifier = SwingModifier.labelFor(field).labelTarget(caption))
            TextField("", onValueChange = {}, modifier = SwingModifier.labelTarget(field))
        }
        awaitIdle()
        val name = onNodeWithText("Name").fetch<JLabel>()
        assertSame(
            onNodeOfType<JTextField>().fetch(),
            name.labelFor,
            "the caption should be associated with the field it captions",
        )
        assertSame(
            name,
            onNodeWithText("Section").fetch<JLabel>().labelFor,
            "the outer caption should be associated with the label it captions",
        )
    }

    // Tests the label builders and their declared chain.
    @Test
    fun everyAccessibilityBuilderAppendsToTheChainWithoutRepeatingIt() {
        assertDeclaredChainCarriedOnce { labelTarget(LabelTarget()) }
        assertDeclaredChainCarriedOnce { labelFor(LabelTarget()) }
    }

    @TestFactory
    fun labelForRawSwing() = LabelForFixture.rawSwingTests()

    @TestFactory
    fun labelForCounts() = LabelForFixture.countTests()

    @TestFactory
    fun labelTargetRawSwing() = LabelTargetFixture.rawSwingTests()

    @TestFactory
    fun labelTargetCounts() = LabelTargetFixture.countTests()
}
