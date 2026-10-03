package com.pesaflow.app.parsers

import com.pesaflow.app.data.income.IncomeSource
import com.pesaflow.app.data.income.IncomeSourceStore
import org.junit.Assert.*
import org.junit.Test


// Income math: monthly equivalents and budget inclusion are pure and pinned.
class IncomeSourceTest {

    @Test
    fun `monthly equivalents scale daily and weekly`() {
        assertEquals(3000.0, IncomeSource(kind = "HUSTLE", expectedAmount = 100.0, frequency = "DAILY").monthlyEquivalent(), 0.001)
        assertEquals(2000.0, IncomeSource(kind = "JOB", expectedAmount = 500.0, frequency = "WEEKLY").monthlyEquivalent(), 0.001)
        assertEquals(15000.0, IncomeSource(kind = "JOB", expectedAmount = 15000.0, frequency = "MONTHLY").monthlyEquivalent(), 0.001)
        assertEquals(40000.0, IncomeSource(kind = "HELB_MPESA", expectedAmount = 40000.0, frequency = "ONCE").monthlyEquivalent(), 0.001)
    }

    @Test
    fun `fuliza excluded unless explicitly opted in`() {
        assertEquals(0.0, IncomeSourceStore.budgetedMonthly(IncomeSource(kind = "FULIZA", expectedAmount = 2000.0)), 0.001)
        assertEquals(
            2000.0,
            IncomeSourceStore.budgetedMonthly(IncomeSource(kind = "FULIZA", expectedAmount = 2000.0, useInBudget = true)),
            0.001
        )
    }

    @Test
    fun `ordinary sources count at monthly equivalent`() {
        assertEquals(
            3000.0,
            IncomeSourceStore.budgetedMonthly(IncomeSource(kind = "HUSTLE", expectedAmount = 100.0, frequency = "DAILY")),
            0.001
        )
    }

    @Test
    fun `next inflow counts down to dated monthly sources`() {
        // Sep 13: HELB day 20 → 7 days; guardian day 5 → passed, wraps to Oct 5 (22 days).
        // Daily hustle counts as a 1-day fare rhythm, so the nearest horizon is 1.
        val now = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.SEPTEMBER, 13, 9, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sources = listOf(
            IncomeSource(kind = "HELB_MPESA", expectedAmount = 8080.0, frequency = "MONTHLY", dayOfMonth = 20),
            IncomeSource(kind = "GUARDIAN", expectedAmount = 5000.0, frequency = "MONTHLY", dayOfMonth = 5),
            IncomeSource(kind = "HUSTLE", expectedAmount = 100.0, frequency = "DAILY"),
            IncomeSource(kind = "JOB", expectedAmount = 15000.0, frequency = "MONTHLY", dayOfMonth = 0)
        )
        assertEquals(1, com.pesaflow.app.data.income.nextInflowDay(sources, now))
        assertEquals(
            7,
            com.pesaflow.app.data.income.nextInflowDay(sources.filter { it.frequency == "MONTHLY" }, now)
        )
    }

    @Test
    fun `next landing uses actual month and year boundaries`() {
        val now = java.util.Calendar.getInstance().apply {
            clear()
            set(2025, java.util.Calendar.DECEMBER, 31, 18, 0)
        }.timeInMillis
        val source = IncomeSource(kind = "PARENT", expectedAmount = 6000.0, frequency = "MONTHLY", dayOfMonth = 15)
        assertEquals(15, source.daysUntilLanding(now))
        val due = com.pesaflow.app.data.income.expectedIncomeLandings(listOf(source), now, 30).single()
        val dueDate = java.util.Calendar.getInstance().apply { timeInMillis = due.third }
        assertEquals(2026, dueDate.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.JANUARY, dueDate.get(java.util.Calendar.MONTH))
        assertEquals(15, dueDate.get(java.util.Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `monthly landing clamps to leap month length and excludes uncertain income`() {
        val now = java.util.Calendar.getInstance().apply {
            clear()
            set(2024, java.util.Calendar.FEBRUARY, 28, 11, 30)
        }.timeInMillis
        val clamped = IncomeSource(kind = "JOB", frequency = "MONTHLY", dayOfMonth = 31)
        assertEquals(1, clamped.daysUntilLanding(now))
        val forecast = com.pesaflow.app.data.income.expectedIncomeLandings(
            listOf(
                clamped.copy(expectedAmount = 8000.0),
                IncomeSource(kind = "HUSTLE", expectedAmount = 600.0, frequency = "WEEKLY"),
                IncomeSource(kind = "PARENT", expectedAmount = 9000.0, frequency = "MONTHLY", dayOfMonth = 0),
                IncomeSource(kind = "FULIZA", expectedAmount = 2000.0, frequency = "MONTHLY", dayOfMonth = 28)
            ),
            now,
            30
        )
        // 1 dated monthly landing + 4 weekly-rhythm events; uncertain + Fuliza excluded.
        assertEquals(5, forecast.size)
        assertEquals(8000.0, forecast.first().second, 0.001)
        val date = java.util.Calendar.getInstance().apply { timeInMillis = forecast.first().third }
        assertEquals(29, date.get(java.util.Calendar.DAY_OF_MONTH))
        assertTrue(forecast.drop(1).all { it.second == 600.0 })
    }

    @Test
    fun `no dated sources means no inflow horizon`() {
        val now = System.currentTimeMillis()
        assertNull(
            com.pesaflow.app.data.income.nextInflowDay(
                listOf(IncomeSource(kind = "HUSTLE", frequency = "DAILY")),
                now
            )
        )
        assertNull(com.pesaflow.app.data.income.nextInflowDay(emptyList(), now))
    }
}
