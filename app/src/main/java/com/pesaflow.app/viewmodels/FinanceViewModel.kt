package com.pesaflow.app.viewmodels

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.pesaflow.app.data.database.AppDatabase
import com.pesaflow.app.data.finance.Commute
import com.pesaflow.app.data.finance.DebtLevel
import com.pesaflow.app.data.finance.FinancialSnapshot
import com.pesaflow.app.data.finance.FoodStyle
import com.pesaflow.app.data.finance.Housing
import com.pesaflow.app.data.finance.IncomeStability
import com.pesaflow.app.data.finance.ProfileSignals
import com.pesaflow.app.data.finance.SnapshotInput
import com.pesaflow.app.data.finance.buildSnapshot
import com.pesaflow.app.data.finance.toSignals
import com.pesaflow.app.data.income.IncomeSource
import com.pesaflow.app.data.ledger.CategoryMemory
import com.pesaflow.app.data.ledger.LedgerGateway
import com.pesaflow.app.data.models.*
import com.pesaflow.app.data.parsers.NaturalLanguageParser
import com.pesaflow.app.data.repositories.FinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class FinanceViewModel(application: Application) : AndroidViewModel(application) {


    private val database: AppDatabase = AppDatabase.getDatabase(application)
    private val repository: FinanceRepository = FinanceRepository(database)

    private val _contactRuleScanStatus = MutableStateFlow<String?>(null)
    val contactRuleScanStatus: StateFlow<String?> = _contactRuleScanStatus.asStateFlow()
    private val _contactRescanActive = MutableStateFlow(false)
    val contactRescanActive: StateFlow<Boolean> = _contactRescanActive.asStateFlow()
    private var contactRuleScanJob: Job? = null

    fun cancelContactRescan() {
        contactRuleScanJob?.cancel()
        contactRuleScanJob = null
        _contactRescanActive.value = false
        _contactRuleScanStatus.value = "Rescan cancelled — your rule is saved and applies to new messages."
    }

    fun reprocessContactSmsHistory(contactName: String = "", matchTerms: String = "") {
        contactRuleScanJob?.cancel()
        contactRuleScanJob = viewModelScope.launch(Dispatchers.IO) {
            _contactRescanActive.value = true
            _contactRuleScanStatus.value = "Starting SMS rescan…"
            val context = getApplication<Application>()
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                _contactRuleScanStatus.value =
                    "Rule saved. Enable SMS access to apply it to existing messages; new scans will use it automatically."
                _contactRescanActive.value = false
                return@launch
            }
            try {
                val scanContext = currentCoroutineContext()
                // Scope: only rows that could match this contact go through
                // reclassification — a notes-only edit rescans nothing extra.
                val needles = ((listOf(contactName) + matchTerms.split(","))
                    .map { it.trim().lowercase() }.filter { it.isNotBlank() }).toSet()
                val result = com.pesaflow.app.data.parsers.scanRecentSms(
                    context = context,
                    maxRows = Int.MAX_VALUE,
                    pageSize = 500,
                    sinceTimestamp = 0L,
                    onProgress = { found, parsed ->
                        _contactRuleScanStatus.value =
                            "Scanning SMS history… $found found, $parsed parsed."
                    },
                    isCancelled = { !scanContext.isActive },
                    persistDerivedSignals = false
                )
                scanContext.ensureActive()
                if (result.error != null) {
                    _contactRuleScanStatus.value =
                        "Rule saved, but SMS history could not be fully scanned: ${result.error}"
                    return@launch
                }
                _contactRuleScanStatus.value =
                    "Applying “${contactName.ifBlank { "contact" }}” to ${result.parsed.size} parsed rows…"
                val (pending, confirmed) = repository.reclassifySmsRows(result.parsed) { row ->
                    needles.isEmpty() || needles.any { row.merchant.lowercase().contains(it) }
                }
                val cappedNote = if (result.capped) " Scan limit reached; not all messages were reviewed." else ""
                _contactRuleScanStatus.value =
                    "Scanned ${result.found} official texts (${result.parsed.size} parsed): recategorized $pending pending and $confirmed confirmed transactions.$cappedNote"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _contactRuleScanStatus.value =
                    "Rule saved, but applying it to SMS history failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _contactRescanActive.value = false
                contactRuleScanJob = null
            }
        }
    }


    // StateFlows for UI
    val allTransactions: StateFlow<List<Transaction>> = repository.allTransactions.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val pendingTransactions: StateFlow<List<PendingTransaction>> = repository.pendingTransactions.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val budgets: StateFlow<List<Budget>> = repository.budgets.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val savingsGoals: StateFlow<List<SavingsGoal>> = repository.savingsGoals.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val universityProfile: StateFlow<UniversityProfile?> = repository.universityProfile.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )
    val bills: StateFlow<List<Bill>> = repository.allBills.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val debts: StateFlow<List<Debt>> = repository.allDebts.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val mealItems: StateFlow<List<MealItem>> = repository.allMealItems.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val belongings: StateFlow<List<Belonging>> = repository.allBelongings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val kitchenStock: StateFlow<List<KitchenStock>> = repository.allKitchenStock.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val userRhythms: StateFlow<List<UserRhythm>> = repository.userRhythms.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val confirmedRhythms: StateFlow<List<UserRhythm>> = repository.confirmedRhythms.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val incomeSources: StateFlow<List<IncomeSource>> = repository.incomeSources.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun addIncomeSource(source: IncomeSource) {
        viewModelScope.launch { repository.addIncomeSource(source) }
    }

    fun setIncomeSources(sources: List<IncomeSource>) {
        viewModelScope.launch { repository.setIncomeSources(sources) }
    }

    fun deleteIncomeSource(id: String) {
        viewModelScope.launch { repository.deleteIncomeSource(id) }
    }

    val financialProfile: StateFlow<FinancialProfile?> = repository.financialProfile.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    fun saveFinancialProfile(profile: FinancialProfile) {
        viewModelScope.launch { repository.saveFinancialProfile(profile) }
    }

    // Phase 8 context facts: explicit, provenance-stamped, user-editable.
    fun setContextFact(fact: com.pesaflow.app.data.context.ContextFact) {
        viewModelScope.launch { repository.setContextFact(fact) }
    }

    fun deleteContextFact(key: String) {
        viewModelScope.launch { repository.deleteContextFact(key) }
    }

    fun clearUserContext() {
        viewModelScope.launch { repository.clearUserContext() }
    }

    // Phase 8 places catalogue: user-reported spots beat bundled packs
    // everywhere prices show. Stored locally; shared only via Online opt-in.
    val places: StateFlow<List<com.pesaflow.app.data.places.Place>> = repository.places().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun addPlace(name: String, area: String, price: Double, kind: String = "FOOD_OUTLET") {
        val clean = name.trim()
        if (clean.isEmpty() || price <= 0) return
        viewModelScope.launch {
            repository.upsertPlace(
                com.pesaflow.app.data.places.Place(
                    name = clean,
                    kind = kind.ifBlank { "FOOD_OUTLET" },
                    area = area.trim(),
                    priceMin = price,
                    priceMax = price,
                    source = "USER_ENTERED"
                )
            )
        }
    }

    fun deletePlace(id: String) {
        viewModelScope.launch { repository.deletePlace(id) }
    }

    val userContextFacts: StateFlow<Map<String, String>> = repository.userContext()
        .map { list -> list.associate { it.key to it.value } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Canonical snapshot (Phase 2): every screen will read money figures
    // from here instead of re-deriving them (UI rewire lands Phase 11).
    // Pure buildSnapshot over combined flows — the ViewModel orchestrates
    // state, the finance package owns the math.
    val financialSnapshot: StateFlow<FinancialSnapshot> = combine(
        allTransactions, budgets, bills, debts, savingsGoals, universityProfile, incomeSources, financialProfile
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val txs = args[0] as List<Transaction>
        @Suppress("UNCHECKED_CAST")
        val budgets = args[1] as List<Budget>
        @Suppress("UNCHECKED_CAST")
        val bills = args[2] as List<Bill>
        @Suppress("UNCHECKED_CAST")
        val debts = args[3] as List<Debt>
        @Suppress("UNCHECKED_CAST")
        val goals = args[4] as List<SavingsGoal>
        val profile = args[5] as UniversityProfile?
        @Suppress("UNCHECKED_CAST")
        val sources = args[6] as List<IncomeSource>
        val stored = args[7] as FinancialProfile?
        val persona = com.pesaflow.app.ui.budgets.parsePersona(getOnboardingAnswers())
        // Declared profile wins; persona bridge is the fallback preset.
        val signals = stored?.toSignals() ?: persona.toSignals(profile, sources, debts)
        buildSnapshot(
            SnapshotInput(
                txs = txs, budgets = budgets, bills = bills, debts = debts, goals = goals,
                incomeSources = sources,
                profile = signals,
                helbExpected = profile?.helbExpected ?: 0.0,
                feesAmount = profile?.feesAmount ?: 0.0
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), buildSnapshot(SnapshotInput(emptyList())))

    fun upsertRhythm(rhythm: UserRhythm) {
        viewModelScope.launch { repository.upsertRhythm(rhythm) }
    }

    fun confirmRhythm(id: String) {
        viewModelScope.launch { repository.confirmRhythm(id) }
    }

    fun dismissRhythm(id: String) {
        viewModelScope.launch { repository.dismissRhythm(id) }
    }


    // Runtime state bindings
    val currentLanguage = MutableStateFlow(AppLanguage.MIXED)
    val nlpInputText = MutableStateFlow("")
    val extractedNlpTransaction = MutableStateFlow<PendingTransaction?>(null)
    val userName = MutableStateFlow("")
    val nickname = MutableStateFlow("")
    val themeMode = MutableStateFlow(AppTheme.DARK)
    val hideBalances = MutableStateFlow(false)
    val hiddenSections = MutableStateFlow(setOf<String>())


    init {
        // Restore persisted identity + preferences (no new dependencies: SharedPreferences only).
        // NOTE: this block must stay AFTER the StateFlow declarations above:
        // init blocks run in textual order.
        val prefs = getApplication<Application>().getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
        userName.value = prefs.getString("user_name", "") ?: ""
        nickname.value = prefs.getString("user_nickname", "") ?: ""
        currentLanguage.value = try {
            AppLanguage.valueOf(prefs.getString("app_language", "MIXED") ?: "MIXED")
        } catch (e: Exception) {
            AppLanguage.MIXED
        }
        themeMode.value = try {
            AppTheme.valueOf(prefs.getString("app_theme", "DARK") ?: "DARK")
        } catch (e: Exception) {
            AppTheme.DARK
        }
        hideBalances.value = prefs.getBoolean("hide_balances", false)
        hiddenSections.value = (prefs.getString("hidden_sections", "") ?: "")
            .split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        // Phase 6: one-way prefs→Room hop for legacy income sources.
        viewModelScope.launch {
            repository.migrateLegacyIncomeSources(getApplication<Application>().applicationContext)
        }
    }


    private fun prefs() = getApplication<Application>().getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)


    // Computed metrics
    val availableBalance: StateFlow<Double> = financialSnapshot
        .map { it.liquid.toDouble() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)


    // Per-pocket balances: the pooled number hides which pocket holds the
    // money. M-Pesa wallet vs cash in hand vs bank, samples excluded, paired
    // transfer legs counted per side (a bank move used to vanish from every
    // pocket). Same helper the tests pin — one rule, both places.
    val mpesaBalance: StateFlow<Double> = financialSnapshot
        .map { it.accounts.getValue(com.pesaflow.app.data.finance.Account.M_PESA).toDouble() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)


    val cashBalance: StateFlow<Double> = financialSnapshot
        .map { it.accounts.getValue(com.pesaflow.app.data.finance.Account.CASH).toDouble() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)


    val bankBalance: StateFlow<Double> = financialSnapshot
        .map { it.accounts.getValue(com.pesaflow.app.data.finance.Account.BANK).toDouble() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)


    // 1-tap drift reconcile: books the ledger-vs-SMS gap as a labeled M-Pesa
    // adjustment so the next drift check reads zero. UI confirms first; the
    // math (direction, threshold) lives in reconcileEntry, unit-tested.
    fun reconcileWallet(smsBalance: Double) {
        val entry = com.pesaflow.app.data.money.reconcileEntry(mpesaBalance.value, smsBalance)
            ?: return
        viewModelScope.launch { repository.insertTransaction(entry) }
    }


    // Last wallet balance harvested from SMS ("New M-PESA balance is KSh X").
    // Display + reconciliation only — the ledger stays the source of truth.
    val smsWalletBalance: StateFlow<Pair<Double, Long>?> = allTransactions.map {
        com.pesaflow.app.data.parsers.readMpesaBalance(getApplication())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)


    // Earned income only: onboarding opening rows (pocket + upkeep) are held
    // cash, never monthly earnings — counting them here inflated "this month"
    // every onboarding month.
    val monthlyIncome: StateFlow<Double> = allTransactions.map { txs ->
        val now = System.currentTimeMillis()
        txs.filter {
            it.isEarnedIncome() && !it.isSample &&
                it.dateTimestamp <= now && isCurrentMonth(it.dateTimestamp)
        }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)


    val monthlyExpenses: StateFlow<Double> = allTransactions.map { txs ->
        val now = System.currentTimeMillis()
        txs.filter {
            it.type == TransactionType.EXPENSE && !it.isSample &&
                it.dateTimestamp <= now && isCurrentMonth(it.dateTimestamp)
        }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)


    val totalSavings: StateFlow<Double> = allTransactions.map { txs ->
        txs.filter { it.type == TransactionType.SAVING && !it.isSample }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)


    // UI actions
    // UI actions — date/isSample/batchId default to the common case so every
    // existing caller keeps working; gateway and backdate pass explicit values.
    fun addManualTransaction(amount: Double, type: TransactionType, category: String, merchant: String, method: PaymentMethod, dateTimestamp: Long = System.currentTimeMillis(), isSample: Boolean = false, batchId: String? = null, notes: String = "") {
        viewModelScope.launch {
            val tx = LedgerGateway.commit(
                repository, amount, type, category, merchant, method,
                TransactionSource.MANUAL, dateTimestamp, "Manual Input Record Entry",
                isSample, batchId, notes
            )
            // The user's explicit choice is ground truth — teach the engine.
            if (tx != null) {
                CategoryMemory.learn(prefs(), tx.merchant, tx.category)
                // Auto-attach: a manual log matching a pending row means the user
                // already handled it — retire the pending so it can't double-enter.
                retireMatchingPendings(tx.amount, tx.merchant, tx.dateTimestamp)
            }
        }
    }


    /** Retires pendings that duplicate an already-committed row (same amount +
     *  merchant within ±24h). Silent by design — the ledger row is the proof. */
    suspend fun retireMatchingPendings(amount: Double, merchant: String, timestamp: Long) {
        val norm = merchant.trim().lowercase()
        val window = 24L * 60 * 60 * 1000
        repository.pendingTransactions.first()
            .filter {
                it.amount == amount && it.merchant.trim().lowercase() == norm &&
                    kotlin.math.abs(it.dateTimestamp - timestamp) < window
            }
            .forEach { repository.deletePendingTransaction(it.id) }
    }


    /** Pre-save duplicate check for the "save anyway?" dialog. Confirmed
     *  ledger only — pendings are auto-attached, not blocked. */
    suspend fun hasConfirmedDuplicate(amount: Double, merchant: String, timestamp: Long): Transaction? {
        val norm = merchant.trim().lowercase()
        val window = 24L * 60 * 60 * 1000
        return repository.allTransactions.first().firstOrNull {
            !it.isSample && it.amount == amount && it.merchant.trim().lowercase() == norm &&
                kotlin.math.abs(it.dateTimestamp - timestamp) < window
        }
    }


    fun parseAndProcessNlp() {
        val parsed = NaturalLanguageParser.parse(nlpInputText.value)
        extractedNlpTransaction.value = parsed
    }


    fun commitExtractedNlp() {
        val tx = extractedNlpTransaction.value ?: return
        viewModelScope.launch {
            val saved = LedgerGateway.commit(
                repository, tx.amount, tx.type, tx.category, tx.merchant,
                tx.paymentMethod, TransactionSource.NLP, tx.dateTimestamp, tx.rawText
            )
            if (saved != null) CategoryMemory.learn(prefs(), saved.merchant, saved.category)
            extractedNlpTransaction.value = null
            nlpInputText.value = ""
        }
    }


    sealed interface Undoable {
        data class Approved(val pending: PendingTransaction, val txId: String) : Undoable
        data class ApprovedAll(val items: List<Approved>) : Undoable
        data class Rejected(val pending: PendingTransaction) : Undoable
        data class Deleted(val tx: Transaction) : Undoable
    }

    // Undo window: last 5 destructive actions, not just one — rapid
    // approve-existing-approve bursts stay recoverable.
    private val undoStack = ArrayDeque<Undoable>(5)


    private fun pushUndo(u: Undoable) {
        if (undoStack.size >= 5) undoStack.removeFirst()
        undoStack.addLast(u)
    }


    fun approvePending(pending: PendingTransaction, finalCategory: String, finalType: TransactionType = pending.type) {
        viewModelScope.launch {
            val category = resolveApprovalCategory(pending, finalCategory)
            // Atomic with the twin-check inside approve: a double-tap burst
            // still books exactly one ledger row.
            val tx = repository.transact {
                repository.approvePendingTransaction(pending, category, finalType)
            }
            pushUndo(Undoable.Approved(pending, tx.id))
            // Approvals are corrections too — teach the engine.
            CategoryMemory.learn(prefs(), tx.merchant, category)
            com.pesaflow.app.data.ledger.ConfidenceMemory.record(prefs(), pending.merchant, true)
            autoLinkBills(listOf(tx))
        }
    }


    // Auto-fulfillment on approve: just-confirmed rows scoring ≥0.85 against
    // an open bill link immediately (the Bills card suggests the rest).
    // Silent when nothing is sure — guesses never eat rows.
    private suspend fun autoLinkBills(newTxs: List<Transaction>) {
        if (newTxs.isEmpty()) return
        val open = repository.allBills.first()
        if (open.none { it.status != "PAID" }) return
        com.pesaflow.app.data.finance.matchBillPayments(open, newTxs)
            .filter { it.score >= 0.85 }
            .forEach { repository.linkBillPayment(it.bill.id, it.tx.id) }
    }


    /**
     * Approval-time upgrade: "Other" verdicts consult learned memory, then
     * keyword inference, before they hit the ledger — approved rows arrive
     * categorized and the health grade can actually climb.
     */
    fun resolveApprovalCategory(pending: PendingTransaction, edited: String): String {
        val memorized = com.pesaflow.app.data.ledger.CategoryMemory.lookup(prefs(), pending.merchant)
        val inferred = com.pesaflow.app.data.parsers.MpesaParser.inferCategory(pending.merchant, pending.type)
        val preferences = prefs()
        val fare = preferences.getString("school_fare_one_way", null)?.toDoubleOrNull() ?: 0.0
        val classTimes = com.pesaflow.app.data.schedule.WeekPlan.loadTimes(getApplication())
        val commuteMatched = pending.type == TransactionType.EXPENSE &&
            com.pesaflow.app.data.schedule.matchesDeclaredCommuteFare(
                pending.amount,
                pending.dateTimestamp,
                fare,
                classTimes
            )
        val finalInference = if (commuteMatched && inferred.equals("Other", ignoreCase = true)) {
            "Transport"
        } else inferred
        return com.pesaflow.app.data.parsers.PendingPolicy.upgradeOtherCategory(edited, memorized, finalInference)
    }


    // Bulk confirm: one tap approves every "sure" row as suggested, one undo
    // slot restores them all. Anything unsure stays for human eyes. The whole
    // sweep runs in one DB transaction so a 500-row approval emits once.
    fun approveAllPending(rows: List<PendingTransaction>) {
        if (rows.isEmpty()) return
        viewModelScope.launch {
            val fresh = mutableListOf<Transaction>()
            val done = repository.transact {
                rows.map { p ->
                    val category = resolveApprovalCategory(p, p.category)
                    val tx = repository.approvePendingTransaction(p, category, p.type)
                    CategoryMemory.learn(prefs(), tx.merchant, category)
                    com.pesaflow.app.data.ledger.ConfidenceMemory.record(prefs(), p.merchant, true)
                    fresh.add(tx)
                    Undoable.Approved(p, tx.id)
                }
            }
            pushUndo(Undoable.ApprovedAll(done))
            autoLinkBills(fresh)
        }
    }


    // Onboarding first-sync: coded + sure rows confirm themselves the moment
    // tracking starts, so insights read real data from the word go. Same
    // policy object as the dashboard bulk bar; same undo slot; codeless or
    // unsure rows stay queued for human eyes.
    fun autoApproveOnboardingSync(prefs: android.content.SharedPreferences, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val rows = repository.pendingOnce().filter {
                com.pesaflow.app.data.parsers.PendingPolicy.isAutoApprovable(
                    it.sourceTransactionId,
                    com.pesaflow.app.data.ledger.ConfidenceMemory.effective(prefs, it.merchant, it.confidenceScore)
                )
            }
            if (rows.isNotEmpty()) approveAllPending(rows)
            onDone(rows.size)
        }
    }


    // User-initiated sweep: pending rows duplicated by double-scans collapse
    // to the earliest — same code, or same amount+merchant+day. Everything
    // else untouched. Batched delete, single emission. Returns removals.
    suspend fun removeDuplicatePending(): Int {
        val rows = repository.pendingOnce().sortedBy { it.dateTimestamp }
        val seenCodes = mutableSetOf<String>()
        val seenFuzzy = mutableSetOf<Triple<Double, String, Long>>()
        val doomed = mutableListOf<String>()
        rows.forEach { p ->
            val code = p.sourceTransactionId.orEmpty()
            val dup = if (code.isNotBlank()) {
                !seenCodes.add(code)
            } else {
                val key = Triple(p.amount, p.merchant.trim().lowercase(), p.dateTimestamp / 86400000L)
                !seenFuzzy.add(key)
            }
            if (dup) doomed.add(p.id)
        }
        repository.deletePendingTransactions(doomed)
        return doomed.size
    }


    fun rejectPending(pending: PendingTransaction) {
        viewModelScope.launch {
            repository.rejectPendingTransaction(pending.id)
            pushUndo(Undoable.Rejected(pending))
            // Rejections teach too — this merchant stops looking "sure".
            com.pesaflow.app.data.ledger.ConfidenceMemory.record(prefs(), pending.merchant, false)
        }
    }


    fun deleteTransactionWithUndo(tx: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(tx.id)
            pushUndo(Undoable.Deleted(tx))
        }
    }


    fun replaceTransaction(oldId: String, tx: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(oldId)
            repository.insertTransaction(tx)
            // Edited category is ground truth too — autosave it like new
            // entries so future rows self-categorize (contact-card memory).
            CategoryMemory.learn(prefs(), tx.merchant, tx.category)
        }
    }


    fun undoLast() {
        val undone = if (undoStack.isEmpty()) return else undoStack.removeLast()
        viewModelScope.launch {
            when (undone) {
                is Undoable.Approved -> {
                    repository.deleteTransaction(undone.txId)
                    repository.insertPendingTransaction(undone.pending)
                }
                is Undoable.ApprovedAll -> undone.items.forEach {
                    repository.deleteTransaction(it.txId)
                    repository.insertPendingTransaction(it.pending)
                }
                is Undoable.Rejected -> repository.insertPendingTransaction(undone.pending)
                is Undoable.Deleted -> repository.insertTransaction(undone.tx)
            }
        }
    }


    fun queueSharedTransaction(pending: PendingTransaction) {
        viewModelScope.launch { repository.insertPendingTransaction(pending) }
    }


    // User-triggered inbox scan for any duration (Transactions "Scan today",
    // longer ranges). Read-only scan; rows queue via tryQueuePending which
    // dedupes by M-Pesa code, so rescans never double-book.
    fun scanInboxDays(
        daysBack: Int,
        maxRows: Int = 500,
        onProgress: (found: Int, parsed: Int) -> Unit = { _, _ -> },
        onDone: (found: Int, queued: Int, error: String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                withContext(Dispatchers.Main) { onDone(0, 0, "SMS permission needed — enable it to scan.") }
                return@launch
            }
            try {
                val result = com.pesaflow.app.data.parsers.scanRecentSms(
                    context = context,
                    daysBack = daysBack,
                    maxRows = maxRows,
                    onProgress = { f, p ->
                        launch(Dispatchers.Main) { onProgress(f, p) }
                    }
                )
                var queued = 0
                result.parsed.forEach { if (tryQueuePending(it)) queued++ }
                withContext(Dispatchers.Main) { onDone(result.found, queued, result.error) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onDone(0, 0, e.message ?: e.javaClass.simpleName) }
            }
        }
    }


    // Suspend variant for inbox scans that need to know inserted vs duplicate.
    suspend fun tryQueuePending(pending: PendingTransaction): Boolean =
        repository.insertPendingTransaction(pending)


    fun queueSharedText(text: String) {
        val parsed = NaturalLanguageParser.parse(text)
        if (parsed != null) {
            viewModelScope.launch { repository.insertPendingTransaction(parsed) }
        }
        // Unparseable text is ignored: no junk rows ever reach the ledger.
    }


    fun addBudget(category: String, limitAmount: Double, type: BudgetType, sharedWith: String = "") {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val day = 24L * 60 * 60 * 1000
            val end = now + when (type) {
                BudgetType.DAILY -> day
                BudgetType.WEEKLY -> 7 * day
                BudgetType.MONTHLY -> 30 * day
                BudgetType.SEMESTER -> 120 * day
                BudgetType.ANNUAL -> 365 * day
            }
            repository.insertBudget(
                Budget(category = category, limitAmount = limitAmount, type = type, startTimestamp = now, endTimestamp = end, sharedWith = sharedWith)
            )
        }
    }


    // Upsert: onboarding re-runs must UPDATE the same category+type row, never
    // stack duplicates — every reader takes firstOrNull, so dupes freeze stale values.
    // Single coroutine (no nested launch): delete-then-insert is atomic from the caller's view.
    fun upsertBudget(category: String, limitAmount: Double, type: BudgetType, sharedWith: String = "") {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val day = 24L * 60 * 60 * 1000
            val end = now + when (type) {
                BudgetType.DAILY -> day
                BudgetType.WEEKLY -> 7 * day
                BudgetType.MONTHLY -> 30 * day
                BudgetType.SEMESTER -> 120 * day
                BudgetType.ANNUAL -> 365 * day
            }
            repository.budgets.first()
                .filter { it.type == type && it.category.equals(category, ignoreCase = true) }
                .forEach { repository.deleteBudget(it.id) }
            repository.insertBudget(
                Budget(category = category, limitAmount = limitAmount, type = type, startTimestamp = now, endTimestamp = end, sharedWith = sharedWith)
            )
        }
    }


    // Opening money is what the student actually holds now, not expected
    // sponsor or HELB income. Each account is seeded independently so pocket
    // balances stay truthful; retries never duplicate an opening row.
    fun seedOpeningMoney(cash: Double, mpesa: Double, bank: Double) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = repository.allTransactions.first()
            listOf(
                Triple("Opening balance", cash, PaymentMethod.CASH),
                Triple("Opening M-Pesa balance", mpesa, PaymentMethod.MPESA),
                Triple("Opening bank balance", bank, PaymentMethod.BANK_TRANSFER)
            ).forEach { (merchant, amount, method) ->
                if (amount > 0 && existing.none { it.merchant == merchant && it.type == TransactionType.INCOME }) {
                    repository.insertTransaction(
                        Transaction(
                            amount = amount,
                            type = TransactionType.INCOME,
                            category = "Income",
                            dateTimestamp = now,
                            merchant = merchant,
                            description = "Actual balance at onboarding",
                            paymentMethod = method,
                            accountKind = when (method) {
                                PaymentMethod.CASH -> "CASH"
                                PaymentMethod.MPESA -> "M_PESA"
                                PaymentMethod.BANK_TRANSFER -> "BANK"
                                else -> "OTHER"
                            },
                            isOpening = true
                        )
                    )
                }
            }
        }
    }


    fun shareBudget(id: String, names: String) {
        viewModelScope.launch { repository.updateBudgetShared(id, names) }
    }


    fun deleteBudget(id: String) {
        viewModelScope.launch { repository.deleteBudget(id) }
    }


    fun applyCalculatedBudgets(rows: List<Pair<String, Double>>, type: BudgetType) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val day = 24L * 60 * 60 * 1000
            val span = when (type) {
                BudgetType.DAILY -> day
                BudgetType.WEEKLY -> 7 * day
                BudgetType.MONTHLY -> 30 * day
                BudgetType.SEMESTER -> 120 * day
                BudgetType.ANNUAL -> 365 * day
            }
            // Merge with manual budgets: only replace same-type budgets whose
            // category is in the new plan — hand-made ones for other
            // categories survive an Apply instead of being wiped.
            val incoming = rows.map { it.first.trim().lowercase() }.toSet()
            repository.budgets.first()
                .filter { it.type == type && incoming.contains(it.category.trim().lowercase()) }
                .forEach { repository.deleteBudget(it.id) }
            rows.forEach { (category, amount) ->
                if (amount > 0) {
                    repository.insertBudget(
                        Budget(
                            category = category,
                            limitAmount = amount,
                            type = type,
                            startTimestamp = now,
                            endTimestamp = now + span
                        )
                    )
                }
            }
        }
    }


    fun addSavingsGoal(title: String, targetAmount: Double, daysFromNow: Int) {
        viewModelScope.launch {
            val target = System.currentTimeMillis() + daysFromNow.coerceAtLeast(1) * 24L * 60 * 60 * 1000
            repository.insertSavingsGoal(
                SavingsGoal(title = title, targetAmount = targetAmount, currentAmount = 0.0, targetTimestamp = target)
            )
        }
    }


    fun deleteSavingsGoal(id: String) {
        viewModelScope.launch { repository.deleteSavingsGoal(id) }
    }


    // Contribute logs a real SAVING ledger row AND bumps the goal, so net worth,
    // balance, reports and planners all move together. One tap = new money in.
    fun contributeToSavingsGoal(goal: SavingsGoal, amount: Double) {
        viewModelScope.launch {
            if (amount <= 0) return@launch
            repository.insertTransaction(
                Transaction(
                    amount = amount,
                    type = TransactionType.SAVING,
                    category = "Savings",
                    dateTimestamp = System.currentTimeMillis(),
                    merchant = goal.title,
                    description = "Saved toward ${goal.title}",
                    paymentMethod = PaymentMethod.CASH
                )
            )
            repository.contributeToSavingsGoal(goal.id, goal.currentAmount + amount)
        }
    }


    fun saveUniversityProfile(profile: UniversityProfile) {
        viewModelScope.launch { repository.saveUniversityProfile(profile) }
    }


    fun addBill(name: String, amount: Double, dueDate: Long, category: String, frequency: String, paybill: String = "", paidBy: String = "ME") {
        viewModelScope.launch {
            repository.insertBill(
                Bill(name = name, amount = amount, dueDate = dueDate, category = category, frequency = frequency, amountRemaining = amount, paybill = paybill.trim(), paidBy = paidBy)
            )
            // Bills drive budgets: a recurring bill adjusts (never duplicates) its monthly budget
            if (paidBy == "ME" && frequency != "ONE_TIME" && category.isNotBlank()) {
                val existing = repository.budgets.first().firstOrNull {
                    it.type == BudgetType.MONTHLY && it.category.equals(category, ignoreCase = true)
                }
                if (existing == null) {
                    val now = System.currentTimeMillis()
                    repository.insertBudget(
                        Budget(
                            category = category,
                            limitAmount = amount,
                            type = BudgetType.MONTHLY,
                            startTimestamp = now,
                            endTimestamp = now + 30L * 24 * 60 * 60 * 1000
                        )
                    )
                } else if (amount > existing.limitAmount) {
                    repository.updateBudgetAmount(existing.id, amount)
                }
            }
        }
    }


    fun markBillPaid(id: String) {
        viewModelScope.launch { repository.markBillPaid(id) }
    }

    // Auto-fulfillment confirm: links the spotted ledger row to the bill so
    // projections stop reserving it. Suggest-only upstream (matchBillPayments);
    // the user taps, never the engine alone.
    fun linkBillPayment(billId: String, txId: String) {
        viewModelScope.launch { repository.linkBillPayment(billId, txId) }
    }


    fun updateBillDetails(bill: Bill, name: String, amount: Double, category: String, frequency: String, paybill: String = "", paidBy: String = bill.paidBy) {
        viewModelScope.launch {
            repository.updateBillAmount(bill.id, amount, bill.dueDate, category, frequency, bill.reminderEnabled, bill.reminderLeadDays, paidBy)
            if (paybill.trim() != bill.paybill) repository.updateBillPaybill(bill.id, paybill)
        }
    }


    fun reopenBill(id: String) {
        viewModelScope.launch { repository.reopenBill(id) }
    }


    fun deleteBill(id: String) {
        viewModelScope.launch { repository.deleteBill(id) }
    }


    // Instalments: replace one lump bill (fees, wifi) with dated parts so
    // near-term safe figures reserve this week's share, not the whole lump.
    // The original is deleted — commitments are replaced, never doubled.
    fun splitBillIntoInstalments(billId: String, parts: Int, stepDays: Int) {
        val bill = bills.value.firstOrNull { it.id == billId } ?: return
        val plan = com.pesaflow.app.data.finance.instalmentSchedule(bill.amount, parts, stepDays)
        if (plan.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val day = 24L * 60 * 60 * 1000
            plan.forEach {
                repository.insertBill(
                    Bill(
                        name = "${bill.name} (${it.index}/${it.of})",
                        amount = it.amount,
                        dueDate = now + it.dueInDays * day,
                        category = bill.category,
                        frequency = if (stepDays >= 28) "MONTHLY" else "WEEKLY",
                        amountRemaining = it.amount,
                        paybill = bill.paybill,
                        paidBy = bill.paidBy
                    )
                )
            }
            repository.deleteBill(billId)
        }
    }


    fun addDebt(person: String, amount: Double, dueDate: Long, description: String, direction: String = "THEY_OWE") {
        viewModelScope.launch {
            repository.insertDebt(
                Debt(person = person, amount = amount, dateBorrowed = System.currentTimeMillis(), direction = direction, dueDate = dueDate, description = description)
            )
        }
    }


    fun markDebtPaid(id: String) {
        viewModelScope.launch { repository.markDebtPaid(id) }
    }


    // Partial settlement: log what moved, shrink what remains. Full payment
    // flows through the same path (remainder hits zero → marked paid).
    fun settleDebtPartial(debt: Debt, paid: Double, method: PaymentMethod) {
        val p = paid.coerceIn(0.0, debt.amount)
        if (p <= 0) return
        val full = p >= debt.amount
        viewModelScope.launch {
            if (debt.direction == "I_OWE") {
                addManualTransaction(p, TransactionType.EXPENSE, "Debt", (if (full) "Repaid " else "Repaid part to ") + debt.person, method)
            } else {
                addManualTransaction(p, TransactionType.INCOME, "Debt", (if (full) "Collected from " else "Collected part from ") + debt.person, method)
            }
            if (full) {
                markDebtPaid(debt.id)
            } else {
                repository.updateDebtAmount(debt.id, debt.amount - p, debt.dueDate, debt.description, debt.status, debt.reminderEnabled, debt.reminderLeadDays)
            }
        }
    }


    fun deleteDebt(id: String) {
        viewModelScope.launch { repository.deleteDebt(id) }
    }


    fun deleteTransaction(id: String) {
        viewModelScope.launch { repository.deleteTransaction(id) }
    }


    fun hasUndo(): Boolean = undoStack.isNotEmpty()


    fun deleteAllTransactions() {
        viewModelScope.launch { repository.deleteAllTransactions() }
    }


    private var lastImportBatch: String? = null


    fun importTransactions(transactions: List<Transaction>, onDone: (added: Int, skipped: Int) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val batch = "csv-${System.currentTimeMillis()}"
            var added = 0
            var skipped = 0
            transactions.forEach {
                val norm = LedgerGateway.normalizeMerchant(it.merchant, it.category)
                // Re-import guard: the same file twice must not double-book.
                // Fuzzy match covers ledger + pending queue alike.
                if (repository.hasFuzzyDuplicate(it.amount, norm, it.dateTimestamp)) skipped++
                else {
                    repository.insertTransaction(it.copy(merchant = norm, batchId = it.batchId ?: batch))
                    added++
                }
            }
            if (added > 0) lastImportBatch = batch
            onDone(added, skipped)
        }
    }


    /** Removes the most recent CSV import. Reports how many rows went away. */
    fun undoLastImport(onDone: (Int) -> Unit) {
        val batch = lastImportBatch ?: return
        viewModelScope.launch {
            val n = repository.deleteBatch(batch)
            lastImportBatch = null
            onDone(n)
        }
    }


    private var lastAutoDeduped: List<Transaction> = emptyList()


    /**
     * Deletes exact-duplicate ledger rows (all but the earliest per group).
     * Groups come from [exactDuplicateGroups]; the removed rows are kept for
     * one-tap [undoAutoDedupe]. One batched statement — never N emissions.
     * Reports how many rows went away.
     */
    fun autoRemoveExactDuplicates(groups: List<List<Transaction>>, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val removed = groups.flatMap { it.drop(1) }
            repository.deleteTransactions(removed.map { it.id })
            lastAutoDeduped = removed
            onDone(removed.size)
        }
    }


    /** Restores rows removed by [autoRemoveExactDuplicates]. Reports how many came back. */
    fun undoAutoDedupe(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            repository.insertTransactions(lastAutoDeduped)
            val n = lastAutoDeduped.size
            lastAutoDeduped = emptyList()
            onDone(n)
        }
    }


    /** Batch ledger delete for merge/bulk surfaces: one statement, one emission. */
    fun deleteTransactions(ids: List<String>, onDone: () -> Unit = {}) {
        if (ids.isEmpty()) {
            onDone()
            return
        }
        viewModelScope.launch {
            repository.deleteTransactions(ids)
            onDone()
        }
    }


    suspend fun sampleCount(): Int = repository.countSamples()


    /** One-tap demo cleanup for Settings. Reports how many rows were removed. */
    fun purgeSamples(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val n = repository.purgeSamples()
            // Demo budgets/goal carry fixed seed IDs (see seedSampleData), so
            // purge removes exactly those rows — never user data.
            repository.deleteBudget("sample-budget-all")
            repository.deleteBudget("sample-budget-food")
            repository.deleteSavingsGoal("sample-goal-laptop")
            onDone(n)
        }
    }


    fun restoreBackup(json: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            // v2 first: typed payload, wipe-then-restore inside one transaction
            // (decode before wipe — a corrupt file must never cost data).
            try {
                val payload = com.pesaflow.app.data.backup.BackupJson
                    .decodeFromString<com.pesaflow.app.data.backup.BackupPayload>(json)
                if (payload.version == 2) {
                    repository.transact {
                        repository.wipeForRestore()
                        repository.insertTransactions(payload.transactions)
                        repository.insertPendingTransactions(payload.pending)
                        payload.budgets.forEach { repository.insertBudget(it) }
                        payload.goals.forEach { repository.insertSavingsGoal(it) }
                        payload.profile?.let { repository.saveUniversityProfile(it) }
                        payload.bills.forEach { repository.insertBill(it) }
                        payload.debts.forEach { repository.insertDebt(it) }
                        payload.meals.forEach { repository.insertMealItem(it) }
                        payload.chamas.forEach { repository.insertChamaGroup(it) }
                        payload.belongings.forEach { repository.insertBelonging(it) }
                        payload.kitchenStock.forEach { repository.insertKitchenStock(it) }
                    }
                    onDone(true)
                    return@launch
                }
            } catch (e: Exception) {
                // Not v2 — fall through to the legacy v1 reader below.
            }
            try {
                val root = org.json.JSONObject(json)
                if (root.optInt("version", 0) != 1) {
                    onDone(false)
                    return@launch
                }
                fun arr(key: String) = root.optJSONArray(key) ?: org.json.JSONArray()
                val txs = mutableListOf<Transaction>()
                val ta = arr("transactions")
                for (i in 0 until ta.length()) {
                    val o = ta.getJSONObject(i)
                    txs.add(
                        Transaction(
                            id = o.optString("id", java.util.UUID.randomUUID().toString()),
                            amount = o.optDouble("amount", 0.0),
                            type = try { TransactionType.valueOf(o.optString("type", "EXPENSE")) } catch (e: Exception) { TransactionType.EXPENSE },
                            category = o.optString("category", "Other"),
                            dateTimestamp = o.optLong("dateTimestamp", System.currentTimeMillis()),
                            merchant = o.optString("merchant", ""),
                            description = o.optString("description", ""),
                            paymentMethod = try { PaymentMethod.valueOf(o.optString("paymentMethod", "OTHER")) } catch (e: Exception) { PaymentMethod.OTHER },
                            source = try { TransactionSource.valueOf(o.optString("source", "MANUAL")) } catch (e: Exception) { TransactionSource.MANUAL },
                            sourceTransactionId = o.optString("sourceTransactionId").ifBlank { null }
                        )
                    )
                }
                repository.insertTransactions(txs)
                val ba = arr("budgets")
                for (i in 0 until ba.length()) {
                    val o = ba.getJSONObject(i)
                    repository.insertBudget(
                        Budget(
                            id = o.optString("id", java.util.UUID.randomUUID().toString()),
                            category = o.optString("category", "Other"),
                            limitAmount = o.optDouble("limitAmount", 0.0),
                            type = try { BudgetType.valueOf(o.optString("type", "MONTHLY")) } catch (e: Exception) { BudgetType.MONTHLY },
                            startTimestamp = o.optLong("startTimestamp", 0L),
                            endTimestamp = o.optLong("endTimestamp", 0L),
                            sharedWith = o.optString("sharedWith", "")
                        )
                    )
                }
                val ga = arr("goals")
                for (i in 0 until ga.length()) {
                    val o = ga.getJSONObject(i)
                    repository.insertSavingsGoal(
                        SavingsGoal(
                            id = o.optString("id", java.util.UUID.randomUUID().toString()),
                            title = o.optString("title", "Goal"),
                            targetAmount = o.optDouble("targetAmount", 0.0),
                            currentAmount = o.optDouble("currentAmount", 0.0),
                            targetTimestamp = o.optLong("targetTimestamp", 0L)
                        )
                    )
                }
                val pa = arr("profile")
                if (pa.length() > 0) {
                    val o = pa.getJSONObject(0)
                    repository.saveUniversityProfile(
                        UniversityProfile(
                            universityName = o.optString("universityName", ""),
                            campus = o.optString("campus", ""),
                            currentSemester = o.optInt("currentSemester", 1),
                            academicYear = o.optString("academicYear", ""),
                            semesterStartTimestamp = o.optLong("semesterStartTimestamp", 0L),
                            semesterEndTimestamp = o.optLong("semesterEndTimestamp", 0L),
                            startingFunding = o.optDouble("startingFunding", 0.0),
                            helbExpected = o.optDouble("helbExpected", 0.0),
                            fundingSource = o.optString("fundingSource", "HELB"),
                            feesAmount = o.optDouble("feesAmount", 0.0),
                            feesDueDate = o.optLong("feesDueDate", 0L)
                        )
                    )
                }
                val la = arr("bills")
                for (i in 0 until la.length()) {
                    val o = la.getJSONObject(i)
                    repository.insertBill(
                        Bill(
                            id = o.optString("id", java.util.UUID.randomUUID().toString()),
                            name = o.optString("name", "Bill"),
                            amount = o.optDouble("amount", 0.0),
                            dueDate = o.optLong("dueDate", 0L),
                            category = o.optString("category", "Other"),
                            frequency = o.optString("frequency", "ONE_TIME"),
                            status = o.optString("status", "UNPAID")
                        )
                    )
                }
                val da = arr("debts")
                for (i in 0 until da.length()) {
                    val o = da.getJSONObject(i)
                    repository.insertDebt(
                        Debt(
                            id = o.optString("id", java.util.UUID.randomUUID().toString()),
                            person = o.optString("person", ""),
                            amount = o.optDouble("amount", 0.0),
                            dateBorrowed = o.optLong("dateBorrowed", 0L),
                            dueDate = o.optLong("dueDate", 0L),
                            description = o.optString("description", ""),
                            status = o.optString("status", "OWING")
                        )
                    )
                }
                val ma = arr("meals")
                for (i in 0 until ma.length()) {
                    val o = ma.getJSONObject(i)
                    repository.insertMealItem(
                        MealItem(
                            id = o.optString("id", java.util.UUID.randomUUID().toString()),
                            name = o.optString("name", ""),
                            mealType = o.optString("mealType", "Lunch"),
                            price = o.optDouble("price", 0.0),
                            component = o.optString("component", "Complete"),
                            source = o.optString("source", "Buy")
                        )
                    )
                }
                val ca = arr("chamas")
                for (i in 0 until ca.length()) {
                    val o = ca.getJSONObject(i)
                    repository.insertChamaGroup(
                        ChamaGroup(
                            id = o.optString("id", java.util.UUID.randomUUID().toString()),
                            name = o.optString("name", ""),
                            contribution = o.optDouble("contribution", 0.0),
                            members = o.optString("members", ""),
                            cycleDays = o.optInt("cycleDays", 30),
                            startTimestamp = o.optLong("startTimestamp", System.currentTimeMillis()),
                            paidCycles = o.optInt("paidCycles", 0)
                        )
                    )
                }
                onDone(true)
            } catch (e: Exception) {
                onDone(false)
            }
        }
    }


    fun addMealItem(name: String, mealType: String, price: Double, component: String = "Complete", source: String = "Buy") {
        viewModelScope.launch { repository.insertMealItem(MealItem(name = name, mealType = mealType, price = price, component = component, source = source)) }
    }


    fun deleteMealItem(id: String) {
        viewModelScope.launch { repository.deleteMealItem(id) }
    }


    fun addBelonging(name: String, category: String, estCost: Double, priority: Int, status: String = "NEED", notes: String = "") {
        viewModelScope.launch {
            repository.insertBelonging(
                Belonging(name = name.trim(), category = category, status = status, estCost = estCost, priority = priority, notes = notes.trim())
            )
        }
    }


    fun markBelonging(item: Belonging, status: String) {
        viewModelScope.launch { repository.updateBelonging(item.copy(status = status)) }
    }


    fun deleteBelonging(id: String) {
        viewModelScope.launch { repository.deleteBelonging(id) }
    }


    fun addKitchenStock(name: String, unit: String, qtyFull: Double, qtyLeft: Double, dailyUse: Double, pricePerPack: Double, expiryTimestamp: Long = 0L, eatByDays: Int = 0) {
        viewModelScope.launch {
            repository.insertKitchenStock(
                KitchenStock(name = name.trim(), unit = unit.trim().ifEmpty { "kg" }, qtyFull = qtyFull, qtyLeft = qtyLeft, dailyUse = dailyUse, pricePerPack = pricePerPack, expiryTimestamp = expiryTimestamp, eatByDays = eatByDays)
            )
        }
    }


    fun logStockUse(item: KitchenStock, days: Double = 1.0) {
        viewModelScope.launch {
            repository.updateKitchenStock(
                item.copy(qtyLeft = (item.qtyLeft - item.dailyUse * days).coerceAtLeast(0.0), updatedAt = System.currentTimeMillis())
            )
        }
    }


    fun setStockPriority(item: KitchenStock, days: Int) {
        viewModelScope.launch {
            repository.updateKitchenStock(item.copy(eatByDays = days, updatedAt = System.currentTimeMillis()))
        }
    }


    fun setStockExpiry(item: KitchenStock, timestamp: Long) {
        viewModelScope.launch {
            repository.updateKitchenStock(item.copy(expiryTimestamp = timestamp, updatedAt = System.currentTimeMillis()))
        }
    }


    fun restockKitchen(item: KitchenStock) {
        viewModelScope.launch {
            repository.updateKitchenStock(item.copy(qtyLeft = item.qtyFull, updatedAt = System.currentTimeMillis()))
        }
    }

    fun purchaseKitchenStock(
        item: KitchenStock,
        quantity: Double,
        method: PaymentMethod,
        onResult: (String?) -> Unit
    ) {
        if (!quantity.isFinite() || quantity <= 0) {
            onResult("Enter a valid quantity, pack size, and price.")
            return
        }
        viewModelScope.launch {
            try {
                val purchased = repository.transact {
                    val latest = repository.getKitchenStock(item.id) ?: return@transact false
                    val cost = com.pesaflow.app.data.models.stockTopUpCost(latest, quantity)
                        ?: return@transact false
                    val updatedStock = com.pesaflow.app.data.models.stockAfterTopUp(latest, quantity)
                        ?: return@transact false
                    repository.updateKitchenStock(updatedStock)
                    repository.insertTransaction(
                        Transaction(
                            amount = cost,
                            type = TransactionType.EXPENSE,
                            category = "Food",
                            dateTimestamp = System.currentTimeMillis(),
                            merchant = "Food stock: ${latest.name}",
                            description = "Bought $quantity ${latest.unit}",
                            paymentMethod = method,
                            source = TransactionSource.MANUAL
                        )
                    )
                    true
                }
                if (purchased) onResult(null)
                else onResult("Could not calculate the purchase. Check the item, quantity, and shop price.")
            } catch (error: android.database.SQLException) {
                android.util.Log.e("FinanceViewModel", "Could not save kitchen stock purchase", error)
                onResult("Couldn't save the purchase. Your stock and budget were not changed.")
            }
        }
    }


    fun deleteKitchenStock(id: String) {
        viewModelScope.launch { repository.deleteKitchenStock(id) }
    }


    fun clearMealItems() {
        viewModelScope.launch { repository.clearMealItems() }
    }


    val chamaGroups: StateFlow<List<ChamaGroup>> = repository.allChamaGroups.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )


    fun addChamaGroup(name: String, contribution: Double, members: String, cycleDays: Int) {
        viewModelScope.launch {
            repository.insertChamaGroup(
                ChamaGroup(name = name, contribution = contribution, members = members, cycleDays = cycleDays)
            )
        }
    }


    fun deleteChamaGroup(id: String) {
        viewModelScope.launch { repository.deleteChamaGroup(id) }
    }


    fun advanceChama(group: ChamaGroup) {
        viewModelScope.launch { repository.advanceChama(group.id, group.paidCycles + 1) }
    }


    // Sample data is one-shot and clearly fake: guarded by a prefs flag AND a
    // non-empty ledger check, so double-taps and re-entry can never pollute
    // real records or stack duplicate budgets.
    fun seedSampleData() {
        viewModelScope.launch {
            val p = prefs()
            if (p.getBoolean("sample_seeded", false)) return@launch
            if (repository.allTransactions.first().isNotEmpty() || repository.budgets.first().isNotEmpty()) {
                p.edit().putBoolean("sample_seeded", true).apply()
                return@launch
            }
            val now = System.currentTimeMillis()
            val day = 24L * 60 * 60 * 1000
            listOf(
                Transaction(amount = 20000.0, type = TransactionType.INCOME, category = "Salary", dateTimestamp = now - 2 * day, merchant = "HELB", description = "Sample semester upkeep", paymentMethod = PaymentMethod.MPESA, source = TransactionSource.MANUAL, isSample = true),
                Transaction(amount = 250.0, type = TransactionType.EXPENSE, category = "Food", dateTimestamp = now - 2 * day, merchant = "Kibanda", description = "Sample lunch", paymentMethod = PaymentMethod.CASH, source = TransactionSource.MANUAL, isSample = true),
                Transaction(amount = 100.0, type = TransactionType.EXPENSE, category = "Transport", dateTimestamp = now - 1 * day, merchant = "Matatu Stage", description = "Sample fare", paymentMethod = PaymentMethod.CASH, source = TransactionSource.MANUAL, isSample = true),
                Transaction(amount = 150.0, type = TransactionType.EXPENSE, category = "Airtime", dateTimestamp = now - 1 * day, merchant = "Safaricom", description = "Sample bundles", paymentMethod = PaymentMethod.MPESA, source = TransactionSource.MANUAL, isSample = true),
                Transaction(amount = 8000.0, type = TransactionType.EXPENSE, category = "Rent", dateTimestamp = now - 5 * day, merchant = "Hostel Caretaker", description = "Sample rent", paymentMethod = PaymentMethod.MPESA, source = TransactionSource.MANUAL, isSample = true)
            ).forEach { repository.insertTransaction(it) }
            repository.insertBudget(Budget(id = "sample-budget-all", category = "ALL", limitAmount = 25000.0, type = BudgetType.MONTHLY, startTimestamp = now, endTimestamp = now + 30 * day))
            repository.insertBudget(Budget(id = "sample-budget-food", category = "Food", limitAmount = 6000.0, type = BudgetType.MONTHLY, startTimestamp = now, endTimestamp = now + 30 * day))
            repository.insertSavingsGoal(SavingsGoal(id = "sample-goal-laptop", title = "Laptop", targetAmount = 80000.0, currentAmount = 5000.0, targetTimestamp = now + 180 * day))
            p.edit().putBoolean("sample_seeded", true).apply()
        }
    }


    fun setLanguage(lang: AppLanguage) {
        currentLanguage.value = lang
        prefs().edit().putString("app_language", lang.name).apply()
    }


    fun setUserName(name: String) {
        userName.value = name.trim()
        prefs().edit().putString("user_name", userName.value).apply()
    }


    fun setNickname(name: String) {
        nickname.value = name.trim()
        prefs().edit().putString("user_nickname", nickname.value).apply()
    }


    // Daily voice uses the nickname; extreme warnings use the full name to sound serious.
    fun displayName(): String = nickname.value.ifBlank { userName.value }

    fun seriousName(): String = userName.value.ifBlank { nickname.value }


    // Onboarding "tell us about you" answers, kept as JSON for future
    // personalization (spending baselines, first-run advice).
    fun saveOnboardingAnswers(json: String) {
        prefs().edit().putString("onboarding_answers", json).apply()
    }


    fun getOnboardingAnswers(): String =
        prefs().getString("onboarding_answers", "").orEmpty()


    fun setThemeMode(mode: AppTheme) {
        themeMode.value = mode
        prefs().edit().putString("app_theme", mode.name).apply()
    }


    fun setHideBalances(hidden: Boolean) {
        hideBalances.value = hidden
        prefs().edit().putBoolean("hide_balances", hidden).apply()
    }


    fun toggleSection(key: String) {
        val updated = hiddenSections.value.toMutableSet()
        if (!updated.add(key)) updated.remove(key)
        hiddenSections.value = updated
        prefs().edit().putString("hidden_sections", updated.joinToString(",")).apply()
    }


    // Localized strings — single source is AppCopy (dashboard section); this
    // function stays as the compatibility delegate so every caller upgrades at once.
    fun getLocalizedString(key: String, arg: String = "", balance: Double = availableBalance.value): String {
        val lang = currentLanguage.value
        if (key == "dashboard_status") {
            return com.pesaflow.app.ui.language.dashStatus(displayName(), seriousName(), balance, lang)
        }
        return com.pesaflow.app.ui.language.dashKey(key, arg, lang)
    }


    private fun isCurrentMonth(timestamp: Long): Boolean {
        val cal = java.util.Calendar.getInstance()
        val txCal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal.get(java.util.Calendar.YEAR) == txCal.get(java.util.Calendar.YEAR) &&
            cal.get(java.util.Calendar.MONTH) == txCal.get(java.util.Calendar.MONTH)
    }
}


