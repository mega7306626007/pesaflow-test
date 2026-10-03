package com.pesaflow.app.data.repositories

import com.pesaflow.app.data.database.AppDatabase
import com.pesaflow.app.data.income.IncomeSource
import com.pesaflow.app.data.models.*
import com.pesaflow.app.data.parsers.hasRelatedSmsNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext


class FinanceRepository(private val database: AppDatabase) {


    val allTransactions: Flow<List<Transaction>> = database.transactionDao().getAllTransactions()
    val pendingTransactions: Flow<List<PendingTransaction>> = database.pendingTransactionDao().getAllPendingTransactions()
    val budgets: Flow<List<Budget>> = database.budgetDao().getAllBudgets()
    val savingsGoals: Flow<List<SavingsGoal>> = database.savingsGoalDao().getAllSavingsGoals()
    val universityProfile: Flow<UniversityProfile?> = database.universityProfileDao().getUniversityProfile()
    val allBills: Flow<List<Bill>> = database.billDao().getAllBills()
    val allDebts: Flow<List<Debt>> = database.debtDao().getAllDebts()
    val allMealItems: Flow<List<MealItem>> = database.mealDao().getAllMealItems()
    val allBelongings: Flow<List<Belonging>> = database.belongingDao().getAllBelongings()
    val allKitchenStock: Flow<List<KitchenStock>> = database.kitchenStockDao().getAllStock()

    // Money rhythms (from PesaFlow main): confirmed fare/rent/payday hypotheses.
    val userRhythms: Flow<List<UserRhythm>> = database.userRhythmDao().getAll()
    val confirmedRhythms: Flow<List<UserRhythm>> = database.userRhythmDao().getConfirmed()

    suspend fun upsertRhythm(rhythm: UserRhythm) = database.userRhythmDao().insert(rhythm)
    suspend fun upsertRhythms(rhythms: List<UserRhythm>) = database.userRhythmDao().insertAll(rhythms)
    suspend fun confirmRhythm(id: String) = database.userRhythmDao().confirm(id)
    suspend fun dismissRhythm(id: String) = database.userRhythmDao().dismiss(id)
    suspend fun deleteRhythm(id: String) = database.userRhythmDao().delete(id)


    // Phase 6 income: Room is the single source of truth. Legacy prefs rows
    // hop over exactly once (then their key is cleared — never two ledgers).
    val incomeSources: Flow<List<IncomeSource>> = database.incomeSourceDao().getAll()

    suspend fun addIncomeSource(source: IncomeSource) = database.incomeSourceDao().insert(source)

    suspend fun setIncomeSources(sources: List<IncomeSource>) = transact {
        database.incomeSourceDao().deleteAll()
        if (sources.isNotEmpty()) database.incomeSourceDao().insertAll(sources)
    }

    suspend fun deleteIncomeSource(id: String) = database.incomeSourceDao().delete(id)


    // Phase 7 profile: declared multidimensional truth, singleton row.
    val financialProfile: Flow<FinancialProfile?> = database.financialProfileDao().getProfile()

    suspend fun saveFinancialProfile(profile: FinancialProfile) =
        database.financialProfileDao().save(profile)


    // Phase 8 user context: fact graph with provenance. Writes merge (never
    // clobber user truth with inference) and invalidate dependents on change.
    fun userContext(): Flow<List<com.pesaflow.app.data.context.ContextFact>> =
        database.userContextDao().getAll()

    suspend fun setContextFact(fact: com.pesaflow.app.data.context.ContextFact) {
        val dao = database.userContextDao()
        val existing = dao.get(fact.key)
        val merged = com.pesaflow.app.data.context.mergeFact(existing, fact)
            ?: return
        if (existing != null && existing.value != merged.value) {
            com.pesaflow.app.data.context.invalidationKeys(fact.key)
                .forEach { dao.delete(it) }
        }
        dao.upsert(merged)
    }

    suspend fun deleteContextFact(key: String) =
        database.userContextDao().delete(key)

