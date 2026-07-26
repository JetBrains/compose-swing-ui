package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Composer
import java.io.File
import java.lang.reflect.Modifier
import javax.tools.Diagnostic
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [WindowScope] as a value only a window hands out for a caller compiling against the built classes,
 * not only for one writing Kotlin against the source.
 *
 * Kotlin's own constraints do not all reach the bytecode. This library targets Java 11, and a class file at
 * that version has no attribute naming the permitted subclasses of a type, so a `sealed` Kotlin type is
 * emitted as an ordinary open one and only the Kotlin compiler refuses to implement it. A final class whose
 * constructor is private is refused by the JVM's own access rules instead, and that is what these cases
 * read: the type is final, no constructor of it is visible to a Java caller, and the two members the library
 * reaches a scope through are synthetic.
 *
 * Each case compiles a Java class of its own with the JDK's compiler driven in-process over the running test
 * classpath, and reads the diagnostic javac emits. Package-private access is package-wide, so a Java class
 * written in the library's own package reaches whatever a Kotlin `internal` declaration leaves public in the
 * bytecode: the probes are compiled from that package as well as from one outside it, and neither reaches a
 * scope.
 */
class WindowScopeJavaAccessTest {
    /**
     * The control the refusals are read against: a probe in the outside package that names the published
     * type and the file class carrying [MenuBar] compiles, so the library is on the probe's classpath and a
     * refusal below is about the declaration it names.
     */
    @Test
    fun aProbeOutsideTheLibraryCompilesAgainstThePublishedScope() {
        val probe =
            compileProbe(
                packageName = OUTSIDE_PACKAGE,
                className = "SeesTheLibrary",
                body =
                    """
                    static final Class<?> SCOPE = WindowScope.class;
                    static final Class<?> MENU_BAR_FILE_CLASS = MenuBarKt.class;
                    """.trimIndent(),
            )

        assertTrue(
            probe.succeeded,
            "a probe naming the published scope must compile, or a refusal below proves nothing; " +
                "javac said:\n${probe.report()}",
        )
    }

    /**
     * The type stands for a realized window, so a value of it a caller made for itself stands for nothing.
     * The class is final in the bytecode, which is a constraint the JVM enforces at this target where the one
     * `sealed` states is not present in the class file at all.
     */
    @Test
    fun theScopeIsFinalInTheBuiltClasses() {
        assertTrue(
            Modifier.isFinal(WindowScope::class.java.modifiers),
            "${WindowScope::class.java.name} must be final in the built classes for a Java caller to be " +
                "unable to implement it",
        )
    }

    /** A Java class cannot subtype what the JVM marks final, whatever the Kotlin compiler would say. */
    @Test
    fun theScopeCannotBeSubclassedFromJava() {
        val probe =
            compileProbe(
                packageName = OUTSIDE_PACKAGE,
                className = "Forged",
                declaration = "public final class Forged extends WindowScope",
                body = "",
            )

        assertRefused(
            probe,
            expectedCodes = setOf(INHERITANCE_FROM_FINAL, INAPPLICABLE_SYMBOL),
            why = "a scope standing for no window must not be declarable as a subtype",
        )
    }

    /**
     * Every constructor of the class is private or synthetic. Kotlin cannot mangle the name of a constructor,
     * so what keeps one out of reach is the access it carries: a private constructor is refused wherever the
     * caller is, and the bridge Kotlin adds for the companion object to call is synthetic, which javac does
     * not resolve.
     */
    @Test
    fun noConstructorOfTheScopeIsVisibleToAJavaCaller() {
        val reachable =
            WindowScope::class.java.declaredConstructors.filterNot { constructor ->
                Modifier.isPrivate(constructor.modifiers) || constructor.isSynthetic
            }

        assertEquals(
            emptyList(),
            reachable,
            "no constructor of ${WindowScope::class.java.name} may be both non-private and non-synthetic",
        )
    }

    /** A caller who could construct a scope could stand one over a root pane belonging to no window. */
    @Test
    fun theScopeCannotBeConstructedFromJava() {
        forEachProbePackage { packageName ->
            val probe =
                compileProbe(
                    packageName = packageName,
                    className = "MakesAScope",
                    body =
                        """
                        static WindowScope over(JRootPane pane) {
                            return new WindowScope(pane);
                        }
                        """.trimIndent(),
                )

            assertRefused(
                probe,
                expectedCodes = setOf(INACCESSIBLE_MEMBER),
                why = "a scope must not be constructible from Java in package '$packageName'",
            )
        }
    }

