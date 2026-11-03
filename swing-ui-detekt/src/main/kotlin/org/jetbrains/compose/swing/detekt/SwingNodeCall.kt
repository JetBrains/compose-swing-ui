package org.jetbrains.compose.swing.detekt

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import java.util.Collections
import java.util.WeakHashMap

internal val NODE_PACKAGE_NAME: FqName = FqName("org.jetbrains.compose.swing.node")
private val NODE_CALLEE_NAMES: Set<Name> = setOf(Name.identifier("SwingNode"), Name.identifier("MenuNode"))

/**
 * Whether [this] names `SwingNode` or `MenuNode`, or an import alias of one, so that it can be a node call. It reads
 * the syntax only, so a rule runs it before it pays for an analysis session. The names that can denote a node call
 * are collected once per file.
 */
internal fun KtCallExpression.mayBeNodeCall(): Boolean {
    val callee = (calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ?: return false
    return callee in containingKtFile.nodeCalleeNames()
}

/** The names in [this] file that can denote a node function, kept for as long as the file object lives. */
private val nodeCalleeNamesByFile: MutableMap<KtFile, Set<String>> = Collections.synchronizedMap(WeakHashMap())

/** The two node names and the import aliases of them in [this] file. */
private fun KtFile.nodeCalleeNames(): Set<String> =
    nodeCalleeNamesByFile.getOrPut(this) {
        val aliases =
            importDirectives
                .filter { it.importedFqName?.shortName() in NODE_CALLEE_NAMES }
                .mapNotNull { it.aliasName }
        NODE_CALLEE_NAMES.mapTo(mutableSetOf()) { it.asString() } + aliases
    }

/** Whether [this] calls the top-level `SwingNode` or `MenuNode` of the node package. */
internal fun KaFunctionCall<*>.isNodeCall(): Boolean {
    val id = symbol.callableId ?: return false
    return id.packageName == NODE_PACKAGE_NAME && id.classId == null && id.callableName in NODE_CALLEE_NAMES
}

/** The resolved node call [call] makes and its factory lambda literal, or null when it is not one. */
internal fun KaSession.nodeCallWithFactory(call: KtCallExpression): Pair<KaFunctionCall<*>, KtLambdaExpression>? {
    val resolved = call.resolveToCall()?.successfulFunctionCallOrNull()?.takeIf { it.isNodeCall() }
    val factory = resolved?.argumentExpression(Name.identifier("factory")) as? KtLambdaExpression
    return if (resolved != null && factory != null) resolved to factory else null
}

/** The argument of [this] call that maps to the parameter named [name], without its parentheses. */
internal fun KaFunctionCall<*>.argumentExpression(name: Name): KtExpression? =
    valueArgumentMapping.entries
        .firstOrNull { it.value.name == name }
        ?.key
        ?.let { KtPsiUtil.safeDeparenthesize(it) }
