package com.pesaflow.app.parsers

import com.pesaflow.app.data.finance.matchBillPayments
import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import org.junit.Assert.*
import org.junit.Test


/** Auto-fulfillment: confirmed rows link to open bills, or stay suggestions. */
class BillMatchingTest {

    private val day = 24 * 3_600_000L
    private val due = 1_757_000_000_000L

    private fun bill(name: String, amount: Double, category: String, status: String = "UNPAID") =
        Bill(name = name, amount = amount, dueDate = due, category = category, status = status)

    private fun expense(merchant: String, amount: Double, category: String, at: Long, sample: Boolean = false) =
        Transaction(
            amount = amount, type = TransactionType.EXPENSE, category = category,
            dateTimestamp = at, merchant = merchant, description = "",
            isSample = sample
        )

    @Test
    fun `caretaker row settles rent by category`() {
        val bills = listOf(bill("Rent", 8000.0, "Rent"))
        val txs = listOf(expense("Hostel Caretaker", 8000.0, "Rent", due - day))
        val out = matchBillPayments(bills, txs, due)
        assertEquals(1, out.size)
        assertEquals("Rent", out.single().bill.name)
        assertTrue(out.single().score >= 0.55)
    }

    @Test
    fun `name tokens match without category help`() {
        val bills = listOf(bill("KPLC tokens", 500.0, "Utilities"))
        val txs = listOf(expense("KPLC PREPAID", 500.0, "Other", due - day))
        assertEquals(1, matchBillPayments(bills, txs, due).size)
    }

    @Test
    fun `wrong amount never links`() {
        val bills = listOf(bill("Rent", 8000.0, "Rent"))
        val txs = listOf(expense("Hostel Caretaker", 7500.0, "Rent", due - day))
        assertTrue(matchBillPayments(bills, txs, due).isEmpty())
    }

    @Test
    fun `amount plus date alone is not enough`() {
        // Groceries at the exact rent figure, right on time — no name
        // evidence, so it must NOT auto-link (score caps at 0.5).
        val bills = listOf(bill("Rent", 8000.0, "Rent"))
        val txs = listOf(expense("Naivas", 8000.0, "Food", due - day))
        assertTrue(matchBillPayments(bills, txs, due).isEmpty())
    }

    @Test
    fun `stale and future rows ignored`() {
        val bills = listOf(bill("Rent", 8000.0, "Rent"))
        val txs = listOf(
            expense("Hostel Caretaker", 8000.0, "Rent", due - 30 * day),
            expense("Hostel Caretaker", 8000.0, "Rent", due + 10 * day)
        )
        assertTrue(matchBillPayments(bills, txs, due).isEmpty())
    }

    @Test
    fun `paid bills samples and income never match`() {
        val bills = listOf(bill("Rent", 8000.0, "Rent", status = "PAID"))
        val txs = listOf(
            expense("Hostel Caretaker", 8000.0, "Rent", due - day, sample = true),
            Transaction(
                amount = 8000.0, type = TransactionType.INCOME, category = "Rent",
                dateTimestamp = due - day, merchant = "Hostel Caretaker", description = ""
            )
        )
        assertTrue(matchBillPayments(bills, txs, due).isEmpty())
        assertTrue(matchBillPayments(emptyList(), txs, due).isEmpty())
    }

    @Test
    fun `exact name match clears the auto-link bar`() {
        // Auto-link needs ≥0.85: full name overlap + exact amount + on date.
        val bills = listOf(bill("KPLC tokens", 500.0, "Utilities"))
        val txs = listOf(expense("KPLC tokens", 500.0, "Utilities", due))
        val out = matchBillPayments(bills, txs, due)
        assertEquals(1, out.size)
        assertTrue(out.single().score >= 0.85)
    }

    @Test
    fun `category-only evidence stays suggest-only`() {
        // Caretaker rent scores ~0.74: suggested on the Bills card, never
        // auto-linked. Guesses must not eat rows.
        val bills = listOf(bill("Rent", 8000.0, "Rent"))
        val txs = listOf(expense("Hostel Caretaker", 8000.0, "Rent", due - day))
        val out = matchBillPayments(bills, txs, due)
        assertEquals(1, out.size)
        assertTrue(out.single().score < 0.85)
    }

    @Test
    fun `one row pays one bill best score wins`() {
        val bills = listOf(
            bill("Rent August", 8000.0, "Rent"),
            bill("Rent September", 8000.0, "Rent")
        )
        val txs = listOf(expense("Hostel Caretaker Sept", 8000.0, "Rent", due - day))
        val out = matchBillPayments(bills, txs, due)
        assertEquals(1, out.size)
    }
}
