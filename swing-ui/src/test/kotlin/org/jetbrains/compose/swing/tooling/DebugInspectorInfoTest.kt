package org.jetbrains.compose.swing.tooling

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.core.SwingCompositionMount
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingApplier
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Container
import java.lang.ref.WeakReference
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JTabbedPane
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * How many times a collection is asked for before an unreachable object is taken to be uncollectable.
 * One [System.gc] is a hint, not a guarantee, so the check retries rather than trusting the first.
 */
private const val COLLECTION_ATTEMPTS = 20

/**
 * Behavioral tests for [isDebugInspectorInfoEnabled]: what turning it on reaches, what turning it off
 * stops, and what a re-insertion it asks for keeps or discards.
 *
 * The switch is process-wide state, so every test leaves it off again - otherwise it would leak into a
 * later test, and into every composition the rest of the suite mounts. The Compose runtime's diagnostic
 * stack trace mode is process-wide too and belongs to the application, which these tests stand in for:
 * they put the runtime in it before each test and back to its default after.
 */
class DebugInspectorInfoTest {
    /** Stands in for an application that enabled Compose diagnostic stack traces. */
    @BeforeTest
    fun enableDiagnosticStackTraces() {
        Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.SourceInformation)
    }

    @AfterTest
    fun turnInspectionOff() {
        SwingUtilities.invokeAndWait { isDebugInspectorInfoEnabled = false }
        Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.None)
    }

    @Test
    fun withInspectionOffNothingIsPublishedAndNoComponentIsAnsweredFor() = runComposeSwingTest {
        setContent { Label(text = "hello", modifier = SwingModifier.testTag(LABEL_TAG)) }

        assertNull(
            (root as JComponent).findCompositionData(),
            "a composition mounted while inspection is off publishes nothing",
        )
        assertNull(
            onNodeWithTag(LABEL_TAG).fetch().findDeclaringGroup(),
            "and so no component it declared is answered for",
        )
        onNodeWithTag(LABEL_TAG).assertTextEquals("hello")
    }

    @Test
    fun turningInspectionOnAfterTheMountReachesWhatIsAlreadyOnScreen() = runComposeSwingTest {
        setContent { Label(text = "hello", modifier = SwingModifier.testTag(LABEL_TAG)) }
        assertNull(
            onNodeWithTag(LABEL_TAG).fetch().findDeclaringGroup(),
            "a composition mounted while inspection was off answers for nothing",
        )

        isDebugInspectorInfoEnabled = true
        awaitIdle()

        assertDeclaredBy(onNodeWithTag(LABEL_TAG).fetch(), "Label.kt")
    }

    @Test
    fun theReInsertionKeepsHoistedStateAndDiscardsWhatTheContentRemembered() = runComposeSwingTest {
        // Hoisted above the mounted content, so the re-insertion cannot discard it. Each is moved off
        // its initial value below, so the widgets are asserted on the state as it now stands.
        var typed by mutableStateOf("")
        var sliderValue by mutableStateOf(30)
        var selectedTab by mutableStateOf(0)
        var buildsOfTheContent = 0

        setContent {
            remember { buildsOfTheContent++ }
            BoxPanel {
                TextField(value = typed, onValueChange = { typed = it })
                Slider(value = sliderValue, onValueChange = { sliderValue = it })
                TabbedPane(selectedIndex = selectedTab, onSelectedIndexChange = { selectedTab = it }) {
                    Label(text = "one", modifier = SwingModifier.tab("One"))
                    Label(text = "two", modifier = SwingModifier.tab("Two"))
                }
            }
        }
        assertEquals(1, buildsOfTheContent, "the content is built once by the initial mount")

        typed = "what the user typed"
        sliderValue = 70
        selectedTab = 1
        awaitIdle()

        val typedText = onNodeOfType<JTextField>().fetch().text
        assertEquals("what the user typed", typedText, "the text field shows what was typed into it")
        assertEquals(70, onNodeOfType<JSlider>().fetch().value, "the slider shows the value it was moved to")
        assertEquals(1, onNodeOfType<JTabbedPane>().fetch().selectedIndex, "the pane shows the selected tab")

        isDebugInspectorInfoEnabled = true
        awaitIdle()

        assertEquals(2, buildsOfTheContent, "turning inspection on builds the content afresh")
        assertEquals(
            "what the user typed",
            onNodeOfType<JTextField>().fetch().text,
            "what the user typed is held above the content, so the re-inserted text field shows it again",
        )
        assertEquals(
            70,
            onNodeOfType<JSlider>().fetch().value,
            "the value it was moved to is held above the content, so the re-inserted slider shows it again",
        )
        assertEquals(
            1,
            onNodeOfType<JTabbedPane>().fetch().selectedIndex,
            "the selected tab is held above the content, so the re-inserted pane shows it again",
        )
    }

    @Test
    fun turningInspectionOffBuildsTheContentAfreshAndWithdrawsWhatItPublished() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        var buildsOfTheContent = 0
        lateinit var parentContext: CompositionContext
        setContent {
            remember { buildsOfTheContent++ }
            parentContext = rememberCompositionContext()
            Label(text = "hello", modifier = SwingModifier.testTag(LABEL_TAG))
        }
        assertEquals(1, buildsOfTheContent, "the content is built once by the initial mount")
        assertNotNull((root as JComponent).findCompositionData(), "and publishes itself while it records")

        isDebugInspectorInfoEnabled = false
        awaitIdle()

        assertEquals(2, buildsOfTheContent, "turning inspection off builds the content afresh")
        assertNull(
            (root as JComponent).findCompositionData(),
            "so the composition's publication is withdrawn",
        )
        assertNull(
            onNodeWithTag(LABEL_TAG).fetch().findDeclaringGroup(),
            "and no component it declared is answered for",
        )

        val islandHost = JPanel()
        val handle = islandHost.setContent(parent = parentContext) { Label(text = "island") }
        try {
            awaitIdle()

            assertNull(
                islandHost.findCompositionData(),
                "a composition mounted after inspection went off publishes nothing",
            )
            assertNull(
                islandHost.components.single().findDeclaringGroup(),
                "and answers for no component it declared",
            )
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun everyFlipOfTheSwitchBuildsTheContentItDrivesAfresh() = runComposeSwingTest {
        var buildsOfTheContent = 0
        setContent {
            remember { buildsOfTheContent++ }
            Label(text = "outer", modifier = SwingModifier.testTag(LABEL_TAG))
        }

        isDebugInspectorInfoEnabled = true
        awaitIdle()
        assertEquals(2, buildsOfTheContent, "the mounted content is built afresh so it records")
        assertDeclaredBy(onNodeWithTag(LABEL_TAG).fetch(), "Label.kt")

        isDebugInspectorInfoEnabled = false
        awaitIdle()
        assertEquals(3, buildsOfTheContent, "and again, without recording, when the switch goes off")

        isDebugInspectorInfoEnabled = true
        awaitIdle()
        assertEquals(4, buildsOfTheContent, "and once more when it goes back on")
        assertDeclaredBy(onNodeWithTag(LABEL_TAG).fetch(), "Label.kt")
    }

    @Test
    fun contentUnderACapturedContextIsReachedOnTheNextPassItTakes() = runComposeSwingTest {
        var buildsOfTheIsland = 0
        var driveTheIsland by mutableStateOf(0)
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        val islandHost = JPanel()
        val handle =
            islandHost.setContent(parent = parentContext) {
                remember { buildsOfTheIsland++ }
                @Suppress("UNUSED_EXPRESSION")
                driveTheIsland
                Label(text = "island", modifier = SwingModifier.testTag(NESTED_TAG))
            }
        try {
            awaitIdle()
            assertEquals(1, buildsOfTheIsland, "the island is built once by its own mount")

            isDebugInspectorInfoEnabled = true
            awaitIdle()
            assertEquals(
                1,
                buildsOfTheIsland,
                "the switch brings about no pass of its own for content under a captured context",
            )

            driveTheIsland++
            waitUntil(timeout = 5.seconds) { buildsOfTheIsland == 2 }
            assertAnsweredWithItsOwnGroup(islandHost.components.single())
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun aSwitchTurnedOffAgainBeforeThePassRunsReachesNothing() = runComposeSwingTest {
        var buildsOfTheContent = 0
        setContent {
            remember { buildsOfTheContent++ }
            Label(text = "hello", modifier = SwingModifier.testTag(LABEL_TAG))
        }
        assertEquals(1, buildsOfTheContent, "the content is built once by the initial mount")

        // Both in one turn of the event loop, so the switch is off again before any pass reads it.
        isDebugInspectorInfoEnabled = true
        isDebugInspectorInfoEnabled = false
        awaitIdle()

        assertEquals(
            1,
            buildsOfTheContent,
            "a composition reads the switch when its pass runs, and by then it stands where it started",
        )
        assertNull((root as JComponent).findCompositionData(), "so the composition publishes nothing")
        assertNull(
            onNodeWithTag(LABEL_TAG).fetch().findDeclaringGroup(),
            "and no component it declared is answered for",
        )
    }

    @Test
    fun aCompositionThatCanPublishNothingIsNeverReInsertedForInspection() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        var buildsOfTheIsland = 0
        // No client-property bag, so this island can publish nothing and answers for nothing.
        val bareHost = Container()
        val handle =
            bareHost.setContent(parent = parentContext) {
                remember { buildsOfTheIsland++ }
                Label(text = "island", modifier = SwingModifier.testTag(NESTED_TAG))
            }
        try {
            awaitIdle()
            assertEquals(1, buildsOfTheIsland, "the island is built once by its own mount")

            isDebugInspectorInfoEnabled = true
            awaitIdle()

            assertEquals(
                1,
                buildsOfTheIsland,
                "rebuilding a composition nothing can read source information off would be cost with no " +
                    "payload, so it is left alone",
            )
            assertNull(
                bareHost.components.single().findDeclaringGroup(),
                "and the component it declared is still answered for by nothing",
            )
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun aCompositionThatCanPublishNothingCollectsNothingWhenItMountsWhileInspectionIsOn() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        isDebugInspectorInfoEnabled = true
        awaitIdle()

        var buildsOfTheIsland = 0
        // No client-property bag, so this island can publish nothing and answers for nothing.
        val bareHost = Container()
        val handle =
            bareHost.setContent(parent = parentContext) {
                remember { buildsOfTheIsland++ }
                Label(text = "island", modifier = SwingModifier.testTag(NESTED_TAG))
            }
        try {
            awaitIdle()
            assertEquals(1, buildsOfTheIsland, "the island is built once and re-inserted for nothing")
            assertNull(
                bareHost.components.single().findDeclaringGroup(),
                "and the component it declared is answered for by nothing",
            )
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun disposingACompositionWithdrawsWhatItPublished() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        isDebugInspectorInfoEnabled = true
        awaitIdle()
        val islandHost = JPanel()
        val handle = islandHost.setContent(parent = parentContext) { Label(text = "island") }
        awaitIdle()
        assertNotNull(islandHost.findCompositionData(), "the island publishes itself on its host")
        assertAnsweredWithItsOwnGroup(islandHost.components.single())

        handle.dispose()

        assertNull(
            islandHost.findCompositionData(),
            "a disposed composition must not stay published on its host",
        )
    }

    @Test
    fun aDisposedCompositionIsNotReachedByTheSwitchAfterwards() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }

        val islandHost = JPanel()
        val handle = islandHost.setContent(parent = parentContext) { Label(text = "island") }
        awaitIdle()
        val islandLabel = islandHost.components.single()

        handle.dispose()
        isDebugInspectorInfoEnabled = true
        awaitIdle()

        assertNull(
            islandHost.findCompositionData(),
            "the switch reaches what is mounted, and a disposed composition is not, so nothing is " +
                "published on the host it was rooted at",
        )
        assertNull(islandLabel.findDeclaringGroup(), "and a component it declared answers with nothing")
    }

    @Test
    fun disposingACompositionThatWasReplacedOnItsHostLeavesTheLiveOneAlone() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        isDebugInspectorInfoEnabled = true
        setContent { parentContext = rememberCompositionContext() }

        // Two compositions rooted at one container, mounted rather than set as islands: `setContent`
        // refuses a container already carrying a live island, and what a mount withdraws once another has
        // taken its host is what is under test.
        val islandHost = JPanel()
        val stale = SwingCompositionMount.nested(parentContext) { observer -> SwingApplier(islandHost, observer) }
        stale.setContent { Label(text = "stale") }
        // Declared only once the stale composition is gone: disposing one empties the container it is
        // rooted at, which is the host these two share.
        var showTheLabel by mutableStateOf(false)
        val live = SwingCompositionMount.nested(parentContext) { observer -> SwingApplier(islandHost, observer) }
        try {
            live.setContent { if (showTheLabel) Label(text = "live") }
            awaitIdle()
            val published =
                assertNotNull(
                    islandHost.findCompositionData(),
                    "the one mounted last is what the host carries",
                )

            stale.dispose()

            assertSame(
                published,
                islandHost.findCompositionData(),
                "and disposing the one it took the host from leaves that publication where it stands",
            )
            showTheLabel = true
            awaitIdle()
            assertAnsweredWithItsOwnGroup(islandHost.components.single())
        } finally {
            live.dispose()
        }
    }

    @Test
    fun aCompositionNobodyDisposedIsCollectableOnceNothingElseHoldsIt() {
        val published = mountAnIslandAndLetGoOfIt()

        repeat(COLLECTION_ATTEMPTS) {
            if (published.get() == null) return
            // A composition stays known to the recomposer that drove it until it is disposed, and a
            // recomposer stays in the runtime's set of running ones until its cancellation has finished
            // unwinding - which happens on the event dispatch thread, after the test body it belonged to
            // returned. Letting that thread run first keeps this a question about what this library
            // holds, rather than a race with a teardown still in flight.
            SwingUtilities.invokeAndWait { }
            System.gc()
        }

        assertNull(
            published.get(),
            "the library's record of what is mounted must hold no strong path to a mounted composition, " +
                "so a composition nobody disposed becomes collectable with everything it holds",
        )
    }

    @Test
    fun theSwitchMustBeSetOnTheEventDispatchThread() {
        val failure = assertFailsWith<IllegalStateException> { isDebugInspectorInfoEnabled = true }
        assertTrue(
            failure.message.orEmpty().contains("Event Dispatch Thread"),
            "the failure must name the thread the switch belongs to, but was: ${failure.message}",
        )
    }
}

