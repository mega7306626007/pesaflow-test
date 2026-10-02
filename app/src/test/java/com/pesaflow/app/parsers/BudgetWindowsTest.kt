package com.pesaflow.app.parsers

import com.pesaflow.app.data.finance.BudgetPace
import com.pesaflow.app.data.finance.budgetTabWindow
import com.pesaflow.app.data.finance.budgetWindowRange
import com.pesaflow.app.data.finance.evaluateCategoryPace
import com.pesaflow.app.data.finance.masterOrCategoryTotal
import com.pesaflow.app.data.finance.periodVerdict
import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.BudgetType
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


/** Budget progress reads the current period — never stored windows. */
class BudgetWindowsTest {

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(y, m, d, h, min, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `monthly window excludes last month rows`() {
        // Sep 13: August spending must not pace September budgets.
        val now = at(2026, Calendar.SEPTEMBER, 13, 9, 0)
        val w = budgetWindowRange(BudgetType.MONTHLY, now)
        assertTrue(at(2026, Calendar.SEPTEMBER, 5) in w)
        assertFalse(at(2026, Calendar.AUGUST, 20) in w)
        assertFalse(at(2026, Calendar.OCTOBER, 2) in w)
    }

    @Test
    fun `weekly window is monday to monday`() {
        val now = at(2026, Calendar.SEPTEMBER, 9, 9, 0) // Wednesday
        val w = budgetWindowRange(BudgetType.WEEKLY, now)
        assertEquals(at(2026, Calendar.SEPTEMBER, 7, 0, 0), w.startInclusive)
        assertEquals(at(2026, Calendar.SEPTEMBER, 14, 0, 0), w.endExclusive)
    }

    @Test
    fun `daily window is today only`() {
        val now = at(2026, Calendar.SEPTEMBER, 9, 9, 0)
        val w = budgetWindowRange(BudgetType.DAILY, now)
        assertTrue(at(2026, Calendar.SEPTEMBER, 9, 23, 59) in w)
        assertFalse(at(2026, Calendar.SEPTEMBER, 8, 23, 59) in w)
    }

    @Test
    fun `semester uses the profile window when present`() {
        val now = at(2026, Calendar.OCTOBER, 10, 9, 0)
        val start = at(2026, Calendar.SEPTEMBER, 7, 0, 0)
        val end = at(2026, Calendar.DECEMBER, 20, 0, 0)
        val w = budgetWindowRange(BudgetType.SEMESTER, now, start, end)
        assertEquals(start, w.startInclusive)
        assertTrue(at(2026, Calendar.SEPTEMBER, 1) !in w)
        assertTrue(at(2026, Calendar.OCTOBER, 1) in w)
    }

@Test
    fun `semester without profile returns empty window`() {
        val now = at(2026, Calendar.OCTOBER, 10, 9, 0)
        val w = budgetWindowRange(BudgetType.SEMESTER, now)
        assertTrue(w.startInclusive >= w.endExclusive)
    }

    @Test
    fun `semester with explicit dates uses profile window`() {
        val now = at(2026, Calendar.OCTOBER, 10, 9, 0)
        val start = at(2026, Calendar.SEPTEMBER, 7, 0, 0)
        val end = at(2026, Calendar.DECEMBER, 20, 0, 0)
        val w = budgetWindowRange(BudgetType.SEMESTER, now, start, end)
        assertEquals(start, w.startInclusive)
        assertFalse(at(2026, Calendar.SEPTEMBER, 1) in w)
        assertTrue(at(2026, Calendar.OCTOBER, 1) in w)
        assertFalse(at(2026, Calendar.MAY, 1) in w)
    }

    @Test
    fun `annual window is the calendar year`() {
        val now = at(2026, Calendar.SEPTEMBER, 9, 9, 0)
        val w = budgetWindowRange(BudgetType.ANNUAL, now)
        assertTrue(at(2026, Calendar.MARCH, 3) in w)
        assertFalse(at(2025, Calendar.DECEMBER, 31) in w)
    }

    @Test
    fun `tab mapping uses calendar weeks not rolling days`() {
        // Wednesday Sep 9: the Weekly tab starts Monday Sep 7, not "6 days ago
        // at the current time" — mid-day boundaries sliced local dates.
        val now = at(2026, Calendar.SEPTEMBER, 9, 9, 0)
        val (type, win, label) = budgetTabWindow("Weekly", now)
        assertEquals(BudgetType.WEEKLY, type)
        assertEquals(at(2026, Calendar.SEPTEMBER, 7, 0, 0), win.startInclusive)
        assertEquals("this week", label)
        assertFalse(at(2026, Calendar.SEPTEMBER, 6, 23, 59) in win)
    }

    private fun budget(category: String, limit: Double, type: BudgetType = BudgetType.MONTHLY) =
        Budget(category = category, limitAmount = limit, type = type, startTimestamp = 0L, endTimestamp = 0L)

    @Test
    fun `master ALL wins over category breakdown`() {
        // Onboarding classic: ALL 20000 + Food 4000 + Rent 8000.
        // Summing both (32000) is what printed phantom "4%" readings.
        val bs = listOf(
            budget("ALL", 20000.0),
            budget("Food", 4000.0),
            budget("Rent", 8000.0)
        )
        assertEquals(20000.0, masterOrCategoryTotal(bs, BudgetType.MONTHLY), 0.001)
    }

    @Test
    fun `no master falls back to category sum`() {
        val bs = listOf(budget("Food", 4000.0), budget("Transport", 3000.0))
        assertEquals(7000.0, masterOrCategoryTotal(bs, BudgetType.MONTHLY), 0.001)
    }

    @Test
    fun `master match is case-insensitive and type-scoped`() {
        val bs = listOf(
            budget("all", 15000.0),
            budget("Food", 4000.0, BudgetType.WEEKLY)
        )
        assertEquals(15000.0, masterOrCategoryTotal(bs, BudgetType.MONTHLY), 0.001)
        assertEquals(4000.0, masterOrCategoryTotal(bs, BudgetType.WEEKLY), 0.001)
    }

    @Test
    fun `empty and non-positive budgets read zero`() {
        assertEquals(0.0, masterOrCategoryTotal(emptyList(), BudgetType.MONTHLY), 0.001)
        assertEquals(
            0.0,
            masterOrCategoryTotal(listOf(budget("Food", 0.0), budget("Rent", -500.0)), BudgetType.MONTHLY),
            0.001
        )
    }

    @Test
    fun `envelope spend scopes categories but not ALL`() {
        // Dashboard banners read total spend for every envelope — "Food
        // crossed 3793 of 1200" next to a healthy Food card. envelopeSpend
        // is the single rule every surface now shares.
        val now = at(2026, Calendar.SEPTEMBER, 13, 9, 0)
        val w = budgetWindowRange(BudgetType.MONTHLY, now)
        val txs = listOf(
            com.pesaflow.app.data.models.Transaction(amount = 3000.0, type = com.pesaflow.app.data.models.TransactionType.EXPENSE, category = "Food", dateTimestamp = at(2026, Calendar.SEPTEMBER, 5), merchant = "Kibanda", description = ""),
            com.pesaflow.app.data.models.Transaction(amount = 793.0, type = com.pesaflow.app.data.models.TransactionType.EXPENSE, category = "Transport", dateTimestamp = at(2026, Calendar.SEPTEMBER, 6), merchant = "Matatu", description = ""),
            com.pesaflow.app.data.models.Transaction(amount = 99999.0, type = com.pesaflow.app.data.models.TransactionType.EXPENSE, category = "Food", dateTimestamp = at(2026, Calendar.AUGUST, 1), merchant = "Old", description = ""),
            com.pesaflow.app.data.models.Transaction(amount = 500.0, type = com.pesaflow.app.data.models.TransactionType.EXPENSE, category = "Food", dateTimestamp = at(2026, Calendar.SEPTEMBER, 7), merchant = "Sample", description = "", isSample = true)
        )
        val nowMs = at(2026, Calendar.SEPTEMBER, 13, 9, 0)
        assertEquals(3000.0, com.pesaflow.app.data.finance.envelopeSpend(txs, "Food", w, nowMs), 0.001)
        assertEquals(3000.0, com.pesaflow.app.data.finance.envelopeSpend(txs, "food", w, nowMs), 0.001)
        assertEquals(3793.0, com.pesaflow.app.data.finance.envelopeSpend(txs, "ALL", w, nowMs), 0.001)
    }

    @Test
    fun `pace judges spend against elapsed time`() {
        // 100 of 1200 on day 2: 1.25x the day-2 expectation (80) — warm,
        // not an emergency. ON_TRACK, not a klaxon.
        assertEquals(
            BudgetPace.ON_TRACK,
            evaluateCategoryPace(100.0, 1200.0, 2, 30)
        )
        // 300 of 1200 on day 2 (25% in 7% of the month): 3.75x pace — hot
        // enough to blow the budget 3x over. AT_RISK is the honest read.
        assertEquals(
            BudgetPace.AT_RISK,
            evaluateCategoryPace(300.0, 1200.0, 2, 30)
        )
        // Same 300 on day 28: plainly safe.
        assertEquals(
            BudgetPace.SAFE,
            evaluateCategoryPace(300.0, 1200.0, 28, 30)
        )
        // 960 of 1200 on day 2: 12x the expected pace — hot.
        assertEquals(
            BudgetPace.AT_RISK,
            evaluateCategoryPace(960.0, 1200.0, 2, 30)
        )
        // Over the cap is over, whatever the day.
        assertEquals(
            BudgetPace.EXCEEDED,
            evaluateCategoryPace(1300.0, 1200.0, 28, 30)
        )
        // No budget, no verdict.
        assertEquals(
            BudgetPace.SAFE,
            evaluateCategoryPace(500.0, 0.0, 15, 30)
        )
    }

    @Test
    fun `verdict warns over cap and hot pace stays calm late`() {
        // Over cap always warns, whatever the day.
        assertTrue(periodVerdict("Monthly", 26000.0, 20000.0, 28, 30).contains("Over Monthly budget"))
        // 85% on day 28: steady month, no klaxon.
        val calm = periodVerdict("Monthly", 17000.0, 20000.0, 28, 30)
        assertTrue(calm.contains("still within pace"))
        assertFalse(calm.contains("Slow down"))
        // 85% on day 3: emergency.
        val hot = periodVerdict("Monthly", 17000.0, 20000.0, 3, 30)
        assertTrue(hot.contains("Slow down"))
        // Off-month tabs keep the plain cap read.
        assertTrue(periodVerdict("Weekly", 1700.0, 2000.0, 3, 30).contains("Slow down"))
        // No target, no verdict.
        assertEquals("", periodVerdict("Monthly", 100.0, 0.0, 15, 30))
    }

    @Test
    fun `tab mapping covers all tabs`() {
        val now = at(2026, Calendar.SEPTEMBER, 9, 9, 0)
        val (dType, dWin, dLabel) = budgetTabWindow("Daily", now)
        assertEquals(BudgetType.DAILY, dType)
        assertEquals("today", dLabel)
        assertFalse(at(2026, Calendar.SEPTEMBER, 8, 23, 59) in dWin)
        val (mType, mWin, mLabel) = budgetTabWindow("Monthly", now)
        assertEquals(BudgetType.MONTHLY, mType)
        assertEquals("this month", mLabel)
        assertEquals(at(2026, Calendar.SEPTEMBER, 1, 0, 0), mWin.startInclusive)
        val (sType, sWin, _) = budgetTabWindow("Semester", now)
        assertEquals(BudgetType.SEMESTER, sType)
        assertEquals(121, sWin.days().size)
        val (uType, _, _) = budgetTabWindow("SomethingElse", now)
        assertEquals(BudgetType.MONTHLY, uType)
    }
}
