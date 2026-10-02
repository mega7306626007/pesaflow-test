package com.pesaflow.app.data.income

import android.content.Context
import androidx.room.*

// Income sources: where the money comes from and how we track it.
// Room-backed and reactive (Phase 6): HELB via M-Pesa is SMS-parsed
// automatically, bank income tracks by expected dates, everything else is
// manual. totalExpected() feeds the budget calculator base fallback,
// Buddy's income answers and the onboarding review.
@Entity(tableName = "income_sources")
data class IncomeSource(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    // HELB_MPESA, HELB_BANK, PARENT, GUARDIAN, HUSTLE, JOB, SCHOLARSHIP, FULIZA, OTHER
    val kind: String = "OTHER",
    val label: String = "",
    val bank: String = "",
    val expectedAmount: Double = 0.0,
    // DAILY, WEEKLY, MONTHLY, ONCE — daily hustle money counts: ×30 monthly.
    val frequency: String = "MONTHLY",
    // Expected day of month (1-31, 0 = not sure → manual input).
    val dayOfMonth: Int = 0,
    val autoTrack: Boolean = false,
    // Fuliza only: user agreed to count borrowed money in budgeting.
    // Beware: it still reflects as a loan (deni) everywhere else.
    val useInBudget: Boolean = false
) {
    // Monthly equivalent so daily/weekly earners compare fairly with salaries.
    fun monthlyEquivalent(): Double = when (frequency) {
        "DAILY" -> expectedAmount * 30
        "WEEKLY" -> expectedAmount * 4
        else -> expectedAmount
    }

    // Days until this source's next dated landing (HELB/bank day-of-month).
    // Undated, daily/weekly (continuous) and one-off sources don't anchor a
    // horizon — null. Pure, unit-tested.
    fun daysUntilLanding(nowMs: Long = System.currentTimeMillis()): Int? {
        if (frequency != "MONTHLY" || dayOfMonth !in 1..31) return null
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
        val today = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val dim = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val target = dayOfMonth.coerceAtMost(dim)
        return if (target >= today) target - today else (dim - today) + target
    }

    fun frequencyLabel(): String = when (frequency) {
        "DAILY" -> "daily"
        "WEEKLY" -> "weekly"
        "ONCE" -> "one-off"
        else -> "monthly"
    }
    fun displayKind(): String = when (kind) {
        "HELB_MPESA" -> "HELB (M-Pesa)"
        "HELB_BANK" -> "HELB (bank)"
        "PARENT" -> "Parents"
        "GUARDIAN" -> "Guardian/sponsor"
        "HUSTLE" -> "Hustle"
        "JOB" -> "Job"
        "SCHOLARSHIP" -> "Scholarship"
        "FULIZA" -> "Fuliza (borrowed)"
        else -> "Other"
    }

    fun trackingNote(): String = when {
        kind == "HELB_MPESA" -> "First batch iko out — I'll parse SMS to see the amounts. ✅"
        kind == "HELB_BANK" && dayOfMonth > 0 -> "Watching ${bank.ifBlank { "your bank" }} around day $dayOfMonth."
        kind == "HELB_BANK" -> "No date set — log it manually when it lands."
        kind == "FULIZA" -> "Borrowed, not income — track it here, clear it fast. 🙂"
        dayOfMonth > 0 -> "Expected around day $dayOfMonth — log it when it lands."
        else -> "No date set — you'll input it manually. 👍"
    }
}


object IncomeSourceStore {
    private const val KEY = "income_sources_json"

    private fun prefs(context: Context) =
        context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)

    fun load(context: Context): List<IncomeSource> {
        val raw = prefs(context).getString(KEY, "[]").orEmpty()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                try {
                    val o = arr.getJSONObject(i)
                    IncomeSource(
                        id = o.optString("id", java.util.UUID.randomUUID().toString()),
                        kind = o.optString("kind", "OTHER"),
                        label = o.optString("label", ""),
                        bank = o.optString("bank", ""),
                        expectedAmount = o.optDouble("expectedAmount", 0.0),
                        frequency = o.optString("frequency", "MONTHLY"),
                        dayOfMonth = o.optInt("dayOfMonth", 0),
                        autoTrack = o.optBoolean("autoTrack", false),
                        useInBudget = o.optBoolean("useInBudget", false)
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, sources: List<IncomeSource>) {
        val arr = org.json.JSONArray()
        sources.forEach { s ->
            arr.put(
                org.json.JSONObject()
                    .put("id", s.id)
                    .put("kind", s.kind)
                    .put("label", s.label)
                    .put("bank", s.bank)
                    .put("expectedAmount", s.expectedAmount)
                    .put("frequency", s.frequency)
                    .put("dayOfMonth", s.dayOfMonth)
                    .put("autoTrack", s.autoTrack)
                    .put("useInBudget", s.useInBudget)
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    // Fuliza is borrowed, never income — excluded so expectations stay honest,
    // unless the user explicitly opts it into budgeting (still a loan everywhere else).
    // Daily/weekly amounts count at monthly equivalent (×30 / ×4).
    fun budgetedMonthly(s: IncomeSource): Double {
        if (s.kind == "FULIZA" && !s.useInBudget) return 0.0
        return s.monthlyEquivalent()
    }

    fun totalExpected(context: Context): Double =
        load(context).sumOf { budgetedMonthly(it) }

    // One-way legacy migration (Phase 6): prefs JSON → Room, then the key is
    // cleared so the database is the single source of truth going forward.
    // Returns the moved rows (empty when nothing to move).
    fun consumeLegacy(context: Context): List<IncomeSource> {
        val moved = load(context)
        if (moved.isNotEmpty()) {
            prefs(context).edit().remove(KEY).apply()
        }
        return moved
    }

    fun autoTrackedKinds(context: Context): List<String> =
        load(context).filter { it.autoTrack }.map { it.displayKind() }.distinct()
}


/**
 * Days until the nearest dated monthly inflow across all sources — the
 * dynamic horizon cash planning should count down to. Null when nothing is
 * dated (horizon falls back to month/semester bounds). Pure, unit-tested.
 */
fun nextInflowDay(sources: List<IncomeSource>, nowMs: Long = System.currentTimeMillis()): Int? =
    sources.mapNotNull { it.daysUntilLanding(nowMs) }.minOrNull()
