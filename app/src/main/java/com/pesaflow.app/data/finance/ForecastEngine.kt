package com.pesaflow.app.data.finance

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType

// Forecast engine (§19, Phase 9): robust daily paces and month-end
// projection, extracted from buildSnapshot with identical math —
// median pace, 75th/25th bands, one-off quarantine (>5× median).
private const val FC_DAY_MS = 24L * 60 * 60 * 1000

data class PaceStats(
    val typical: Double,
    val cautious: Double,
    val favourable: Double,
    val activeDays: Int
)

fun dailyPaces(
    txs: List<Transaction>,
    nowMs: Long = System.currentTimeMillis(),
    windowDays: Long = 28
): PaceStats {
    val daily = txs.filter {
        it.type == TransactionType.EXPENSE && !it.isSample &&
            it.dateTimestamp >= nowMs - windowDays * FC_DAY_MS &&
            it.dateTimestamp <= nowMs
    }.groupBy { it.dateTimestamp / FC_DAY_MS }
        .mapValues { (_, l) -> l.sumOf { it.amount } }.values.toList()
    val typical = median(daily)
    val clean = daily.filter { typical == 0.0 || it <= typical * 5 }
    return PaceStats(
        typical = median(clean),
        cautious = percentile(clean, 75.0),
        favourable = percentile(clean, 25.0),
        activeDays = daily.size
    )
}

fun projectMonthEnd(
    flexible: Money,
    daily: Double,
    incomeIn: Money,
    billsDue: Money,
    daysLeft: Int
): Money =
    (flexible + incomeIn - Money.of(daily * daysLeft) - billsDue).coerceAtLeast(Money.ZERO)
