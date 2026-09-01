package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.deliverEvent
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import java.awt.AWTEvent
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The registration the recomposers on one toolkit share to queue a settlement ahead of a repaint.
 *
 * A toolkit listener is notified for every event in the mask and is held for as long as it is
 * registered, so one per recomposer would cost every recomposer a walk over every event, and a
 * recomposer that outlived its own disposal would keep answering events for content that is gone. One
 * registration stands for every live recomposer on a toolkit, and none stands while there are none.
 */
class EventDispatchHookTest {
    @Test
    fun recomposersOnOneToolkitShareOneRegistrationThatOutlastsAllButTheLast() = runSwingTest {
        val composition = JPanel()
        val toolkit = composition.toolkit
        val standing = toolkit.awtEventListeners.size

        val first = SwingRecomposer.create(composition)
        val second = SwingRecomposer.create(JPanel())
        assertEquals(
            standing + 1,
            toolkit.awtEventListeners.size,
            "two live recomposers on one toolkit listen to it through one registration, so an event is " +
                "walked once however many recomposers are live",
        )

        first.dispose()
        assertEquals(
            standing + 1,
            toolkit.awtEventListeners.size,
            "the registration stands while a recomposer still settles its moves from it",
        )

        second.dispose()
        assertEquals(
            standing,
            toolkit.awtEventListeners.size,
            "the last recomposer to be disposed withdraws the registration, which is what stops it " +
                "holding its clock for the life of the toolkit",
        )
    }

    @Test
    fun aClockDisposedOnItsOwnWithdrawsItself() = runSwingTest {
        val toolkit = JPanel().toolkit
        val standing = toolkit.awtEventListeners.size
        val recomposer = Recomposer(Dispatchers.Swing)
        val clock = SwingUiDispatcher().frameClock.apply { pace(recomposer) }
        try {
            EventDispatchHook.subscribe(toolkit, clock)

            clock.dispose()

            assertEquals(
                standing,
                toolkit.awtEventListeners.size,
                "a clock disposed without the recomposer that subscribed it stayed a subscriber, which " +
                    "holds the registration - and the clock - for the life of the toolkit",
            )
        } finally {
            clock.dispose()
            recomposer.cancel()
        }
    }

    @Test
    fun aSettlementIsQueuedBehindFocusEventsTheWidgetCommitsOn() = runSwingTest {
        val composition = JPanel()
        val toolkit = composition.toolkit
        val standing = toolkit.getAWTEventListeners(AWTEvent.FOCUS_EVENT_MASK).size

        val recomposer = SwingRecomposer.create(composition)
        try {
            assertEquals(
                standing + 1,
                toolkit.getAWTEventListeners(AWTEvent.FOCUS_EVENT_MASK).size,
                "a focus loss is what commits an edited formatted text field, and the commit moves a " +
                    "declared value with no report behind it, so the recomposer must settle from that event too",
            )
        } finally {
            recomposer.dispose()
        }
    }

    @Test
    fun aDragStepQueuesASettlementAndAPlainMoveDoesNot() = runSwingTest {
        val composition = JPanel()
        val recomposer = SwingRecomposer.create(composition)
        var mounted: DisposableHandle? = null
        try {
            var declared by mutableStateOf("mounted")
            var composedWith = ""
            mounted = composition.setContent(parent = recomposer.compositionContext) { composedWith = declared }

            declared = "moved"
            composition.deliverEvent(composition.mouseEvent(MouseEvent.MOUSE_MOVED, 0))
            yield()

            assertEquals(
                "mounted",
                composedWith,
                "a plain move changes no declared value, so nothing may be queued for it: a write it " +
                    "coincides with settles on the pass that follows, not from a settlement of its own",
            )
            awaitUntil("the write the move coincided with has settled on its own pass") { composedWith == "moved" }

            declared = "dragged"
            composition.deliverEvent(composition.mouseEvent(MouseEvent.MOUSE_DRAGGED, InputEvent.BUTTON1_DOWN_MASK))
            yield()

            assertEquals(
                "dragged",
                composedWith,
                "a drag step is settled from the settlement queued behind it, ahead of the repaint it provokes",
            )
        } finally {
            mounted?.dispose()
            recomposer.dispose()
        }
    }

    private fun Component.mouseEvent(
        id: Int,
        modifiersEx: Int,
    ): MouseEvent = MouseEvent(this, id, System.currentTimeMillis(), modifiersEx, 1, 1, 0, false, MouseEvent.NOBUTTON)
}
