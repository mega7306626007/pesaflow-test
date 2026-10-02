package com.pesaflow.app.data.parsers

import com.pesaflow.app.data.models.TransactionType
import java.util.Calendar

// Rhythm engines: clusters of past transactions become hypotheses with
// evidence and confidence — NEVER conclusions. Below 0.7 a hypothesis stays
// silent; at 0.7+ it earns one confirmation card ("19 of 22 class mornings").
// Every verdict trains the parser permanently.
//
// Engines are pure: callers map their rows in (Transaction, PendingTransaction,
// scan results) and supply isClassDay (weekday heuristic at onboarding, real
// timetable at review). No Android imports — fully unit-tested.
data class LedgerRow(
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val merchant: String,
    val ts: Long,
    // Provenance rides along so rhythm/payday engines can exclude demo and
    // onboarding-equity rows instead of treating them as habits/paydays.
    // Defaulted: all existing call sites compile untouched.
    val isSample: Boolean = false,
    val isOpening: Boolean = false
)

enum class HypothesisKind { FARE, RENT, RECURRING }

data class Deduction(
    val kind: HypothesisKind,
    val title: String,
    val evidence: String,
    val confidence: Float,
    val amount: Double,
    val category: String,
    val merchant: String
)

/** Review bar: hypotheses below this never surface. */
const val DEDUCTION_BAR = 0.7f

private fun hourOf(ts: Long): Int =
    Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.HOUR_OF_DAY)

private fun dayOfMonth(ts: Long): Int =
    Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_MONTH)

private fun dayOfWeek(ts: Long): Int =
    Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_WEEK)

private fun monthId(ts: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
}

/**
 * Fare rhythm: same-ish morning Transport spend on class days. Flexible by
 * design: the dominant band anchors, ±50 absorbs hiked peak fares and nearby
 * routes (different destinations, one school run); two truly different lives
 * spread wide and pay for it in confidence instead of splitting silently.
 * Wide 5–12 window: first classes shift by hours across days and terms.
 */
fun deduceFare(
    rows: List<LedgerRow>,
    isClassDay: (Long) -> Boolean = { ts ->
        dayOfWeek(ts) in listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
    }
): Deduction? {
    val mornings = rows.filter {
        it.type == TransactionType.EXPENSE &&
            it.category.equals("Transport", ignoreCase = true) &&
            it.amount in 10.0..500.0 &&
            hourOf(it.ts) in 5..12
    }
    if (mornings.size < 4) return null
    // Dominant nearest-10 band anchors; ±50 tolerance absorbs the rest.
    val best = mornings.groupBy { (it.amount / 10).toInt() }.maxByOrNull { it.value.size }
        ?: return null
    val anchor = medianD(best.value.map { it.amount })
    val group = mornings.filter { kotlin.math.abs(it.amount - anchor) <= 50.0 }
    if (group.size < 4) return null
    // Rival-life guard: a comparable second cluster means two different
    // lives — silence beats a blended fiction.
    if ((mornings.size - group.size) * 2 >= group.size) return null
    val amount = group.map { it.amount }.average()
    // Variance penalty, measured against the ±50 tolerance window: a 40–60
    // wobble barely registers; a window-spanning sprawl pays full price.
    val spread = ((group.maxOf { it.amount } - group.minOf { it.amount }) / 100.0).coerceAtMost(1.0)
    val distinctDays = group.map { monthId(it.ts) to dayOfMonth(it.ts) }.toSet().size
    val classMornings = group.count { isClassDay(it.ts) }
    val sundayShare = group.count { dayOfWeek(it.ts) == Calendar.SUNDAY }.toDouble() / group.size
    if (classMornings < 3) return null
    val confidence = (0.45 + 0.07 * minOf(classMornings, 5) - 0.15 * sundayShare - 0.3 * spread)
        .coerceIn(0.0, 0.95).toFloat()
    val range = group.minOf { it.amount }.toInt().let { lo ->
        group.maxOf { it.amount }.toInt().let { hi ->
            if (lo == hi) "" else " · KSh $lo-$hi typical"
        }
    }
    return Deduction(
        kind = HypothesisKind.FARE,
        title = "Morning fare · KSh ${amount.toInt()}",
        evidence = "$classMornings of $distinctDays mornings on class days$range",
        confidence = confidence,
        amount = amount,
        category = "Transport",
        merchant = group.groupBy { it.merchant }.maxByOrNull { it.value.size }?.key ?: "Matatu"
    )
}

