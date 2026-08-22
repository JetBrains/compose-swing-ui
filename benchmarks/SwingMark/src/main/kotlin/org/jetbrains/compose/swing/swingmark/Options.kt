package org.jetbrains.compose.swing.swingmark

import java.util.Calendar
import java.util.Date
import javax.swing.UIManager
import kotlin.system.exitProcess

private const val BAD_ARGUMENTS = 1

/** The port's own answer to the original's `-version`, which names the build the suite came from. */
private const val VERSION = "SwingMark on compose-swing-ui"

private const val DATE_MARKER = "-mmdd"

/** What the suite's command line says, with the original's defaults. */
internal class Options {
    var lookAndFeel: String = UIManager.getCrossPlatformLookAndFeelClassName()
    var runs: Int = 1
    var autoQuit: Boolean = false
    var reportFile: String? = null
    var memoryReportFile: String? = null
    var doubleBuffering: Boolean = true
    var sleepBetweenRuns: Boolean = false
    var blitScrolling: Boolean = false
}

/**
 * Reads the command line as the original reads it, printing what the original prints.
 *
 * An unrecognised argument ends the run, as it does there: a benchmark that ignores an option reports a
 * number for something other than what was asked for. So does `-lf` together with `-n`, which name two
 * different look and feels.
 */
internal fun parseOptions(args: Array<String>): Options {
    val options = Options()
    var namedLookAndFeels = 0
    var index = 0

    while (index < args.size) {
        val argument = args[index]
        when {
            argument.startsWith("-lf") -> {
                options.lookAndFeel = args[++index]
                namedLookAndFeels++
            }

            argument.startsWith("-n") -> {
                options.lookAndFeel = UIManager.getSystemLookAndFeelClassName()
                namedLookAndFeels++
            }

            argument.startsWith("-r") -> {
                options.runs = args[++index].toInt()
                println("Will run test ${options.runs} times in the same VM")
            }

            argument == "-f" -> {
                options.reportFile = datedReportName(args[++index])
                println("Will write test report to file: ${options.reportFile}")
            }

            argument == "-m" -> {
                options.memoryReportFile = args[++index]
                println("Will write memory report to file: ${options.memoryReportFile}")
            }

            readSwitch(options, argument) -> {
                Unit
            }

            else -> {
                println("Unexpected Argument: $argument")
                exitProcess(BAD_ARGUMENTS)
            }
        }
        index++
    }

    if (namedLookAndFeels > 1) {
        println("-lf and -n are mutually exclusive\n")
        exitProcess(BAD_ARGUMENTS)
    }
    return options
}

/** Reads an option that carries no value of its own, answering whether [argument] was one. */
private fun readSwitch(
    options: Options,
    argument: String,
): Boolean {
    when (argument) {
        "-q" -> {
            options.autoQuit = true
            println("Program will automatically terminate after last run")
        }

        "-db=off" -> {
            options.doubleBuffering = false
            println("Will run without double buffering")
        }

        "-sleep" -> {
            options.sleepBetweenRuns = true
            println("Will sleep for 5 seconds between runs")
        }

        "-blit" -> {
            options.blitScrolling = true
            println("Will use fast window blitting")
        }

        "-version" -> {
            println(VERSION)
        }

        else -> {
            return false
        }
    }
    return true
}

/** Replaces a `-mmdd` marker in a report name with today's month and day, as the original does. */
private fun datedReportName(name: String): String {
    val marker = name.indexOf(DATE_MARKER)
    if (marker == -1) return name
    val today = Calendar.getInstance().apply { time = Date() }
    val month = today.get(Calendar.MONTH) + 1
    val day = today.get(Calendar.DAY_OF_MONTH)
    return name.substring(0, marker) + month + "-" + day + name.substring(marker + DATE_MARKER.length)
}
