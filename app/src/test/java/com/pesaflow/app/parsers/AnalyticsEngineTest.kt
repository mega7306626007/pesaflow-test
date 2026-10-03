package com.pesaflow.app.parsers

import com.pesaflow.app.data.analytics.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class AnalyticsEngineTest {

    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 1, 12, 0) }.timeInMillis
    private fun tx(amount: Double, category: String, ts: Long, type: com.pesaflow.app.data.models.TransactionType = com.pesaflow.app.data.models.TransactionType.EXPENSE, opening: Boolean = false): com.pesaflow.app.data.models.Transaction {
        return com.pesaflow.app.data.models.Transaction(amount = amount, type = type, category = category, dateTimestamp = ts, merchant = "Test", isOpening = opening)
    }
    private fun day(offset: Int): Long = now - offset * 24L * 60 * 60 * 1000

    @Test
    fun `classifies food and transport correctly`() {
        assertEquals(ExpenseCategory.FOOD, classifyCategory("Food"))
        assertEquals(ExpenseCategory.FOOD, classifyCategory("Uzingo"))
        assertEquals(ExpenseCategory.TRANSPORT, classifyCategory("Matatu"))
        assertEquals(ExpenseCategory.TRANSPORT, classifyCategory("Fuel"))
    }

    @Test
    fun `report with empty history`() {
        val r = buildAnalyticsReport(emptyList(), 30, now)
        assertEquals(0.0, r.totalSpent, 0.001)
        assertEquals(0, r.activityDays)
        assertEquals(0, r.categorySummaries.size)
        assertEquals(0.0, r.dailyAvg, 0.001)
        assertTrue(r.netFlow >= 0)
    }

    @Test
    fun `activity coverage counts only recorded non-sample days`() {
        val txs = listOf(
            tx(100.0, "Food", day(0)),
            tx(200.0, "Food", day(1)),
            tx(500.0, "Food", day(2)).copy(isSample = true),
            tx(1000.0, "Opening", day(3), opening = true)
        )
        val report = buildAnalyticsReport(txs, 30, now)
        assertEquals(2, report.activityDays)
    }

    @Test
    fun `calculates category shares`() {
        val txs = listOf(
            tx(100.0, "Food", day(0)),
            tx(100.0, "Food", day(1)),
            tx(300.0, "Rent", day(2))
        )
        val r = buildAnalyticsReport(txs, 7, now)
        assertEquals(500.0, r.totalSpent, 0.001)
        val food = r.categorySummaries.find { it.category == ExpenseCategory.FOOD }
        assertNotNull(food)
        assertEquals(200.0, food!!.total, 0.001)
        assertEquals(40, food.sharePercent)
        assertEquals(60, r.categorySummaries.find { it.category == ExpenseCategory.BILLS }!!.sharePercent)
    }

    @Test
    fun `detects trend direction`() {
        val recent = listOf(tx(200.0, "Food", day(0)), tx(200.0, "Food", day(1)))
        val older = listOf(tx(50.0, "Food", day(20)), tx(50.0, "Food", day(25)))
        val txs = recent + older
        val r = buildAnalyticsReport(txs, 30, now)
        val food = r.categorySummaries.find { it.category == ExpenseCategory.FOOD }
        assertNotNull(food)
        assertTrue("Food should be trending up", food!!.trendPercent > 0)
    }

    @Test
    fun `savings rate is correct`() {
        val txs = listOf(
            tx(500.0, "Food", day(0), com.pesaflow.app.data.models.TransactionType.INCOME),
            tx(300.0, "Food", day(1))
        )
        val r = buildAnalyticsReport(txs, 7, now)
        assertEquals(40, r.savingsRate)
    }

    @Test
    fun `opening balance is held cash not reported income`() {
        val txs = listOf(
            tx(5000.0, "Income", day(0), com.pesaflow.app.data.models.TransactionType.INCOME, opening = true),
            tx(1200.0, "Income", day(0), com.pesaflow.app.data.models.TransactionType.INCOME)
        )
        val report = buildAnalyticsReport(txs, 7, now)
        assertEquals(1200.0, report.totalIncome, 0.001)
        assertEquals(1200.0, report.netFlow, 0.001)
    }

    @Test
    fun `heatmap grid dimensions match`() {
        val txs = (0..20).map { tx(10.0, "Food", day(it)) }
        val r = buildAnalyticsReport(txs, 30, now)
        assertTrue(r.heatmap.grid.size <= 8)
        r.heatmap.grid.forEach { assertEquals(7, it.size) }
        assertEquals(r.heatmap.weekLabels.size, r.heatmap.grid.size)
    }

    @Test
    fun `fastest growing category detection`() {
        val txs = mutableListOf<com.pesaflow.app.data.models.Transaction>()
        // Food trending up 100%
        txs.add(tx(100.0, "Food", day(20)))
        txs.add(tx(100.0, "Food", day(21)))
        // Transport flat
        txs.add(tx(100.0, "Transport", day(20)))
        txs.add(tx(100.0, "Transport", day(21)))
        // Add recent food spike
        txs.add(tx(300.0, "Food", day(0)))
        val r = buildAnalyticsReport(txs, 30, now)
        val fastest = fastestGrowingCategory(r)
        assertNotNull(fastest)
        assertEquals(ExpenseCategory.FOOD, fastest)
    }

    @Test
    fun `overBudgetCategories flag high spenders`() {
        val txs = (0..5).map { tx(500.0, "Food", day(it)) }
        val r = buildAnalyticsReport(txs, 30, now)
        val over = overBudgetCategories(r, 0.5)
        assertTrue(over.isNotEmpty())
    }

    @Test
    fun `weekdayProfile is Mon-first length 7`() {
        val txs = (0..13).map { tx(100.0, "Food", day(it)) }
        val r = buildAnalyticsReport(txs, 30, now)
        assertEquals(7, r.weekdayProfile.size)
    }

    @Test
    fun `trend off dust baseline reports flat not fantasy`() {
        // Older half totals KSh 50: a percent off that is noise, never signal.
        val txs = listOf(
            tx(5000.0, "Food", day(0)),
            tx(50.0, "Food", day(20))
        )
        val r = buildAnalyticsReport(txs, 30, now)
        val food = r.categorySummaries.find { it.category == ExpenseCategory.FOOD }!!
        assertEquals(0.0, food.trendPercent, 0.0)
        assertNull(fastestGrowingCategory(r))
    }

    @Test
    fun `trend caps at 999`() {
        val txs = listOf(
            tx(20000.0, "Food", day(0)),
            tx(1000.0, "Food", day(20))
        )
        val r = buildAnalyticsReport(txs, 30, now)
        val food = r.categorySummaries.find { it.category == ExpenseCategory.FOOD }!!
        assertEquals(999.0, food.trendPercent, 0.001)
    }

    @Test
    fun `heatmap rows are calendar weeks with matching labels`() {
        // Sunday 2026-09-13 23:00 local: Monday 00:01 and Sunday 23:59 belong
        // to the same calendar week, so they share row 0 at columns 0 and 6.
        val sundayNight = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 13, 23, 59, 59)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        fun at(d: Int, h: Int, min: Int): Long {
            return Calendar.getInstance().apply {
                set(2026, Calendar.SEPTEMBER, d, h, min, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        val txs = listOf(
            tx(100.0, "Food", at(7, 0, 1)),
            tx(300.0, "Transport", at(13, 23, 59)),
            tx(50.0, "Food", at(6, 23, 59))
        )
        val r = buildAnalyticsReport(txs, 30, sundayNight)
        assertEquals(100.0, r.heatmap.grid[0][0], 0.001)
        assertEquals(300.0, r.heatmap.grid[0][6], 0.001)
        assertEquals(400.0, r.heatmap.grid[0].sum(), 0.001)
        // The previous Sunday sits in the older row, and row 0's label is
        // the Monday that starts it.
        assertEquals(50.0, r.heatmap.grid[1].sum(), 0.001)
        assertEquals("07-09", r.heatmap.weekLabels[0])
        assertEquals(r.heatmap.weekLabels.size, r.heatmap.grid.size)
    }
}
