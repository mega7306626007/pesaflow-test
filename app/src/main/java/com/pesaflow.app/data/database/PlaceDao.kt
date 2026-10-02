package com.pesaflow.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pesaflow.app.data.places.Place
import kotlinx.coroutines.flow.Flow


@Dao
interface PlaceDao {

    @Query("SELECT * FROM places ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<Place>>

    @Query("SELECT * FROM places WHERE kind = :kind ORDER BY updatedAt DESC")
    suspend fun byKind(kind: String): List<Place>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(place: Place)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM places")
    suspend fun deleteAll()
}
