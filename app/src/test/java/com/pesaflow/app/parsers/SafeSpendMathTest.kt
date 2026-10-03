package com.pesaflow.app.parsers

import com.pesaflow.app.ui.dashboard.buddySafeDaily
import com.pesaflow.app.ui.dashboard.safeDayFigure
import org.junit.Assert.*
import org.junit.Test


/** Safe-to-spend day math breathes with data and the calendar. */
class SafeSpendMathTest {

    @Test
    fun `late month divides by days left not thirty`() {
        // 28th of a 30-day month: KSh 3000 left over 3 days = 1000/day.
        val fig = safeDayFigure(
            monthlyLimit = 9000.0, spentMonth = 6000.0,
            planDaily = 0, billDaily = 0, weekdayFactor = 1.0,
            todaySpend = 0, dayOfMonth = 28, daysInMonth = 30
        )
        assertEquals(3, fig.daysLeft)
        assertEquals(3000.0, fig.remaining, 0.001)
        assertEquals(1000, fig.dailyTarget)
        assertEquals(1000, fig.allowance)
        assertEquals(1000, fig.left)
    }

    @Test
    fun `new spending shrinks tomorrow's target`() {
        val before = safeDayFigure(9000.0, 1000.0, 0, 0, 1.0, 0, 10, 30)
        val after = safeDayFigure(9000.0, 4000.0, 0, 0, 1.0, 0, 10, 30)
        // 21 days left: 8000/21 vs 5000/21.
        assertEquals(380, before.dailyTarget)
        assertEquals(238, after.dailyTarget)
        assertTrue(after.dailyTarget < before.dailyTarget)
    }

    @Test
    fun `reserves come off the top`() {
        val fig = safeDayFigure(9000.0, 0.0, 100, 50, 1.0, 0, 1, 30)
        // (9000 - 150*30) / 30 = 150/day.
        assertEquals(150, fig.dailyTarget)
    }

    @Test
    fun `over pace floors at zero never negative`() {
        val fig = safeDayFigure(5000.0, 9000.0, 0, 0, 1.0, 500, 20, 30)
        assertTrue(fig.remaining < 0)
        assertEquals(0, fig.dailyTarget)
        assertEquals(0, fig.allowance)
        assertEquals(-500, fig.left)
    }

    @Test
    fun `weekday factor paces the allowance`() {
        val fig = safeDayFigure(9000.0, 0.0, 0, 0, 1.4, 0, 1, 30)
        assertEquals(300, fig.dailyTarget)
        assertEquals(420, fig.allowance)
    }

    @Test
    fun `single day month never divides by zero`() {
        val fig = safeDayFigure(1000.0, 0.0, 0, 0, 1.0, 0, 28, 28)
        assertEquals(1, fig.daysLeft)
        assertEquals(1000, fig.dailyTarget)
    }

    @Test
    fun `actual flexible cash caps budget pace`() {
        val fig = safeDayFigure(
            monthlyLimit = 30_000.0, spentMonth = 0.0,
            planDaily = 0, billDaily = 0, weekdayFactor = 1.0,
            todaySpend = 0, dayOfMonth = 1, daysInMonth = 30,
            flexibleCash = 900.0
        )
        assertEquals(30, fig.dailyTarget)
    }

    @Test
    fun `buddy safe daily is capped by both budget and free cash`() {
        assertEquals(30, buddySafeDaily(30_000.0, 900.0, 30))
        assertEquals(100, buddySafeDaily(3_000.0, 9_000.0, 30))
        assertEquals(0, buddySafeDaily(-100.0, 9_000.0, 30))
        assertEquals(0, buddySafeDaily(3_000.0, 9_000.0, 0))
    }
}
