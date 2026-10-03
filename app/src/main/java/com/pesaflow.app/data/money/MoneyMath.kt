package com.pesaflow.app.data.money

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import java.util.Calendar

// Single home for hero money math: every balance, income and savings figure
// the UI shows must come through here, so the ledger can never be counted
// two different ways on two different screens.
// (Best-of both trees: ported from PesaFlow main and adapted — this tree
// flags demo rows with isSample instead of an OPENING source, and books
// opening upkeep as real INCOME, so statistics exclude samples only.)

fun ledgerBalance(txs: List<Transaction>): Double = txs.sumOf {
    when (it.type) {
        TransactionType.INCOME -> it.amount
        TransactionType.EXPENSE -> -it.amount
        TransactionType.SAVING -> -it.amount
        TransactionType.INVESTMENT -> -it.amount
        TransactionType.TRANSFER -> 0.0
    }
}

// Ziidi holding: top-ups in as SAVING, withdrawals back as INCOME (both
// merchant "Ziidi"). Never negative — a ledger can't over-withdraw. The hero
// card adds it back because moving money into Ziidi already left the
// balance: the pair is neutral by construction, so hero == total funds
// under control, not a double count.
fun ziidiHoldings(txs: List<Transaction>): Double = txs.sumOf {
    when {
        it.type == TransactionType.SAVING && it.merchant.contains("ziidi", ignoreCase = true) -> it.amount
        it.type == TransactionType.INCOME && it.merchant.contains("ziidi", ignoreCase = true) -> -it.amount
        else -> 0.0
    }
}.coerceAtLeast(0.0)

// Per-pocket balance: which pocket holds the money. Non-transfer rows read
// their payment method (unchanged); paired transfer legs read their stamped
// accountKind side (OUT leaves, IN arrives) — previously both legs read 0,
// so every M-Pesa→bank move silently broke all three pockets. Unpaired
// legacy transfers still read 0. Pure, unit-tested.
private fun pocketAccounts(method: PaymentMethod): Set<String> = when (method) {
    // Canonical account vocabulary (Account enum): M_PESA, CASH, BANK, ...
    PaymentMethod.MPESA -> setOf("M_PESA")
    PaymentMethod.CASH -> setOf("CASH")
    PaymentMethod.BANK_TRANSFER -> setOf("BANK")
    else -> setOf(method.name)
}

fun pocketBalance(txs: List<Transaction>, method: PaymentMethod): Double =
    txs.filter { !it.isSample }.sumOf { tx ->
        when {
            tx.type == TransactionType.TRANSFER && tx.transferSide == "OUT" &&
                tx.accountKind.uppercase() in pocketAccounts(method) -> -tx.amount
            tx.type == TransactionType.TRANSFER && tx.transferSide == "IN" &&
                tx.accountKind.uppercase() in pocketAccounts(method) -> tx.amount
            tx.type == TransactionType.TRANSFER -> 0.0
            tx.paymentMethod != method -> 0.0
            else -> when (tx.type) {
                TransactionType.INCOME -> tx.amount
                TransactionType.EXPENSE -> -tx.amount
                TransactionType.SAVING -> -tx.amount
                TransactionType.INVESTMENT -> -tx.amount
                TransactionType.TRANSFER -> 0.0
            }
        }
    }

fun isCurrentMonth(ts: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
    val ref = Calendar.getInstance().apply { timeInMillis = nowMs }
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    return c.get(Calendar.YEAR) == ref.get(Calendar.YEAR) &&
        c.get(Calendar.MONTH) == ref.get(Calendar.MONTH)
}

// Month-scoped totals for rates and verdicts: demo/sample rows and opening
// equity never count as earnings (they still count in held cash).
fun monthScopedTotal(
    txs: List<Transaction>,
    type: TransactionType,
    nowMs: Long = System.currentTimeMillis(),
    excludeSamples: Boolean = true
): Double = txs.filter {
    it.type == type &&
        (!excludeSamples || !it.isSample) &&
        (type != TransactionType.INCOME || !it.isOpening) &&
        isCurrentMonth(it.dateTimestamp, nowMs)
}.sumOf { it.amount }

// Opening-equity basis: ledger opening rows (cash, M-Pesa, bank) win when
// present; profile funding covers the manual path (set under University with
// no seeded rows). Never both — that counted pocket money twice.
fun openingBasis(openingRows: Double, profileFunding: Double): Double =
    openingRows + (if (openingRows <= 0) profileFunding else 0.0)

// Earned-income cash: opening equity is held cash, never earnings.
fun liquidCash(opening: Double, earnedIncome: Double, spent: Double, saved: Double, invested: Double): Double =
    opening + earnedIncome - spent - saved - invested

// Spendable now: ledger liquid ± the unconfirmed SMS queue. Pending rows are
// real money movements awaiting categorization (the SMS already happened),
// so the ledger understates reality until they confirm. Anchored on the LEDGER
// (cash + bank + M-Pesa), never the SMS reading alone — that reading is
// M-Pesa-only and goes stale. Pure, unit-tested.
fun spendableNow(ledgerLiquid: Double, pendingIn: Double, pendingOut: Double): Double =
    ledgerLiquid + pendingIn - pendingOut

// Drift zones: tiny gaps stay silent, small gaps get a gentle nudge with the
// 1-tap fix, large gaps get the red flag. Nothing ever auto-writes to the
// ledger invisibly — every adjustment is user-confirmed and labeled.
// Pure, unit-tested.
enum class DriftZone { IN_SYNC, MINOR, MAJOR }

fun evaluateDrift(smsBalance: Double, ledgerMpesa: Double): Pair<DriftZone, Double> {
    val drift = ledgerMpesa - smsBalance
    val zone = when (kotlin.math.abs(drift)) {
        in 0.0..50.0 -> DriftZone.IN_SYNC
        in 50.0..500.0 -> DriftZone.MINOR
        else -> DriftZone.MAJOR
    }
    return zone to drift
}

// 1-tap drift reconcile: ledger M-Pesa pocket vs last SMS wallet reading.
// Returns the labeled adjustment row, or null when already within a shilling.
// Ledger-high means spending went unlogged (EXPENSE pulls it down); SMS-high
// means income went unlogged (INCOME tops it up). Pure, unit-tested.
fun reconcileEntry(ledgerMpesa: Double, smsBalance: Double, nowMs: Long = System.currentTimeMillis()): Transaction? {
    val drift = ledgerMpesa - smsBalance
    if (kotlin.math.abs(drift) < 1) return null
    return Transaction(
        amount = kotlin.math.abs(drift),
        type = if (drift > 0) TransactionType.EXPENSE else TransactionType.INCOME,
        category = "Other",
        dateTimestamp = nowMs,
        merchant = "Balance adjustment",
        description = "1-tap drift reconcile",
        paymentMethod = com.pesaflow.app.data.models.PaymentMethod.MPESA,
        accountKind = "MPESA"
    )
}
