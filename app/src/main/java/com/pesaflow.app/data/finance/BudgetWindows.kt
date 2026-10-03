package com.pesaflow.app.data.finance

import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.time.TimeRange
import com.pesaflow.app.data.time.addDays
import com.pesaflow.app.data.time.monthRange
import com.pesaflow.app.data.time.startOfDay
import com.pesaflow.app.data.time.thisWeekRange
import com.pesaflow.app.data.time.todayRange
import com.pesaflow.app.data.time.yearRange

// Budget progress windows: budgets are paced against the CURRENT period,
// never the stored [startTimestamp, endTimestamp] (those go stale the month
// after creation and sum whole histories — "113k of 150k while holding 5k").
// Day→today, Week→Mon–Sun, Month→calendar month, Semester→profile window
// (rolling 120d without one), Annual→calendar year. Pure, unit-tested.
fun budgetWindowRange(
    type: BudgetType,
    now: Long,
    semesterStartMs: Long = 0L,
    semesterEndMs: Long = 0L
): TimeRange {
    return when (type) {
        BudgetType.DAILY -> todayRange(now)
        BudgetType.WEEKLY -> thisWeekRange(now)
        BudgetType.MONTHLY -> monthRange(now)
        BudgetType.SEMESTER -> {
            // Only compute when the user has explicitly set semester dates.
            // Without explicit dates the caller (e.g. Dashboard) will receive
            // a zero-length range and show the "set dates" message.
            if (semesterStartMs <= 0L) TimeRange(0L, 0L)
            else {
                val tomorrow = addDays(startOfDay(now), 1)
                val start = semesterStartMs
                val end = if (semesterEndMs > start) minOf(semesterEndMs, tomorrow) else tomorrow
                if (start >= end) TimeRange(end, end) else TimeRange(start, end)
            }
        }
        BudgetType.ANNUAL -> yearRange(now)
    }
}

// Budget active window: stored [startTimestamp, endTimestamp] goes stale the
// month after creation. Readers that need "the current cap" must prefer rows
// covering now. end<=0 means legacy open-ended (no expiry stamped).
fun isBudgetActive(b: Budget, nowMs: Long): Boolean {
    if (b.limitAmount <= 0.0) return false
    if (b.startTimestamp > nowMs) return false
    if (b.endTimestamp <= 0L) return true
    return b.endTimestamp >= nowMs
}

// Master-envelope precedence: the ALL envelope IS the period target when set
// (categories are its breakdown, not extra money). Without ALL, the category
// sum stands in. Summing both double-counts the wallet — tiny phantom
// percentages like "4% of 70k". Same rule FinancialEngine uses for daily
// pace; every new budget surface must call this. Pure, unit-tested.
//
// Active-first: rows covering nowMs win; when none cover now (legacy rows
// with start=0/end=0 or stale test fixtures) fall back to all rows so old
// data still reads. Multiple ALL rows pick latest start (update-in-place),
// never summed.
fun masterOrCategoryTotal(
    budgets: List<Budget>,
    type: BudgetType,
    nowMs: Long = System.currentTimeMillis()
): Double {
    val scoped = budgets.filter { it.type == type && it.limitAmount > 0 }
    if (scoped.isEmpty()) return 0.0
    val active = scoped.filter { isBudgetActive(it, nowMs) }
    val pool = if (active.isNotEmpty()) active else scoped
    // Latest row per category wins (upsert semantics — no stacking).
    val latest = pool.groupBy { it.category.trim().uppercase() }
        .values.mapNotNull { rows -> rows.maxByOrNull { it.startTimestamp } }
    val master = latest
        .filter { it.category.equals("ALL", ignoreCase = true) }
        .maxByOrNull { it.startTimestamp }
    if (master != null) return master.limitAmount
    return latest
        .filter { !it.category.equals("ALL", ignoreCase = true) }
        .sumOf { it.limitAmount }
}

// Envelope spend: what one budget row measures. ALL envelopes read every
// expense; category envelopes read only their own (case-insensitive).
// The dashboard warning banners used to read TOTAL spend for EVERY envelope
// ("Food crossed: 3793 of 1200" next to a healthy Food card) — every budget
// surface must call this. Pure, unit-tested.
fun envelopeSpend(
    txs: List<Transaction>,
    category: String,
    win: TimeRange,
    nowMs: Long
): Double = txs.filter {
    it.type == TransactionType.EXPENSE && !it.isSample &&
        it.dateTimestamp in win && it.dateTimestamp <= nowMs &&
        (category.equals("ALL", ignoreCase = true) || it.category.equals(category, ignoreCase = true))
}.sumOf { it.amount }

// Prorated pace: spend judged against time elapsed, not just the hard cap.
// 80% used on day 2 is an emergency; 80% used on day 28 is a steady month.
// Pure, unit-tested.
enum class BudgetPace { SAFE, ON_TRACK, AT_RISK, EXCEEDED }

fun evaluateCategoryPace(
    actualSpent: Double,
    monthlyBudget: Double,
    currentDay: Int,
    totalDaysInMonth: Int
): BudgetPace {
    if (monthlyBudget <= 0) return BudgetPace.SAFE
    if (actualSpent > monthlyBudget) return BudgetPace.EXCEEDED
    val expected = monthlyBudget * (currentDay.coerceAtLeast(1).toDouble() / totalDaysInMonth.coerceAtLeast(1))
    val ratio = actualSpent / expected.coerceAtLeast(1.0)
    return when {
        ratio <= 1.0 -> BudgetPace.SAFE
        ratio <= 1.25 -> BudgetPace.ON_TRACK
        else -> BudgetPace.AT_RISK
    }
}

// Hero verdict copy: over-cap always warns; the 80% klaxon fires only when
// pace is hot (or off the Monthly tab, where pace doesn't apply). Pure,
// unit-tested — every screen showing a budget verdict must call this.
fun periodVerdict(tab: String, spent: Double, target: Double, dom: Int, dim: Int): String {
    if (target <= 0) return ""
    val pct = (spent / target * 100).toInt()
    val pace = evaluateCategoryPace(spent, target, dom, dim)
    val hot = pace != BudgetPace.SAFE && pace != BudgetPace.ON_TRACK
    return when {
        spent > target -> "Over $tab budget by KSh ${(spent - target).toInt()} ($pct%) — essentials only. 🛑"
        pct >= 80 && (tab != "Monthly" || hot) -> "$pct% used — KSh ${(target - spent).toInt()} left. Slow down. ⚠️"
        pct >= 80 -> "$pct% used — KSh ${(target - spent).toInt()} left, still within pace. 👌"
        else -> "$pct% used — KSh ${(target - spent).toInt()} left. On track 👌."
    }
}

/**
 * Budgets-screen tab mapping: which budget type, which live window, which
 * human label. Weekly means the calendar week everywhere in the app —
 * never a drifting rolling 7 days.
 */
fun budgetTabWindow(tab: String, now: Long): Triple<BudgetType, TimeRange, String> {
    return when (tab) {
        "Daily" -> Triple(BudgetType.DAILY, todayRange(now), "today")
        "Weekly" -> Triple(BudgetType.WEEKLY, thisWeekRange(now), "this week")
        "Semester" -> Triple(
            BudgetType.SEMESTER,
            TimeRange(addDays(startOfDay(now), -120), addDays(startOfDay(now), 1)),
            "last 120 days"
        )
        else -> Triple(BudgetType.MONTHLY, monthRange(now), "this month")
    }
}
