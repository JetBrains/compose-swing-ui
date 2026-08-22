package org.jetbrains.compose.swing.swingmark

import org.jetbrains.compose.swing.swingmark.harness.SwingMarkTest
import org.jetbrains.compose.swing.swingmark.declared.ListTest as DeclaredListTest
import org.jetbrains.compose.swing.swingmark.declared.SliderTest as DeclaredSliderTest
import org.jetbrains.compose.swing.swingmark.declared.SubMenusTest as DeclaredSubMenusTest
import org.jetbrains.compose.swing.swingmark.declared.TableRowTest as DeclaredTableRowTest
import org.jetbrains.compose.swing.swingmark.declared.TextAreaTest as DeclaredTextAreaTest
import org.jetbrains.compose.swing.swingmark.declared.TreeTest as DeclaredTreeTest
import org.jetbrains.compose.swing.swingmark.raw.ListTest as RawListTest
import org.jetbrains.compose.swing.swingmark.raw.SliderTest as RawSliderTest
import org.jetbrains.compose.swing.swingmark.raw.SubMenusTest as RawSubMenusTest
import org.jetbrains.compose.swing.swingmark.raw.TableRowTest as RawTableRowTest
import org.jetbrains.compose.swing.swingmark.raw.TextAreaTest as RawTextAreaTest
import org.jetbrains.compose.swing.swingmark.raw.TreeTest as RawTreeTest

/** The two ways the suite drives a screen. Both are timed by the same harness. */
internal enum class Arm(
    val label: String,
) {
    /** Swing widgets built and driven by their setters, as the JDK's own suite drives them. */
    RAW("raw"),

    /** The same screen declared through compose-swing-ui, driven by writing state. */
    DECLARED("declared"),
}

/** One test, as the pair of arms that show the same screen and make the same changes to it. */
internal class TestPair(
    private val raw: SwingMarkTest,
    private val declared: SwingMarkTest,
) {
    /** The name both arms report, which is the name the original reports. */
    val testName: String = raw.testName

    init {
        require(raw.testName == declared.testName) {
            "the arms of one test report different names: '${raw.testName}' and '${declared.testName}'"
        }
    }

    /**
     * What [arm] of this test is called wherever a run is read per arm: the row of the XML report, and
     * the stem of the span name the trace records that arm's timed section under. One name, so the two
     * are read together.
     */
    fun rowName(arm: Arm): String = "${arm.label} $testName"

    /**
     * The span the trace records [arm]'s timed section under on repetition [run], which is counted from
     * zero and named from one, as the suite names its runs everywhere it prints one.
     *
     * The repetitions of one arm show the same screen and make the same changes to it, so nothing but
     * the name tells them apart in a trace, and nothing but the state of the VM behind them differs.
     * A reader that holds them apart reads a repetition against the ones before it rather than mixing
     * them into one figure, and drops the first as the printed comparison drops it.
     */
    fun sectionName(
        arm: Arm,
        run: Int,
    ): String = "${rowName(arm)} run ${run + 1}"

    operator fun get(arm: Arm): SwingMarkTest =
        when (arm) {
            Arm.RAW -> raw
            Arm.DECLARED -> declared
        }
}

/**
 * Every test the suite runs, in the order `TestList.txt` names them.
 *
 * A test joins the suite as one file per arm and one line here; [blitScrolling] is what `-blit` asks for,
 * which the raw arm's scrolling tests set on the viewport they build.
 */
internal fun testPairs(blitScrolling: Boolean): List<TestPair> =
    listOf(
        TestPair(RawSubMenusTest(), DeclaredSubMenusTest()),
        TestPair(RawTextAreaTest(blitScrolling), DeclaredTextAreaTest()),
        TestPair(RawSliderTest(), DeclaredSliderTest()),
        TestPair(RawListTest(blitScrolling), DeclaredListTest()),
        TestPair(RawTableRowTest(blitScrolling), DeclaredTableRowTest()),
        TestPair(RawTreeTest(blitScrolling), DeclaredTreeTest()),
    )
