package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.AppLanguage
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.dashboard.buildInsights
import com.pesaflow.app.ui.dashboard.monthEndForecast
import org.junit.Assert.*
import org.junit.Test


class SmartInsightsEngineTest {

    private fun tx(amount: Double, type: TransactionType, category: String) = Transaction(
        amount = amount,
        type = type,
        category = category,
        dateTimestamp = System.currentTimeMillis(),
        merchant = "Test",
        description = "",
        paymentMethod = PaymentMethod.CASH,
        source = TransactionSource.MANUAL
    )

    private fun monthTs(monthOffset: Int, day: Int): Long {
        val c = java.util.Calendar.getInstance()
        c.add(java.util.Calendar.MONTH, monthOffset)
        c.set(java.util.Calendar.DAY_OF_MONTH, minOf(day, c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)))
        c.set(java.util.Calendar.HOUR_OF_DAY, 12)
        c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun txAt(amount: Double, type: TransactionType, category: String, ts: Long) = Transaction(
        amount = amount,
        type = type,
        category = category,
        dateTimestamp = ts,
        merchant = "Test",
        description = "",
        paymentMethod = PaymentMethod.CASH,
        source = TransactionSource.MANUAL
    )

    @Test
    fun `opening equity never trips the overspend alarm`() {
        // Onboarding month: pocket 1000 seeded as an opening row, then 5000
        // spent. Opening is held cash, not earnings — judging pace against
        // zero earned income must stay silent, not scream "Danger".
        val opening = txAt(1000.0, TransactionType.INCOME, "Income", System.currentTimeMillis()).copy(
            merchant = "Opening balance",
            isOpening = true
        )
        val txs = listOf(
            opening,
            tx(2000.0, TransactionType.EXPENSE, "Food"),
            tx(2000.0, TransactionType.EXPENSE, "Transport"),
            tx(1000.0, TransactionType.EXPENSE, "Airtime")
        )
        val out = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList())
        assertTrue(out.none { it.contains("Danger") })
    }

    @Test
    fun `dust baseline gets absolutes never fantasy percents`() {
        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
        val txs = listOf(
            txAt(5000.0, TransactionType.EXPENSE, "Food", monthTs(0, today)),
            txAt(12.0, TransactionType.EXPENSE, "Food", monthTs(-1, today))
        )
        val out = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList())
        val mom = out.first { it.contains("last month") }
        assertTrue(mom.contains("vs KSh"))
        assertFalse(mom.contains("%"))
    }

    @Test
    fun `month comparison uses matching month to date window`() {
        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
        val previous = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.MONTH, -1)
            set(java.util.Calendar.DAY_OF_MONTH, minOf(today, getActualMaximum(java.util.Calendar.DAY_OF_MONTH)))
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val current = monthTs(0, today)
        val laterDay = today + 1
        val previousMonthMax = java.util.Calendar.getInstance().apply { timeInMillis = previous }
            .getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val beyondMonthToDate = java.util.Calendar.getInstance().apply {
            timeInMillis = previous
            set(java.util.Calendar.DAY_OF_MONTH, if (laterDay <= previousMonthMax) laterDay else previousMonthMax)
        }.timeInMillis
        val txs = listOf(
            txAt(50.0, TransactionType.EXPENSE, "Food", current),
            txAt(50.0, TransactionType.EXPENSE, "Food", previous),
            txAt(if (laterDay <= previousMonthMax) 20_000.0 else 0.0, TransactionType.EXPENSE, "Food", beyondMonthToDate)
        )
        val comparison = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList())
            .first { it.contains("same days last month") }
        assertTrue(comparison, comparison.contains("KSh 50 month-to-date vs KSh 50"))
    }

    @Test
    fun `watched categories report first`() {
        val txs = listOf(
            txAt(800.0, TransactionType.EXPENSE, "Food", monthTs(0, 5)),
            txAt(200.0, TransactionType.EXPENSE, "Transport", monthTs(0, 6))
        )
        val watched = buildInsights(
            txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList(),
            watched = setOf("food")
        )
        assertTrue(watched.any { it.contains("Watching food") && it.contains("KSh 800") })
        val unwatched = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList())
        assertTrue(unwatched.none { it.contains("Watching") })
    }

    @Test
    fun `other dominance nudges to review`() {
        val txs = listOf(
            txAt(4000.0, TransactionType.EXPENSE, "Other", monthTs(0, 5)),
            txAt(500.0, TransactionType.EXPENSE, "Transport", monthTs(0, 6))
        )
        val out = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList())
        assertTrue(out.any { it.contains("uncategorized") })
    }

    @Test
    fun `empty history returns single hint`() {
        val out = buildInsights(emptyList(), emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList())
        assertEquals(1, out.size)
        assertTrue(out[0].contains("Add transactions"))
    }

    @Test
    fun `top category insight names food`() {
        val txs = listOf(
            tx(800.0, TransactionType.EXPENSE, "Food"),
            tx(200.0, TransactionType.EXPENSE, "Transport")
        )
        val out = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList())
        assertTrue(out.any { it.contains("Food") })
    }

    @Test
    fun `over income verdict appears when overspent`() {
        val txs = listOf(
            tx(1000.0, TransactionType.INCOME, "Salary"),
            tx(1500.0, TransactionType.EXPENSE, "Shopping")
        )
        val out = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList())
        assertTrue(out.any { it.contains("Danger") })
    }

    @Test
    fun `sheng output differs from english`() {
        val txs = listOf(tx(100.0, TransactionType.EXPENSE, "Food"))
        val en = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList())
        val sh = buildInsights(txs, emptyList(), AppLanguage.SHENG, "", emptyList(), emptyList(), emptyList())
        assertNotEquals(en, sh)
    }

    @Test
    fun `forecast math finds broke day`() {
        val c = java.util.Calendar.getInstance()
        c.set(2026, java.util.Calendar.OCTOBER, 10, 12, 0, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        val fc = monthEndForecast(9000.0, 900.0, 10000.0, 0.0, c.timeInMillis)
        assertEquals(12, fc.brokeDay)
        assertEquals(21, fc.daysLeft)
        assertEquals(27900.0, fc.projectedTotal, 0.01)
    }

    @Test
    fun `already over when held cash is gone`() {
        val txs = listOf(tx(9000.0, TransactionType.EXPENSE, "Shopping"))
        val out = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList(), heldBalance = 1000.0)
        assertTrue(out.any { it.contains("already over") })
    }

    @Test
    fun `reassurance when pace fits held cash`() {
        val txs = listOf(tx(9000.0, TransactionType.EXPENSE, "Shopping"))
        val out = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList(), heldBalance = 10_000_000.0)
        assertTrue(out.any { it.contains("inside your") })
    }

    @Test
    fun `hustle lens reports landed share`() {
        val txs = listOf(tx(500.0, TransactionType.EXPENSE, "Food"))
        val out = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList(), hustleExpected = 4000.0, hustleLanded = 1000.0)
        assertTrue(out.any { it.contains("Hustle lens") && it.contains("25%") })
    }

    @Test
    fun `no hustle declared means no hustle lens`() {
        val txs = listOf(tx(500.0, TransactionType.EXPENSE, "Food"))
        val out = buildInsights(txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList())
        assertTrue(out.none { it.contains("Hustle lens") })
    }

    @Test
    fun `stale busy day is flagged`() {
        val txs = (1..3).map { w ->
            val c = java.util.Calendar.getInstance()
            c.add(java.util.Calendar.DAY_OF_MONTH, -7 * w)
            while (c.get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.MONDAY) c.add(java.util.Calendar.DAY_OF_MONTH, -1)
            c.set(java.util.Calendar.HOUR_OF_DAY, 8)
            c.set(java.util.Calendar.MINUTE, 0)
            c.set(java.util.Calendar.SECOND, 0)
            c.set(java.util.Calendar.MILLISECOND, 0)
            tx(c.timeInMillis, TransactionType.EXPENSE, "Transport")
        } +
            // One spend stamped now: guarantees current-month spend so the
            // engine gets past the empty-month early return even on the 1st,
            // when all past Mondays fall in the previous month.
            tx(200.0, TransactionType.EXPENSE, "Transport")
        val out = buildInsights(
            txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList(),
            // Both declared: whatever weekday today is, at least one stays
            // stale (Sundays are excluded from stale detection by the engine).
            weekPlan = mapOf("Sat" to setOf("morning"), "Fri" to setOf("morning"))
        )
        assertTrue(out.any { it.contains("Timetable check") && (it.contains("Sat") || it.contains("Fri")) })
    }

    @Test
    fun `matching grid stays quiet`() {
        val c = java.util.Calendar.getInstance()
        while (c.get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.MONDAY) c.add(java.util.Calendar.DAY_OF_MONTH, -1)
        c.set(java.util.Calendar.HOUR_OF_DAY, 8)
        c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        val txs = listOf(tx(c.timeInMillis, TransactionType.EXPENSE, "Transport"))
        val out = buildInsights(
            txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(), emptyList(), emptyList(),
            weekPlan = mapOf("Mon" to setOf("morning"))
        )
        assertTrue(out.none { it.contains("Timetable check") })
    }

    private fun tx(ts: Long, type: TransactionType, category: String) = Transaction(
        amount = 50.0,
        type = type,
        category = category,
        dateTimestamp = ts,
        merchant = "Test",
        description = "",
        paymentMethod = PaymentMethod.CASH,
        source = TransactionSource.MANUAL
    )
}
