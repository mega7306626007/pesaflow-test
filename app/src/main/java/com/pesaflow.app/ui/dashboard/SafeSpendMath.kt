package com.pesaflow.app.ui.dashboard

import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.BudgetType

// Safe-to-spend day math, pure and unit-tested. Dynamic by construction:
// the target is remaining money over remaining days (never a frozen
// monthly/30), so it breathes with every transaction and every sunrise.
// Yesterday needs no separate rollover — it already sits inside spentMonth.
data class SafeDayFigure(
    /** Days left including today. */
    val daysLeft: Int,
    val spentMonth: Double,
    /** monthlyLimit - spentMonth - reserves. May be negative (over pace). */
    val remaining: Double,
    /** remaining / daysLeft, floored at 0. */
    val dailyTarget: Int,
    /** dailyTarget paced by the weekday factor. */
    val allowance: Int,
    /** allowance - todaySpend. May be negative (paused). */
    val left: Int
)

data class SafeSpendBudgetLimits(
    val monthly: Double?,
    val daily: Double?,
    val weekly: Double?
)

fun safeSpendBudgetLimits(
    budgets: List<Budget>,
    nowMs: Long = System.currentTimeMillis()
): SafeSpendBudgetLimits {
    fun amountFor(type: BudgetType): Double? {
        val scoped = budgets.filter { it.type == type && it.limitAmount > 0.0 }
        if (scoped.isEmpty()) return null
        // Active window wins (expired/future rows never cap today); legacy
        // rows with no usable window fall back to latest-row semantics so old
        // fixtures still read.
        val active = scoped.filter { com.pesaflow.app.data.finance.isBudgetActive(it, nowMs) }
        val pool = if (active.isNotEmpty()) active else scoped
        val period = pool
            .groupBy { it.category.trim().uppercase() }
            .values
            .mapNotNull { rows -> rows.maxByOrNull { it.startTimestamp } }
        val master = period
            .filter { it.category.equals("ALL", ignoreCase = true) }
            .maxByOrNull { it.startTimestamp }
        if (master != null) return master.limitAmount
        return period
            .filterNot { it.category.equals("ALL", ignoreCase = true) }
            .sumOf { it.limitAmount }
            .takeIf { it > 0.0 }
    }
    return SafeSpendBudgetLimits(
        monthly = amountFor(BudgetType.MONTHLY),
        daily = amountFor(BudgetType.DAILY),
        weekly = amountFor(BudgetType.WEEKLY)
    )
}

fun safeDayFigure(
    monthlyLimit: Double,
    spentMonth: Double,
    planDaily: Int,
    billDaily: Int,
    weekdayFactor: Double,
    todaySpend: Int,
    dayOfMonth: Int,
    daysInMonth: Int,
    flexibleCash: Double = Double.POSITIVE_INFINITY
): SafeDayFigure {
    val daysLeft = (daysInMonth - dayOfMonth + 1).coerceAtLeast(1)
    val reserve = (planDaily + billDaily) * daysLeft
    val remaining = monthlyLimit - spentMonth - reserve
    val budgetPace = (remaining / daysLeft).coerceAtLeast(0.0)
    val cashPace = (flexibleCash.coerceAtLeast(0.0) / daysLeft)
    val dailyTarget = minOf(budgetPace, cashPace).toInt()
    val allowance = (dailyTarget * weekdayFactor).toInt()
    return SafeDayFigure(daysLeft, spentMonth, remaining, dailyTarget, allowance, allowance - todaySpend)
}

fun buddySafeDaily(budgetRemaining: Double, freeBalance: Double, daysLeft: Int): Int {
    if (daysLeft <= 0) return 0
    return (minOf(budgetRemaining, freeBalance).coerceAtLeast(0.0) / daysLeft).toInt()
}


// Weekly allowance is exactly the weekly target — last week's balance is
// commentary for the verdict copy, never spendable math. Adding unspent weeks
// doubled the envelope ("left of KSh 2X this week") the same way monthly
// carry doubled the hero target. Pure, unit-tested.
fun weeklyAllowance(weekTarget: Int): Int = weekTarget.coerceAtLeast(0)


// Bills reserve: only bills due within the next 30 days (plus overdue ones
// already due) eat today's allowance. Charging every open bill ever — fees
// due in December included — is what made "bills eat 320% of budget" real.
// Pure, unit-tested.
fun reserveBillDaily(
    bills: List<com.pesaflow.app.data.models.Bill>,
    nowMs: Long = System.currentTimeMillis()
): Int {
    val horizon = nowMs + 30L * 24 * 60 * 60 * 1000
    val due = bills
        .filter { it.status != "PAID" && it.paidBy == "ME" && it.dueDate <= horizon }
        .sumOf { it.amountRemaining.takeIf { remaining -> remaining > 0.0 } ?: it.amount }
    return if (due > 0) (due / 30).toInt() else 0
}
