package org.jetbrains.compose.swing.samples.agent.harness

import java.io.File

/** The executable [name] names: itself when it has a folder part, else the first match on the `PATH`. */
internal fun executable(
    name: String,
    path: String = System.getenv("PATH").orEmpty(),
    pathExt: String = System.getenv("PATHEXT").orEmpty(),
    isWindows: Boolean = System.getProperty("os.name").startsWith("Windows"),
): File? {
    val extensions = pathExt.split(';').filter { it.isNotEmpty() }
    val names =
        if (isWindows && extensions.none { name.endsWith(it, ignoreCase = true) }) {
            extensions.map { name + it }
        } else {
            listOf(name)
        }
    val candidates =
        if (File(name).isAbsolute || File.separatorChar in name) {
            names.asSequence().map(::File)
        } else {
            path
                .splitToSequence(if (isWindows) ';' else File.pathSeparatorChar)
                .filter { it.isNotEmpty() }
                .flatMap { folder -> names.asSequence().map { File(folder, it) } }
        }
    return candidates.firstOrNull { it.isFile && (isWindows || it.canExecute()) }
}
