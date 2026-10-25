package com.akib.mycompanion.context.device

import com.akib.mycompanion.context.data.ContextRepository
import com.akib.mycompanion.context.data.TakenOverStore
import com.akib.mycompanion.context.domain.ContextSnapshot
import com.akib.mycompanion.context.domain.Rule
import com.akib.mycompanion.context.domain.RuleEvaluator
import com.akib.mycompanion.core.host.HostedComponent
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.rules.Reconciler
import com.akib.mycompanion.core.rules.TakenOver
import com.akib.mycompanion.core.rules.Wanted
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.settings.Settings
import com.akib.mycompanion.core.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface TakenOverStorage {
    suspend fun load(): TakenOver

    suspend fun save(takenOver: TakenOver)
}

fun interface RunRecorder {
    suspend fun record(time: Long, ruleName: String, changes: String)
}

/**
 * Makes the phone match the active rules: works out what to change with the core [Reconciler],
 * applies it, remembers what it took over and writes the history.
 */
@Singleton
class RulesEngine @Inject constructor(
    private val device: DeviceSettings,
    private val storage: TakenOverStorage,
    private val runs: RunRecorder,
    private val log: AppLog,
    private val clock: Clock,
) {
    private val lock = Mutex()
    private val mutableActive = MutableStateFlow<Set<Long>>(emptySet())

    /** Rules whose triggers match right now, for the "active" badge. */
    val active: StateFlow<Set<Long>> = mutableActive.asStateFlow()

    suspend fun run(rules: List<Rule>, snapshot: ContextSnapshot) = lock.withLock {
        val applying = rules.filter { RuleEvaluator.applies(it, snapshot) }
        mutableActive.value = applying.map { it.id }.toSet()
        apply(applying, rules)
    }

    /** Puts back everything the rules changed (feature switched off, or everything paused). */
    suspend fun restoreAll() = lock.withLock {
        mutableActive.value = emptySet()
        apply(emptyList(), emptyList())
    }

    private suspend fun apply(applying: List<Rule>, all: List<Rule>) {
        val wanted = applying.map { rule -> Wanted(rule.id, rule.order, rule.actions.associate { it.key to it.value }) }
        val result = Reconciler.reconcile(wanted, storage.load(), device::read)
        val names = all.associate { it.id to it.name }
        result.changes.groupBy { it.ruleId }.forEach { (ruleId, changes) ->
            val described = changes.joinToString(", ") { change ->
                val outcome = when (val written = device.write(change.key, change.value)) {
                    WriteResult.Applied -> ""
                    WriteResult.AskedUser -> " (asked you)"
                    is WriteResult.NotAllowed -> " (failed: ${written.reason})"
                }
                "${change.key} → ${change.value}$outcome"
            }
            val name = ruleId?.let { names[it] ?: "Rule $it" } ?: "Restored"
            log.i(TAG, "$name: $described")
            runs.record(clock.nowMillis(), name, described)
        }
        storage.save(result.takenOver)
    }

    private companion object {
        const val TAG = "Rules"
    }
}

/** Runs the rules in the background service while "Context rules" is on and nothing is paused. */
class RulesComponent @Inject constructor(
    private val repository: ContextRepository,
    private val sensors: ContextSensors,
    private val places: PlaceDetector,
    private val engine: RulesEngine,
) : HostedComponent {
    override val name = "context-rules"

    override fun isWanted(settings: Settings) = settings.isActive(Feature.CONTEXT_RULES)

    override fun start(scope: CoroutineScope) {
        places.start(scope)
        scope.launch {
            try {
                combine(repository.rules, sensors.snapshots()) { rules, snapshot -> rules to snapshot }
                    .collectLatest { (rules, snapshot) -> engine.run(rules, snapshot) }
            } finally {
                withContext(NonCancellable) { engine.restoreAll() }
            }
        }
    }
}

class StoredTakenOver @Inject constructor(private val store: TakenOverStore) : TakenOverStorage {
    override suspend fun load() = store.load()

    override suspend fun save(takenOver: TakenOver) = store.save(takenOver)
}
