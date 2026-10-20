package org.jetbrains.compose.swing.samples.agent.harness

import java.nio.file.Path

/**
 * Every agent the app offers. [piExecutable] gives the path to the pi executable, which stores its sessions in
 * [dataFolder].
 */
internal fun defaultAgents(
    dataFolder: Path,
    piExecutable: () -> String,
): List<Agent> = listOf(PiRpcAgent(piExecutable, dataFolder))