    suspend fun clearUserContext() =
        database.userContextDao().deleteAll()


    // Phase 8 places catalogue: user-entered local knowledge.
    fun places(): Flow<List<com.pesaflow.app.data.places.Place>> =
        database.placeDao().getAll()

    suspend fun upsertPlace(place: com.pesaflow.app.data.places.Place) =
        database.placeDao().upsert(place)

    suspend fun deletePlace(id: String) =
        database.placeDao().delete(id)

    suspend fun migrateLegacyIncomeSources(context: android.content.Context) {
        val moved = com.pesaflow.app.data.income.IncomeSourceStore.consumeLegacy(context)
        if (moved.isNotEmpty()) {
            val existing = database.incomeSourceDao().getAll().first()
                .map { it.kind to it.label }.toSet()
            database.incomeSourceDao().insertAll(moved.filter { (it.kind to it.label) !in existing })
        }
    }


    // Phase 3 accounts: balances live here once postings arrive (Phase 4);
    // today they back future payroll-style opening seeds and audits.
    val moneyAccounts: Flow<List<MoneyAccount>> = database.moneyAccountDao().getAll()

    suspend fun upsertMoneyAccount(account: MoneyAccount) = database.moneyAccountDao().upsert(account)

    suspend fun deleteMoneyAccount(id: String) = database.moneyAccountDao().delete(id)

    // Atomic transfer pairing: both legs share one group id or neither does.
    suspend fun linkTransfer(groupId: String, vararg ids: String) = transact {
        ids.forEach { database.transactionDao().updateTransferGroup(it, groupId) }
    }

    suspend fun unlinkTransfer(vararg ids: String) = transact {
        ids.forEach { database.transactionDao().updateTransferGroup(it, null) }
    }


    suspend fun insertMealItem(item: MealItem) = database.mealDao().insertMealItem(item)


    suspend fun deleteMealItem(id: String) = database.mealDao().deleteMealItem(id)


    suspend fun insertBelonging(item: Belonging) = database.belongingDao().insertBelonging(item)


    suspend fun updateBelonging(item: Belonging) = database.belongingDao().updateBelonging(item)


    suspend fun deleteBelonging(id: String) = database.belongingDao().deleteBelonging(id)


    suspend fun insertKitchenStock(item: KitchenStock) = database.kitchenStockDao().insertStock(item)

    suspend fun getKitchenStock(id: String) = database.kitchenStockDao().getStock(id)


    suspend fun updateKitchenStock(item: KitchenStock) = database.kitchenStockDao().updateStock(item)


    suspend fun deleteKitchenStock(id: String) = database.kitchenStockDao().deleteStock(id)


    suspend fun clearMealItems() = database.mealDao().clearMealItems()


    val allChamaGroups: Flow<List<ChamaGroup>> = database.chamaDao().getAllChamaGroups()


    suspend fun insertChamaGroup(group: ChamaGroup) = database.chamaDao().insertChamaGroup(group)


    suspend fun deleteChamaGroup(id: String) = database.chamaDao().deleteChamaGroup(id)


    suspend fun advanceChama(id: String, cycles: Int) = database.chamaDao().updatePaidCycles(id, cycles)


    fun getTransactionsInTimeframe(start: Long, end: Long): Flow<List<Transaction>> =
        database.transactionDao().getTransactionsInTimeframe(start, end)


    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> =
        database.transactionDao().getTransactionsByType(type)


    fun getTransactionsByCategory(category: String): Flow<List<Transaction>> =
        database.transactionDao().getTransactionsByCategory(category)


    fun getTransactionsByCategoryInTimeframe(start: Long, end: Long): Flow<List<TransactionSummary>> =
        database.transactionDao().getTransactionsByCategoryInTimeframe(start, end)


    fun getConfirmedTransactions(): Flow<List<Transaction>> =
        database.transactionDao().getConfirmedTransactions()


