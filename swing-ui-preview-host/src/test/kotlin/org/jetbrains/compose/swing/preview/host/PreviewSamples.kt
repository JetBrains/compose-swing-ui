package org.jetbrains.compose.swing.preview.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.tooling.Preview

/** The subjects the renderer is measured against. Each is exactly what a user would write. */
@Preview
@Composable
fun LabelPreview() {
    Button("Preview", onClick = {})
}

@Preview(widthPx = 320, heightPx = 200)
@Composable
fun SizedPreview() {
    Column {
        Label("Top")
        Button("Press", onClick = {})
    }
}

// A preview that states no size and prefers to be wider than any pane, so that what a width limit does
// to it is visible. Nothing constrains its minimum, which is what lets it be laid out narrower.
@Preview
@Composable
fun WidePreview() {
    Label("A label long enough that no preview pane is ever going to be as wide as it prefers to be")
}

// A preview that states no size and prefers to be narrow, so that a limit wider than it leaves it be.
@Preview
@Composable
fun NarrowPreview() {
    Label("Narrow")
}

@Composable
fun UnannotatedPreview() {
    Label("Not a preview")
}

/** Uses the composition and emits nothing, as a preview whose content is entirely conditioned out does. */
@Preview
@Composable
fun EmptyPreview() {
    val emit = remember { false }
    if (emit) Label("Never reached")
}

/** A preview declared in an object rather than at the top level. */
object PreviewsInAnObject {
    @Preview
    @Composable
    fun MemberPreview() {
        Label("In an object")
    }
}

@Preview(lookAndFeel = "com.example.NoSuchLookAndFeel")
@Composable
fun AbsentLookAndFeelPreview() {
    Label("Never rendered")
}

/** Prints on both streams, so a report can be shown to carry only what the host itself wrote. */
@Preview
@Composable
fun PrintingPreview() {
    println("a preview printing to standard output")
    System.err.println("a preview printing to the error stream")
    Label("Noisy")
}

/** A preview in a plain class, which the renderer has to instantiate to invoke. */
class PreviewsInAClass {
    @Preview
    @Composable
    fun MemberPreview() {
        Label("In a class")
    }
}

/** A preview in a class that cannot be instantiated, since its constructor demands an argument. */
class PreviewsNeedingAnArgument(
    private val label: String,
) {
    @Preview
    @Composable
    fun MemberPreview() {
        Label(label)
    }
}

/** Renders under a look and feel every JVM ships, so installing one can be measured. */
@Preview(lookAndFeel = "javax.swing.plaf.metal.MetalLookAndFeel")
@Composable
fun MetalPreview() {
    Label("Metal")
}

/**
 * Private, the way a preview usually is: nothing calls it, so nothing has reason to see it.
 */
@Preview(widthPx = 120, heightPx = 40)
@Composable
private fun PrivatePreview() {
    Label("Private")
}

/**
 * Renders under a theme that is neither the JDK's nor this module's, defined as a properties file and
 * reached only through the classpath. What a project previewing under its own theme writes.
 */
@Preview(lookAndFeel = "org.jetbrains.compose.swing.preview.host.SampleTheme", widthPx = 120, heightPx = 40)
@Composable
fun ThemedPreview() {
    Label("Themed")
}

/**
 * Never settles: it asks for a frame, writes state, and so is owed another one for as long as frames
 * arrive. What a preview that animates looks like to the renderer.
 */
@Preview(widthPx = 120, heightPx = 40)
@Composable
fun NeverSettlingPreview() {
    var frames by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frames++ }
        }
    }
    Label("Frame $frames")
}

/** Rendered twice, once per occurrence, so a repeated annotation can be measured. */
@Preview(name = "Narrow", widthPx = 120, heightPx = 40)
@Preview(name = "Wide", widthPx = 400, heightPx = 40)
@Composable
fun RepeatedPreview() {
    Label("Repeated")
}

/** A set of renderings worth reusing, declared the way a project declares its own. */
@Preview(name = "Metal", lookAndFeel = "javax.swing.plaf.metal.MetalLookAndFeel")
@Preview(name = "Nimbus", lookAndFeel = "javax.swing.plaf.nimbus.NimbusLookAndFeel")
annotation class PreviewLookAndFeels

@PreviewLookAndFeels
@Composable
fun MultiPreview() {
    Button("Through a set", onClick = {})
}

/** Sets compose: an annotation class that carries another, and states one of its own besides. */
@PreviewLookAndFeels
@Preview(name = "Sized", widthPx = 200, heightPx = 60)
annotation class PreviewEverything

@PreviewEverything
@Composable
fun NestedMultiPreview() {
    Label("Through nested sets")
}

/** Two annotation classes that carry each other, so the walk has to terminate on its own. */
@PreviewCycleB
@Preview(name = "Cyclic")
annotation class PreviewCycleA

@PreviewCycleA
annotation class PreviewCycleB

@PreviewCycleA
@Composable
fun CyclicMultiPreview() {
    Label("Through a cycle")
}
