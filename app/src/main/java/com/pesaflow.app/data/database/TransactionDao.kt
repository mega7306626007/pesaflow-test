package com.pesaflow.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pesaflow.app.data.models.*
import kotlinx.coroutines.flow.Flow


@Dao
interface TransactionDao {


    @Query("SELECT * FROM transactions ORDER BY dateTimestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>


    @Query("SELECT * FROM transactions WHERE confirmed = 1 ORDER BY dateTimestamp DESC")
    fun getConfirmedTransactions(): Flow<List<Transaction>>


    @Query("SELECT * FROM transactions WHERE dateTimestamp >= :start AND dateTimestamp <= :end ORDER BY dateTimestamp DESC")
    fun getTransactionsInTimeframe(start: Long, end: Long): Flow<List<Transaction>>


    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY dateTimestamp DESC")
    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>>


    @Query("SELECT * FROM transactions WHERE category = :category ORDER BY dateTimestamp DESC")
    fun getTransactionsByCategory(category: String): Flow<List<Transaction>>


    @Query("SELECT * FROM transactions WHERE sourceTransactionId = :code")
    suspend fun findBySourceCode(code: String): Transaction?


    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): Transaction?


    @Query("SELECT COUNT(*) FROM transactions WHERE dateTimestamp >= :start AND dateTimestamp <= :end")
    fun countInTimeframe(start: Long, end: Long): Long


    @Query("SELECT category, SUM(amount) as amount, COUNT(*) as count FROM transactions WHERE confirmed = 1 AND dateTimestamp >= :start AND dateTimestamp <= :end GROUP BY category")
    fun getTransactionsByCategoryInTimeframe(start: Long, end: Long): Flow<List<TransactionSummary>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<Transaction>)


    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: String)


    // Batch removal: one statement, one Flow emission. Deleting N rows
    // one-by-one re-emits the whole ledger N times — after a big scan that
    // recomposition storm ANRs the app ("keeps stopping" on Remove).
    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int


    @Query("SELECT * FROM transactions WHERE amount = :amount AND dateTimestamp >= :start AND dateTimestamp <= :end")
    suspend fun findInWindow(amount: Double, start: Long, end: Long): List<Transaction>


    @Query("SELECT COUNT(*) FROM transactions WHERE isSample = 1")
    suspend fun countSamples(): Int


    @Query("DELETE FROM transactions WHERE isSample = 1")
    suspend fun deleteSamples(): Int


    @Query("DELETE FROM transactions WHERE batchId = :batch")
    suspend fun deleteBatch(batch: String): Int


    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()


    @Query("UPDATE transactions SET confirmed = :confirmed WHERE id = :id")
    suspend fun updateConfirmation(id: String, confirmed: Boolean)

    @Query("UPDATE transactions SET category = :category, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCategory(id: String, category: String, updatedAt: Long)


    @Query("UPDATE transactions SET transferGroupId = :groupId WHERE id = :id")
    suspend fun updateTransferGroup(id: String, groupId: String?)


    @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId")
    suspend fun getByTransferGroup(groupId: String): List<Transaction>


    @Query("SELECT * FROM transactions WHERE (amount >= :minAmount AND amount <= :maxAmount) OR (merchant LIKE '%' || :searchTerm || '%' OR category LIKE '%' || :searchTerm || '%')")
    fun searchTransactions(minAmount: Double, maxAmount: Double, searchTerm: String): Flow<List<Transaction>>
}