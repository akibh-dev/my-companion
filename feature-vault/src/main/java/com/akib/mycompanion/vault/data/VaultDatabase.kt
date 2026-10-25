package com.akib.mycompanion.vault.data

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

/**
 * A vault row. Everything you typed or saved is in [payload], encrypted; only what's needed to sort
 * and to find the trash stays readable. [keyId] says which key sealed it: "main", or "inbox" for
 * things shared while the vault was locked.
 */
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    @ColumnInfo(name = "key_id") val keyId: String,
    val payload: ByteArray,
    val pinned: Boolean,
    val favourite: Boolean,
    val created: Long,
    val updated: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
    val size: Long,
    val blob: String?,
) {
    override fun equals(other: Any?) = other is ItemEntity && other.id == id && other.updated == updated

    override fun hashCode() = id.hashCode()
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM items")
    suspend fun all(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE key_id = :keyId")
    suspend fun withKey(keyId: String): List<ItemEntity>

    @Query("SELECT COUNT(*) FROM items WHERE key_id = 'inbox'")
    suspend fun inboxCount(): Int

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Upsert
    suspend fun upsert(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM items WHERE deleted_at IS NOT NULL AND deleted_at < :before")
    suspend fun trashedBefore(before: Long): List<ItemEntity>
}

@Database(entities = [ItemEntity::class], version = 1, exportSchema = false)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun dao(): VaultDao

    companion object {
        fun create(context: Context) = Room.databaseBuilder(context, VaultDatabase::class.java, "vault.db").build()
    }
}
