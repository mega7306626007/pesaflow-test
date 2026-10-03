package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.time.inPastOrNow
import com.pesaflow.app.data.time.previousWeekRange
import com.pesaflow.app.data.time.thisWeekRange
import org.junit.Assert.*
import org.junit.Test


class NotificationLogicTest {

    data class TestTx(
        val amount: Double,
        val type: TransactionType,
        val category: String,
        val dateTimestamp: Long,
        val source: TransactionSource = TransactionSource.MANUAL
    )

    private val day = 24L * 60 * 60 * 1000
    private fun daysAgo(n: Int) = System.currentTimeMillis() - n * day
    /** Fixed local datetime (2026-09-07 is a Monday). Relative now - n*day
     * construction is weekday-flaky against calendar weeks, so Sunday-report
     * tests pin real dates. */
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long {
        return java.util.Calendar.getInstance().apply {
            set(y, m, d, h, min, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    private fun startOfDay() = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis


    @Test
    fun `night report shows today spending and category breakdown`() {
        val dayStart = startOfDay()
        val txs = listOf(
            TestTx(500.0, TransactionType.EXPENSE, "Food", dayStart + 1000, TransactionSource.MPESA_SMS),
            TestTx(200.0, TransactionType.EXPENSE, "Transport", dayStart + 2000),
            TestTx(100.0, TransactionType.EXPENSE, "Airtime", dayStart + 3000, TransactionSource.MPESA_SMS),
            TestTx(1000.0, TransactionType.INCOME, "Salary", dayStart + 4000)
        )
        val todaySpent = txs.filter { it.type == TransactionType.EXPENSE && it.dateTimestamp >= dayStart }.sumOf { it.amount }
        val mpesaSpent = txs.filter { it.type == TransactionType.EXPENSE && it.dateTimestamp >= dayStart && it.source == TransactionSource.MPESA_SMS }.sumOf { it.amount }
        val manualSpent = todaySpent - mpesaSpent
        val topCat = txs.filter { it.type == TransactionType.EXPENSE && it.dateTimestamp >= dayStart }
            .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
            .maxByOrNull { it.value }

        assertEquals(800.0, todaySpent, 0.001)
        assertEquals(600.0, mpesaSpent, 0.001)
        assertEquals(200.0, manualSpent, 0.001)
        assertEquals("Food", topCat?.key)
        assertEquals(500.0, topCat?.value ?: 0.0, 0.001)
    }


    @Test
    fun `night report compares today vs yesterday`() {
        val dayStart = startOfDay()
        val txs = listOf(
            TestTx(500.0, TransactionType.EXPENSE, "Food", dayStart + 1000),
            TestTx(300.0, TransactionType.EXPENSE, "Food", dayStart - day + 1000)
        )
        val todaySpent = txs.filter { it.type == TransactionType.EXPENSE && it.dateTimestamp >= dayStart }.sumOf { it.amount }
        val yesterdaySpent = txs.filter { it.type == TransactionType.EXPENSE && it.dateTimestamp >= dayStart - day && it.dateTimestamp < dayStart }.sumOf { it.amount }
        val change = if (yesterdaySpent > 0) ((todaySpent - yesterdaySpent) / yesterdaySpent * 100).toInt() else 0

        assertEquals(500.0, todaySpent, 0.001)
        assertEquals(300.0, yesterdaySpent, 0.001)
        assertEquals(66, change)
    }


    @Test
    fun `sunday report shows week summary and week-over-week change`() {
        // Sunday 2026-09-13 15:00 local: current week Mon Sep 7 - Sun Sep 13,
        // previous week Mon Aug 31 - Sun Sep 6. Midnight edges must not leak.
        val now = at(2026, java.util.Calendar.SEPTEMBER, 13, 15, 0)
        val week = thisWeekRange(now)
        val prevWeek = previousWeekRange(now)
        val txs = listOf(
            TestTx(500.0, TransactionType.EXPENSE, "Food", at(2026, java.util.Calendar.SEPTEMBER, 8)),
            TestTx(300.0, TransactionType.EXPENSE, "Transport", at(2026, java.util.Calendar.SEPTEMBER, 10, 9, 30)),
            TestTx(200.0, TransactionType.EXPENSE, "Food", at(2026, java.util.Calendar.SEPTEMBER, 12, 20, 15)),
            TestTx(50.0, TransactionType.EXPENSE, "Food", at(2026, java.util.Calendar.SEPTEMBER, 7, 0, 0)),
            TestTx(100.0, TransactionType.EXPENSE, "Airtime", at(2026, java.util.Calendar.SEPTEMBER, 1), TransactionSource.MPESA_SMS),
            TestTx(150.0, TransactionType.EXPENSE, "Shopping", at(2026, java.util.Calendar.SEPTEMBER, 3, 18, 0)),
            TestTx(25.0, TransactionType.EXPENSE, "Food", at(2026, java.util.Calendar.SEPTEMBER, 6, 23, 59)),
            TestTx(1000.0, TransactionType.INCOME, "Salary", at(2026, java.util.Calendar.SEPTEMBER, 9, 8, 0))
        )
        fun inWeek(t: TestTx) = t.dateTimestamp in week && inPastOrNow(t.dateTimestamp, now)
        val weekSpent = txs.filter { it.type == TransactionType.EXPENSE && inWeek(it) }.sumOf { it.amount }
        val prevWeekSpent = txs.filter { it.type == TransactionType.EXPENSE && it.dateTimestamp in prevWeek }.sumOf { it.amount }
        val mpesaWeek = txs.filter { it.type == TransactionType.EXPENSE && inWeek(it) && it.source == TransactionSource.MPESA_SMS }.sumOf { it.amount }
        val weekIncome = txs.filter { it.type == TransactionType.INCOME && inWeek(it) }.sumOf { it.amount }

        assertEquals(1050.0, weekSpent, 0.001)
        assertEquals(275.0, prevWeekSpent, 0.001)
        assertEquals(0.0, mpesaWeek, 0.001)
        assertEquals(1000.0, weekIncome, 0.001)
    }


    @Test
    fun `sunday report identifies top category`() {
        val now = at(2026, java.util.Calendar.SEPTEMBER, 13, 15, 0)
        val week = thisWeekRange(now)
        val txs = listOf(
            TestTx(500.0, TransactionType.EXPENSE, "Food", at(2026, java.util.Calendar.SEPTEMBER, 8)),
            TestTx(300.0, TransactionType.EXPENSE, "Food", at(2026, java.util.Calendar.SEPTEMBER, 10)),
            TestTx(100.0, TransactionType.EXPENSE, "Transport", at(2026, java.util.Calendar.SEPTEMBER, 9))
        )
        val topCat = txs.filter { it.type == TransactionType.EXPENSE && it.dateTimestamp in week && inPastOrNow(it.dateTimestamp, now) }
            .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
            .maxByOrNull { it.value }

        assertEquals("Food", topCat?.key)
        assertEquals(800.0, topCat?.value ?: 0.0, 0.001)
    }


    @Test
    fun `night report handles empty day gracefully`() {
        val dayStart = startOfDay()
        val txs = listOf(
            TestTx(300.0, TransactionType.EXPENSE, "Food", dayStart - day)
        )
        val todaySpent = txs.filter { it.type == TransactionType.EXPENSE && it.dateTimestamp >= dayStart }.sumOf { it.amount }
        val todayCount = txs.filter { it.type == TransactionType.EXPENSE && it.dateTimestamp >= dayStart }.size

        assertEquals(0.0, todaySpent, 0.001)
        assertEquals(0, todayCount)
    }


    @Test
    fun `mpesa parser categorizes expanded merchant names`() {
        assertEquals("Transport", com.pesaflow.app.data.parsers.MpesaParser.inferCategory("Matatu Fare", TransactionType.EXPENSE))
        assertEquals("Transport", com.pesaflow.app.data.parsers.MpesaParser.inferCategory("Uber Nairobi", TransactionType.EXPENSE))
        assertEquals("Data", com.pesaflow.app.data.parsers.MpesaParser.inferCategory("Safaricom Bundle", TransactionType.EXPENSE))
        assertEquals("Food", com.pesaflow.app.data.parsers.MpesaParser.inferCategory("Kibanda Lunch", TransactionType.EXPENSE))
        assertEquals("Clothes", com.pesaflow.app.data.parsers.MpesaParser.inferCategory("Shirt Store", TransactionType.EXPENSE))
        assertEquals("Kujibamba", com.pesaflow.app.data.parsers.MpesaParser.inferCategory("Salon Beauty", TransactionType.EXPENSE))
        assertEquals("Health", com.pesaflow.app.data.parsers.MpesaParser.inferCategory("Pharmacy Plus", TransactionType.EXPENSE))
        assertEquals("Printing", com.pesaflow.app.data.parsers.MpesaParser.inferCategory("Cyber Cafe", TransactionType.EXPENSE))
        assertEquals("Data", com.pesaflow.app.data.parsers.MpesaParser.inferCategory("Wifi Kenya", TransactionType.EXPENSE))
        assertEquals("School", com.pesaflow.app.data.parsers.MpesaParser.inferCategory("University Fees", TransactionType.EXPENSE))
    }


    @Test
    fun `safe to spend prioritization shows food and essentials when below 100`() {
        val allowance = 80.0
        val foodShare = (allowance * 0.8).toInt()
        val essentialsShare = (allowance * 0.2).toInt()
        assertEquals(64, foodShare)
        assertEquals(16, essentialsShare)
        assertEquals(80, foodShare + essentialsShare)
    }


    @Test
    fun `safe to spend week prioritization when below 700`() {
        val weekAllowance = 500.0
        val foodShare = (weekAllowance * 0.6).toInt()
        val essentialsShare = (weekAllowance * 0.4).toInt()
        assertEquals(300, foodShare)
        assertEquals(200, essentialsShare)
        assertEquals(500, foodShare + essentialsShare)
    }


    @Test
    fun `month pace flags overspending above 115 percent of expected`() {
        val limit = 25000.0
        val dayOfMonth = 10
        val daysInMonth = 30
        val expected = limit * dayOfMonth / daysInMonth
        val monthSpent = expected * 1.3
        assertTrue(monthSpent > expected * 1.15)
        val calmSpent = expected * 0.5
        assertTrue(calmSpent < expected * 0.7)
    }


    @Test
    fun `bills due soon filter catches 3 day window`() {
        val now = System.currentTimeMillis()
        val dues = listOf(now + 1 * day, now + 2 * day, now + 10 * day)
        val soon = dues.filter { it in now..(now + 3 * day) }
        assertEquals(2, soon.size)
        val week = dues.filter { it in now..(now + 7 * day) }
        assertEquals(2, week.size)
    }


    @Test
    fun `top 3 categories ordered by spend`() {
        val txs = listOf(
            TestTx(500.0, TransactionType.EXPENSE, "Food", daysAgo(1)),
            TestTx(300.0, TransactionType.EXPENSE, "Transport", daysAgo(2)),
            TestTx(200.0, TransactionType.EXPENSE, "Food", daysAgo(3)),
            TestTx(100.0, TransactionType.EXPENSE, "Airtime", daysAgo(1)),
            TestTx(50.0, TransactionType.EXPENSE, "Shopping", daysAgo(4))
        )
        val top3 = txs.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }.take(3)
        assertEquals(3, top3.size)
        assertEquals("Food", top3[0].key)
        assertEquals(700.0, top3[0].value, 0.001)
        assertEquals("Transport", top3[1].key)
    }


    @Test
    fun `week savings rate math`() {
        val income = 5000.0
        val spent = 3500.0
        val rate = ((income - spent) / income * 100).toInt()
        assertEquals(30, rate)
        assertTrue(rate >= 20)
        val tightRate = ((income - 4500.0) / income * 100).toInt()
        assertTrue(tightRate < 20)
    }


    @Test
    fun `priciest weekday picks max day total`() {
        val now = System.currentTimeMillis()
        val weekStart = now - 7 * day
        val txs = listOf(
            TestTx(800.0, TransactionType.EXPENSE, "Shopping", weekStart + 2 * day),
            TestTx(100.0, TransactionType.EXPENSE, "Food", weekStart + 4 * day)
        )
        val best = (0..6).map { i ->
            val d0 = weekStart + i * day
            txs.filter { it.type == TransactionType.EXPENSE && it.dateTimestamp >= d0 && it.dateTimestamp < d0 + day }.sumOf { it.amount }
        }.maxOrNull()
        assertEquals(800.0, best ?: 0.0, 0.001)
    }


    @Test
    fun `saving type excluded from expense insights but reduces balance`() {
        val txs = listOf(
            TestTx(500.0, TransactionType.EXPENSE, "Food", daysAgo(0)),
            TestTx(500.0, TransactionType.SAVING, "Savings", daysAgo(0)),
            TestTx(2000.0, TransactionType.INCOME, "Salary", daysAgo(0))
        )
        val monthExpense = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        assertEquals(500.0, monthExpense, 0.001)
        val balance = txs.sumOf { when (it.type) { TransactionType.INCOME -> it.amount; else -> -it.amount } }
        assertEquals(1000.0, balance, 0.001)
    }


    @Test
    fun `night report day window starts at 12am sharp`() {
        val dayStart = startOfDay()
        val txs = listOf(
            TestTx(100.0, TransactionType.EXPENSE, "Food", dayStart - 1000),
            TestTx(200.0, TransactionType.EXPENSE, "Food", dayStart),
            TestTx(300.0, TransactionType.EXPENSE, "Transport", dayStart + 1000),
            TestTx(400.0, TransactionType.EXPENSE, "Airtime", dayStart - day)
        )
        // Same >= dayStart rule the NightReportWorker uses
        val today = txs.filter { it.type == TransactionType.EXPENSE && it.dateTimestamp >= dayStart }
        assertEquals(2, today.size)
        assertEquals(500.0, today.sumOf { it.amount }, 0.001)
        // Yesterday's window catches the 23:59 straggler, not today's items
        val yesterday = txs.filter {
            it.type == TransactionType.EXPENSE &&
                it.dateTimestamp >= dayStart - day && it.dateTimestamp < dayStart
        }
        assertEquals(2, yesterday.size)
        assertEquals(500.0, yesterday.sumOf { it.amount }, 0.001)
    }


    @Test
    fun `night report week window covers rolling 7 days`() {        val dayStart = startOfDay()
        val weekStart = dayStart - 6 * day
        val txs = listOf(
            TestTx(100.0, TransactionType.EXPENSE, "Food", weekStart),
            TestTx(200.0, TransactionType.EXPENSE, "Food", dayStart),
            TestTx(999.0, TransactionType.EXPENSE, "Shopping", weekStart - 1000)
        )
        val week = txs.filter { it.type == TransactionType.EXPENSE && it.dateTimestamp >= weekStart }
        assertEquals(2, week.size)
        assertEquals(300.0, week.sumOf { it.amount }, 0.001)
    }


    @Test
    fun `mpesa share weaves into headline top category and biggest hit`() {
        val dayStart = startOfDay()
        val txs = listOf(
            TestTx(500.0, TransactionType.EXPENSE, "Food", dayStart + 1000, TransactionSource.MPESA_SMS),
            TestTx(200.0, TransactionType.EXPENSE, "Food", dayStart + 2000),
            TestTx(100.0, TransactionType.EXPENSE, "Transport", dayStart + 3000, TransactionSource.MPESA_SMS)
        )
        fun isExp(t: TestTx) = t.type == TransactionType.EXPENSE
        val todaySpent = txs.filter { isExp(it) && it.dateTimestamp >= dayStart }.sumOf { it.amount }
        val mpesaTxns = txs.filter { isExp(it) && it.dateTimestamp >= dayStart && it.source == TransactionSource.MPESA_SMS }
        val mpesaToday = mpesaTxns.sumOf { it.amount }
        val manualToday = todaySpent - mpesaToday
        // Headline carries the M-Pesa share, no separate sentence
        val headline = "Today: KSh ${todaySpent.toInt()} (${txs.size} items, KSh ${mpesaToday.toInt()} via M-Pesa)"
        assertEquals("Today: KSh 800 (3 items, KSh 600 via M-Pesa)", headline)
        // Top category names its M-Pesa portion
        val top = txs.filter { isExp(it) }.groupBy { it.category }
            .mapValues { e -> e.value.sumOf { it.amount } }.maxByOrNull { it.value }
        assertEquals("Food", top?.key)
        val mOfTop = mpesaTxns.filter { it.category == top?.key }.sumOf { it.amount }
        assertEquals(500.0, mOfTop, 0.001)
        // Only non-top M-Pesa categories get the "also" clause
        val summary = mpesaTxns.groupBy { it.category }
            .mapValues { e -> e.value.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
        val rest = summary.drop(1)
        assertEquals(1, rest.size)
        assertEquals("Transport", rest[0].key)
        assertEquals(200.0, manualToday, 0.001)
    }


    @Test
    fun `stock days left and refill cost math`() {
        val unga = com.pesaflow.app.data.models.KitchenStock(
            name = "Unga", unit = "kg", qtyFull = 2.0, qtyLeft = 0.5, dailyUse = 0.25, pricePerPack = 180.0
        )
        assertEquals(2.0, com.pesaflow.app.data.models.stockDaysLeft(unga), 0.001)
        // (2.0 - 0.5) / 2.0 of the pack missing → 75% of 180
        assertEquals(135.0, com.pesaflow.app.data.models.stockRefillCost(unga), 0.001)
        val full = unga.copy(qtyLeft = 2.0)
        assertEquals(0.0, com.pesaflow.app.data.models.stockRefillCost(full), 0.001)
        val noUse = unga.copy(dailyUse = 0.0)
        assertTrue(com.pesaflow.app.data.models.stockDaysLeft(noUse) > 100000)
    }


    @Test
    fun `replenish date lands daysLeft ahead`() {
        val now = System.currentTimeMillis()
        val oil = com.pesaflow.app.data.models.KitchenStock(
            name = "Oil", unit = "L", qtyFull = 1.0, qtyLeft = 0.5, dailyUse = 0.1, pricePerPack = 350.0
        )
        val refill = com.pesaflow.app.data.models.stockReplenishDate(oil, now)
        val diffDays = (refill - now).toDouble() / (24 * 60 * 60 * 1000)
        assertEquals(5.0, diffDays, 0.01)
    }


    @Test
    fun `needs sort by priority then cost`() {
        data class Need(val name: String, val priority: Int, val cost: Double)
        val needs = listOf(
            Need("Jacket", 2, 2500.0),
            Need("Lab coat", 1, 1200.0),
            Need("Socks", 1, 300.0)
        ).sortedWith(compareBy({ it.priority }, { it.cost }))
        assertEquals("Socks", needs[0].name)
        assertEquals("Lab coat", needs[1].name)
        assertEquals("Jacket", needs[2].name)
    }


    @Test
    fun `survival stock alone covers all days cheapest first`() {
        val stock = listOf(
            com.pesaflow.app.data.models.KitchenStock(name = "Unga", unit = "kg", qtyFull = 2.0, qtyLeft = 1.0, dailyUse = 0.5, pricePerPack = 200.0),
            com.pesaflow.app.data.models.KitchenStock(name = "Rice", unit = "kg", qtyFull = 1.0, qtyLeft = 1.0, dailyUse = 0.25, pricePerPack = 120.0)
        )
        val plan = com.pesaflow.app.ui.university.planSurvival(stock, emptyList(), 5, 0.0)
        // Rice: 4 days at 30/day. Unga: 2 days at 100/day → rice burns first
        assertEquals(5, plan.days.size)
        assertTrue(plan.days.all { it.fromStock })
        assertEquals("Rice", plan.days[0].staple)
        assertEquals("Unga", plan.days[4].staple)
        assertEquals(0.0, plan.totalCost, 0.001)
        assertTrue(plan.possible)
    }


    @Test
    fun `survival gap buys cheapest cook staple`() {
        val stock = listOf(
            com.pesaflow.app.data.models.KitchenStock(name = "Unga", unit = "kg", qtyFull = 2.0, qtyLeft = 0.5, dailyUse = 0.5, pricePerPack = 200.0)
        )
        val cook = listOf(
            com.pesaflow.app.data.models.MealItem(name = "Githeri", mealType = "Lunch", price = 60.0, component = "Complete", source = "Cook"),
            com.pesaflow.app.data.models.MealItem(name = "Pilau", mealType = "Lunch", price = 100.0, component = "Complete", source = "Buy")
        )
        val plan = com.pesaflow.app.ui.university.planSurvival(stock, cook.filter { it.source == "Cook" }, 4, 500.0)
        assertEquals(4, plan.days.size)
        assertEquals(1, plan.days.count { it.fromStock })
        assertEquals(3, plan.days.count { !it.fromStock })
        assertEquals(180.0, plan.totalCost, 0.001)
        assertTrue(plan.possible)
        assertEquals(listOf("Githeri" to 3), plan.shopping)
    }


    @Test
    fun `survival impossible when cash short reports shortfall`() {
        val stock = listOf(
            com.pesaflow.app.data.models.KitchenStock(name = "Unga", unit = "kg", qtyFull = 2.0, qtyLeft = 0.5, dailyUse = 0.5, pricePerPack = 200.0)
        )
        val cook = listOf(
            com.pesaflow.app.data.models.MealItem(name = "Githeri", mealType = "Lunch", price = 60.0, component = "Complete", source = "Cook")
        )
        val plan = com.pesaflow.app.ui.university.planSurvival(stock, cook, 4, 100.0)
        assertFalse(plan.possible)
        assertEquals(80.0, plan.shortfall, 0.001)
    }


    @Test
    fun `survival with no filler and no stock says so`() {
        val plan = com.pesaflow.app.ui.university.planSurvival(emptyList(), emptyList(), 3, 1000.0)
        assertTrue(plan.noFiller)
        assertFalse(plan.possible)
        val zero = com.pesaflow.app.ui.university.planSurvival(emptyList(), emptyList(), 0, 0.0)
        assertTrue(zero.possible)
        assertTrue(zero.days.isEmpty())
    }


    @Test
    fun `bill lead days widen the due window`() {
        data class B(val due: Long, val lead: Int)
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val bills = listOf(B(now + 5 * day, 7), B(now + 5 * day, 3), B(now + 1 * day, 1))
        // Window edge is inclusive: due exactly at the lead boundary counts.
        val due = bills.filter { it.due <= now + maxOf(1, it.lead) * day }
        assertEquals(2, due.size)
    }
}
