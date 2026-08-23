package org.jetbrains.compose.swing.passcost

import kotlin.system.exitProcess

private const val BAD_ARGUMENTS = 1

/** What one run measures, as the command line names it. */
internal class Options(
    /** How many passes one batch measures. */
    val passes: Int = DEFAULT_PASSES,
    /**
     * The texts an arm's name must contain one of to be measured, matched without regard to case. Empty
     * measures every arm.
     */
    val only: List<String> = emptyList(),
    /** Whether the run prints the arms it would measure and stops there. */
    val listArms: Boolean = false,
)

/**
 * Reads the command line.
 *
 * An unrecognized argument ends the run rather than being ignored: a run that measured something other
 * than what was asked for still prints a table, and the table would be read as the answer.
 */
internal fun parseOptions(args: Array<String>): Options {
    var passes = DEFAULT_PASSES
    val only = mutableListOf<String>()
    var listArms = false
    var index = 0

    while (index < args.size) {
        when (val argument = args[index]) {
            "-p" -> {
                passes = readPasses(valueOf(args, ++index, argument))
            }

            "-only" -> {
                only += valueOf(args, ++index, argument)
            }

            "-list" -> {
                listArms = true
            }

            "-help" -> {
                usage(0)
            }

            else -> {
                println("Unexpected argument: $argument")
                usage(BAD_ARGUMENTS)
            }
        }
        index++
    }
    return Options(passes, only, listArms)
}

/** The value at [index], or an ended run where [argument] was given without one. */
private fun valueOf(
    args: Array<String>,
    index: Int,
    argument: String,
): String {
    if (index < args.size) return args[index]
    println("$argument needs a value")
    usage(BAD_ARGUMENTS)
}

private fun readPasses(named: String): Int {
    val passes = named.toIntOrNull()
    if (passes == null || passes <= 0) {
        println("passes per batch must be a positive number, not '$named'")
        usage(BAD_ARGUMENTS)
    }
    return passes
}

private fun usage(status: Int): Nothing {
    println(
        """
        PassCost [-p <passes>] [-only <text>] [-list]

          -p <passes>   how many passes one batch measures; $DEFAULT_PASSES by default
          -only <text>  measure only the arms whose name contains <text>; repeatable
          -list         print the arms that would be measured, and measure nothing
        """.trimIndent(),
    )
    exitProcess(status)
}
