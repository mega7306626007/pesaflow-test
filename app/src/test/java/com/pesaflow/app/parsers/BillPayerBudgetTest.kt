package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.ui.budgets.monthlyBillReserve
import com.pesaflow.app.ui.dashboard.reserveBillDaily
import org.junit.Assert.assertEquals
import org.junit.Test

class BillPayerBudgetTest {
    private val now = 1_700_000_000_000L
    private val dueSoon = now + 10L * 24 * 60 * 60 * 1000

    @Test
    fun externallyFundedBillsDoNotReduceTheUserBudget() {
        val bill = Bill(
            name = "Semester fees",
            amount = 1_000.0,
            dueDate = dueSoon,
            category = "School",
            paidBy = "HELB"
        )

        assertEquals(0, reserveBillDaily(listOf(bill), now))
        assertEquals(0, monthlyBillReserve(bill, now))
    }

    @Test
    fun userFundedBillsContinueToReserveMoney() {
        val bill = Bill(
            name = "Rent",
            amount = 1_000.0,
            dueDate = dueSoon,
            category = "Rent"
        )

        assertEquals(33, reserveBillDaily(listOf(bill), now))
        assertEquals(1_000, monthlyBillReserve(bill, now))
    }
}