private fun medianD(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val s = values.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}

/**
 * Rent anchor: same large amount on the same day-of-month across 2+ months.
 * Persona-inconsistent anchors (a "rent" for a parents-home profile) are the
 * caller's job to doubt — the engine reports the rhythm, not the meaning.
 */
fun deduceRent(rows: List<LedgerRow>): Deduction? {
    val big = rows.filter { it.type == TransactionType.EXPENSE && it.amount >= 1000.0 }
    if (big.size < 2) return null
    val byDom = big.groupBy { dayOfMonth(it.ts) }
    var best: List<LedgerRow>? = null
    for ((_, g) in byDom) {
        if (g.size < 2) continue
        if (g.map { monthId(it.ts) }.toSet().size < 2) continue
        val ratio = g.maxOf { it.amount } / g.minOf { it.amount }
        if (ratio > 1.15) continue
        if (best == null || g.size > best!!.size) best = g
    }
    val group = best ?: return null
    val amount = group.map { it.amount }.average()
    val months = group.map { monthId(it.ts) }.toSet().size
    val confidence = (0.55f + 0.15f * (group.size - 1)).coerceAtMost(0.9f)
    return Deduction(
        kind = HypothesisKind.RENT,
        title = "Monthly anchor · KSh ${amount.toInt()} on the ${dayOfMonth(group[0].ts)}th",
        evidence = "KSh ${amount.toInt()} · $months months running",
        confidence = confidence,
        amount = amount,
        category = "Rent",
        merchant = group.groupBy { it.merchant }.maxByOrNull { it.value.size }?.key ?: "Rent"
    )
}

/**
 * Recurrence: same merchant + stable amount on a ~monthly cadence
 * (subscriptions, chama, insurance). Interval median 20–40 days.
 */
fun deduceRecurring(rows: List<LedgerRow>): List<Deduction> {
    val out = mutableListOf<Deduction>()
    val byParty = rows.filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.merchant.lowercase() to (it.amount / 100).toInt() }
    for ((_, g) in byParty) {
        if (g.size < 2) continue
        val sorted = g.sortedBy { it.ts }
        val gaps = sorted.zipWithNext { a, b -> (b.ts - a.ts) / (24.0 * 60 * 60 * 1000) }
        val medianGap = gaps.sorted().let { if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
        if (medianGap < 20 || medianGap > 40) continue
        val amount = sorted.map { it.amount }.average()
        out.add(
            Deduction(
                kind = HypothesisKind.RECURRING,
                title = "${sorted[0].merchant} · KSh ${amount.toInt()}/mo",
                evidence = "every ~${medianGap.toInt()} days · ${g.size} times",
                confidence = (0.6f + 0.1f * (g.size - 1)).coerceAtMost(0.85f),
                amount = amount,
                category = sorted[0].category,
                merchant = sorted[0].merchant
            )
        )
    }
    return out.sortedByDescending { it.confidence }
}

/** Draft prefill: session paces + confident deductions, blanks only. */
data class DraftProfile(
    val transportMonthly: Double?,
    val rentMonthly: Double?,
    val recurring: List<Deduction>
)

fun buildDraft(rows: List<LedgerRow>, isClassDay: (Long) -> Boolean): DraftProfile {
    val fare = deduceFare(rows, isClassDay)?.takeIf { it.confidence >= DEDUCTION_BAR }
    val rent = deduceRent(rows)?.takeIf { it.confidence >= DEDUCTION_BAR }
    return DraftProfile(
        // 22 class days a month — documented assumption, user-editable downstream.
        transportMonthly = fare?.let { it.amount * 22 },
        rentMonthly = rent?.amount,
        recurring = deduceRecurring(rows).filter { it.confidence >= DEDUCTION_BAR }
    )
}
