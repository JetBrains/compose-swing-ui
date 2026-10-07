package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager2
import javax.swing.JPanel
import javax.swing.JTextArea

/** A stock parent whose child placement and size preference reads are supplied by the scenario. */
internal class ScriptedStockLayout(
    private val sizeOf: ((Container) -> Dimension)? = null,
    private val place: ScriptedStockLayout.(Component, Int) -> Unit,
) : LayoutManager2 {
    /** Number of parent layouts. */
    internal var layouts: Int = 0

    /** First recorded child preferred size. */
    internal var firstPreferredSize: Dimension? = null

    /** Whether the scenario reads preferences at both widths. */
    internal var readsAtBothWidths: Boolean = true

    /** An explicit width grant, when the scenario overrides its normal grant. */
    internal var grantedWidth: Int? = null

    override fun layoutContainer(parent: Container) {
        layouts++
        place(parent.getComponent(0), parent.width)
    }

    override fun preferredLayoutSize(parent: Container): Dimension =
        sizeOf?.invoke(parent) ?: parent.getComponent(0).preferredSize

    override fun minimumLayoutSize(parent: Container): Dimension =
        sizeOf?.invoke(parent) ?: parent.getComponent(0).minimumSize

    override fun maximumLayoutSize(target: Container): Dimension = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

    override fun getLayoutAlignmentX(target: Container): Float = 0f

    override fun getLayoutAlignmentY(target: Container): Float = 0f

    override fun invalidateLayout(target: Container): Unit = Unit

    override fun addLayoutComponent(
        comp: Component,
        constraints: Any?,
    ): Unit = Unit

    override fun addLayoutComponent(
        name: String?,
        comp: Component,
    ): Unit = Unit

    override fun removeLayoutComponent(comp: Component): Unit = Unit
}

/** A public four-hook policy with scenario-supplied intrinsic results and measured size. */
internal class ScriptedMeasurePolicy(
    private val minimumWidth: (Int) -> Int,
    private val preferredWidth: (Int) -> Int,
    private val minimumHeight: (Int) -> Int,
    private val preferredHeight: (Int) -> Int,
    private val measureSize: (Constraints) -> Dimension = { Dimension(it.minWidth, it.minHeight) },
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult = measureSize(constraints).let { layout(it.width, it.height) {} }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = minimumWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = preferredWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = minimumHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = preferredHeight(width)
}

/** Mounts content under a scripted Swing parent with the given host size. */
internal fun ComposeSwingTest.setStockContent(
    parent: JPanel,
    width: Int = 320,
    height: Int = 600,
    content:
        @Composable
        @SwingComposable
        () -> Unit,
): Unit =
    setContent {
        SwingNode(
            factory = { parent },
            modifier = SwingModifier.testTag("host").preferredSize(width, height),
            content = content,
        )
    }

/** Shows the same wrapping leaf directly under a stock parent or inside a Foundation column. */
internal fun ComposeSwingTest.setWrappingContent(
    layout: ScriptedStockLayout,
    inContainer: Boolean,
    area: JTextArea = wrappingTextArea(),
    width: Int = 320,
    height: Int = 600,
): Unit =
    setStockContent(JPanel(layout).apply { if (!inContainer) add(area) }, width, height) {
        if (inContainer) Column { SwingNode(factory = { area }, modifier = SwingModifier.fillMaxWidth()) }
    }

/** A wrapping Swing control shared by the native-parity scenarios. */
internal fun wrappingTextArea(): JTextArea =
    JTextArea().apply {
        text = WRAPPING_TEXT
        lineWrap = true
        wrapStyleWord = true
    }
