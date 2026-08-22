package org.jetbrains.compose.swing.swingmark

import org.jetbrains.compose.swing.test.ComposeSwingTest
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.UIManager

/** The size both arms of a screen are laid out at, so neither is compared at a size of its own. */
internal val SCREEN_SIZE: Dimension = Dimension(800, 600)

/**
 * Builds both arms of the test named [testName] and hands them to [body] as the cards they were built
 * into, laid out at [SCREEN_SIZE].
 *
 * Each arm is built through its own `mount`, which is what the suite calls, so what is compared is what
 * the suite shows - including a widget an arm adds beside its screen, such as the menu bar the
 * `Sub-Menus` test hangs above its list. Each arm is then driven to the fullest state its run reaches
 * through `buildUp`, because a screen that is empty until it is driven would otherwise be compared
 * empty against empty and agree about nothing. Driving `runTest` past that point is the benchmark's job,
 * not this gate's.
 *
 * Both mounts are released before this returns, which takes down the runtime the declared arm composes
 * on along with its content.
 */
internal suspend fun ComposeSwingTest.withArms(
    testName: String,
    body: suspend (raw: JPanel, declared: JPanel) -> Unit,
) {
    installCrossPlatformLookAndFeel()
    val pair = testPairs(blitScrolling = false).single { it.testName == testName }
    val cards = Arm.entries.associateWith { JPanel(BorderLayout()) }
    val mounted = Arm.entries.map { pair[it].mount(cards.getValue(it)) }
    try {
        settleDeclaredArm()
        for (arm in Arm.entries) pair[arm].buildUp()
        settleDeclaredArm()
        // TODO restore once swing-ui-test publishes layoutOffscreen.
        // for (card in cards.values) card.layoutOffscreen(SCREEN_SIZE)
        body(cards.getValue(Arm.RAW), cards.getValue(Arm.DECLARED))
    } finally {
        mounted.forEach { it.dispose() }
    }
}

/**
 * The look and feel the suite runs under, which decides every border, inset and color the two arms are
 * compared on. Installed before either arm builds a widget, and only once: a second call would have to
 * rebuild the widgets already standing.
 */
private fun installCrossPlatformLookAndFeel() {
    val crossPlatform = UIManager.getCrossPlatformLookAndFeelClassName()
    if (UIManager.getLookAndFeel()?.javaClass?.name == crossPlatform) return
    UIManager.setLookAndFeel(crossPlatform)
}

/**
 * Lets the declared arm finish arriving at its widgets.
 *
 * Content mounted under a named parent composes on the `setContent` call, so the widgets are already
 * built when this runs; the rounds are for the effects that composition launched, which reach the
 * widgets over turns of the library's runtime. A screen that needed more than this would be caught by
 * the comparison rather than pass it.
 */
private suspend fun ComposeSwingTest.settleDeclaredArm() {
    repeat(SETTLE_ROUNDS) { awaitEventsDelivered() }
}

private const val SETTLE_ROUNDS = 3
