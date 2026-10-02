package com.pesaflow.app.parsers

import com.pesaflow.app.data.analytics.paydaySplurge
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.LedgerRow
import org.junit.Assert.*
import org.junit.Test


class SpendTimingTest {

    private val day = 24L * 60 * 60 * 1000
    private val base = 1_700_000_000_000L

    private fun row(amount: Double, type: TransactionType, daysAgo: Long) =
        LedgerRow(amount, type, "Food", "M", base - daysAgo * day)

    @Test
    fun `needs at least two paydays`() {
        assertNull(paydaySplurge(listOf(row(10000.0, TransactionType.INCOME, 10))))
        assertNull(paydaySplurge(emptyList()))
    }

    @Test
    fun `onboarding equity is not a payday`() {
        // Pocket + upkeep seeded together used to fake a 2-payday splurge in
        // every onboarding month. Opening rows must never count.
        val rows = listOf(
            LedgerRow(5000.0, TransactionType.INCOME, "Income", "Opening balance", base - 10 * day, isOpening = true),
            LedgerRow(3000.0, TransactionType.INCOME, "Income", "Monthly upkeep", base - 10 * day, isOpening = true),
            row(10000.0, TransactionType.INCOME, 40),
            row(9000.0, TransactionType.EXPENSE, 39)
        )
        val r = paydaySplurge(rows)
        assertTrue(r == null || r.paydays == 1)
    }

    @Test
    fun `instant splurger reads near one hundred percent`() {
        val rows = listOf(
            row(10000.0, TransactionType.INCOME, 40),
            row(9000.0, TransactionType.EXPENSE, 39),
            row(10000.0, TransactionType.INCOME, 10),
            row(9500.0, TransactionType.EXPENSE, 9)
        )
        val r = paydaySplurge(rows)!!
        assertEquals(2, r.paydays)
        assertTrue(r.avgPctSpent3d >= 85)
        assertTrue(r.avgPctSpent7d >= 85)
    }

    @Test
    fun `slow burner reads low`() {
        val rows = listOf(
            row(10000.0, TransactionType.INCOME, 40),
            row(1000.0, TransactionType.EXPENSE, 39),
            row(1000.0, TransactionType.EXPENSE, 20),
            row(10000.0, TransactionType.INCOME, 10),
            row(1000.0, TransactionType.EXPENSE, 9)
        )
        val r = paydaySplurge(rows)!!
        assertTrue(r.avgPctSpent7d <= 25)
    }

    @Test
    fun `spending belongs to the most recent payday`() {
        // Expense on day 25 sits between payday-40 and payday-10: only payday-10 owns it.
        val rows = listOf(
            row(10000.0, TransactionType.INCOME, 40),
            row(5000.0, TransactionType.EXPENSE, 25),
            row(10000.0, TransactionType.INCOME, 10)
        )
        val r = paydaySplurge(rows)!!
        // Payday-40: 0% in 7d. Payday-10: 50% in 7d (5000 on day 25 is 15d out → 0%).
        assertEquals(0, r.avgPctSpent7d)
    }
}