    /**
     * The bridge Kotlin generates so the companion object can call the private constructor takes a marker
     * argument beyond the pane. It is public in the bytecode, so what puts it out of reach is that it is
     * synthetic: javac sees the private constructor as the only candidate, and that one argument too few.
     */
    @Test
    fun theSyntheticConstructorBridgeCannotBeCalledFromJava() {
        forEachProbePackage { packageName ->
            val probe =
                compileProbe(
                    packageName = packageName,
                    className = "CallsTheBridge",
                    body =
                        """
                        static WindowScope over(JRootPane pane) {
                            return new WindowScope(pane, null);
                        }
                        """.trimIndent(),
                )

            assertRefused(
                probe,
                expectedCodes = setOf(INAPPLICABLE_SYMBOL),
                why = "the constructor bridge must not be callable from Java in package '$packageName'",
            )
        }
    }

    /** The factory is how a window hands a scope out; reaching it is standing in for a window. */
    @Test
    fun theFactoryThatMakesAScopeCannotBeCalledFromJava() {
        assertDeclaredAndSynthetic(COMPANION_CLASS, FACTORY_METHOD)

        forEachProbePackage { packageName ->
            val probe =
                compileProbe(
                    packageName = packageName,
                    className = "CallsTheFactory",
                    body =
                        """
                        static WindowScope over(JRootPane pane) {
                            return WindowScope.Companion.$FACTORY_METHOD(pane);
                        }
                        """.trimIndent(),
                )

            assertRefused(
                probe,
                expectedCodes = setOf(UNRESOLVED_METHOD),
                why =
                    "the factory a window hands a scope out through must not be callable from Java in " +
                        "package '$packageName'",
            )
        }
    }

    /**
     * The scope carries the window's root pane for the menu bar's sake, so a caller reaching the pane off a
     * scope would reach into the window the library realizes.
     */
    @Test
    fun aScopesRootPaneCannotBeReadFromJava() {
        assertDeclaredAndSynthetic(WindowScope::class.java.name, PANE_GETTER)

        forEachProbePackage { packageName ->
            val probe =
                compileProbe(
                    packageName = packageName,
                    className = "ReadsThePane",
                    body =
                        """
                        static JRootPane of(WindowScope scope) {
                            return scope.$PANE_GETTER();
                        }
                        """.trimIndent(),
                )

            assertRefused(
                probe,
                expectedCodes = setOf(UNRESOLVED_METHOD),
                why =
                    "the root pane a scope carries must not be readable from Java in package " +
                        "'$packageName'",
            )
        }
    }

    /**
     * The declaration itself is published, and it is reached with a scope: what keeps it on a window is that
     * the receiver cannot be made, so the call is refused for want of one.
     */
    @Test
    fun aMenuBarCannotBeDeclaredOnAScopeMadeFromJava() {
        assertTrue(
            Class.forName(MENU_BAR_FILE_CLASS).declaredMethods.any { method ->
                method.name == "MenuBar" &&
                    method.parameterTypes.toList() ==
                    listOf(WindowScope::class.java, Function2::class.java, Composer::class.java, Int::class.java)
            },
            "$MENU_BAR_FILE_CLASS must declare MenuBar(WindowScope, Function2, Composer, int) for the " +
                "probe below to be about its receiver",
        )

        val probe =
            compileProbe(
                packageName = OUTSIDE_PACKAGE,
                className = "DeclaresAMenuBar",
                extraImports = listOf("androidx.compose.runtime.Composer", "kotlin.jvm.functions.Function2"),
                body =
                    """
                    @SuppressWarnings("rawtypes")
                    static void on(JRootPane pane, Function2 content, Composer composer) {
                        MenuBarKt.MenuBar(new WindowScope(pane), content, composer, 0);
                    }
                    """.trimIndent(),
            )

        assertRefused(
            probe,
            expectedCodes = setOf(INACCESSIBLE_MEMBER),
            why = "a menu bar must not be declarable on a scope a caller made for itself",
        )
    }

    /** The outcome of one probe compilation: whether javac accepted it, and the errors it emitted. */
    private class ProbeCompilation(
        val succeeded: Boolean,
        val errors: List<Diagnostic<out JavaFileObject>>,
    ) {
        /** The diagnostics as javac reported them, for a failure message that says what went wrong. */
        fun report(): String = errors.joinToString("\n") { "${it.code}: ${it.getMessage(null)}" }.ifEmpty { "nothing" }
    }

    /** Runs [case] once for a caller outside the library and once for one in the library's own package. */
    private fun forEachProbePackage(case: (packageName: String) -> Unit) {
        listOf(OUTSIDE_PACKAGE, LIBRARY_PACKAGE).forEach(case)
    }

    private fun assertRefused(
        probe: ProbeCompilation,
        expectedCodes: Set<String>,
        why: String,
    ) {
        assertFalse(probe.succeeded, "$why, but the probe compiled")
        assertEquals(
            expectedCodes,
            probe.errors.mapTo(mutableSetOf()) { it.code },
            "$why, and for that reason alone; javac said:\n${probe.report()}",
        )
    }