/**
 * Exact-duplicate groups for auto-remove: same amount + merchant + type +
 * method on the same day, timestamps within 10 minutes. First element of
 * each group is the keeper (earliest); the rest are safe to delete.
 * Conservative on purpose — two matatu rides hours apart never group.
 */
fun exactDuplicateGroups(txs: List<Transaction>): List<List<Transaction>> {
    val dayMs = 24L * 60 * 60 * 1000
    return txs.filter { !it.isSample }
        .groupBy {
            "${it.amount}|${it.merchant.trim().lowercase()}|${it.type}|${it.paymentMethod}|${it.dateTimestamp / dayMs}"
        }
        .values.filter { it.size > 1 }
        .mapNotNull { g ->
            val ordered = g.sortedBy { it.dateTimestamp }
            val keep = ordered.first()
            val dupes = ordered.drop(1).filter { it.dateTimestamp - keep.dateTimestamp <= 10 * 60 * 1000 }
            if (dupes.isEmpty()) null else listOf(keep) + dupes
        }
}

// Persona → ProfileSignals bridge (Phase 2): legacy six-persona presets
// survive only as fallback mappings into multidimensional dimensions.
// Declared answers and observed behaviour take over in Phases 6–8.
private fun com.pesaflow.app.ui.budgets.Persona.toSignals(
    profile: com.pesaflow.app.data.models.UniversityProfile?,
    sources: List<IncomeSource>,
    debts: List<com.pesaflow.app.data.models.Debt>
): ProfileSignals {
    val kinds = sources.map { it.kind }.toSet() +
        (if (profile?.fundingSource != "SELF") setOf("HELB") else emptySet())
    val stability = when {
        kinds.any { it == "JOB" || it == "SALARY" } -> IncomeStability.FIXED
        kinds.any { it == "HUSTLE" } && kinds.size == 1 -> IncomeStability.VARIABLE
        kinds.isEmpty() -> IncomeStability.NONE
        kinds.any { it == "HUSTLE" } -> IncomeStability.MIXED
        else -> IncomeStability.MIXED
    }
    val owed = debts.filter { it.status != "PAID" && it.direction == "I_OWE" }.sumOf { it.amount }
    return ProfileSignals(
        housing = when (this) {
            com.pesaflow.app.ui.budgets.Persona.PARENTS_FAR,
            com.pesaflow.app.ui.budgets.Persona.PARENTS_NEAR -> Housing.PARENTS
            com.pesaflow.app.ui.budgets.Persona.RENT_COMMUTE,
            com.pesaflow.app.ui.budgets.Persona.RENT_WALK -> Housing.RENTAL
            else -> Housing.HOSTEL
        },
        commute = when (this) {
            com.pesaflow.app.ui.budgets.Persona.PARENTS_FAR,
            com.pesaflow.app.ui.budgets.Persona.RENT_COMMUTE -> Commute.LONG
            com.pesaflow.app.ui.budgets.Persona.PARENTS_NEAR -> Commute.SHORT
            com.pesaflow.app.ui.budgets.Persona.RENT_WALK -> Commute.WALK
            else -> Commute.SHORT
        },
        food = when (this) {
            com.pesaflow.app.ui.budgets.Persona.HOSTEL_NOCOOK -> FoodStyle.BUY
            com.pesaflow.app.ui.budgets.Persona.HOSTEL_COOK -> FoodStyle.COOK
            com.pesaflow.app.ui.budgets.Persona.PARENTS_FAR,
            com.pesaflow.app.ui.budgets.Persona.PARENTS_NEAR -> FoodStyle.HOME_FED
            else -> FoodStyle.MIXED
        },
        incomeStability = stability,
        incomeKinds = kinds,
        debtLevel = when {
            owed <= 0 -> DebtLevel.NONE
            owed < 5000 -> DebtLevel.LOW
            owed < 20000 -> DebtLevel.MEDIUM
            else -> DebtLevel.HIGH
        }
    )
}