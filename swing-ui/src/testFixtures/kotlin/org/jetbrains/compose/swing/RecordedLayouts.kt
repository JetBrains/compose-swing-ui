package org.jetbrains.compose.swing

import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager
import java.awt.LayoutManager2
import java.util.Objects

/**
 * A layout manager that delegates behavior to [delegate] and records calls made by its parent.
 * The default delegate places no children.
 * A [LayoutManager] delegate uses unbounded maximum size, 0.5 alignment, and no-op invalidation.
 *
 * @param delegate receives layout calls; the default leaves child bounds unchanged.
 * @param onLayout runs after the delegate lays out the parent.
 */
public open class RecordingLayout(
    private val delegate: LayoutManager = EmptyLayoutManager,
    private val onLayout: (Container) -> Unit = {},
) : LayoutManager2 {
    /** Every child registration, in call order. */
    public val registrations: MutableList<LayoutRegistration> = mutableListOf()

    /** Every child removal, in call order. */
    public val removals: MutableList<Component> = mutableListOf()

    /** Every invalidation, in call order. */
    public val invalidations: MutableList<Container> = mutableListOf()

    /** Every layout request, in call order. */
    public val layouts: MutableList<Container> = mutableListOf()

    override fun addLayoutComponent(
        component: Component,
        constraints: Any?,
    ) {
        registrations += LayoutRegistration(component, constraints)
        val layoutManager2 = delegate as? LayoutManager2
        if (layoutManager2 == null) {
            delegate.addLayoutComponent(constraints as? String, component)
        } else {
            layoutManager2.addLayoutComponent(component, constraints)
        }
    }

    override fun addLayoutComponent(
        name: String?,
        component: Component,
    ) {
        registrations += LayoutRegistration(component, name)
        delegate.addLayoutComponent(name, component)
    }

    override fun removeLayoutComponent(component: Component) {
        removals += component
        delegate.removeLayoutComponent(component)
    }

    override fun preferredLayoutSize(parent: Container): Dimension = delegate.preferredLayoutSize(parent)

    override fun minimumLayoutSize(parent: Container): Dimension = delegate.minimumLayoutSize(parent)

    override fun layoutContainer(parent: Container) {
        layouts += parent
        delegate.layoutContainer(parent)
        onLayout(parent)
    }

    override fun invalidateLayout(target: Container) {
        invalidations += target
        (delegate as? LayoutManager2)?.invalidateLayout(target)
    }

    override fun maximumLayoutSize(target: Container): Dimension =
        (delegate as? LayoutManager2)?.maximumLayoutSize(target) ?: Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

    override fun getLayoutAlignmentX(target: Container): Float =
        (delegate as? LayoutManager2)?.getLayoutAlignmentX(target) ?: Component.CENTER_ALIGNMENT

    override fun getLayoutAlignmentY(target: Container): Float =
        (delegate as? LayoutManager2)?.getLayoutAlignmentY(target) ?: Component.CENTER_ALIGNMENT
}

/** The child and constraints passed to a layout manager during registration. */
public class LayoutRegistration(
    /** The registered child. */
    public val component: Component,
    /** The constraints supplied during registration. */
    public val constraints: Any?,
) {
    override fun equals(other: Any?): Boolean =
        other is LayoutRegistration && component == other.component && constraints == other.constraints

    override fun hashCode(): Int = Objects.hash(component, constraints)

    override fun toString(): String = "LayoutRegistration(component=$component, constraints=$constraints)"
}

/**
 * A [MeasurementLayoutManager] that delegates layout calls and records each child's declaration.
 * The default delegate has empty preferred and minimum sizes and lays out nothing.
 */
public open class RecordingMeasurementLayout(
    delegate: LayoutManager = EmptyLayoutManager,
) : RecordingLayout(delegate),
    MeasurementLayoutManager {
    /** Every declaration, in call order. */
    public val declarations: MutableList<LayoutDeclaration> = mutableListOf()

    private val currentDeclarations = HashMap<Component, LayoutDeclaration>()

    /** The elements currently declared for [component]. */
    public fun elementsOf(component: Component): List<ParentLayoutElement> =
        currentDeclarations[component]?.elements.orEmpty()

    /** The parent data currently declared for [component]. */
    public fun parentDataOf(component: Component): Any? = currentDeclarations[component]?.parentData

    override fun removeLayoutComponent(component: Component) {
        super.removeLayoutComponent(component)
        currentDeclarations.remove(component)
    }

    override fun declareComponentLayout(
        component: Component,
        parentData: Any?,
        elements: List<ParentLayoutElement>,
    ) {
        val declaration = LayoutDeclaration(component, parentData, elements)
        declarations += declaration
        currentDeclarations[component] = declaration
    }
}

/** One atomic declaration made for a child of a measuring layout. */
public class LayoutDeclaration(
    /** The child whose layout is declared. */
    public val component: Component,
    /** The data supplied to its measuring parent. */
    public val parentData: Any?,
    /** The elements in declaration order. */
    public val elements: List<ParentLayoutElement>,
) {
    override fun equals(other: Any?): Boolean =
        other is LayoutDeclaration && component == other.component &&
            parentData == other.parentData && elements == other.elements

    override fun hashCode(): Int = Objects.hash(component, parentData, elements)

    override fun toString(): String =
        "LayoutDeclaration(component=$component, parentData=$parentData, elements=$elements)"
}

private object EmptyLayoutManager : LayoutManager {
    override fun addLayoutComponent(
        name: String?,
        component: Component,
    ): Unit = Unit

    override fun removeLayoutComponent(component: Component): Unit = Unit

    override fun preferredLayoutSize(parent: Container): Dimension = Dimension()

    override fun minimumLayoutSize(parent: Container): Dimension = Dimension()

    override fun layoutContainer(parent: Container): Unit = Unit
}
