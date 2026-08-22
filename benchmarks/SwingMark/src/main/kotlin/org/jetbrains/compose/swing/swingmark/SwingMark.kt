package org.jetbrains.compose.swing.swingmark

import org.jetbrains.compose.swing.swingmark.harness.PaintCounter
import org.jetbrains.compose.swing.swingmark.harness.Watchdog
import org.jetbrains.compose.swing.swingmark.harness.rest
import org.jetbrains.compose.swing.swingmark.harness.syncRam
import java.awt.Toolkit
import java.util.Date
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.system.exitProcess

private const val LOOK_AND_FEEL_FAILED = 1
private const val SLEEP_BEEPS = 10
private const val SLEEP_MILLIS = 900L

/**
 * SwingMark, run twice over: once on raw Swing widgets, once on the same screens declared through
 * compose-swing-ui.
 *
 * Each test shows the screen its original shows and drives it through the same changes, in the same order
 * and the same number of times. The two arms are timed by the same harness and run interleaved in the one
 * VM, and the suite closes with what they cost against each other. The command line and the printed
 * timings are the original's, so a run of the raw arm is read beside a run of the JDK's own suite.
 */
fun main(args: Array<String>) {
    println("Starting SwingMark")
    val startTime = System.currentTimeMillis()
    println("SwingMark Test started at ${Date(startTime)}")
    // The runtime this suite is timed on, so a report read beside the original's states whether the two
    // ran on the same one. A run of each on different runtimes compares the runtimes, not the suites.
    println("Java: ${System.getProperty("java.version")} (${System.getProperty("java.vm.name")})")

    val options = parseOptions(args)
    switchLookAndFeel(options.lookAndFeel)
    Thread.currentThread().priority = Thread.NORM_PRIORITY - 1
    Watchdog.start()

    var suite = openSuite(options)
    val report = Report(Date(startTime), options.runs, suite.panel.rowNames)
    val comparison = Comparison()
    println("Startup Time: ${System.currentTimeMillis() - startTime}")

    repeat(options.runs) { run ->
        suite.panel.runTests(run, report, comparison)
        if (run < options.runs - 1) {
            closeSuite(suite)
            suite = openSuite(options, run + 2)
            println(" **** Starting run ${run + 2}****")
            maybeSleep(options)
        }
    }

    println("Score: ${System.currentTimeMillis() - startTime}")
    comparison.print(options.runs)
    options.reportFile?.let(report::writeTimes)
    options.memoryReportFile?.let(report::writeMemory)
    if (options.autoQuit) exitProcess(0)
}

/** The suite's window and the panel running the tests in it. */
private class Suite(
    val panel: SwingMarkPanel,
    val frame: JFrame,
)

/**
 * Builds the suite's window and shows it.
 *
 * Shown, not hidden: a real widget paints only while it is on screen, and painting is most of what these
 * tests do, so a run behind a hidden window would report a fraction of the truth.
 */
private fun openSuite(
    options: Options,
    run: Int = 1,
): Suite {
    lateinit var panel: SwingMarkPanel
    lateinit var frame: JFrame
    SwingUtilities.invokeAndWait {
        PaintCounter.install()
        PaintCounter.isDoubleBufferingEnabled = options.doubleBuffering
        panel = SwingMarkPanel(testPairs(options.blitScrolling), options.blitScrolling)
        frame =
            JFrame(if (run == 1) "SwingMarks" else "SwingMarks $run").apply {
                defaultCloseOperation = JFrame.EXIT_ON_CLOSE
                contentPane.add(panel)
                // Packed, not sized: the original packs, so a window of any other size paints a
                // different area.
                pack()
                isVisible = true
                // Focused, because the menu test posts key events, which AWT delivers only to a window
                // that holds the keyboard focus.
                toFront()
                requestFocus()
            }
    }
    rest()
    return Suite(panel, frame)
}

private fun closeSuite(suite: Suite) {
    SwingUtilities.invokeAndWait {
        suite.panel.dispose()
        suite.frame.isVisible = false
        suite.frame.dispose()
    }
    rest()
}

private fun switchLookAndFeel(lookAndFeel: String) {
    println("Setting L&F to: $lookAndFeel")
    try {
        UIManager.setLookAndFeel(lookAndFeel)
    } catch (failure: ReflectiveOperationException) {
        println(failure)
        exitProcess(LOOK_AND_FEEL_FAILED)
    } catch (failure: UnsupportedOperationException) {
        println(failure)
        exitProcess(LOOK_AND_FEEL_FAILED)
    }
}

/** Beeps and collects between runs, which `-sleep` turns on, so a run starts from a quiet machine. */
private fun maybeSleep(options: Options) {
    if (!options.sleepBetweenRuns) return
    repeat(SLEEP_BEEPS) {
        Toolkit.getDefaultToolkit().beep()
        syncRam()
        Thread.sleep(SLEEP_MILLIS)
    }
}
