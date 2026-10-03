package com.pesaflow.app.data.finance

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.time.startOfDay
import com.pesaflow.app.data.time.startOfWeek
import java.util.Calendar

// Behaviour engine (§13, Phase 8): what the ledger statistically observes,
// what to suggest, and what is effective. Precedence: declaration >
// confirmation > observation > default. Observation NEVER rewrites anything
// by itself — it only proposes, and only the user confirms.
// Honest limits: cook-vs-buy split is not on the ledger (it lives in meal
// items), so food gets no suggestions until that input is wired.
data class ObservedSignals(
    val commuteDaysPerWeek: Double,
    val avgTransportDaily: Double,
    val activeDaysPerWeek: Double,
    val foodDailyAvg: Double,
    val incomeMonthsHit: Int,
    val sampleDays: Int
)

data class ProfileSuggestion(
    val id: String, // stable: "commute:LONG"
    val dimension: String, // "commute", "incomeStability", ...
    val current: String,
    val suggested: String,
    val reason: String
)

private const val BEH_DAY_MS = 24L * 60 * 60 * 1000

fun observeSignals(
    txs: List<Transaction>,
    windowDays: Long = 60,
    nowMs: Long = System.currentTimeMillis()
): ObservedSignals {
    val since = nowMs - windowDays * BEH_DAY_MS
    val rows = txs.filter { !it.isSample && it.dateTimestamp >= since && it.dateTimestamp <= nowMs }
    fun dow(ts: Long): Int =
        Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_WEEK)
    val transport = rows.filter {
        it.type == TransactionType.EXPENSE && it.category.equals("Transport", ignoreCase = true)
    }
    // Days-per-week averaged over weeks that actually show movement —
    // dividing by the whole window would let one quiet month erase a habit.
    // Weeks are canonical Monday-start calendar weeks (see TimeWindows):
    // the old UTC-midnight bucket split local days and broke grouping.
    fun daysPerWeek(txs: List<Transaction>): Double {
        val byWeek = txs.groupBy { startOfWeek(it.dateTimestamp) }.filterValues { it.isNotEmpty() }
        if (byWeek.size < 2) return 0.0
        return byWeek.values.map { week ->
            week.map { dow(it.dateTimestamp) }.toSet().size
        }.average()
    }
    val months = rows.filter { it.type == TransactionType.INCOME && !it.isOpening }
        .map {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }
            c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
        }.toSet().size
    val foodDays = rows.filter {
        it.type == TransactionType.EXPENSE && it.category.equals("Food", ignoreCase = true)
    }.groupBy { startOfDay(it.dateTimestamp) }.mapValues { (_, l) -> l.sumOf { it.amount } }
    return ObservedSignals(
        commuteDaysPerWeek = daysPerWeek(transport),
        avgTransportDaily = if (transport.isEmpty()) 0.0 else transport.sumOf { it.amount } / transport.map { startOfDay(it.dateTimestamp) }.toSet().size.coerceAtLeast(1),
        activeDaysPerWeek = daysPerWeek(rows.filter { it.type == TransactionType.EXPENSE }),
        foodDailyAvg = if (foodDays.isEmpty()) 0.0 else foodDays.values.average(),
        incomeMonthsHit = months,
        sampleDays = rows.map { startOfDay(it.dateTimestamp) }.toSet().size
    )
}

fun suggestProfileUpdates(
    declared: ProfileSignals,
    observed: ObservedSignals
): List<ProfileSuggestion> {
    if (observed.sampleDays < 5) return emptyList() // too thin to say anything
    val out = mutableListOf<ProfileSuggestion>()
    // Walk/short declarations against a 4+ day fare reality.
    if ((declared.commute == Commute.WALK || declared.commute == Commute.SHORT) &&
        observed.commuteDaysPerWeek >= 4
    ) {
        out.add(
            ProfileSuggestion(
                id = "commute:LONG",
                dimension = "commute",
                current = declared.commute.name,
                suggested = Commute.LONG.name,
                reason = "Transport spending lands ${"%.1f".format(observed.commuteDaysPerWeek)} days a week — that reads like a long commute, not a walk."
            )
        )
    }
    // Long declarations with near-zero movement.
    if (declared.commute == Commute.LONG && observed.commuteDaysPerWeek <= 1 &&
        observed.avgTransportDaily <= 0.0
    ) {
        out.add(
            ProfileSuggestion(
                id = "commute:SHORT",
                dimension = "commute",
                current = declared.commute.name,
                suggested = Commute.SHORT.name,
                reason = "Almost no fare movement lately — the long commute may be over."
            )
        )
    }
    // Income declared fixed but nothing landed in two months.
    if (declared.incomeStability == IncomeStability.FIXED && observed.incomeMonthsHit == 0) {
        out.add(
            ProfileSuggestion(
                id = "incomeStability:VARIABLE",
                dimension = "incomeStability",
                current = declared.incomeStability.name,
                suggested = IncomeStability.VARIABLE.name,
                reason = "No income landed in the window — budgets should stop counting a salary."
            )
        )
    }
    // Nothing declared but steady landings observed.
    if (declared.incomeStability == IncomeStability.NONE && observed.incomeMonthsHit >= 2) {
        out.add(
            ProfileSuggestion(
                id = "incomeStability:FIXED",
                dimension = "incomeStability",
                current = declared.incomeStability.name,
                suggested = IncomeStability.FIXED.name,
                reason = "Pay landed ${observed.incomeMonthsHit} months running — worth declaring as fixed."
            )
        )
    }
    return out
}

// Effective profile: base declaration with ONLY user-confirmed suggestion
// ids applied. Unknown ids are ignored, never guessed.
fun applySuggestions(
    base: ProfileSignals,
    confirmedIds: Set<String>,
    all: List<ProfileSuggestion>
): ProfileSignals {
    var cur = base
    all.filter { it.id in confirmedIds }.forEach { s ->
        cur = when (s.dimension) {
            "commute" -> cur.copy(commute = Commute.values().firstOrNull { it.name == s.suggested } ?: cur.commute)
            "incomeStability" -> cur.copy(
                incomeStability = IncomeStability.values().firstOrNull { it.name == s.suggested } ?: cur.incomeStability
            )
            "food" -> cur.copy(food = FoodStyle.values().firstOrNull { it.name == s.suggested } ?: cur.food)
            "housing" -> cur.copy(housing = Housing.values().firstOrNull { it.name == s.suggested } ?: cur.housing)
            else -> cur
        }
    }
    return cur
}
