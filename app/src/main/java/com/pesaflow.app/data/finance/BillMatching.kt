package com.pesaflow.app.data.finance

import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType

// Auto-fulfillment matcher: links confirmed ledger EXPENSE rows to open bills
// so paid bills clear themselves ("self-healing" without a parallel event
// table — Bill.linkedPaymentId + markBillPaidWith already exist, nothing ever
// auto-linked before). Suggest-only: the UI confirms, the user stays boss.
//
// A row matches a bill only when ALL hold:
// - bill UNPAID, row is a real (non-sample) EXPENSE,
// - amounts within max(KSh 1, 2%),
// - row dated in [due − 14d, due + 3d],
// - name evidence: merchant/description token overlap with the bill name,
//   or same category (a caretaker row rarely says "rent").
// Score = 0.5·name + 0.3·amount + 0.2·date; threshold 0.55 means some name
// evidence is mandatory (amount + date alone cap at 0.5). One row pays at
// most one bill — highest score wins, ties break by earliest due. Pure,
// fully unit-tested.
data class BillPaymentMatch(
    val bill: Bill,
    val tx: Transaction,
    val score: Double
)

private val DAY_MS = 24L * 60 * 60 * 1000

private fun tokens(s: String): Set<String> =
    s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 1 }.toSet()

fun matchBillPayments(
    bills: List<Bill>,
    txs: List<Transaction>,
    nowMs: Long = System.currentTimeMillis()
): List<BillPaymentMatch> {
    val open = bills.filter { it.status != "PAID" && it.paidBy == "ME" && it.amount > 0 }
    if (open.isEmpty()) return emptyList()
    val rows = txs.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp <= nowMs }
    if (rows.isEmpty()) return emptyList()

    data class Scored(val bill: Bill, val tx: Transaction, val score: Double)
    val scored = mutableListOf<Scored>()
    for (bill in open) {
        val billTokens = tokens(bill.name)
        val tolerance = maxOf(1.0, bill.amount * 0.02)
        val winStart = bill.dueDate - 14 * DAY_MS
        val winEnd = bill.dueDate + 3 * DAY_MS
        for (tx in rows) {
            if (kotlin.math.abs(tx.amount - bill.amount) > tolerance) continue
            if (tx.dateTimestamp < winStart || tx.dateTimestamp > winEnd) continue
            val txTokens = tokens(tx.merchant + " " + tx.description)
            val overlap = if (billTokens.isEmpty()) 0.0
            else billTokens.intersect(txTokens).size.toDouble() / billTokens.size
            val categoryHit = tx.category.equals(bill.category, ignoreCase = true)
            val nameSim = maxOf(overlap, if (categoryHit) 0.5 else 0.0)
            val amountSim = 1.0 - kotlin.math.abs(tx.amount - bill.amount) / bill.amount
            val dateSim = 1.0 - kotlin.math.abs(tx.dateTimestamp - bill.dueDate).toDouble() / (17 * DAY_MS)
            val score = 0.5 * nameSim + 0.3 * amountSim + 0.2 * dateSim.coerceIn(0.0, 1.0)
            if (score >= 0.55) scored.add(Scored(bill, tx, score))
        }
    }
    // Greedy best-first: one row, one bill.
    val usedTx = mutableSetOf<String>()
    val usedBill = mutableSetOf<String>()
    return scored
        .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.bill.dueDate })
        .filter { usedTx.add(it.tx.id) && usedBill.add(it.bill.id) }
        .map { BillPaymentMatch(it.bill, it.tx, it.score) }
}
