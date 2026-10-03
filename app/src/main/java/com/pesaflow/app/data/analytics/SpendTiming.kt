package com.pesaflow.app.data.analytics

import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.LedgerRow

// Payday timing: does money burn right after it lands? For every income
// event, share spent within 3 and 7 days after. Pure, unit-tested.
data class SplurgeReport(
    val paydays: Int,
    val avgPctSpent3d: Int,
    val avgPctSpent7d: Int
)

private const val DAY_MS = 24L * 60 * 60 * 1000

fun paydaySplurge(rows: List<LedgerRow>, minPaydays: Int = 2): SplurgeReport? {
    // Demo rows and onboarding equity are not paydays: pocket + upkeep seeded
    // together used to fake a 2-payday splurge in every onboarding month.
    val live = rows.filter { !it.isSample && !it.isOpening }
    val incomes = live.filter { it.type == TransactionType.INCOME && it.earnedIncome && it.amount > 0 }
        .sortedBy { it.ts }
    if (incomes.size < minPaydays) return null
    val expenses = live.filter { it.type == TransactionType.EXPENSE && it.amount > 0 }
    // One payday's shadow ends where the next begins — spending belongs to
    // the most recent income, never double-counted across paydays.
    val pct3 = mutableListOf<Double>()
    val pct7 = mutableListOf<Double>()
    incomes.forEachIndexed { i, inc ->
        val horizonEnd = if (i + 1 < incomes.size) minOf(inc.ts + 30 * DAY_MS, incomes[i + 1].ts) else inc.ts + 30 * DAY_MS
        val after = expenses.filter { it.ts in inc.ts..horizonEnd }
        val spent3 = after.filter { it.ts <= inc.ts + 3 * DAY_MS }.sumOf { it.amount }
        val spent7 = after.filter { it.ts <= inc.ts + 7 * DAY_MS }.sumOf { it.amount }
        pct3.add((spent3 / inc.amount * 100).coerceIn(0.0, 100.0))
        pct7.add((spent7 / inc.amount * 100).coerceIn(0.0, 100.0))
    }
    return SplurgeReport(
        paydays = incomes.size,
        avgPctSpent3d = pct3.average().toInt(),
        avgPctSpent7d = pct7.average().toInt()
    )
}
