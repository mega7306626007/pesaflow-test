package com.pesaflow.app.parsers

import com.pesaflow.app.data.finance.Money
import com.pesaflow.app.data.finance.dailyPaces
import com.pesaflow.app.data.finance.projectMonthEnd
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import org.junit.Assert.*
import org.junit.Test


// Forecast engine: robust paces, quarantine, projection math.
class ForecastEngineTest {

    private fun expense(amount: Double, daysAgo: Int) = Transaction(
        amount = amount, type = TransactionType.EXPENSE, category = "Food",
        dateTimestamp = System.currentTimeMillis() - daysAgo * 24L * 60 * 60 * 1000,
        merchant = "Test", description = "", paymentMethod = PaymentMethod.MPESA,
        source = TransactionSource.MANUAL
    )

    @Test
    fun `spike is quarantined from all paces`() {
        val txs = (1..20).map { expense(400.0 + it * 10, it) } + expense(25000.0, 3)
        val p = dailyPaces(txs)
        assertEquals(20, p.activeDays)
        assertTrue(p.typical in 300.0..700.0)
        assertTrue(p.cautious < 5000.0)
        assertTrue(p.favourable <= p.typical)
        assertTrue(p.typical <= p.cautious)
    }

    @Test
    fun `empty history yields zeros`() {
        val p = dailyPaces(emptyList())
        assertEquals(0.0, p.typical, 0.0)
        assertEquals(0.0, p.cautious, 0.0)
        assertEquals(0.0, p.favourable, 0.0)
        assertEquals(0, p.activeDays)
    }

    @Test
    fun `future expenses do not distort observed daily pace`() {
        val now = System.currentTimeMillis()
        val p = dailyPaces(
            listOf(expense(250.0, 0), expense(20_000.0, -1)),
            nowMs = now
        )

        assertEquals(1, p.activeDays)
        assertEquals(250.0, p.typical, 0.0)
    }

    @Test
    fun `projection subtracts pace bills from flexible`() {
        val end = projectMonthEnd(Money.of(10000.0), 500.0, Money.ZERO, Money.of(1000.0), 10)
        assertEquals(Money.of(4000.0), end)
    }

    @Test
    fun `projection never goes negative`() {
        val end = projectMonthEnd(Money.of(1000.0), 500.0, Money.ZERO, Money.of(1000.0), 10)
        assertEquals(Money.ZERO, end)
    }
}
