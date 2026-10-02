package com.pesaflow.app.data.finance

import com.pesaflow.app.data.analytics.RecurringPattern
import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.data.time.addDays
import com.pesaflow.app.data.time.startOfDay

// Forward cash flow: the app's eyes forward. Given where money lands
// (predicted paydays), where it must go (open bills, monthly commitments)
// and today's balance, project daily balances ahead — the low point and
// the broke date, not just the rear-view. Pure Kotlin, fully unit-tested.

data class CashFlowEvent(
    val label: String,
    /** +income / −outgoing. */
    val amount: Double
)

data class DailyPoint(
    val dayStart: Long,
    val balance: Double,
    val events: List<CashFlowEvent>
)

data class CashFlowProjection(
    val days: List<DailyPoint>,
    val lowest: DailyPoint,
    /** First day the balance goes negative, null when it stays positive. */
    val brokeDate: Long?,
    /** Balance at the end of the horizon. */
    val endBalance: Double
)

/**
 * Project daily balances [horizonDays] out from [now].
 *
 * Inputs:
 * - balance: money held right now (ledger balance)
 * - paydays: (label, amount, next landing day-start) from predictPaydays
 * - bills: open bills — each lands on its dueDate
 * - recurring: monthly commitments (25-35 day patterns) — each lands on its
 *   nextExpectedDate
 * - dailyBurn: optional average daily spend, subtracted every day. Zero by
 *   default — with it on, the projection is a burn model; with it off, it is
 *   a commitments-only model (income vs known outflows).
 */
fun projectCashFlow(
    balance: Double,
    now: Long,
    horizonDays: Int = 30,
    paydays: List<Triple<String, Double, Long>> = emptyList(),
    bills: List<Bill> = emptyList(),
    recurring: List<RecurringPattern> = emptyList(),
    dailyBurn: Double = 0.0
): CashFlowProjection {
    val todayStart = startOfDay(now)
    val eventsByDay = mutableMapOf<Long, MutableList<CashFlowEvent>>()

    fun addEvent(dayStart: Long, label: String, amount: Double) {
        if (dayStart < todayStart || dayStart >= todayStart + horizonDays.toLong() * 24 * 60 * 60 * 1000) return
        eventsByDay.getOrPut(dayStart) { mutableListOf() }.add(CashFlowEvent(label, amount))
    }

    paydays.forEach { (label, amount, next) -> addEvent(startOfDay(next), label, amount) }
    bills.filter { it.status != "PAID" && it.paidBy == "ME" }
        .forEach { b -> addEvent(startOfDay(b.dueDate), b.name, -b.amount) }
    recurring.filter { it.medianIntervalDays in 25..35 }.forEach { r ->
        addEvent(startOfDay(r.nextExpectedDate), r.merchant, -r.amount)
    }

    val days = mutableListOf<DailyPoint>()
    var running = balance
    var lowest = Double.MAX_VALUE
    var lowestDay: DailyPoint? = null
    var brokeDate: Long? = null
    for (d in 0 until horizonDays) {
        val dayStart = addDays(todayStart, d)
        val events = eventsByDay[dayStart].orEmpty()
        events.forEach { running += it.amount }
        if (dailyBurn > 0) running -= dailyBurn
        val point = DailyPoint(dayStart, running, events)
        days.add(point)
        if (running < lowest) {
            lowest = running
            lowestDay = point
        }
        if (brokeDate == null && running < 0) brokeDate = dayStart
    }
    return CashFlowProjection(
        days = days,
        lowest = lowestDay ?: DailyPoint(todayStart, balance, emptyList()),
        brokeDate = brokeDate,
        endBalance = running
    )
}
