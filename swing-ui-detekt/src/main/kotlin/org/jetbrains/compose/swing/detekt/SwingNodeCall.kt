package org.jetbrains.compose.swing.detekt

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaSmartCastedReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import java.util.Collections
import java.util.WeakHashMap

internal val NODE_PACKAGE_NAME: FqName = FqName("org.jetbrains.compose.swing.node")
private val NODE_CALLEE_NAMES: Set<Name> = setOf(Name.identifier("SwingNode"), Name.identifier("MenuNode"))
internal val UPDATE: Name = Name.identifier("update")
internal val BLOCK: Name = Name.identifier("block")
internal val SWING_NODE_UPDATER: ClassId = ClassId(NODE_PACKAGE_NAME, Name.identifier("SwingNodeUpdater"))

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

/** Whether [this] is `SwingNodeUpdater.set` or `SwingNodeUpdater.update`. */
internal fun KaFunctionSymbol.isUpdaterWrite(): Boolean {
    val id = callableId ?: return false
    return id.callableName in setOf(Name.identifier("set"), UPDATE) && id.classId == SWING_NODE_UPDATER
}

/**
 * An updater block: the block lambda literal of a `set` or `update` call, the parameter symbol of its receiver and
 * the class symbol of the receiver's type, or null when that type is not a class.
 */
internal class UpdaterBlock(
    val block: KtLambdaExpression,
    val receiver: KaSymbol,
    val component: KaClassSymbol?,
)

/** A node call and the updater blocks of the `update` lambda literal it passes, in source order. */
internal class NodeUpdate(
    val call: KaFunctionCall<*>,
    val blocks: List<UpdaterBlock>,
)

/** The node call [call] makes with an `update` lambda literal, or null when it is not one. */
internal fun KaSession.nodeUpdate(call: KtCallExpression): NodeUpdate? {
    val resolved = call.resolveToCall()?.successfulFunctionCallOrNull()?.takeIf { it.isNodeCall() }
    val update = resolved?.argumentExpression(UPDATE) as? KtLambdaExpression
    return if (resolved != null && update != null) NodeUpdate(resolved, updaterBlocks(update)) else null
}

/**
 * The blocks of the `set` and `update` calls that the `update` lambda literal [update] makes on its own receiver. A
 * call on any other updater, such as one the lambda captures, is left out.
 */
@OptIn(KaExperimentalApi::class)
private fun KaSession.updaterBlocks(update: KtLambdaExpression): List<UpdaterBlock> {
    val updater = update.functionLiteral.symbol.receiverParameter ?: return emptyList()
    return update
        .collectDescendantsOfType<KtCallExpression>()
        .mapNotNull { call ->
            call
                .resolveToCall()
                ?.successfulFunctionCallOrNull()
                ?.takeIf { it.symbol.isUpdaterWrite() && isOnReceiver(it, updater) }
        }.mapNotNull { write -> write.argumentExpression(BLOCK) as? KtLambdaExpression }
        .mapNotNull { block ->
            block.functionLiteral.symbol.receiverParameter?.let {
                UpdaterBlock(block, it, (it.returnType as? KaClassType)?.symbol as? KaClassSymbol)
            }
        }
}

/**
 * Whether the dispatch receiver of [call] is [receiver], as an implicit receiver or an explicit `this`, with or
 * without a smart cast.
 */
@OptIn(KaExperimentalApi::class)
internal fun KaSession.isOnReceiver(
    call: KaCallableMemberCall<*, *>?,
    receiver: KaSymbol,
): Boolean = isReceiver(call?.partiallyAppliedSymbol?.dispatchReceiver, receiver)

/**
 * Whether [call] is made on the updater [receiver], as a member such as `reconcile` or an extension such as
 * `reconcileWithChildren`.
 */
@OptIn(KaExperimentalApi::class)
internal fun KaSession.isOnUpdater(
    call: KaCallableMemberCall<*, *>?,
    receiver: KaSymbol,
): Boolean = isOnReceiver(call, receiver) || isReceiver(call?.partiallyAppliedSymbol?.extensionReceiver, receiver)

@OptIn(KaExperimentalApi::class)
private fun KaSession.isReceiver(
    value: KaReceiverValue?,
    receiver: KaSymbol,
): Boolean =
    when (val dispatch = value) {
        is KaSmartCastedReceiverValue -> {
            isReceiver(dispatch.original, receiver)
        }

        is KaImplicitReceiverValue -> {
            dispatch.symbol == receiver
        }

        is KaExplicitReceiverValue -> {
            (KtPsiUtil.safeDeparenthesize(dispatch.expression) as? KtThisExpression)?.resolveSymbol() == receiver
        }

        else -> {
            false
        }
    }
