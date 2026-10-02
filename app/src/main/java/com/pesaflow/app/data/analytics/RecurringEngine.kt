package com.pesaflow.app.data.analytics

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType

/**
 * Recurring transaction engine: pure Kotlin, no Android imports.
 *
 * Detects, categorizes, and previews recurring transactions
 * (subscriptions, chama contributions, insurance).
 * Every result is a hypothesis — the user confirms.
 */
data class RecurringPattern(
    val merchant: String,
    val category: String,
    val amount: Double,
    val medianIntervalDays: Int,
    val occurrences: Int,
    val firstSeen: Long,
    val lastSeen: Long,
    val confidence: Float,
    val nextExpectedDate: Long,
    val variance: Double, // 0 = perfectly regular
    val suggestion: String
)

data class RecurringPreview(
    val patterns: List<RecurringPattern>,
    val totalMonthlyCommitment: Double,
    val count: Int,
    val atRisk: List<RecurringPattern> // patterns that might be duplicates or errors
)

/** Detect recurring patterns from transaction history. */
fun detectRecurring(
    transactions: List<Transaction>,
    minOccurrences: Int = 2,
    minIntervalDays: Int = 20,
    maxIntervalDays: Int = 40
): List<RecurringPattern> {
    val expenses = transactions.filter { it.type == TransactionType.EXPENSE && !it.isSample }
    // Group by merchant + amount band (nearest 100).
    val byParty = expenses.groupBy { it.merchant.lowercase() to ((it.amount / 100).toInt()) }
    val patterns = mutableListOf<RecurringPattern>()
    for ((_, group) in byParty) {
        if (group.size < minOccurrences) continue
        val sorted = group.sortedBy { it.dateTimestamp }
        val gaps = sorted.zipWithNext { a, b -> (b.dateTimestamp - a.dateTimestamp) / (24.0 * 60 * 60 * 1000) }
        if (gaps.size < minOccurrences - 1) continue
        val sortedGaps = gaps.sorted()
        val medianGap = if (sortedGaps.size % 2 == 1) sortedGaps[sortedGaps.size / 2]
        else (sortedGaps[sortedGaps.size / 2 - 1] + sortedGaps[sortedGaps.size / 2]) / 2
        if (medianGap < minIntervalDays || medianGap > maxIntervalDays) continue
        val amount = sorted.map { it.amount }.average()
        val variance = if (amount > 0) sortedGaps.map { Math.abs(it - medianGap) / medianGap }.average() else 0.0
        val confidence = (0.5 + 0.15 * (sorted.size - 1) - 0.2 * variance).coerceIn(0.3, 0.95).toFloat()
        val firstSeen = sorted.first().dateTimestamp
        val lastSeen = sorted.last().dateTimestamp
        val nextExpected = lastSeen + (medianGap * 24L * 60 * 60 * 1000).toLong()
        val suggestion = buildRecurringSuggestion(sorted.first().merchant, amount, medianGap.toInt())
        patterns.add(
            RecurringPattern(
                merchant = sorted.first().merchant,
                category = sorted.first().category,
                amount = amount,
                medianIntervalDays = medianGap.toInt(),
                occurrences = sorted.size,
                firstSeen = firstSeen,
                lastSeen = lastSeen,
                confidence = confidence,
                nextExpectedDate = nextExpected,
                variance = variance,
                suggestion = suggestion
            )
        )
    }
    return patterns.sortedByDescending { it.confidence }
}

private fun buildRecurringSuggestion(merchant: String, amount: Double, intervalDays: Int): String {
    val intervalWord = when {
        intervalDays in 25..35 -> "monthly"
        intervalDays in 18..24 -> "~3-week"
        intervalDays >= 36 -> "long interval"
        else -> "regular"
    }
    return "$merchant · KSh ${amount.toInt()} · ${intervalWord} subscription"
}

/** Build a full recurring preview including risk assessment. */
fun buildRecurringPreview(
    transactions: List<Transaction>,
    now: Long = System.currentTimeMillis()
): RecurringPreview {
    val patterns = detectRecurring(transactions)
    val totalMonthly = patterns.filter { it.medianIntervalDays in 25..35 }
        .sumOf { it.amount * (30.0 / it.medianIntervalDays) }
    // Risk: patterns with very high variance or very few occurrences.
    val atRisk = patterns.filter { it.variance > 0.5 || it.occurrences < 3 }
    return RecurringPreview(
        patterns = patterns,
        totalMonthlyCommitment = totalMonthly,
        count = patterns.size,
        atRisk = atRisk
    )
}

/** Bill frequency for a detected pattern: monthly rhythms bill monthly,
 *  weekly rhythms weekly, everything else a one-off reminder. */
fun billFrequencyFor(medianIntervalDays: Int): String = when (medianIntervalDays) {
    in 25..35 -> "MONTHLY"
    in 6..8 -> "WEEKLY"
    else -> "ONE_TIME"
}

/** Check if a new transaction matches an existing recurring pattern. */
fun matchesRecurring(
    tx: Transaction,
    patterns: List<RecurringPattern>,
    tolerancePercent: Double = 10.0
): RecurringPattern? {
    val low = tx.merchant.lowercase()
    val amount = tx.amount
    return patterns.firstOrNull { p ->
        p.merchant.lowercase() == low &&
            amount > 0 &&
            Math.abs(amount - p.amount) / p.amount <= tolerancePercent / 100
    }
}

/** Payday prediction (from PesaFlow main): same INCOME sender on a monthly
 * rhythm → next landing. Returns (merchant, amount, expectedTimestamp),
 * soonest first. */
fun predictPaydays(
    txs: List<Transaction>,
    now: Long = System.currentTimeMillis()
): List<Triple<String, Double, Long>> {
    val dayMs = 24L * 60 * 60 * 1000
    return txs.filter { it.type == TransactionType.INCOME && !it.isSample && !it.isOpening }
        .groupBy { it.merchant.trim().lowercase() }
        .mapNotNull { (_, list) ->
            if (list.size < 2) return@mapNotNull null
            val sorted = list.map { it.dateTimestamp }.sorted()
            val gaps = sorted.zipWithNext { a, b -> (b - a) / dayMs }
            if (gaps.isEmpty()) return@mapNotNull null
            val median = gaps.sorted()[gaps.size / 2]
            if (median !in 25..35) return@mapNotNull null
            val avg = list.map { it.amount }.average()
            if (avg <= 0) return@mapNotNull null
            val label = list.maxByOrNull { it.dateTimestamp }?.merchant?.takeIf { it.isNotBlank() } ?: "Income"
            Triple(label, avg, (sorted.maxOrNull() ?: now) + median * dayMs)
        }
        .filter { it.third > now - 7 * dayMs }
        .sortedBy { it.third }
}
