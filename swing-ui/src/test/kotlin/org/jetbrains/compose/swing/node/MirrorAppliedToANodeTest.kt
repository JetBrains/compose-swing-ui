package org.jetbrains.compose.swing.node

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JCheckBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MirrorAppliedToANodeTest {
    @Test
    fun aThrowingOnChangedIsReportedAndSettlementAndLaterPassesStillApply() = runComposeSwingTest {
        val boom = IllegalStateException("boom")
        var declared by mutableStateOf(false)
        lateinit var mirror: MirrorState<Boolean>
        val widget = JCheckBox()
        setContent {
            mirror = rememberMirrorState(false)
            SwingNode(
                factory = {
                    widget.apply {
                        addItemListener { mirror.report(isSelected) { throw boom } }
                    }
                },
                update = { declare(declared, mirror, JCheckBox::isSelected, JCheckBox::setSelected) },
            )
        }

        widget.isSelected = true
        assertSame(
            boom,
            assertFailsWith<IllegalStateException>("the onChanged failure is reported") { awaitIdle() },
            "the original onChanged failure reaches the caller",
        )
        awaitIdle()
        assertEquals(false, widget.isSelected, "settlement restores the standing declaration")
        assertEquals(false, mirror.value, "the mirror follows the restored declaration")

        declared = true
        awaitIdle()
        assertEquals(true, widget.isSelected, "a later pass still applies")
        assertEquals(true, mirror.value, "the mirror follows the later declaration")
    }

    @Test
    fun aMirrorNoNodeHasAppliedFailsAtTheFirstChange() {
        val mirror = MirrorState(false)

        val failure =
            assertFailsWith<IllegalStateException>("reporting requires a node to apply the mirror") {
                mirror.report(true) {}
            }

        assertTrue(
            "applyMirror()" in failure.message.orEmpty(),
            "the failure must name what applies the mirror to a node, and said: ${failure.message}",
        )
    }

    @Test
    fun aMirrorNoNodeHasAppliedStillMirrorsAnObservedChange() {
        val mirror = MirrorState(false)

        assertTrue(mirror.observed(true), "an observed move must be mirrored without a node having applied it")
        assertTrue(mirror.value, "an observed move must be mirrored without a node having applied it")
    }

    @Test
    fun aMirrorMovedToAnotherCompositionLetsGoOfTheOneItLeft() = runComposeSwingTest {
        var declared by mutableStateOf(false)
        var adopt = true
        val reports = mutableListOf<Boolean>()
        val widget = JCheckBox()
        lateinit var mirror: MirrorState<Boolean>
        val nested =
            composeInNestedComposition {
                mirror = rememberMirrorState(false)
                SwingNode(
                    factory = {
                        widget.apply {
                            addItemListener {
                                mirror.report(isSelected) { selected ->
                                    reports += selected
                                    if (adopt) declared = selected
                                }
                            }
                        }
                    },
                    update = { declare(declared, mirror, JCheckBox::isSelected, JCheckBox::setSelected) },
                )
            }

        nested.moveContentOut()
        awaitIdle()
        nested.dispose()
        awaitIdle()

        widget.isSelected = true
        awaitIdle()
        assertEquals(
            listOf(true),
            reports,
            "the moved widget reports through the mirror after its old owner is disposed",
        )
        assertTrue(widget.isSelected, "the surviving composition keeps an adopted change")
        assertTrue(mirror.value, "the mirror follows the adopted change")

        adopt = false
        widget.isSelected = false
        awaitIdle()
        assertEquals(listOf(true, false), reports, "settlement does not report the declaration as another user change")
        assertTrue(widget.isSelected, "the surviving composition restores the declaration after a rejected change")
        assertTrue(mirror.value, "the mirror follows the restored declaration")
    }
}
