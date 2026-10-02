package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.Regime
import com.pesaflow.app.data.parsers.SessionWindow
import com.pesaflow.app.data.parsers.detectBreakMonths
import com.pesaflow.app.data.parsers.monthlyTransportSeries
import com.pesaflow.app.data.parsers.resolveCalendar
import com.pesaflow.app.data.parsers.summarizeByRegime
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


/** Session windows, break detection, and regime summaries. Pure JVM. */
class SessionWindowsTest {

    private fun ms(y: Int, m: Int, d: Int, h: Int = 12): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m - 1)
            set(Calendar.DAY_OF_MONTH, d)
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun tx(amount: Double, type: TransactionType, category: String, ts: Long, merchant: String = "M"): PendingTransaction {
        return PendingTransaction(
            amount = amount,
            type = type,
            category = category,
            merchant = merchant,
            dateTimestamp = ts,
            paymentMethod = PaymentMethod.MPESA,
            source = TransactionSource.MPESA_SMS,
            sourceTransactionId = null,
            rawText = ""
        )
    }

    @Test
    fun `continuing student gets prior year plus break plus pre-uni`() {
        val cal = resolveCalendar(ms(2026, 9, 1), ms(2027, 4, 30), now = ms(2026, 10, 1), firstYear = false)
        assertEquals(Regime.SESSION, cal.regimeOf(ms(2026, 10, 15)))
        assertEquals(Regime.SESSION, cal.regimeOf(ms(2027, 2, 10)))
        assertEquals(Regime.SESSION, cal.regimeOf(ms(2025, 10, 15)))
        assertEquals(Regime.BREAK, cal.regimeOf(ms(2026, 6, 15)))
        assertEquals(Regime.BREAK, cal.regimeOf(ms(2027, 6, 15)))
        assertEquals(Regime.PRE_UNI, cal.regimeOf(ms(2025, 6, 15)))
    }

    @Test
    fun `first year has no prior session history`() {
        val cal = resolveCalendar(ms(2026, 9, 1), ms(2027, 4, 30), now = ms(2026, 10, 1), firstYear = true)
        assertEquals(Regime.SESSION, cal.regimeOf(ms(2026, 10, 15)))
        assertEquals(Regime.PRE_UNI, cal.regimeOf(ms(2026, 6, 15)))
        assertEquals(Regime.PRE_UNI, cal.regimeOf(ms(2025, 10, 15)))
    }

    @Test
    fun `attachment overrides session`() {
        val cal = resolveCalendar(
            ms(2026, 9, 1), ms(2027, 4, 30), now = ms(2026, 10, 1), firstYear = false,
            attachmentSpans = listOf(SessionWindow(ms(2026, 5, 4), ms(2026, 7, 31)))
        )
        assertEquals(Regime.ATTACHMENT, cal.regimeOf(ms(2026, 6, 15)))
        assertEquals(Regime.SESSION, cal.regimeOf(ms(2026, 10, 15)))
    }

    @Test
    fun `empty dates degrade to pre-uni never session`() {
        val cal = resolveCalendar(0L, 0L)
        assertEquals(Regime.PRE_UNI, cal.regimeOf(ms(2026, 10, 15)))
        assertTrue(cal.sessionSpans.isEmpty())
    }

    @Test
    fun `collapse detector finds break months only`() {
        val series = mapOf(
            "2026-09" to 2000.0, "2026-10" to 2100.0, "2026-11" to 1900.0,
            "2026-12" to 1800.0, "2027-01" to 2200.0, "2027-02" to 2000.0,
            "2026-06" to 100.0, "2026-07" to 50.0
        )
        assertEquals(setOf("2026-06", "2026-07"), detectBreakMonths(series))
    }

    @Test
    fun `flat series and thin data propose nothing`() {
        assertTrue(detectBreakMonths(mapOf("2026-09" to 2000.0, "2026-10" to 2100.0, "2026-11" to 1900.0)).isEmpty())
        assertTrue(detectBreakMonths(mapOf("2026-09" to 2000.0, "2026-10" to 100.0)).isEmpty())
        assertTrue(detectBreakMonths(mapOf("2026-09" to 0.0, "2026-10" to 0.0, "2026-11" to 0.0)).isEmpty())
    }

    @Test
    fun `summary paces session months per-day and quarantines break`() {
        val rows = listOf(
            tx(1000.0, TransactionType.EXPENSE, "Transport", ms(2026, 10, 1)),
            tx(1000.0, TransactionType.EXPENSE, "Transport", ms(2026, 10, 11)),
            tx(5000.0, TransactionType.INCOME, "Salary", ms(2026, 10, 5)),
            tx(9000.0, TransactionType.EXPENSE, "Food", ms(2026, 6, 15))
        )
        val cal = resolveCalendar(ms(2026, 9, 1), ms(2027, 4, 30), now = ms(2026, 10, 15), firstYear = false)
        val summary = summarizeByRegime(rows) { cal.regimeOf(it) }
        val session = summary[Regime.SESSION]!!
        assertEquals(2000.0, session.expense, 0.001)
        assertEquals(5000.0, session.income, 0.001)
        // 11-day span -> per-day 181.8 -> monthly pace 5454.5; break 9000 excluded.
        assertEquals(2000.0 / 11 * 30, session.monthlyPace, 0.01)
        assertEquals(9000.0, summary[Regime.BREAK]!!.expense, 0.001)
        assertTrue(summary[Regime.ATTACHMENT]!!.txCount == 0)
    }

    @Test
    fun `transport series groups expense transport by month`() {        val rows = listOf(
            tx(500.0, TransactionType.EXPENSE, "Transport", ms(2026, 10, 1)),
            tx(700.0, TransactionType.EXPENSE, "Transport", ms(2026, 10, 20)),
            tx(200.0, TransactionType.EXPENSE, "Food", ms(2026, 10, 5)),
            tx(999.0, TransactionType.INCOME, "Salary", ms(2026, 10, 5))
        )
        val series = monthlyTransportSeries(rows)
        assertTrue(series.size == 1)
        assertEquals(1200.0, series["2026-10"]!!, 0.001)
    }
}
