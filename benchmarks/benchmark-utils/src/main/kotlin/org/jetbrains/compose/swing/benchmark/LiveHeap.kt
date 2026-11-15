// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import java.lang.management.ManagementFactory
import javax.management.JMX
import javax.management.ObjectName

/**
 * Reads the live objects of the JVM heap as per-class instance and byte counts.
 *
 * The census counts only what survives a full collection, because a retained-bytes figure that includes garbage
 * would depend on when the last collection happened. The only source of such a count is the JVM's class histogram
 * diagnostic command, and its output is text, so this class parses it.
 */
internal class LiveHeap {
    /** The diagnostic command the platform exposes only as an MBean operation. */
    interface DiagnosticCommands {
        fun gcClassHistogram(arguments: Array<String>): String
    }

    private val diagnostics =
        JMX.newMBeanProxy(
            ManagementFactory.getPlatformMBeanServer(),
            ObjectName("com.sun.management:type=DiagnosticCommand"),
            DiagnosticCommands::class.java,
        )
    private val fullCollector = ManagementFactory.getGarbageCollectorMXBeans().first { it.name == "MarkSweepCompact" }
    private val whitespace = Regex("\\s+")

    fun histogram(result: MutableMap<String, ClassUsage>? = null): MutableMap<String, ClassUsage> {
        // Without -all the histogram counts only objects surviving a full collection. The JVM skips that collection
        // while a thread is inside a JNI critical region, and the histogram then counts unreachable objects too, so
        // it repeats until a full collection ran; a young collection leaves unreachable old objects.
        var histogram: String
        do {
            val collections = fullCollector.collectionCount
            histogram = diagnostics.gcClassHistogram(emptyArray())
        } while (fullCollector.collectionCount == collections)
        return parse(histogram, result ?: HashMap(histogram.count { it == '\n' }))
    }

    fun parse(
        histogram: String,
        result: MutableMap<String, ClassUsage> = mutableMapOf(),
    ): MutableMap<String, ClassUsage> {
        // A histogram row reads "num: instances bytes class name", so the class name is the fourth cell.
        val classColumn = 3
        result.values.forEach {
            it.clear()
        }
        for (line in histogram.lineSequence()) {
            val cells = line.trim().split(whitespace)
            if (cells.size > classColumn && cells[0].endsWith(":")) {
                val name = cells[classColumn]
                // The JDK fills unused heap ranges with these classes; they hold no live data. The histogram names
                // classes only as text, and the JDK-internal class is not accessible.
                if ("jdk.internal.vm.Filler" !in name) {
                    val usage = result.getOrPut(name) { ClassUsage(0, 0) }
                    usage.add(cells[1].toLong(), cells[2].toLong())
                }
            }
        }
        return result
    }

    /**
     * The instance and byte totals of one class.
     *
     * It is mutable so that a census can refill the same objects on every call; a fresh map per call would put the
     * measurement's own allocations into the heap it measures.
     */
    class ClassUsage(
        instances: Long,
        bytes: Long,
    ) {
        var instances: Long = instances
            private set
        var bytes: Long = bytes
            private set

        fun clear() {
            instances = 0
            bytes = 0
        }

        fun add(
            instances: Long,
            bytes: Long,
        ) {
            this.instances += instances
            this.bytes += bytes
        }
    }
}
