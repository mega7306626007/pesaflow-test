package com.pesaflow.app.ui.dashboard

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.UserRhythm
import com.pesaflow.app.data.models.RhythmKind
import com.pesaflow.app.data.models.isEarnedIncome
import com.pesaflow.app.data.repositories.FinanceRepository
import com.pesaflow.app.ui.budgets.Persona
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

// Turns confirmed transactions into testable money-rhythm
// hypotheses: "you spend ~KSh50 on fares every day 7-10am",
// "rent ~KSh5000 on the 5th", "income ~KSh15000 around the 1st".
// Each surfaces as a confirm card on Home — you tap "Yes" or
// "No" and PesaFlow remembers. Confirmed ones drive fare
// reserves, rent alerts and payday predictions everywhere.
// (Best-of both trees: ported from PesaFlow main; the living-group
// input is this tree's Persona instead of LivingSituation.)
data class RhythmHypothesis(
    val kind: RhythmKind,
    val category: String,
    val confidence: Float,
    val hint: String,            // human-readable sentence
    val dayOfMonth: Int,         // -1 = any day; real calendar day otherwise
    val amount: Double,
    val supportingCodes: List<String>,
    val evidence: Int = 0,       // observed runs backing this guess
    val skewed: Boolean = false  // window held holiday months: confirm
)

