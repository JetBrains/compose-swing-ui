package org.jetbrains.compose.swing.passcost.harness

import java.lang.management.ManagementFactory

private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

/**
 * Turns on the JVM's per-thread allocation accounting, which [currentThreadAllocatedBytes] reads.
 *
 * Raises if the JVM cannot account for allocation at all: a figure of zero bytes would otherwise read
 * as a runtime that allocates nothing.
 */
internal fun enableAllocationCounting() {
    check(threads.isThreadAllocatedMemorySupported) {
        "this JVM does not account for per-thread allocation, so nothing here can be measured"
    }
    if (!threads.isThreadAllocatedMemoryEnabled) threads.isThreadAllocatedMemoryEnabled = true
}

/**
 * How many bytes the calling thread has allocated since it started, as the JVM accounts for it.
 *
 * Only a difference between two reads on one thread means anything: the number itself carries
 * everything that thread ever allocated. Every read here is taken on the event dispatch thread, which
 * is the thread the composition recomposes and writes to the widgets on.
 */
internal fun currentThreadAllocatedBytes(): Long = threads.currentThreadAllocatedBytes
