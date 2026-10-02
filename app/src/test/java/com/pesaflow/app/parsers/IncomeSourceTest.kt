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
        assertEquals(7, com.pesaflow.app.data.income.nextInflowDay(sources, now))
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