class RhythmEngine(
    private val repo: FinanceRepository? = null
) {
    fun hypotheses(): Flow<List<RhythmHypothesis>> =
        repo!!.confirmedRhythms.map { rhythms ->
            rhythms.map { r ->
                RhythmHypothesis(
                    kind = RhythmKind.values().firstOrNull { it.name == r.kind } ?: RhythmKind.CUSTOM,
                    category = r.category,
                    confidence = r.confidence,
                    hint = r.hint,
                    dayOfMonth = r.dayOfMonth,
                    amount = r.amount,
                    supportingCodes = listOf(r.sourceCode)
                )
            }
        }

    fun userRhythms(): Flow<List<UserRhythm>> = repo!!.userRhythms

    // Scan the given window of confirmed transactions and produce
    // candidate hypotheses. Nothing is stored until the user confirms
    // on Home. Defaults preserve the old 4-month lookback; pass the
    // semester window (and skewed=true when it holds holiday months)
    // for term-time truth.
    fun propose(
        txs: List<Transaction>,
        persona: Persona = Persona.HOSTEL_COOK,
        windowMs: Long = 4 * 30L * 24 * 60 * 60 * 1000,
        nowMs: Long = System.currentTimeMillis(),
        skewed: Boolean = false
    ): List<RhythmHypothesis> {
        val out = mutableListOf<RhythmHypothesis>()
        val recent = txs.filter { it.dateTimestamp >= nowMs - windowMs && it.dateTimestamp <= nowMs }
        val skewTag = if (skewed) " \u00b7 holiday months inside \u2014 confirm" else ""
        val commuteHeavy = persona == Persona.RENT_COMMUTE || persona == Persona.PARENTS_FAR

        fun domOf(ts: Long): Int =
            Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_MONTH)

        val incomeTxs = recent.filter { it.isEarnedIncome() && !it.isSample }
        val expenseTxs = recent.filter { it.type == TransactionType.EXPENSE }
        val transportTxs = expenseTxs.filter { it.category.equals("Transport", ignoreCase = true) }
        val transportByDay = transportTxs.groupBy { it.dateTimestamp / (24L * 60 * 60 * 1000) }
            .mapValues { (_, l) -> l.sumOf { it.amount } }
        if (transportByDay.size >= 5 && expenseTxs.isNotEmpty()) {
            val avgFare = transportTxs.map { it.amount }.sum() / transportByDay.size
            if (avgFare > 0) {
                val hourBands = mutableMapOf<Int, Int>()
                transportTxs.forEach { tx ->
                    val h = Calendar.getInstance().apply { timeInMillis = tx.dateTimestamp }.get(Calendar.HOUR_OF_DAY)
                    hourBands[h] = (hourBands[h] ?: 0) + 1
                }
                val peak = hourBands.maxByOrNull { it.value }?.key ?: 8
                val startH = maxOf(0, peak - 2)
                val endH = minOf(23, peak + 2)
                val fareWindow = "$startH-" + endH + "am"
                val kind = if (commuteHeavy) RhythmKind.FARE_WINDOW else RhythmKind.CUSTOM
                val days = transportByDay.size
                out.add(RhythmHypothesis(
                    kind = kind,
                    category = "Transport",
                    confidence = (0.55f + 0.05f * days).coerceAtMost(0.95f),
                    hint = "You spend ~KSh " + avgFare.toInt() + " on fares daily around " + fareWindow +
                        " \u00b7 seen on " + days + " days" + skewTag,
                    dayOfMonth = -1,
                    amount = avgFare,
                    supportingCodes = transportByDay.keys.map { it.toString() }.take(3),
                    evidence = days,
                    skewed = skewed
                ))
            }
        }

        val rentTxs = expenseTxs.filter { it.category.equals("Rent", ignoreCase = true) }
        if (rentTxs.isNotEmpty()) {
            val byDom = rentTxs.groupBy { domOf(it.dateTimestamp) }
            val top = byDom.maxByOrNull { it.value.size }
            val day = top?.key ?: domOf(rentTxs.map { it.dateTimestamp }.maxOrNull() ?: nowMs)
            val amtRows = top?.value ?: rentTxs
            val avgAmt = amtRows.map { it.amount }.average()
            val runs = rentTxs.map { it.dateTimestamp / (24L * 60 * 60 * 1000) }.toSet().size
            out.add(RhythmHypothesis(
                kind = RhythmKind.RENT_DAY,
                category = "Rent",
                confidence = (0.55f + 0.1f * runs).coerceAtMost(0.95f),
                hint = "You pay rent ~KSh ${avgAmt.toInt()} on or around the ${dayLabel(day)}" +
                    " \u00b7 seen " + runs + " time(s)" + skewTag,
                dayOfMonth = day,
                amount = avgAmt,
                supportingCodes = rentTxs.map { (it.dateTimestamp / (24L * 60 * 60 * 1000)).toString() }.take(3),
                evidence = runs,
                skewed = skewed
            ))
        }

        val pay = incomeTxs.groupBy { it.merchant.trim().lowercase() }
            .mapNotNull { (_, list) ->
                if (list.size < 2) return@mapNotNull null
                val sorted = list.map { it.dateTimestamp }.sorted()
                val gaps = sorted.zipWithNext { a, b -> (b - a) / (24L * 60 * 60 * 1000) }
                if (gaps.isEmpty()) return@mapNotNull null
                val median = gaps.sorted()[gaps.size / 2]
                if (median !in 25..35) return@mapNotNull null
                Triple(list, list.map { it.amount }.average(), sorted.maxOrNull() ?: nowMs)
            }
            .maxByOrNull { it.second }
        if (pay != null) {
            val (list, avgAmt, latest) = pay
            val label = list.maxByOrNull { it.dateTimestamp }?.merchant?.takeIf { it.isNotBlank() } ?: "Income"
            out.add(RhythmHypothesis(
                kind = RhythmKind.PAYDAY,
                category = "Income",
                confidence = (0.55f + 0.1f * list.size).coerceAtMost(0.95f),
                hint = label + " ~KSh " + avgAmt.toInt() + " lands around the " + dayLabel(domOf(latest)) +
                    " \u00b7 seen " + list.size + " time(s)" + skewTag,
                dayOfMonth = domOf(latest),
                amount = avgAmt,
                supportingCodes = list.map { (it.dateTimestamp / (24L * 60 * 60 * 1000)).toString() }.take(3),
                evidence = list.size,
                skewed = skewed
            ))
        }

        return out.take(4)
    }

    private fun dayLabel(day: Int): String {
        val ord = when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$day$ord"
    }
}
