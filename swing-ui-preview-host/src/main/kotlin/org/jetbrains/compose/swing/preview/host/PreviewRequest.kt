package org.jetbrains.compose.swing.preview.host

import androidx.compose.runtime.reflect.ComposableMethod
import androidx.compose.runtime.reflect.getDeclaredComposableMethod
import org.jetbrains.compose.swing.tooling.Preview
import java.lang.reflect.Modifier

/**
 * The size a preview asked to be laid out at: each dimension its own annotation states, and `null`
 * where it left that dimension to the content.
 */
internal class PreviewSize(
    val width: Int?,
    val height: Int?,
)

/** One way a composable asked to be rendered, as its own annotation asked for it. */
internal class PreviewRequest(
    val name: String,
    val method: ComposableMethod,
    val receiver: Any?,
    val size: PreviewSize,
    val lookAndFeel: String,
)

/**
 * Every rendering the composable named by [fqName] asks for - a class name and a method name, dot
 * separated, where the class name of a top-level function is its file facade (`…FooKt`).
 *
 * @throws PreviewFailure if the composable cannot be found, or asks for no rendering at all.
 */
internal fun resolveRequests(fqName: String): List<PreviewRequest> {
    val owner = loadOwner(fqName)
    val method = composableMethod(owner, fqName.substringAfterLast('.'))
    val previews = previewsOf(method.asMethod())
    if (previews.isEmpty()) {
        throw PreviewFailure(
            "'$fqName' asks for no rendering: nothing on it is @Preview, and no annotation it carries " +
                "leads to one.",
        )
    }
    val receiver = receiverFor(owner, method)
    return previews.map { preview ->
        PreviewRequest(
            name = preview.name,
            method = method,
            receiver = receiver,
            size = requestedSize(preview),
            lookAndFeel = preview.lookAndFeel,
        )
    }
}

/** The class declaring the preview named by [fqName]: everything up to its last dot. */
private fun loadOwner(fqName: String): Class<*> {
    val className = fqName.substringBeforeLast('.', missingDelimiterValue = "")
    if (className.isEmpty()) {
        throw PreviewFailure("'$fqName' names no class: expected a class name and a method name, dot separated.")
    }
    return try {
        Class.forName(className)
    } catch (absent: ClassNotFoundException) {
        throw PreviewFailure(
            "Class '$className' is not on the preview classpath. A top-level function lives in its " +
                "file's facade class, so the name usually ends in 'Kt'.",
            absent,
        )
    }
}

/**
 * The composable [methodName] names, ready to invoke.
 *
 * Made accessible because a preview is usually private: nothing calls it, so nothing needs to see it,
 * and the compiler is right about that. Reflection has to be told it is being invoked on purpose.
 */
private fun composableMethod(
    owner: Class<*>,
    methodName: String,
): ComposableMethod =
    try {
        owner.getDeclaredComposableMethod(methodName).apply { asMethod().isAccessible = true }
    } catch (absent: NoSuchMethodException) {
        throw PreviewFailure(
            "'${owner.name}' declares no composable '$methodName' that takes no parameters. A preview " +
                "function cannot take parameters, because nothing supplies arguments to it.",
            absent,
        )
    }

/**
 * The instance to invoke the method on: `null` for the static method a top-level or `@JvmStatic`
 * function compiles to, an object's singleton where the preview sits in one, and otherwise a fresh
 * instance of the declaring class.
 */
private fun receiverFor(
    owner: Class<*>,
    method: ComposableMethod,
): Any? =
    if (Modifier.isStatic(method.asMethod().modifiers)) {
        null
    } else {
        owner.singletonInstance() ?: owner.instantiate()
    }

/** The `INSTANCE` an object compiles to, or `null` where the declaring class is not one. */
private fun Class<*>.singletonInstance(): Any? =
    declaredFields
        .firstOrNull { it.name == "INSTANCE" && Modifier.isStatic(it.modifiers) }
        ?.apply { isAccessible = true }
        ?.get(null)

private fun Class<*>.instantiate(): Any =
    try {
        getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    } catch (uninstantiable: ReflectiveOperationException) {
        throw PreviewFailure(
            "'$name' holds the preview but cannot be instantiated: a preview in a class needs a " +
                "constructor that takes no arguments, or belongs in an object or at the top level.",
            uninstantiable,
        )
    }

/**
 * The size the annotation states.
 *
 * Each dimension stands on its own, as the annotation's own documentation promises: stating a width
 * and leaving the height to the content is how a preview asks to be laid out at a width and to take
 * whatever height its content needs there.
 */
private fun requestedSize(annotation: Preview): PreviewSize =
    PreviewSize(
        width = annotation.widthPx.takeIf { it > 0 },
        height = annotation.heightPx.takeIf { it > 0 },
    )
