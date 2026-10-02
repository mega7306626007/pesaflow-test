package com.pesaflow.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pesaflow.app.data.context.ContextFact
import kotlinx.coroutines.flow.Flow


@Dao
interface UserContextDao {

    @Query("SELECT * FROM user_context")
    fun getAll(): Flow<List<ContextFact>>

    @Query("SELECT * FROM user_context WHERE `key` = :key")
    suspend fun get(key: String): ContextFact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fact: ContextFact)

    @Query("DELETE FROM user_context WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM user_context")
    suspend fun deleteAll()
}
