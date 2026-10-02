package com.pesaflow.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pesaflow.app.data.models.*
import kotlinx.coroutines.flow.Flow


@Dao
interface BillDao {


    @Query("SELECT * FROM bills ORDER BY dueDate ASC")
    fun getAllBills(): Flow<List<Bill>>


    @Query("SELECT * FROM bills WHERE category = :category")
    fun getBillsByCategory(category: String): Flow<List<Bill>>


    @Query("SELECT * FROM bills WHERE frequency = :frequency")
    fun getBillsByFrequency(frequency: String): Flow<List<Bill>>


    @Query("SELECT * FROM bills WHERE dueDate >= :start AND dueDate <= :end ORDER BY dueDate ASC")
    fun getBillsInTimeframe(start: Long, end: Long): Flow<List<Bill>>


    @Query("SELECT * FROM bills WHERE dueDate < :now AND status != 'PAID'")
    fun getOverdueBills(now: Long): Flow<List<Bill>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: Bill)


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBills(bills: List<Bill>)


    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteBill(id: String)


    @Query("DELETE FROM bills")
    suspend fun deleteAllBills()


    @Query("UPDATE bills SET amount = :amount, dueDate = :dueDate, category = :category, frequency = :frequency, reminderEnabled = :reminder, reminderLeadDays = :leadDays, paidBy = :paidBy WHERE id = :id")
    suspend fun updateBill(
        id: String,
        amount: Double,
        dueDate: Long,
        category: String,
        frequency: String,
        reminder: Boolean,
        leadDays: Int,
        paidBy: String
    )


    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getById(id: String): Bill?


    @Query("UPDATE bills SET status = 'PAID' WHERE id = :id")
    suspend fun markBillPaid(id: String)


    @Query("UPDATE bills SET status = 'PAID', linkedPaymentId = :paymentId, amountRemaining = :remaining WHERE id = :id")
    suspend fun markBillPaidWith(id: String, paymentId: String, remaining: Double)


    @Query("UPDATE bills SET status = 'UNPAID' WHERE id = :id")
    suspend fun reopenBill(id: String)


    @Query("UPDATE bills SET status = 'UNPAID', linkedPaymentId = NULL, amountRemaining = :amount WHERE id = :id")
    suspend fun reopenBillWith(id: String, amount: Double)


    @Query("UPDATE bills SET paybill = :paybill WHERE id = :id")
    suspend fun updateBillPaybill(id: String, paybill: String)
}