    suspend fun insertTransaction(transaction: Transaction) {
        database.transactionDao().insertTransaction(transaction)
        // Demo rows retire the moment real money lands: samples inflate the
        // hero balance while every statistic excludes them — that split is
        // the "weird values" report. First real row purges them; they are
        // labeled samples, never user data, so no undo is owed.
        if (!transaction.isSample) {
            database.transactionDao().deleteSamples()
        }
    }


    suspend fun insertTransactions(transactions: List<Transaction>) {
        database.transactionDao().insertTransactions(transactions)
    }


    suspend fun deleteTransaction(id: String) {
        database.transactionDao().deleteTransaction(id)
    }


    /** Batch delete: single statement so bulk removes emit once. */
    suspend fun deleteTransactions(ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        return database.transactionDao().deleteByIds(ids)
    }


    suspend fun deleteAllTransactions() {
        database.transactionDao().deleteAllTransactions()
    }


    /** Runs a block atomically — restores and multi-row writes use this so a
     *  killed process can never leave half a ledger behind. Manual
     *  begin/end (not the withTransaction helper) so the block keeps a plain
     *  suspend shape with zero receiver-type subtleties. */
    suspend fun <R> transact(block: suspend () -> R): R = withContext(Dispatchers.IO) {
        database.beginTransaction()
        try {
            val result = block()
            database.setTransactionSuccessful()
            result
        } finally {
            database.endTransaction()
        }
    }


    /** Empties every table before a v2 restore. Decode first, wipe second —
     *  a corrupt file must never cost the user their data. */
    suspend fun wipeForRestore() {
        database.transactionDao().deleteAllTransactions()
        database.pendingTransactionDao().deleteAllPendingTransactions()
        database.budgetDao().deleteAllBudgets()
        database.savingsGoalDao().deleteAllSavingsGoals()
        database.billDao().deleteAllBills()
        database.debtDao().deleteAllDebts()
        database.mealDao().clearMealItems()
        database.chamaDao().deleteAllChamaGroups()
        database.belongingDao().deleteAllBelongings()
        database.kitchenStockDao().deleteAllStock()
        database.userRhythmDao().deleteAll()
        database.moneyAccountDao().deleteAll()
        database.universityProfileDao().deleteUniversityProfile()
    }


    suspend fun updateConfirmation(id: String, confirmed: Boolean) {
        database.transactionDao().updateConfirmation(id, confirmed)
    }


    suspend fun insertPendingTransaction(pending: PendingTransaction): Boolean {
        // Check for duplicates before inserting (pending + confirmed ledgers).
        // Returns false when skipped as a duplicate so callers can say so honestly.
        val code = pending.sourceTransactionId ?: ""
        if (code.isNotEmpty()) {
            if (database.pendingTransactionDao().findBySourceCode(code) != null) return false
            if (database.transactionDao().findBySourceCode(code) != null) return false
        }
        if (hasRelatedSmsDuplicate(pending)) return false
        if (code.isEmpty()) {
            // Codeless rows (bundles, bare notices) carry no M-Pesa code — match
            // by amount + merchant within 24h instead. Deliberately NOT applied
            // to coded rows, where two identical lunches in one day are legit.
            if (hasFuzzyDuplicate(pending.amount, pending.merchant, pending.dateTimestamp)) return false
        }
        database.pendingTransactionDao().insertPendingTransaction(pending)
        return true
    }

    private suspend fun hasRelatedSmsDuplicate(pending: PendingTransaction): Boolean {
        val window = 10L * 60 * 1000
        val start = pending.dateTimestamp - window
        val end = pending.dateTimestamp + window
        return database.pendingTransactionDao().findInWindow(pending.amount, start, end)
            .any { pending.hasRelatedSmsNotice(it) } ||
            database.transactionDao().findInWindow(pending.amount, start, end)
                .any { pending.hasRelatedSmsNotice(it) }
    }


