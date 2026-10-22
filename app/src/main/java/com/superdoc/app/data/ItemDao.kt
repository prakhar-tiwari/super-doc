package com.superdoc.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<ItemEntity>>

    @Query(
        "SELECT * FROM items WHERE deletedAt IS NULL AND (title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%') " +
            "ORDER BY updatedAt DESC"
    )
    fun search(query: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND type = 'PDF' ORDER BY updatedAt DESC")
    fun observePdfs(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeRecycleBin(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE id = :id")
    fun observe(id: Long): Flow<ItemEntity?>

    @Query("SELECT title FROM items WHERE deletedAt IS NULL AND id != :exceptId")
    suspend fun activeTitles(exceptId: Long): List<String>

    @Query("SELECT * FROM items WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun deletedBefore(before: Long): List<ItemEntity>

    @Query("SELECT * FROM items WHERE isSample = 1")
    suspend fun samples(): List<ItemEntity>

    @Query("SELECT * FROM items")
    suspend fun all(): List<ItemEntity>

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteForever(id: Long)
}
