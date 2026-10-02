package com.pesaflow.app.parsers

import com.pesaflow.app.data.finance.calculateSemesterRunway
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemesterRunwayTest {

    private fun tx(
        amount: Double,
        type: TransactionType,
        timestamp: Long,
        opening: Boolean = false,
        sample: Boolean = false
    ) = Transaction(
        amount = amount,
        type = type,
        category = "Test",
        dateTimestamp = timestamp,
        merchant = "Test",
        description = "",
        paymentMethod = PaymentMethod.CASH,
        source = TransactionSource.MANUAL,
        isOpening = opening,
        isSample = sample
    )

    @Test
    fun `opening cash wins over duplicate profile funding and all outflows count`() {
        val day = 24L * 60 * 60 * 1000
        val now = 100L * day
        val start = now - 10 * day
        val end = now + 10 * day
        val rows = listOf(
            tx(5000.0, TransactionType.INCOME, start, opening = true),
            tx(3000.0, TransactionType.INCOME, start + 1, opening = true),
            tx(20000.0, TransactionType.INCOME, start + 2),
            tx(3000.0, TransactionType.EXPENSE, start + 3),
            tx(2000.0, TransactionType.SAVING, start + 4),
            tx(1000.0, TransactionType.INVESTMENT, start + 5),
            tx(9000.0, TransactionType.EXPENSE, end),
            tx(8000.0, TransactionType.INCOME, now + 1)
        )

        val runway = calculateSemesterRunway(
            transactions = rows,
            startTimestamp = start,
            endTimestamp = end,
            startingFunding = 5000.0,
            committed = 4000.0,
            now = now
        )!!

        assertEquals(8000.0, runway.openingFunds, 0.001)
        assertEquals(20000.0, runway.income, 0.001)
        assertEquals(6000.0, runway.outflows, 0.001)
        assertEquals(22000.0, runway.remainingBeforeCommitments, 0.001)
        assertEquals(18000.0, runway.availableAfterCommitments, 0.001)
        assertEquals(600.0, runway.dailyPace, 0.001)
        assertEquals(9000.0, runway.weeklyAllowance, 0.001)
        assertEquals(12000.0, runway.projectedEndAfterCommitments, 0.001)
    }

    @Test
    fun `manual funding is used when no opening row exists`() {
        val day = 24L * 60 * 60 * 1000
        val now = 100L * day
        val runway = calculateSemesterRunway(
            transactions = emptyList(),
            startTimestamp = now - day,
            endTimestamp = now + 6 * day,
            startingFunding = 5000.0,
            committed = 0.0,
            now = now
        )!!

        assertEquals(5000.0, runway.openingFunds, 0.001)
        assertEquals(5000.0, runway.remainingBeforeCommitments, 0.001)
        assertFalse(runway.isUpcoming)
    }

    @Test
    fun `invalid or absent term dates do not produce a forecast`() {
        assertNull(calculateSemesterRunway(emptyList(), 0L, 100L, 0.0, 0.0, 50L))
        assertNull(calculateSemesterRunway(emptyList(), 100L, 100L, 0.0, 0.0, 50L))
    }

    @Test
    fun `upcoming and ended terms are explicitly identified`() {
        val day = 24L * 60 * 60 * 1000
        val upcoming = calculateSemesterRunway(
            emptyList(), 110 * day, 130 * day, 1000.0, 0.0, 100 * day
        )!!
        val ended = calculateSemesterRunway(
            emptyList(), 70 * day, 90 * day, 1000.0, 0.0, 100 * day
        )!!

        assertTrue(upcoming.isUpcoming)
        assertEquals(10, upcoming.daysUntilStart)
        assertEquals(20, upcoming.daysRemaining)
        assertTrue(ended.isEnded)
        assertEquals(0, ended.daysRemaining)
    }
}
