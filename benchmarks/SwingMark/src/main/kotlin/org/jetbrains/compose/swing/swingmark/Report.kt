package org.jetbrains.compose.swing.swingmark

import java.awt.Toolkit
import java.io.File
import java.io.PrintWriter
import java.util.Date

/**
 * The XML report `-f` and `-m` write, in the original's own shape, so a reader of either suite's report
 * parses both the same way. A row is one arm of one test, named by both.
 */
internal class Report(
    private val startTime: Date,
    private val runs: Int,
    private val rowNames: List<String>,
) {
    /** Milliseconds per row per run, indexed by run then by row. */
    val times: Array<LongArray> = Array(runs) { LongArray(rowNames.size) }

    /** Used memory and heap size at the end of each run. */
    val memory: Array<LongArray> = Array(runs) { LongArray(2) }

    fun writeTimes(path: String) {
        println("Writing report to file: $path")
        write(path) { writer ->
            writer.println("<DATA RUNS=\"$runs\" TESTS=\"${rowNames.size}\" >")
            rowNames.forEachIndexed { row, name ->
                writer.println(name + "\t" + (0 until runs).joinToString("\t") { run -> "${times[run][row]}" })
            }
            writer.println("</DATA>")
        }
    }

    fun writeMemory(path: String) {
        println("Writing memory report to file: $path")
        write(path) { writer ->
            writer.println("Used Memory\tHeapSize")
            for (run in 0 until runs) {
                writer.println("${memory[run][0]}\t${memory[run][1]}")
            }
        }
    }

    private fun write(
        path: String,
        body: (PrintWriter) -> Unit,
    ) {
        PrintWriter(File(path).bufferedWriter()).use { writer ->
            writeHeader(writer)
            body(writer)
            writer.println()
            writer.println("</REPORT>")
        }
    }

    private fun writeHeader(writer: PrintWriter) {
        writer.println("<REPORT>")
        writer.println("<NAME>SwingMark</NAME>")
        writer.println()
        writer.println("<DATE>$startTime</DATE>")
        writer.println("<VERSION>${System.getProperty("java.version")}</VERSION>")
        writer.println("<VENDOR>${System.getProperty("java.vendor")}</VENDOR>")
        writer.println("<DIRECTORY>${System.getProperty("java.home")}</DIRECTORY>")
        writer.println("<VM_INFO>${vmInfo()}</VM_INFO>")
        writer.println("<OS>${System.getProperty("os.name")} version ${System.getProperty("os.version")}</OS>")
        writer.println("<BIT_DEPTH>${Toolkit.getDefaultToolkit().colorModel.pixelSize}</BIT_DEPTH>")
        writer.println()
    }

    private fun vmInfo(): String {
        val name = System.getProperty("java.vm.name")
        val version = System.getProperty("java.vm.info")
        return if (name != null && version != null) "$name $version" else "Undefined"
    }
}
