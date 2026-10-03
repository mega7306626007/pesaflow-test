package com.pesaflow.app.data.finance

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType

// Presentation-only breakdown of Current Balance (§5 of the transformation).
// Mirrors FinancialEngine.buildSnapshot's filters EXACTLY (!isSample,
// dateTimestamp <= now, TRANSFER excluded unless paired) so the explained
// numbers always reconcile with snap.liquid. Never used for math — the
// engine remains the single source of truth.
data class BalanceBreakdown(
    val received: Double,
    val spending: Double,
    val opening: Double,
    val saved: Double,
    val transferNet: Double,
    val liquid: Double,
    val futureExcludedCount: Int,
    val futureExcludedNet: Double,
    val sampleExcludedCount: Int
)

fun balanceBreakdown(txs: List<Transaction>, nowMs: Long): BalanceBreakdown {
    val future = txs.filter { it.dateTimestamp > nowMs }
    val futureNet = future.sumOf {
        when (it.type) {
            TransactionType.INCOME -> it.amount
            TransactionType.EXPENSE -> -it.amount
            TransactionType.SAVING -> -it.amount
            TransactionType.INVESTMENT -> -it.amount
            TransactionType.TRANSFER -> 0.0
        }
    }
    val samples = txs.count { it.isSample }
    val real = txs.filter { !it.isSample && it.dateTimestamp <= nowMs }
    val received = real.filter { it.type == TransactionType.INCOME && !it.isOpening }.sumOf { it.amount }
    val opening = real.filter { it.type == TransactionType.INCOME && it.isOpening }.sumOf { it.amount }
    val spending = real.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val saved = real.filter { it.type == TransactionType.SAVING || it.type == TransactionType.INVESTMENT }.sumOf { it.amount }
    var tNet = 0.0
    real.filter { it.type == TransactionType.TRANSFER && it.transferGroupId != null }.forEach {
        when (it.transferSide) {
            "OUT" -> tNet -= it.amount
            "IN" -> tNet += it.amount
        }
    }
    return BalanceBreakdown(
        received = received,
        spending = spending,
        opening = opening,
        saved = saved,
        transferNet = tNet,
        liquid = received + opening - spending - saved + tNet,
        futureExcludedCount = future.size,
        futureExcludedNet = futureNet,
        sampleExcludedCount = samples
    )
}
