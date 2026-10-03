package com.pesaflow.app.data.database

import androidx.room.*
import com.pesaflow.app.data.models.KitchenStock
import kotlinx.coroutines.flow.Flow


@Dao
interface KitchenStockDao {
    @Query("SELECT * FROM kitchen_stock ORDER BY name ASC")
    fun getAllStock(): Flow<List<KitchenStock>>

    @Query("SELECT * FROM kitchen_stock WHERE id = :id")
    suspend fun getStock(id: String): KitchenStock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStock(item: KitchenStock)

    @Update
    suspend fun updateStock(item: KitchenStock)

    @Query("DELETE FROM kitchen_stock WHERE id = :id")
    suspend fun deleteStock(id: String)

    @Query("DELETE FROM kitchen_stock")
    suspend fun deleteAllStock()
}
