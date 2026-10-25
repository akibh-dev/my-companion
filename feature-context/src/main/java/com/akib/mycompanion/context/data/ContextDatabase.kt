package com.akib.mycompanion.context.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.akib.mycompanion.context.domain.Place
import com.akib.mycompanion.context.domain.Rule
import com.akib.mycompanion.context.domain.RuleCodec
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean,
    @ColumnInfo(name = "sort_order") val order: Int,
    val triggers: String,
    val actions: String,
) {
    fun toDomain() = Rule(
        id,
        name,
        enabled,
        order,
        RuleCodec.decodeTriggers(triggers),
        RuleCodec.decodeActions(actions)
    )

    companion object {
        fun from(rule: Rule) = RuleEntity(
            rule.id,
            rule.name,
            rule.enabled,
            rule.order,
            RuleCodec.encodeTriggers(rule.triggers),
            RuleCodec.encodeActions(rule.actions),
        )
    }
}

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "radius_m") val radiusMeters: Int,
    /** One network name per line. */
    @ColumnInfo(name = "wifi_names") val wifiNames: String,
) {
    fun toDomain() = Place(id, name, latitude, longitude, radiusMeters, wifiNames.lines().filter { it.isNotBlank() })

    companion object {
        fun from(place: Place) =
            PlaceEntity(
                place.id,
                place.name,
                place.latitude,
                place.longitude,
                place.radiusMeters,
                place.wifiNames.joinToString("\n")
            )
    }
}

/** History (plan §11, "RuleRun"): what a rule changed and when. */
@Entity(tableName = "rule_runs")
data class RuleRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    @ColumnInfo(name = "rule_name") val ruleName: String,
    val changes: String,
)

@Dao
interface ContextDao {
    @Query("SELECT * FROM rules ORDER BY sort_order, id")
    fun observeRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun rule(id: Long): RuleEntity?

    @Insert
    suspend fun insertRule(rule: RuleEntity): Long

    @Upsert
    suspend fun upsertRule(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    @Query("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM rules")
    suspend fun nextOrder(): Int

    @Query("SELECT * FROM places ORDER BY name COLLATE NOCASE")
    fun observePlaces(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun place(id: Long): PlaceEntity?

    @Insert
    suspend fun insertPlace(place: PlaceEntity): Long

    @Upsert
    suspend fun upsertPlace(place: PlaceEntity)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun deletePlace(id: Long)

    @Insert
    suspend fun insertRun(run: RuleRunEntity)

    @Query("SELECT * FROM rule_runs ORDER BY time DESC LIMIT :limit")
    fun observeRuns(limit: Int = 200): Flow<List<RuleRunEntity>>

    @Query("DELETE FROM rule_runs WHERE time < :before")
    suspend fun deleteRunsBefore(before: Long)
}

@Database(entities = [RuleEntity::class, PlaceEntity::class, RuleRunEntity::class], version = 1, exportSchema = false)
abstract class ContextDatabase : RoomDatabase() {
    abstract fun dao(): ContextDao

    companion object {
        fun create(context: Context): ContextDatabase =
            Room.databaseBuilder(context, ContextDatabase::class.java, "context.db").build()
    }
}
