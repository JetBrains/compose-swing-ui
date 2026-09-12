package org.jetbrains.compose.swing.test

import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.io.path.createTempDirectory

/**
 * In-process Kotlin compiler harness that compiles a snippet with the Compose compiler plugin
 * and captures diagnostics on the current test classpath.
 */
@InternalSwingUiApi
public object InProcessCompilerHarness {
    /** Source snippet to compile. */
    @InternalSwingUiApi
    public class SourceSpec(
        /** Path of the source file relative to the temporary source root. */
        public val relativePath: String,
        /** Source file contents. */
        public val contents: String,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SourceSpec) return false
            return relativePath == other.relativePath && contents == other.contents
        }

        override fun hashCode(): Int = 31 * relativePath.hashCode() + contents.hashCode()

        override fun toString(): String = "SourceSpec(relativePath='$relativePath', contents='$contents')"
    }

    /** Compiler outcome holding the [exitCode] and diagnostic [output]. */
    @InternalSwingUiApi
    public class CompilationResult(
        /** Exit code returned by the compiler. */
        public val exitCode: ExitCode,
        /** Raw diagnostic stream emitted by the compiler. */
        public val output: String,
    ) {
        /** Error diagnostics emitted by the compiler, stripped of source locations. */
        public fun errors(): List<String> =
            output
                .lineSequence()
                .mapNotNull { line ->
                    val separator = ERROR_DIAGNOSTIC_MARKER.find(line) ?: return@mapNotNull null
                    line.substring(separator.range.last + 1)
                }.toList()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CompilationResult) return false
            return exitCode == other.exitCode && output == other.output
        }

        override fun hashCode(): Int = 31 * exitCode.hashCode() + output.hashCode()

        override fun toString(): String = "CompilationResult(exitCode=$exitCode, output='$output')"
    }

    /** Compiles [source] with the Compose compiler plugin loaded. */
    public fun compileSnippet(
        relativePath: String,
        source: String,
    ): CompilationResult = compileSnippet(SourceSpec(relativePath, source), composePluginClasspath)

    /** Compiles [source] with the compiler plugin JARs at [pluginClasspath] loaded. */
    public fun compileSnippet(
        source: SourceSpec,
        pluginClasspath: List<File>,
    ): CompilationResult {
        val projectDir = createTempDirectory(prefix = "swing-ui-inprocess-compiler").toFile()
        val sourceRoot = projectDir.resolve("src").apply(File::mkdirs)
        val classesDir = projectDir.resolve("classes").apply(File::mkdirs)

        val sourceFile = sourceRoot.resolve(source.relativePath)
        sourceFile.parentFile.mkdirs()
        sourceFile.writeText(source.contents)

        val compilerOutput = ByteArrayOutputStream()
        return PrintStream(compilerOutput, true, Charsets.UTF_8.name()).use { output ->
            val args = buildCompilerArgs(classesDir, sourceFile, pluginClasspath)
            val exitCode = K2JVMCompiler().exec(output, *args)
            CompilationResult(exitCode, compilerOutput.toString(Charsets.UTF_8))
        }
    }

    private fun buildCompilerArgs(
        classesDir: File,
        sourceFile: File,
        pluginClasspath: List<File>,
    ): Array<String> =
        buildList {
            add("-d")
            add(classesDir.absolutePath)
            add("-module-name")
            add("inprocess-compiler-test")
            // Inherit the test classpath so the snippet resolves library composables and Compose runtime.
            add("-classpath")
            add(System.getProperty("java.class.path").orEmpty())
            add("-no-stdlib")
            add("-no-reflect")
            add("-jvm-target")
            add("11")
            pluginClasspath.forEach { jar -> add("-Xplugin=${jar.absolutePath}") }
            add(sourceFile.absolutePath)
        }.toTypedArray()

    private val ERROR_DIAGNOSTIC_MARKER = Regex(""":\s*error:\s*""")

    private const val PLUGIN_CLASSPATH_PROPERTY = "compose.compiler.plugin.classpath"

    /** Compose compiler plugin JARs resolved from [PLUGIN_CLASSPATH_PROPERTY]. */
    public val composePluginClasspath: List<File> by lazy { resolveComposePluginClasspath() }

    /** Resolves and validates the plugin JARs specified by [PLUGIN_CLASSPATH_PROPERTY]. */
    public fun resolveComposePluginClasspath(): List<File> {
        val raw =
            System.getProperty(PLUGIN_CLASSPATH_PROPERTY)
                ?: throw AssertionError(
                    "System property '$PLUGIN_CLASSPATH_PROPERTY' is not set; run via Gradle (./gradlew test) " +
                        "or pass -D$PLUGIN_CLASSPATH_PROPERTY=<path-to-compose-compiler-plugin.jar>.",
                )
        val jars = raw.split(File.pathSeparator).filter(String::isNotBlank).map(::File)
        val missing = jars.filterNot(File::exists)
        if (jars.isEmpty() || missing.isNotEmpty()) {
            throw AssertionError(
                "System property '$PLUGIN_CLASSPATH_PROPERTY' does not point to existing JARs. Value: '$raw'. " +
                    if (jars.isEmpty()) {
                        "No JAR paths were listed."
                    } else {
                        "Missing: ${missing.joinToString { it.path }}."
                    },
            )
        }
        return jars
    }
}
