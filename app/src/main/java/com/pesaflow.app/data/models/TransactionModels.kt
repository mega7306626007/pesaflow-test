package com.pesaflow.app.data.models

import androidx.room.*
import java.util.UUID
import kotlinx.serialization.Serializable


@Serializable
enum class TransactionType { INCOME, EXPENSE, SAVING, INVESTMENT, TRANSFER }
// TRANSFER = money moved between own pockets (withdrawals, reversals, M-Pesa↔
// bank shuffles). Excluded from spent/income everywhere by construction.


@Serializable
enum class TransactionSource { MANUAL, MPESA_SMS, NOTIFICATION, SHARE_TO_APP, RECEIPT_OCR, CSV_IMPORT, NLP }


@Serializable
enum class PaymentMethod { CASH, MPESA, BANK_TRANSFER, AIRTIME, OTHER }


@Serializable
enum class BudgetType { DAILY, WEEKLY, MONTHLY, SEMESTER, ANNUAL }


enum class AppLanguage { ENGLISH, KISWAHILI, SHENG, MIXED }


enum class AppTheme { SYSTEM, LIGHT, DARK, AMOLED }


@Serializable
@Entity(
    tableName = "transactions",
    // Milestone: one M-Pesa code can never exist twice. NULLs (manual/codeless
    // rows) are exempt by SQLite semantics — only coded rows are constrained.
    indices = [Index(value = ["sourceTransactionId"], unique = true)]
)
data class Transaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val subcategory: String = "",
    val dateTimestamp: Long, // Epoch timestamp in milliseconds
    val merchant: String,
    val description: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.OTHER,
    val source: TransactionSource = TransactionSource.MANUAL,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val recurring: Boolean = false,
    val confirmed: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sourceTransactionId: String? = null, // e.g., M-Pesa transaction code
    val receiptImagePath: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isSample: Boolean = false, // demo/seed rows — excluded from sums, one-tap purgeable
    val batchId: String? = null, // CSV/scan import batch — undo a whole import at once
    val accountKind: String = "", // Account.name where it lives; "" = derive (legacy rows)
    val transferGroupId: String? = null, // paired internal moves share one id
    val transferSide: String = "", // OUT = leaves this account, IN = arrives; "" = unpaired legacy
    val isOpening: Boolean = false // opening equity, never monthly earned income
)


@Serializable
@Entity(tableName = "money_accounts")
data class MoneyAccount(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val kind: String, // Account.name: M_PESA, CASH, BANK, SAVINGS, ZIIDI, OTHER
    val label: String = "",
    val openingMinorUnits: Long = 0L, // opening equity in minor units
    val createdAt: Long = System.currentTimeMillis()
)


@Serializable
@Entity(
    tableName = "pending_transactions",
    indices = [Index(value = ["sourceTransactionId"], unique = true)]
)
data class PendingTransaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val subcategory: String = "",
    val merchant: String,
    val dateTimestamp: Long,
    val paymentMethod: PaymentMethod,
    val source: TransactionSource,
    val sourceTransactionId: String?,
    val rawText: String,
    val confidenceScore: Float = 1.0f,
    val displayCategory: String = "",
    val displayMerchant: String = ""
)

fun Transaction.isFulizaBorrowing(): Boolean =
    type == TransactionType.INCOME &&
        (subcategory.equals("Borrowed funds", ignoreCase = true) ||
            merchant.contains("fuliza", ignoreCase = true))

fun PendingTransaction.isFulizaBorrowing(): Boolean =
    type == TransactionType.INCOME &&
        (subcategory.equals("Borrowed funds", ignoreCase = true) ||
            merchant.contains("fuliza", ignoreCase = true))

fun Transaction.isEarnedIncome(): Boolean =
    type == TransactionType.INCOME && !isOpening && !isFulizaBorrowing() &&
        !isInternalTransferIncome()

fun PendingTransaction.isEarnedIncome(): Boolean =
    type == TransactionType.INCOME && !isFulizaBorrowing() &&
        !isInternalTransferIncome()

private fun Transaction.isInternalTransferIncome(): Boolean =
    subcategory.equals("Ziidi transfer", ignoreCase = true) ||
        subcategory.equals("Own account transfer", ignoreCase = true)

private fun PendingTransaction.isInternalTransferIncome(): Boolean =
    subcategory.equals("Ziidi transfer", ignoreCase = true) ||
        subcategory.equals("Own account transfer", ignoreCase = true)


@Serializable
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val category: String = "ALL", // "ALL" for global wallet budget
    val limitAmount: Double,
    val type: BudgetType,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val sharedWith: String = "" // comma-separated household names sharing this envelope
)


@Serializable
@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val targetTimestamp: Long = 0L
)


@Serializable
@Entity(tableName = "university_profiles")
data class UniversityProfile(
    @PrimaryKey val id: String = "SINGLETON_USER_PROFILE",
    val universityName: String = "",
    val campus: String = "",
    val programme: String = "",
    val yearOfStudy: String = "",
    val currentSemester: Int = 1,
    val academicYear: String = "",
    val semesterStartTimestamp: Long = 0L,
    val semesterEndTimestamp: Long = 0L,
    val startingFunding: Double = 0.0,
    val helbExpected: Double = 0.0,
    val feesAmount: Double = 0.0,
    val feesDueDate: Long = 0L,
    val fundingSource: String = "HELB" // HELB, SELF, BOTH
)