/**
 * Asserts the group answering for [component] is the one holding it, so the composition that declared it
 * is the one that answers.
 *
 * A declaration trace is built from a `CompositionErrorContext`, which is read back off a scope the
 * composition recorded, and content providing no `CompositionLocal` records none. A component of such
 * content is asserted through its group alone.
 */
private fun assertAnsweredWithItsOwnGroup(component: Component) {
    val group = assertNotNull(component.findDeclaringGroup(), "no group answers for $component")
    assertSame(
        component,
        (group.node as? SwingComponentNode)?.component,
        "the group answered with holds the component the search started at",
    )
}

/**
 * Mounts an island, drops the handle that would dispose it, and hands back a weak reference to what it
 * published. Everything strong is confined to this call's own frame, which is gone by the time the
 * caller looks at the reference.
 */
private fun mountAnIslandAndLetGoOfIt(): WeakReference<Any> {
    var islandHost: JPanel? = JPanel()
    runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        lateinit var parentContext: CompositionContext
        setContent { parentContext = rememberCompositionContext() }
        islandHost!!.setContent(parent = parentContext) { Label(text = "island") }
        awaitIdle()
    }
    val published =
        assertNotNull(
            islandHost!!.publishedCompositionData(),
            "the island must still be published, which is what says nobody disposed it",
        )
    islandHost = null
    return WeakReference(published)
}