    /**
     * Asserts [className] declares a method named [methodName] and that every such method is synthetic, so a
     * probe refused for not resolving that name measures the barrier the annotation puts up rather than a
     * name that has moved.
     */
    private fun assertDeclaredAndSynthetic(
        className: String,
        methodName: String,
    ) {
        val declared = Class.forName(className).declaredMethods.filter { it.name == methodName }
        assertTrue(
            declared.isNotEmpty(),
            "$className must declare $methodName for the probe below to be about reaching it from Java",
        )
        assertTrue(
            declared.all { it.isSynthetic },
            "$className.$methodName must be synthetic, which is what javac refuses to resolve",
        )
    }

    /**
     * Compiles one Java class named [className] in [packageName], with [body] as its members, against the
     * classpath this test runs on. The file is written under the directory its package names, so a probe in
     * the library's own package is compiled as a member of that package.
     */
    private fun compileProbe(
        packageName: String,
        className: String,
        body: String,
        declaration: String = "public final class $className",
        extraImports: List<String> = emptyList(),
    ): ProbeCompilation {
        val compiler =
            ToolProvider.getSystemJavaCompiler()
                ?: throw AssertionError(
                    "No system Java compiler: these tests read what javac tells a Java caller, so they " +
                        "must run on a JDK rather than a JRE.",
                )
        val projectDir = createTempDirectory(prefix = "swing-ui-java-access").toFile()
        val packageDir = projectDir.resolve(packageName.replace('.', '/')).apply(File::mkdirs)
        val sourceFile =
            packageDir.resolve("$className.java").apply {
                writeText(probeSource(packageName, declaration, extraImports, body))
            }
        val classesDir = projectDir.resolve("classes").apply(File::mkdirs)

        val diagnostics = DiagnosticCollector<JavaFileObject>()
        val succeeded =
            compiler.getStandardFileManager(diagnostics, null, Charsets.UTF_8).use { fileManager ->
                val units = fileManager.getJavaFileObjectsFromFiles(listOf(sourceFile))
                val options =
                    listOf(
                        "-classpath",
                        System.getProperty("java.class.path").orEmpty(),
                        "-d",
                        classesDir.absolutePath,
                        "-nowarn",
                    )
                compiler.getTask(null, fileManager, diagnostics, options, null, units).call()
            }

        return ProbeCompilation(
            succeeded = succeeded,
            errors = diagnostics.diagnostics.filter { it.kind == Diagnostic.Kind.ERROR },
        )
    }

    /**
     * One probe's Java source. What the library declares is imported only for a probe outside it: a probe in
     * the library's own package names those types without an import, and an import of a type out of the
     * package the file itself declares is what javac would report instead of the access under test.
     */
    private fun probeSource(
        packageName: String,
        declaration: String,
        extraImports: List<String>,
        body: String,
    ): String {
        val libraryImports =
            if (packageName == LIBRARY_PACKAGE) {
                emptyList()
            } else {
                listOf("$LIBRARY_PACKAGE.MenuBarKt", "$LIBRARY_PACKAGE.WindowScope")
            }
        val imports = (libraryImports + "javax.swing.JRootPane" + extraImports).joinToString("\n") { "import $it;" }
        return "package $packageName;\n\n$imports\n\n$declaration {\n$body\n}\n"
    }

    private companion object {
        /** A package of the probe's own, so the probe compiles as a caller outside the library. */
        private const val OUTSIDE_PACKAGE = "outsider"

        /** The library's own package, which a Java class can be written in to claim package-private access. */
        private const val LIBRARY_PACKAGE = "org.jetbrains.compose.swing.window"

        /** javac's code for naming a member that is not accessible where it is named. */
        private const val INACCESSIBLE_MEMBER = "compiler.err.report.access"

        /** javac's code for a call whose arguments fit no candidate it can see. */
        private const val INAPPLICABLE_SYMBOL = "compiler.err.cant.apply.symbol"

        /** javac's code for extending a class the bytecode marks final. */
        private const val INHERITANCE_FROM_FINAL = "compiler.err.cant.inherit.from.final"

        /** javac's code for a method call it cannot resolve, which is what a synthetic method is to it. */
        private const val UNRESOLVED_METHOD = "compiler.err.cant.resolve.location.args"

        private const val MENU_BAR_FILE_CLASS = "$LIBRARY_PACKAGE.MenuBarKt"

        private const val COMPANION_CLASS = "$LIBRARY_PACKAGE.WindowScope\$Companion"

        /** The JVM name of the factory, a member of an internal companion object so left unmangled. */
        private const val FACTORY_METHOD = "of"

        /** The JVM name of the pane accessor, mangled because it is an internal member of a class. */
        private const val PANE_GETTER = "getRootPane\$swing_ui"
    }
}
