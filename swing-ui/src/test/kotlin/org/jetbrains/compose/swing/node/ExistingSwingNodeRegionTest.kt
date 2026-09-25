package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.failureOf
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Under a host holding regions, an [ExistingSwingNode] names the region its component stands in. */
class ExistingSwingNodeRegionTest {
    @Test
    fun aClaimNamingARegionFillsIt() = runComposeSwingTest {
        setContent {
            Framed {
                FramedHeader(SwingModifier.background(Color.RED))
                Label("body", SwingModifier.slot(FramedProtocol, BODY_REGION, BodyAttachment))
            }
        }

        val parent = onNodeOfType<FramedPanel>().fetch()
        val layout = parent.layout as BorderLayout
        assertSame(parent.header, layout.getLayoutComponent(BorderLayout.NORTH), "the header should stay where it was")
        assertEquals(Color.RED, parent.header.background, "the modifier should reach the claimed header")
        assertEquals("body", (layout.getLayoutComponent(BorderLayout.CENTER) as JLabel).text)
    }

    @Test
    fun aClaimNamingAnotherRegionStaysWhereItIsAndFillsTheRegionItNames() = runComposeSwingTest {
        var region by mutableStateOf(HEADER_REGION)
        var body by mutableStateOf(false)
        setContent {
            Framed {
                ExistingSwingNode(claim = FramedPanel::header, modifier = SwingModifier.slot(FramedProtocol, region))
                if (body) Label("body", SwingModifier.slot(FramedProtocol, BODY_REGION, BodyAttachment))
            }
        }
        val parent = onNodeOfType<FramedPanel>().fetch()
        val events = parent.recordAddsAndRemovesOf(parent.header)

        region = BODY_REGION
        awaitIdle()

        val layout = parent.layout as BorderLayout
        assertSame(parent.header, layout.getLayoutComponent(BorderLayout.NORTH), "the header should stay where it was")
        assertEquals(emptyList(), events, "the claimed component should never be added to or removed from its parent")

        val message =
            failureOf {
                body = true
                awaitIdle()
            }

        assertTrue(
            message.contains("holds one component per region, but two children declare $BODY_REGION"),
            "the region check should count the claim in the region it names now: $message",
        )
    }

    @Test
    fun aSecondChildNamingTheRegionAClaimFillsIsRefused() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    Framed {
                        FramedHeader()
                        ExistingSwingNode(
                            claim = FramedPanel::footer,
                            modifier = SwingModifier.slot(FramedProtocol, HEADER_REGION),
                        )
                    }
                }
                awaitIdle()
            }

        assertTrue(
            message.contains("holds one component per region, but two children declare $HEADER_REGION"),
            "the refusal should say the region is filled twice: $message",
        )
    }

    @Test
    fun twoClaimsNamingDifferentRegionsAreRefusedAsTwoClaims() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    Framed {
                        ExistingSwingNode(
                            claim = FramedPanel::header,
                            modifier = SwingModifier.slot(FramedProtocol, BODY_REGION),
                        )
                        FramedHeader()
                    }
                }
                awaitIdle()
            }

        assertTrue(
            message.contains("A JLabel is declared twice at once"),
            "the refusal should say the component is claimed twice: $message",
        )
        assertFalse(
            message.contains("$HEADER_REGION is declared twice") || message.contains("$BODY_REGION is declared twice"),
            "the refusal should not name a region only one of the claims declares: $message",
        )
    }

    @Test
    fun aCreatedNodeNamingARegionWithoutAnAttachmentIsRefused() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    Framed {
                        FramedHeader()
                        Label("body", SwingModifier.slot(FramedProtocol, BODY_REGION))
                    }
                }
                awaitIdle()
            }

        assertTrue(
            message.contains("names the region $BODY_REGION without a SlotAttachment, so nothing would put it there"),
            "the refusal should say a created component needs an attachment: $message",
        )
    }

    @Test
    fun aClaimNamingNoRegionUnderAHostHoldingRegionsIsRefusedWithTheRegions() = runComposeSwingTest {
        val message =
            failureOf {
                setContent { Framed { ExistingSwingNode(claim = FramedPanel::footer) } }
                awaitIdle()
            }

        assertTrue(
            message.contains(
                "A JLabel claimed by ExistingSwingNode names no region of the FramedPanel, which holds each child in " +
                    "one. Name the region it stands in through one of: $HEADER_REGION, $BODY_REGION.",
            ),
            "the refusal should list the regions: $message",
        )
    }

    @Test
    fun aClaimNamingARegionWithAnAttachmentIsRefused() = runComposeSwingTest {
        val message =
            failureOf {
                setContent {
                    Framed {
                        ExistingSwingNode(
                            claim = FramedPanel::header,
                            modifier = SwingModifier.slot(FramedProtocol, HEADER_REGION, BodyAttachment),
                        )
                    }
                }
                awaitIdle()
            }

        assertTrue(
            message.contains("names the region $HEADER_REGION with a SlotAttachment"),
            "the refusal should say a claimed component takes no attachment: $message",
        )
    }
}

internal const val HEADER_REGION = "Header()"
internal const val BODY_REGION = "SwingModifier.body()"

internal class FramedPanel : JPanel(BorderLayout()) {
    val header: JLabel = JLabel("header")
    val footer: JLabel = JLabel("footer")

    init {
        add(header, BorderLayout.NORTH)
        add(footer, BorderLayout.SOUTH)
    }
}

internal val FramedProtocol: ParentProtocol = parentProtocolOf("FramedPanel slot") { it is FramedPanel }

internal val BodyAttachment =
    SlotAttachment { host, component, _ ->
        host.add(component, BorderLayout.CENTER)
        return@SlotAttachment { host.remove(component) }
    }

@Composable
internal inline fun Framed(crossinline content: @Composable () -> Unit) {
    SwingNode(
        factory = { FramedPanel() },
        childPlacement = ChildPlacement.Slots(HEADER_REGION, BODY_REGION),
    ) { content() }
}

@Composable
internal fun FramedHeader(modifier: SwingModifier = SwingModifier) {
    ExistingSwingNode(claim = FramedPanel::header, modifier = modifier.slot(FramedProtocol, HEADER_REGION))
}
