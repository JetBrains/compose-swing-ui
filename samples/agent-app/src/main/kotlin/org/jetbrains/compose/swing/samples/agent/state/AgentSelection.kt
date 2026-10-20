package org.jetbrains.compose.swing.samples.agent.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.compose.swing.samples.agent.harness.Agent
import org.jetbrains.compose.swing.samples.agent.harness.AgentModel
import org.jetbrains.compose.swing.samples.agent.i18n.message
import java.util.Locale

/**
 * The agent and model the next prompt goes to. [onChange] runs after each change, so the owner can store it.
 * [storedAgent] and [storedModel] are the choice of the last run, if any. Each available agent lists its models in
 * [loadModels]; until its list arrives the agent offers none.
 */
@Stable
internal class AgentSelection(
    val agents: List<Agent>,
    storedAgent: String?,
    storedModel: String?,
    private val onChange: () -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val availability =
        mutableStateMapOf<Agent, Boolean>().apply {
            for (listed in agents) put(listed, listed.isAvailable)
        }
    private var preferredModel = storedModel

    fun isAvailable(agent: Agent?): Boolean = availability[agent] == true

    /** The agent the next prompt goes to; `null` when none of [agents] is available. */
    var agent by mutableStateOf(
        agents.firstOrNull { isAvailable(it) && it.displayName == storedAgent }
            ?: agents.firstOrNull { isAvailable(it) },
    )
        private set

    private val discoveries = mutableStateMapOf<Agent, ModelDiscovery>()

    val discovery: ModelDiscovery get() = agent?.let { discoveries[it] ?: ModelDiscovery.Loading } ?: unavailable()

    val canSend: Boolean get() = isAvailable(agent) && models.any { it.id == model }

    /** The models the selected agent offers, empty until it has listed them. */
    val models: List<AgentModel> get() = (discovery as? ModelDiscovery.Ready)?.models.orEmpty()

    /** The model the next prompt runs on; `null` until the agent has named one. */
    var model by mutableStateOf(storedModel)
        private set

    /** The context window the meter measures usage against, in tokens; `null` when the model does not say. */
    val contextLimit: Int? get() = models.firstOrNull { it.id == model }?.contextWindow

    /**
     * Asks every available agent for its models, and picks the first agent and model when the current ones are gone,
     * for example after the pi executable changed. Call it again when the available agents may have changed.
     */
    suspend fun loadModels() {
        discoveries.clear()
        for (listed in agents) discoveries[listed] = ModelDiscovery.Loading
        val available = withContext(ioDispatcher) { agents.associateWith { it.isAvailable } }
        availability.putAll(available)
        if (!isAvailable(agent)) agent = agents.firstOrNull { isAvailable(it) }
        model = null
        for (listed in agents) {
            discoveries[listed] =
                if (!isAvailable(listed)) {
                    unavailable()
                } else {
                    try {
                        ModelDiscovery.Ready(listed.listModels())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (
                        // Discovery failures are shown to the user; cancellation escapes to the caller.
                        @Suppress("TooGenericExceptionCaught") failure: Exception,
                    ) {
                        ModelDiscovery.Failed(
                            failure.message ?: message(Locale.getDefault(), "agents.discovery.failed"),
                        )
                    }
                }
        }
        model = models.firstOrNull { it.id == preferredModel }?.id ?: models.firstOrNull()?.id
        if (model != null) {
            preferredModel = model
            onChange()
        }
    }

    fun selectAgent(agent: Agent) {
        this.agent = agent
        model = (discoveries[agent] as? ModelDiscovery.Ready)?.models?.firstOrNull()?.id
        if (model != null) {
            preferredModel = model
            onChange()
        }
    }

    /** Makes [model] the one the next prompt runs on; it is one of the current agent's [models]. */
    fun selectModel(model: @NonNls String) {
        this.model = model
        preferredModel = model
        onChange()
    }

    /** Selects the next available agent, wrapping around. */
    fun cycleAgent() {
        val available = agents.filter { isAvailable(it) }
        if (available.isNotEmpty()) selectAgent(available[(available.indexOf(agent) + 1) % available.size])
    }

    /** Selects the current agent's next model, wrapping around. */
    fun cycleModel() {
        val ids = models.map { it.id }
        if (ids.isNotEmpty()) selectModel(ids[(ids.indexOf(model) + 1) % ids.size])
    }
}

internal sealed interface ModelDiscovery {
    data object Loading : ModelDiscovery

    data class Ready(
        val models: List<AgentModel>,
    ) : ModelDiscovery

    data class Failed(
        val reason: @Nls String,
    ) : ModelDiscovery
}

private fun unavailable() = ModelDiscovery.Failed(message(Locale.getDefault(), "agents.none.title"))
