package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.DEDUCTION_BAR
import com.pesaflow.app.data.parsers.HypothesisKind
import com.pesaflow.app.data.parsers.LedgerRow
import com.pesaflow.app.data.parsers.buildDraft
import com.pesaflow.app.data.parsers.deduceFare
import com.pesaflow.app.data.parsers.deduceRecurring
import com.pesaflow.app.data.parsers.deduceRent
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


/** Rhythm engines: fares, rent anchors, recurrences. Pure JVM. */
class DeductionsTest {

    private fun ms(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m - 1)
            set(Calendar.DAY_OF_MONTH, d)
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun row(amount: Double, category: String, ts: Long, merchant: String = "M"): LedgerRow {
        return LedgerRow(amount, TransactionType.EXPENSE, category, merchant, ts)
    }

    @Test
    fun `fare rhythm surfaces with class-day evidence`() {
        val days = listOf(1, 2, 3, 4, 7, 8, 9, 10)
        val rows = days.map { row(50.0, "Transport", ms(2026, 9, it, 8, 12), "Stage 46") }
        val d = deduceFare(rows, isClassDay = { true })!!
        assertEquals(HypothesisKind.FARE, d.kind)
        assertEquals(50.0, d.amount, 0.01)
        assertTrue("conf ${d.confidence} should clear the bar", d.confidence >= DEDUCTION_BAR)
        assertTrue(d.evidence.contains("8 of 8"))
    }

    @Test
    fun `fare stays silent without class-day support`() {
        val rows = (1..8).map { row(50.0, "Transport", ms(2026, 9, it, 8, 12)) }
        assertNull(deduceFare(rows, isClassDay = { false }))
        assertNull(deduceFare(rows.take(2), isClassDay = { true }))
    }

    @Test
    fun `hiked peak fares ride the same rhythm`() {
        // 70s with 100s mixed in: one school run, ±50 tolerance holds.
        val days = listOf(1, 2, 3, 4, 7, 8, 9, 10)
        val rows = days.mapIndexed { i, d ->
            row(if (i % 3 == 2) 100.0 else 70.0, "Transport", ms(2026, 9, d, 8, 12), "Stage 46")
        }
        val f = deduceFare(rows, isClassDay = { true })!!
        assertTrue(f.amount in 70.0..100.0)
        assertTrue(f.evidence.contains("70-100"))
        assertTrue(f.confidence >= DEDUCTION_BAR)
    }

    @Test
    fun `late morning classes still count`() {
        val rows = (1..5).map { row(60.0, "Transport", ms(2026, 9, it, 11, 45), "Stage 46") }
        val f = deduceFare(rows, isClassDay = { true })
        assertNotNull(f)
    }

    @Test
    fun `configured class window accepts afternoon commute and rejects unrelated rides`() {
        val commute = listOf(1, 2, 3, 4, 7, 8, 9, 10)
            .map { row(100.0, "Transport", ms(2026, 10, it, 13, 30), "Stage 46") }
        val unrelated = listOf(11, 12, 13, 14)
            .map { row(100.0, "Transport", ms(2026, 10, it, 8, 30), "Stage 46") }
        val result = deduceFare(
            commute + unrelated,
            isClassDay = { true },
            isClassTime = { ts ->
                Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.HOUR_OF_DAY) in 13..19
            }
        )
        assertNotNull(result)
        assertTrue(result!!.evidence.contains("8 of 8"))
    }

    @Test
    fun `two different lives stay silent`() {
        // 50s and 200s equally: no dominant run, spread kills confidence.
        val rows = (1..5).map { row(50.0, "Transport", ms(2026, 9, it, 8, 0)) } +
            (11..15).map { row(200.0, "Transport", ms(2026, 9, it, 8, 0)) }
        assertNull(deduceFare(rows, isClassDay = { true }))
    }

    @Test
    fun `rent anchor needs two months same date`() {
        val rows = listOf(
            row(15000.0, "Rent", ms(2026, 9, 3), "John Apartments"),
            row(15000.0, "Rent", ms(2026, 10, 3), "John Apartments"),
            row(15200.0, "Rent", ms(2026, 11, 3), "John Apartments")
        )
        val d = deduceRent(rows)!!
        assertEquals(HypothesisKind.RENT, d.kind)
        assertEquals(15066.0, d.amount, 1.0)
        assertTrue(d.confidence >= DEDUCTION_BAR)
        assertTrue(d.evidence.contains("3 months"))
    }

    @Test
    fun `rent stays silent on single month or drifting amounts`() {
        val oneMonth = listOf(
            row(15000.0, "Rent", ms(2026, 9, 3)),
            row(15000.0, "Rent", ms(2026, 9, 17))
        )
        assertNull(deduceRent(oneMonth))
        val drift = listOf(
            row(10000.0, "Rent", ms(2026, 9, 3)),
            row(15000.0, "Rent", ms(2026, 10, 3))
        )
        assertNull(deduceRent(drift))
    }

    @Test
    fun `monthly recurrence surfaces weekly does not`() {
        val monthly = listOf(
            row(1000.0, "Bills", ms(2026, 9, 5), "Zuku"),
            row(1000.0, "Bills", ms(2026, 10, 5), "Zuku"),
            row(1000.0, "Bills", ms(2026, 11, 4), "Zuku")
        )
        val found = deduceRecurring(monthly)
        assertEquals(1, found.size)
        assertEquals(HypothesisKind.RECURRING, found[0].kind)
        assertTrue(found[0].evidence.contains("30 days"))
        val weekly = (0..3).map { row(200.0, "Food", ms(2026, 9, 1 + it * 7), "Kibanda") }
        assertTrue(deduceRecurring(weekly).isEmpty())
    }

    @Test
    fun `draft prefills transport and rent from confident deductions`() {
        val rows = (1..8).map { row(50.0, "Transport", ms(2026, 9, it, 8, 5), "Stage 46") } +
            listOf(
                row(15000.0, "Rent", ms(2026, 9, 3), "John Apartments"),
                row(15000.0, "Rent", ms(2026, 10, 3), "John Apartments"),
                row(1000.0, "Bills", ms(2026, 9, 5), "Zuku"),
                row(1000.0, "Bills", ms(2026, 10, 5), "Zuku")
            )
        val draft = buildDraft(rows, isClassDay = { true })
        assertEquals(50.0 * 22, draft.transportMonthly!!, 0.01)
        assertEquals(15000.0, draft.rentMonthly!!, 0.01)
        // Zuku subscription + rent anchor both recur monthly.
        assertEquals(2, draft.recurring.size)
        assertTrue(draft.recurring.any { it.merchant == "Zuku" })
    }

    @Test
    fun `draft stays empty when nothing clears the bar`() {
        val rows = listOf(row(50.0, "Transport", ms(2026, 9, 1, 8, 5)))
        val draft = buildDraft(rows, isClassDay = { false })
        assertNull(draft.transportMonthly)
        assertNull(draft.rentMonthly)
        assertTrue(draft.recurring.isEmpty())
    }
}
