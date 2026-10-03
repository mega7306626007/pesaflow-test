package com.pesaflow.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pesaflow.app.data.models.*
import kotlinx.coroutines.flow.Flow


@Dao
interface PendingTransactionDao {


    @Query("SELECT * FROM pending_transactions ORDER BY dateTimestamp DESC")
    fun getAllPendingTransactions(): Flow<List<PendingTransaction>>


    @Query("SELECT * FROM pending_transactions WHERE sourceTransactionId = :code")
    suspend fun findBySourceCode(code: String): PendingTransaction?


    @Query("SELECT EXISTS(SELECT 1 FROM pending_transactions WHERE sourceTransactionId = :code LIMIT 1)")
    suspend fun isDuplicateMpesa(code: String): Boolean


    @Query("SELECT * FROM pending_transactions WHERE amount = :amount AND dateTimestamp >= :start AND dateTimestamp <= :end")
    suspend fun findInWindow(amount: Double, start: Long, end: Long): List<PendingTransaction>


    // IGNORE (not REPLACE): same twin-safety as the confirmed ledger.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPendingTransaction(pending: PendingTransaction)


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPendingTransactions(pending: List<PendingTransaction>)


    @Query("DELETE FROM pending_transactions WHERE id = :id")
    suspend fun deletePendingTransaction(id: String)


    // Same batch rule as the ledger: one statement, one emission.
    @Query("DELETE FROM pending_transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int


    @Query("DELETE FROM pending_transactions")
    suspend fun deleteAllPendingTransactions()


    @Query("UPDATE pending_transactions SET category = :category, merchant = :merchant WHERE id = :id")
    suspend fun updatePendingTransaction(id: String, category: String, merchant: String)

    @Query("UPDATE pending_transactions SET category = :category, displayCategory = :displayCategory, displayMerchant = :displayMerchant WHERE id = :id")
    suspend fun updateClassification(
        id: String,
        category: String,
        displayCategory: String,
        displayMerchant: String
    )
}