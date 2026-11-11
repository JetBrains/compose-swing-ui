// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.RecordingMeasurementLayout
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Separator
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.failureOf
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A host declaring `ChildPlacement.Slots(content = ...)` installs the one child that names no region
 * through that attachment, and holds the children that name a region to the region they name. The host
 * here is written with public API only.
 */
class ContentRegionTest {
    @Test
    fun aHostDeclaringNoRegionAndNoContentIsRefusedWhenItIsDeclared() {
        val refusal = assertFailsWith<IllegalArgumentException> { ChildPlacement.Slots() }
        assertEquals(
            "ChildPlacement.Slots needs a region name or a content attachment: " +
                "a host declared with neither can hold no child.",
            refusal.message,
        )
    }

    @Test
    fun anUnnamedChildIsInstalledAsTheHostsContent() = runComposeSwingTest {
        setContent {
            Titled {
                TitledTitle()
                Label("body")
            }
        }

        val host = onNodeOfType<TitledPanel>().fetch()
        assertEquals("body", (host.body as JLabel).text, "the unnamed child should be installed through the content")
        assertSame(host, host.body?.parent, "the content attachment adds the child to the host")
        assertSame(host.title, (host.layout as BorderLayout).getLayoutComponent(BorderLayout.NORTH))
    }

    @Test
    fun theContentFollowsTheChildTheCompositionDeclares() = runComposeSwingTest {
        var first by mutableStateOf(true)
        setContent {
            Titled {
                if (first) Label("first") else Separator()
            }
        }
        val host = onNodeOfType<TitledPanel>().fetch()
        assertEquals("first", (host.body as JLabel).text)

        first = false
        awaitIdle()

        assertTrue(host.body is JSeparator, "the replacement should be the content: ${host.body}")
        assertEquals(1, host.components.count { it !== host.title }, "the replaced child should be gone")
    }