    /** Same amount + same merchant within ±24h in either ledger. */
    suspend fun hasFuzzyDuplicate(amount: Double, merchant: String, timestamp: Long, windowMs: Long = 24L * 60 * 60 * 1000): Boolean {
        val norm = merchant.trim().lowercase()
        val start = timestamp - windowMs
        val end = timestamp + windowMs
        val nearPending = database.pendingTransactionDao().findInWindow(amount, start, end)
        if (nearPending.any { it.merchant.trim().lowercase() == norm }) return true
        val nearConfirmed = database.transactionDao().findInWindow(amount, start, end)
        return nearConfirmed.any { it.merchant.trim().lowercase() == norm }
    }


    suspend fun countSamples(): Int = database.transactionDao().countSamples()


    /** Removes all demo rows. Returns how many were removed. */
    suspend fun purgeSamples(): Int {
        val n = database.transactionDao().countSamples()
        if (n > 0) database.transactionDao().deleteSamples()
        return n
    }


    /** Removes one CSV/scan import batch. Returns how many rows were removed. */
    suspend fun deleteBatch(batch: String): Int =
        database.transactionDao().deleteBatch(batch)


    suspend fun insertPendingTransactions(pending: List<PendingTransaction>) {
        for (p in pending) {
            insertPendingTransaction(p)
        }
    }

    suspend fun reclassifySmsRows(rows: List<PendingTransaction>): Pair<Int, Int> {
        if (rows.isEmpty()) return 0 to 0
        val pendingRows = database.pendingTransactionDao().getAllPendingTransactions().first()
            .filter { it.source == TransactionSource.MPESA_SMS }
        val confirmedRows = database.transactionDao().getAllTransactions().first()
            .filter { it.source == TransactionSource.MPESA_SMS }
        val updatedPendingIds = mutableSetOf<String>()
        val updatedTransactionIds = mutableSetOf<String>()
        var pendingUpdated = 0
        var transactionsUpdated = 0

        rows.filter { it.displayCategory.isNotBlank() }.forEach { parsed ->
            val code = parsed.sourceTransactionId?.takeIf { it.isNotBlank() }
            val pending = if (code != null) {
                pendingRows.firstOrNull { it.sourceTransactionId == code }
            } else {
                pendingRows.firstOrNull {
                    it.sourceTransactionId == null &&
                        it.amount == parsed.amount &&
                        it.dateTimestamp == parsed.dateTimestamp &&
                        it.rawText == parsed.rawText
                }
            }
            if (pending != null && pending.id !in updatedPendingIds && pending.category != parsed.category) {
                database.pendingTransactionDao().updateClassification(
                    pending.id,
                    parsed.category,
                    parsed.displayCategory,
                    parsed.displayMerchant
                )
                updatedPendingIds.add(pending.id)
                pendingUpdated++
            }

            val transaction = if (code != null) {
                confirmedRows.firstOrNull { it.sourceTransactionId == code }
            } else {
                confirmedRows.firstOrNull {
                    it.sourceTransactionId == null &&
                        it.amount == parsed.amount &&
                        it.dateTimestamp == parsed.dateTimestamp &&
                        it.description == parsed.rawText
                }
            }
            if (transaction != null && transaction.id !in updatedTransactionIds &&
                transaction.category != parsed.category
            ) {
                database.transactionDao().updateCategory(
                    transaction.id,
                    parsed.category,
                    System.currentTimeMillis()
                )
                updatedTransactionIds.add(transaction.id)
                transactionsUpdated++
            }
        }
        return pendingUpdated to transactionsUpdated
    }


    suspend fun deletePendingTransaction(id: String) {
        database.pendingTransactionDao().deletePendingTransaction(id)
    }


    /** Batch pending delete: single statement so sweeps emit once. */
    suspend fun deletePendingTransactions(ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        return database.pendingTransactionDao().deleteByIds(ids)
    }


    /** One-shot pending list for user-initiated sweeps (dedup, bulk ops). */
    suspend fun pendingOnce(): List<PendingTransaction> =
        database.pendingTransactionDao().getAllPendingTransactions().first()


