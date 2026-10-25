package com.akib.mycompanion.context.data

import android.content.Context
import androidx.core.content.edit
import com.akib.mycompanion.context.domain.Place
import com.akib.mycompanion.context.domain.Rule
import com.akib.mycompanion.core.rules.TakenOver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class RuleRun(val time: Long, val ruleName: String, val changes: String)

@Singleton
class ContextRepository @Inject constructor(private val dao: ContextDao) {
    val rules: Flow<List<Rule>> = dao.observeRules().map { list -> list.map { it.toDomain() } }
    val places: Flow<List<Place>> = dao.observePlaces().map { list -> list.map { it.toDomain() } }
    val runs: Flow<List<RuleRun>> = dao.observeRuns().map { list ->
        list.map {
            RuleRun(
                it.time,
                it.ruleName,
                it.changes
            )
        }
    }

    suspend fun rule(id: Long): Rule? = dao.rule(id)?.toDomain()

    /** New rules go last in the conflict order. */
    suspend fun save(rule: Rule): Rule = if (rule.id == 0L) {
        val ordered = rule.copy(order = dao.nextOrder())
        ordered.copy(id = dao.insertRule(RuleEntity.from(ordered)))
    } else {
        dao.upsertRule(RuleEntity.from(rule))
        rule
    }

    suspend fun deleteRule(id: Long) = dao.deleteRule(id)

    /** Moves a rule one step up (wins more conflicts) or down. */
    suspend fun move(rules: List<Rule>, rule: Rule, up: Boolean) {
        val index = rules.indexOfFirst { it.id == rule.id }
        val other = rules.getOrNull(if (up) index - 1 else index + 1) ?: return
        dao.upsertRule(RuleEntity.from(rule.copy(order = other.order)))
        dao.upsertRule(RuleEntity.from(other.copy(order = rule.order)))
    }

    suspend fun place(id: Long): Place? = dao.place(id)?.toDomain()

    suspend fun save(place: Place): Place = if (place.id == 0L) {
        place.copy(id = dao.insertPlace(PlaceEntity.from(place)))
    } else {
        dao.upsertPlace(PlaceEntity.from(place))
        place
    }

    suspend fun deletePlace(id: Long) = dao.deletePlace(id)

    suspend fun recordRun(time: Long, ruleName: String, changes: String) {
        dao.insertRun(RuleRunEntity(time = time, ruleName = ruleName, changes = changes))
        dao.deleteRunsBefore(time - KEEP_RUNS_MILLIS)
    }

    private companion object {
        const val KEEP_RUNS_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}

/**
 * Which device settings the rules have taken over, kept across restarts so a rule that ends after a
 * reboot still puts the old value back.
 */
@Singleton
class TakenOverStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("context_rules", Context.MODE_PRIVATE)

    suspend fun load(): TakenOver = withContext(Dispatchers.IO) {
        TakenOver(decode(prefs.getString(BEFORE, "")), decode(prefs.getString(APPLIED, "")))
    }

    suspend fun save(takenOver: TakenOver) = withContext(Dispatchers.IO) {
        // commit: a setting must never be changed without remembering how to put it back.
        prefs.edit(commit = true) {
            putString(BEFORE, encode(takenOver.before))
            putString(APPLIED, encode(takenOver.applied))
        }
    }

    private fun encode(map: Map<String, String>) =
        map.entries.joinToString("&") { (k, v) -> URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8") }

    private fun decode(text: String?): Map<String, String> = text.orEmpty().split('&').filter {
        '=' in it
    }.associate { pair ->
        URLDecoder.decode(pair.substringBefore('='), "UTF-8") to URLDecoder.decode(pair.substringAfter('='), "UTF-8")
    }

    private companion object {
        const val BEFORE = "before"
        const val APPLIED = "applied"
    }
}