@Serializable
@Entity(tableName = "chama_groups")
data class ChamaGroup(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val contribution: Double,
    val members: String = "", // comma-separated rotation order
    val cycleDays: Int = 30,
    val startTimestamp: Long = System.currentTimeMillis(),
    val paidCycles: Int = 0
)


@Serializable
@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Double,
    val dueDate: Long, // Epoch timestamp in milliseconds
    val category: String,
    val frequency: String = "ONE_TIME", // ONE_TIME, MONTHLY, WEEKLY, CUSTOM
    val status: String = "UNPAID", // UNPAID, PAID
    val reminderEnabled: Boolean = false,
    val reminderLeadDays: Int = 3,
    val linkedPaymentId: String? = null, // ledger row that actually paid it; null = unpaid
    val amountRemaining: Double = 0.0, // >0 = remainder owed; 0 = full amount (legacy/unsplit)
    val paybill: String = "", // M-Pesa paybill / till to pay it; "" = cash or unknown
    val paidBy: String = "ME" // ME, PARENTS, SPONSOR, HELB, OTHER
)


@Serializable
@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val person: String,
    val amount: Double,
    val dateBorrowed: Long, // Epoch timestamp in milliseconds
    val dueDate: Long, // Epoch timestamp in milliseconds
    val description: String = "",
    val status: String = "OWING", // OWING, PAID, OVERDUE
    val direction: String = "THEY_OWE", // THEY_OWE (they owe me) or I_OWE (I owe them)
    val reminderEnabled: Boolean = false,
    val reminderLeadDays: Int = 3
)


@Serializable
@Entity(tableName = "meal_items")
data class MealItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mealType: String = "Lunch", // Breakfast, Lunch, Supper, Snack
    val price: Double,
    val component: String = "Complete", // Starch, Mboga, Protein, Complete, Other
    val source: String = "Buy" // Cook (raw, you cook) or Buy (ready cooked)
)


@Serializable
@Entity(tableName = "belongings")
data class Belonging(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String = "Other", // Clothes, Books, Shoes, Electronics, Other
    val status: String = "NEED", // HAVE, NEED
    val estCost: Double = 0.0,
    val priority: Int = 2, // 1 must-have, 2 nice, 3 dream
    val notes: String = ""
)


@Serializable
@Entity(tableName = "kitchen_stock")
data class KitchenStock(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String, // unga, oil, sukuma...
    val unit: String = "kg",
    val qtyFull: Double = 1.0, // pack size you buy
    val qtyLeft: Double = 1.0, // what's on the shelf now
    val dailyUse: Double = 0.25, // what a cooking day consumes
    val pricePerPack: Double = 0.0,
    val expiryTimestamp: Long = 0L, // 0 = not perishable; else eat-by date
    val eatByDays: Int = 0, // 0 = no priority; else eat-within-N-days target (1 or 5)
    val updatedAt: Long = System.currentTimeMillis()
)


// Pure stock math (kept here so unit tests cover it, not UI code).
fun stockDaysLeft(s: KitchenStock): Double =
    if (s.dailyUse > 0) (s.qtyLeft / s.dailyUse).coerceAtLeast(0.0) else Double.MAX_VALUE


fun stockRefillCost(s: KitchenStock): Double {
    if (s.qtyFull <= 0 || s.pricePerPack <= 0) return 0.0
    val fraction = ((s.qtyFull - s.qtyLeft) / s.qtyFull).coerceIn(0.0, 1.0)
    return s.pricePerPack * fraction
}

fun stockTopUpCost(s: KitchenStock, quantity: Double): Double? {
    if (
        !quantity.isFinite() || quantity <= 0 ||
        !s.qtyFull.isFinite() || s.qtyFull <= 0 ||
        !s.pricePerPack.isFinite() || s.pricePerPack <= 0
    ) return null
    return s.pricePerPack / s.qtyFull * quantity
}

fun stockAfterTopUp(s: KitchenStock, quantity: Double, now: Long = System.currentTimeMillis()): KitchenStock? {
    if (stockTopUpCost(s, quantity) == null || !s.qtyLeft.isFinite() || s.qtyLeft < 0) return null
    return s.copy(qtyLeft = s.qtyLeft + quantity, updatedAt = now)
}


fun stockReplenishDate(s: KitchenStock, now: Long = System.currentTimeMillis()): Long {
    val days = stockDaysLeft(s).toLong().coerceAtMost(3650)
    return now + days * 24L * 60 * 60 * 1000
}


data class TransactionSearchFilter(
    var merchant: String = "",
    var category: String = "",
    var minAmount: Double? = null,
    var maxAmount: Double? = null,
    var startDate: Long? = null,
    var endDate: Long? = null,
    var paymentMethod: PaymentMethod? = null,
    var transactionType: TransactionType? = null,
    var keyword: String = ""
)


data class TransactionSummary(
    val category: String,
    val amount: Double,
    val count: Int
)