    suspend fun approvePendingTransaction(pending: PendingTransaction, customizedCategory: String, finalType: TransactionType = pending.type): Transaction {
        val transaction = Transaction(
            amount = pending.amount,
            type = finalType,
            category = customizedCategory,
            subcategory = pending.subcategory,
            dateTimestamp = pending.dateTimestamp,
            merchant = pending.merchant,
            description = pending.rawText,
            paymentMethod = pending.paymentMethod,
            source = pending.source,
            sourceTransactionId = pending.sourceTransactionId,
            confirmed = true,
            accountKind = com.pesaflow.app.data.finance.accountKindFor(
                pending.paymentMethod, pending.merchant, finalType
            ).name
        )
        insertTransaction(transaction)
        deletePendingTransaction(pending.id)
        return transaction
    }


    suspend fun rejectPendingTransaction(id: String) {
        database.pendingTransactionDao().deletePendingTransaction(id)
    }


    // Budget operations
    suspend fun insertBudget(budget: Budget) = database.budgetDao().insertBudget(budget)


    suspend fun deleteBudget(id: String) = database.budgetDao().deleteBudget(id)


    suspend fun updateBudgetAmount(id: String, amount: Double) = database.budgetDao().updateBudgetAmount(id, amount)


    suspend fun deleteBudgetsByType(type: BudgetType) = database.budgetDao().deleteBudgetsByType(type)


    suspend fun updateBudgetShared(id: String, names: String) = database.budgetDao().updateSharedWith(id, names)


    fun getActiveBudgets(start: Long, end: Long): Flow<List<Budget>> =
        database.budgetDao().getActiveBudgets(start, end)


    // Savings Goals operations
    suspend fun insertSavingsGoal(goal: SavingsGoal) = database.savingsGoalDao().insertSavingsGoal(goal)


    suspend fun deleteSavingsGoal(id: String) = database.savingsGoalDao().deleteSavingsGoal(id)


    suspend fun contributeToSavingsGoal(id: String, newAmount: Double) =
        database.savingsGoalDao().updateSavingsGoalCurrent(id, newAmount)


    suspend fun updateSavingsGoalCurrent(id: String, current: Double) =
        database.savingsGoalDao().updateSavingsGoalCurrent(id, current)


    fun getGoalsWithRemaining(minCurrent: Double): Flow<List<SavingsGoal>> =
        database.savingsGoalDao().getGoalsWithRemaining(minCurrent)


    fun getCompletedGoals(): Flow<List<SavingsGoal>> =
        database.savingsGoalDao().getCompletedGoals()


    // University Profile operations
    suspend fun saveUniversityProfile(profile: UniversityProfile) =
        database.universityProfileDao().saveUniversityProfile(profile)


    // Bill operations
    suspend fun insertBill(bill: Bill) = database.billDao().insertBill(bill)


    suspend fun deleteBill(id: String) = database.billDao().deleteBill(id)


    suspend fun updateBillAmount(id: String, amount: Double, dueDate: Long, category: String, frequency: String, reminder: Boolean, leadDays: Int, paidBy: String) =
        database.billDao().updateBill(id, amount, dueDate, category, frequency, reminder, leadDays, paidBy)


    suspend fun markBillPaid(id: String) = database.billDao().markBillPaid(id)


    // Auto-fulfillment link: an already-confirmed ledger row settles the bill
    // (reopen keeps the user's row — only auto-created "billpay:" rows void).
    suspend fun linkBillPayment(id: String, paymentId: String) =
        database.billDao().markBillPaidWith(id, paymentId, 0.0)

    suspend fun updateBillPaybill(id: String, paybill: String) =
        database.billDao().updateBillPaybill(id, paybill.trim())


