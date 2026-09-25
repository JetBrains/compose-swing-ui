package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Separator
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.failureOf
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
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

@Composable
private inline fun Titled(crossinline content: @Composable () -> Unit) {
    SwingNode(factory = { TitledPanel() }, childPlacement = TitledPlacement) { content() }
}

@Composable
private fun TitledTitle() {
    ExistingSwingNode(claim = TitledPanel::title, modifier = SwingModifier.slot(TitledProtocol, TITLE_REGION))
}
