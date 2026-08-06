package org.jetbrains.compose.swing.window

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [WindowScope] as a value only a window hands out, for a caller compiling against the built classes
 * rather than against the Kotlin source.
 *
 * Kotlin's own constraints do not all reach the bytecode. This library targets Java 11, and a class file at
 * that version has no attribute naming the permitted subclasses of a type, so a `sealed` Kotlin type is
 * emitted as an ordinary open one and only the Kotlin compiler refuses to implement it. What holds at this
 * target instead is what the JVM itself enforces: a final class with no accessible constructor, and members
 * marked synthetic so `javac` will not resolve a call to them. These cases read the built class for exactly
 * that: finality, constructor visibility, the factory a caller would otherwise call, and the accessor a
 * caller would otherwise bind to.
 */
class WindowScopeJavaAccessTest {
    @Test
    fun theScopeIsFinalInTheBuiltClasses() {
        assertTrue(
            Modifier.isFinal(WindowScope::class.java.modifiers),
            "${WindowScope::class.java.name} must be final in the built classes: at the Java 11 target this " +
                "library compiles to, a `sealed` Kotlin type carries no class-file attribute barring a " +
                "Java subclass, so finality is the only thing stopping one",
        )
    }

    @Test
    fun noConstructorOfTheScopeIsVisibleToAJavaCaller() {
        val reachable =
            WindowScope::class.java.declaredConstructors.filterNot { constructor ->
                Modifier.isPrivate(constructor.modifiers) || constructor.isSynthetic
            }

        assertEquals(
            emptyList(),
            reachable,
            "every constructor of ${WindowScope::class.java.name} must be private or synthetic, or a Java " +
                "caller could build a scope standing for no window",
        )
    }

    @Test
    fun theFactoryThatMakesAScopeIsSyntheticSoAJavaCallerCannotBindToIt() {
        val factories = WindowScope.Companion::class.java.declaredMethods.filter { it.name == "of" }

        assertTrue(
            factories.isNotEmpty(),
            "${WindowScope.Companion::class.java.name} must declare a method named of for this case to " +
                "be about reaching it",
        )
        assertTrue(
            factories.all { it.isSynthetic },
            "the factory that makes a scope must be synthetic, or a Java caller could build a scope over " +
                "any root pane",
        )
    }

    @Test
    fun theRootPaneAccessorIsSyntheticSoAJavaCallerCannotBindToIt() {
        val accessors = WindowScope::class.java.declaredMethods.filter { "RootPane" in it.name }

        assertTrue(
            accessors.isNotEmpty(),
            "${WindowScope::class.java.name} must declare an accessor naming its root pane for this case " +
                "to be about reaching it",
        )
        assertTrue(
            accessors.all { it.isSynthetic },
            "the root pane accessor must be synthetic, or a Java caller could reach into the window a " +
                "scope stands for through it",
        )
    }
}