    // Phase 5 bill accounting: a bill becomes an expense only through a real
    // payment row, created and linked atomically. Re-marking never double-pays:
    // an existing link returns the same row. Reopen voids only auto-created
    // payments (batchId "billpay:<id>") — user money is never touched.
    suspend fun payBill(
        id: String,
        method: PaymentMethod = PaymentMethod.MPESA,
        dateTimestamp: Long = System.currentTimeMillis()
    ): Transaction? = transact {
        val bill = database.billDao().getById(id) ?: return@transact null
        if (bill.status == "PAID") {
            return@transact bill.linkedPaymentId?.let { database.transactionDao().getById(it) }
        }
        bill.linkedPaymentId?.let { database.transactionDao().getById(it) }?.let { return@transact it }
        val due = bill.amountRemaining.takeIf { it > 0 } ?: bill.amount
        val pay = Transaction(
            amount = due,
            type = TransactionType.EXPENSE,
            category = bill.category,
            dateTimestamp = dateTimestamp,
            merchant = bill.name,
            description = "Bill payment: ${bill.name}",
            paymentMethod = method,
            source = TransactionSource.MANUAL,
            confirmed = true,
            accountKind = com.pesaflow.app.data.finance.accountKindFor(method, bill.name, TransactionType.EXPENSE).name,
            batchId = "billpay:$id"
        )
        database.transactionDao().insertTransaction(pay)
        database.billDao().markBillPaidWith(id, pay.id, 0.0)
        pay
    }

    suspend fun reopenBill(id: String) = transact {
        val bill = database.billDao().getById(id) ?: return@transact
        bill.linkedPaymentId?.let { pid ->
            val pay = database.transactionDao().getById(pid)
            if (pay != null && pay.batchId == "billpay:$id") database.transactionDao().deleteTransaction(pid)
        }
        database.billDao().reopenBillWith(id, bill.amount)
    }

    // Phase 4 transfers: one call books both legs atomically under a shared
    // group id — OUT leaves the source account, IN arrives in the destination.
    // Wealth-neutral by construction; the engine proves it per snapshot.
    suspend fun recordTransfer(
        amount: Double,
        fromKind: String,
        toKind: String,
        merchant: String,
        dateTimestamp: Long = System.currentTimeMillis(),
        method: PaymentMethod = PaymentMethod.MPESA,
        category: String = "Transfer"
    ): String = transact {
        val group = java.util.UUID.randomUUID().toString()
        database.transactionDao().insertTransaction(
            Transaction(
                amount = amount, type = TransactionType.TRANSFER, category = category,
                dateTimestamp = dateTimestamp, merchant = "Transfer to $merchant",
                description = "Internal move $fromKind → $toKind",
                paymentMethod = method, source = TransactionSource.MANUAL, confirmed = true,
                accountKind = fromKind, transferGroupId = group, transferSide = "OUT"
            )
        )
        database.transactionDao().insertTransaction(
            Transaction(
                amount = amount, type = TransactionType.TRANSFER, category = category,
                dateTimestamp = dateTimestamp, merchant = "Transfer from $merchant",
                description = "Internal move $fromKind → $toKind",
                paymentMethod = method, source = TransactionSource.MANUAL, confirmed = true,
                accountKind = toKind, transferGroupId = group, transferSide = "IN"
            )
        )
        group
    }


    fun getBillsInTimeframe(start: Long, end: Long): Flow<List<Bill>> =
        database.billDao().getBillsInTimeframe(start, end)


    fun getOverdueBills(now: Long): Flow<List<Bill>> =
        database.billDao().getOverdueBills(now)


    // Debt operations
    suspend fun insertDebt(debt: Debt) = database.debtDao().insertDebt(debt)


    suspend fun deleteDebt(id: String) = database.debtDao().deleteDebt(id)


    suspend fun updateDebtAmount(id: String, amount: Double, dueDate: Long, description: String, status: String, reminder: Boolean, leadDays: Int) =
        database.debtDao().updateDebt(id, amount, dueDate, description, status, reminder, leadDays)


    suspend fun markDebtPaid(id: String) = database.debtDao().markDebtPaid(id)


    fun getOverdueDebts(now: Long): Flow<List<Debt>> =
        database.debtDao().getOverdueDebts(now)


    fun totalOwing(): Double = database.debtDao().totalOwing()


    fun totalOverdue(): Double = database.debtDao().totalOverdue()
}