    @Test
    fun twoUnnamedChildrenAreRefusedNamingBoth() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    Titled {
                        Label("one")
                        Separator()
                    }
                }
                awaitIdle()
            }

        assertTrue("shows one unnamed child as its content" in message, "the refusal should say why: $message")
        assertTrue("TitledPanel" in message, "the refusal should name the host: $message")
        assertTrue("JLabel" in message && "JSeparator" in message, "the refusal should name both children: $message")
    }

    @Test
    fun aClaimedChildNamingNoRegionIsRefusedOnArrival() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    Titled {
                        ExistingSwingNode(claim = TitledPanel::title)
                    }
                }
                awaitIdle()
            }

        assertEquals(
            "A JLabel claimed by ExistingSwingNode cannot be the unnamed child of a TitledPanel, which installs " +
                "that child as its content while the claimed component's parent already holds it. Name the " +
                "region it stands in through $TITLE_REGION.",
            message,
            "the refusal should name the claim, the host and the region to name",
        )
    }

    @Test
    fun contentMovesBetweenAnIndexedHostAndAContentHost() = runComposeSwingTest {
        var inTitled by mutableStateOf(false)
        setContent {
            val content = remember { movableContentOf { Label("body") } }
            Panel {
                Titled { if (inTitled) content() }
                Panel { if (!inTitled) content() }
            }
        }
        val label = onNodeWithText("body").fetch()
        val host = onNodeOfType<TitledPanel>().fetch()
        assertNull(host.body, "the host starts with no content")

        inTitled = true
        awaitIdle()
        assertSame(label, host.body, "the content host should take the moved child as its content")

        inTitled = false
        awaitIdle()
        assertNull(host.body, "the content region should be released when the child leaves")
        assertTrue(label.parent is JPanel && label.parent !== host, "the indexed host should hold the child again")
    }

    @Test
    fun aChildThatStopsNamingItsRegionBecomesTheContent() = runComposeSwingTest {
        var named by mutableStateOf(true)
        val footer =
            SlotAttachment { host, component, _ ->
                host.add(component, BorderLayout.SOUTH)
                return@SlotAttachment { host.remove(component) }
            }
        setContent {
            Titled {
                Label("child", if (named) SwingModifier.slot(TitledProtocol, TITLE_REGION, footer) else SwingModifier)
            }
        }
        val host = onNodeOfType<TitledPanel>().fetch()
        val label = onNodeWithText("child").fetch()
        assertNull(host.body, "a child naming a region is not the content")
        assertSame(label, (host.layout as BorderLayout).getLayoutComponent(BorderLayout.SOUTH))

        named = false
        awaitIdle()

        assertSame(label, host.body, "the child naming no region should be installed through the content attachment")
        assertNull(
            (host.layout as BorderLayout).getLayoutComponent(BorderLayout.SOUTH),
            "its region should be released",
        )
    }

    @TestFactory
    fun aContentChildDeclaringALayoutConstraintIsRefused(): List<DynamicTest> = listOf(
        constraintRefused("when it arrives", constrainedAtFirst = true),
        constraintRefused("when its modifier changes", constrainedAtFirst = false),
    )

    private fun constraintRefused(
        name: String,
        constrainedAtFirst: Boolean,
    ) = DynamicTest.dynamicTest(name) {
        runComposeSwingTest {
            var constrained by mutableStateOf(constrainedAtFirst)
            assertFailsWith<IllegalStateException> {
                setContent {
                    Titled {
                        Label(
                            "body",
                            if (constrained) SwingModifier.layoutConstraint(BorderLayout.SOUTH) else SwingModifier,
                        )
                    }
                }
                awaitIdle()
                constrained = true
                awaitIdle()
            }
        }
    }

    @Test
    fun aMeasuringHostReceivesTheNewDeclarationOfItsContentChild() = runComposeSwingTest {
        var wide by mutableStateOf(false)
        val layout = RecordingMeasurementLayout()
        setContent {
            SwingNode(
                factory = { JPanel(layout) },
                childPlacement =
                    ChildPlacement.Slots(
                        content =
                            SlotAttachment { host, component, _ ->
                                host.add(component)
                                return@SlotAttachment { host.remove(component) }
                            },
                    ),
            ) {
                Label("body", SwingModifier then if (wide) Wide else Narrow)
            }
        }
        onNodeWithText("body").assertProperty(listOf<ParentLayoutElement>(Narrow), "parent layout elements") {
            layout.elementsOf(this)
        }

        wide = true
        awaitIdle()

        onNodeWithText("body").assertProperty(listOf<ParentLayoutElement>(Wide), "parent layout elements") {
            layout.elementsOf(this)
        }
    }

    @Test
    fun aParkedContentChildGivesTheContentUpToItsSuccessor() = runComposeSwingTest {
        var parked by mutableStateOf(false)
        setContent {
            Titled {
                ReusableContentHost(active = !parked) { Label("parked") }
                if (parked) Label("fresh")
            }
        }
        val host = onNodeOfType<TitledPanel>().fetch()
        assertEquals("parked", (host.body as JLabel).text)

        parked = true
        awaitIdle()

        assertEquals("fresh", (host.body as JLabel).text, "the live child should fill the content")
    }
}

private const val TITLE_REGION = "TitledTitle()"

/** A panel with a claimed title above a body installed through a setter of its own. */
internal class TitledPanel : JPanel(BorderLayout()) {
    val title: JLabel = JLabel("title")

    var body: Component? = null
        set(value) {
            field?.let(::remove)
            field = value
            value?.let { add(it, BorderLayout.CENTER) }
        }

    init {
        add(title, BorderLayout.NORTH)
    }
}

private val TitledProtocol: ParentProtocol = parentProtocolOf("TitledPanel slot") { it is TitledPanel }

private val BodyContent =
    SlotAttachment { host, component, _ ->
        val panel = host as TitledPanel
        panel.body = component
        return@SlotAttachment { if (panel.body === component) panel.body = null }
    }

private val TitledPlacement = ChildPlacement.Slots(TITLE_REGION, content = BodyContent)

private data object Narrow : ParentLayoutElement {
    override val parentProtocol: ParentProtocol get() = MeasuringProtocol
}

private data object Wide : ParentLayoutElement {
    override val parentProtocol: ParentProtocol get() = MeasuringProtocol
}

@Composable
private inline fun Titled(crossinline content: @Composable () -> Unit) {
    SwingNode(factory = { TitledPanel() }, childPlacement = TitledPlacement) { content() }
}

@Composable
private fun TitledTitle() {
    ExistingSwingNode(claim = TitledPanel::title, modifier = SwingModifier.slot(TitledProtocol, TITLE_REGION))
}
