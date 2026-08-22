package org.jetbrains.compose.swing.passcost.harness

/**
 * What one pass cost the event dispatch thread: the time it held that thread, what it allocated, and how
 * many frames it took to settle.
 */
internal class PassMeasurement(
    val nanos: Long,
    val bytes: Long,
    val frames: Int,
)
