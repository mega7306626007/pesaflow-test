package com.pesaflow.app.parsers

import com.pesaflow.app.data.finance.MoneyFormatter
import com.pesaflow.app.data.finance.SnapshotInput
import com.pesaflow.app.data.finance.balanceBreakdown
import com.pesaflow.app.data.finance.buildSnapshot
import com.pesaflow.app.data.finance.instalmentSchedule
import com.pesaflow.app.data.meals.STAPLES
import com.pesaflow.app.data.meals.stapleByName
import com.pesaflow.app.data.income.IncomeSource
import com.pesaflow.app.data.income.expectedIncomeLandings
import com.pesaflow.app.data.income.nextInflowDay
import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import org.junit.Assert.*
import org.junit.Test

// Transformation validation: scenarios A–M. Engine semantics pinned so UX
// work can never silently change financial truth.
class TransformationValidationTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()

    private fun tx(amount: Double, type: TransactionType, ts: Long = now - day, opening: Boolean = false) =
        Transaction(amount = amount, type = type, category = "T", dateTimestamp = ts,
            merchant = "M", paymentMethod = PaymentMethod.MPESA, isOpening = opening)

    private fun snap(txs: List<Transaction>, fees: Double = 0.0, helb: Double = 0.0) =
        buildSnapshot(SnapshotInput(txs = txs, feesAmount = fees, helbExpected = helb, nowMs = now))

    @Test fun `A empty ledger is zero not null`() {
        val s = snap(emptyList())
        assertEquals(0.0, s.liquid.toDouble(), 0.001)
        assertEquals(0.0, s.flexible.toDouble(), 0.001)
    }

    @Test fun `B opening only becomes liquid but never earned`() {
        val s = snap(listOf(tx(5000.0, TransactionType.INCOME, opening = true)))
        assertEquals(5000.0, s.liquid.toDouble(), 0.001)
        assertEquals(0.0, s.monthlyEarnedIncome.toDouble(), 0.001)
    }

    @Test fun `C income only`() {
        val s = snap(listOf(tx(3000.0, TransactionType.INCOME)))
        assertEquals(3000.0, s.liquid.toDouble(), 0.001)
    }

    @Test fun `D expenses only may go negative`() {
        val s = snap(listOf(tx(1200.0, TransactionType.EXPENSE)))
        assertEquals(-1200.0, s.liquid.toDouble(), 0.001)
        assertEquals(0.0, s.flexible.toDouble(), 0.001)
    }

    @Test fun `E income plus expenses`() {
        val s = snap(listOf(tx(3000.0, TransactionType.INCOME), tx(1200.0, TransactionType.EXPENSE)))
        assertEquals(1800.0, s.liquid.toDouble(), 0.001)
    }

    @Test fun `F future transactions excluded`() {
        val s = snap(listOf(
            tx(1000.0, TransactionType.INCOME),
            tx(99999.0, TransactionType.INCOME, ts = now + 10 * day)
        ))
        assertEquals(1000.0, s.liquid.toDouble(), 0.001)
        val bb = balanceBreakdown(
            listOf(
                tx(1000.0, TransactionType.INCOME),
                tx(99999.0, TransactionType.INCOME, ts = now + 10 * day)
            ), now
        )
        assertEquals(1, bb.futureExcludedCount)
        assertEquals(1000.0, bb.liquid, 0.001)
    }

    @Test fun `G paired transfer nets zero`() {
        val g = "g1"
        val out = tx(500.0, TransactionType.TRANSFER).copy(transferGroupId = g, transferSide = "OUT")
        val inn = tx(500.0, TransactionType.TRANSFER).copy(transferGroupId = g, transferSide = "IN")
        val s = snap(listOf(tx(1000.0, TransactionType.INCOME), out, inn))
        assertEquals(1000.0, s.liquid.toDouble(), 0.001)
    }

    @Test fun `G unpaired transfer ignored`() {
        val s = snap(listOf(
            tx(1000.0, TransactionType.INCOME),
            tx(500.0, TransactionType.TRANSFER)
        ))
        assertEquals(1000.0, s.liquid.toDouble(), 0.001)
    }

    @Test fun `H savings reduce liquid but keep assets`() {
        val s = snap(listOf(tx(2000.0, TransactionType.INCOME), tx(500.0, TransactionType.SAVING)))
        assertEquals(1500.0, s.liquid.toDouble(), 0.001)
        assertEquals(2000.0, s.totalAssets.toDouble(), 0.001)
    }

    @Test fun `I planned fee commits but does not move balance`() {
        val s = snap(listOf(tx(10000.0, TransactionType.INCOME)), fees = 32000.0)
        assertEquals(10000.0, s.liquid.toDouble(), 0.001)
        assertEquals(32000.0, s.upcomingFees.toDouble(), 0.001)
        assertEquals(0.0, s.flexible.toDouble(), 0.001)
    }

    @Test fun `J actual fee payment moves balance`() {
        val s = snap(listOf(
            tx(10000.0, TransactionType.INCOME),
            tx(2000.0, TransactionType.EXPENSE)
        ), fees = 32000.0)
        assertEquals(8000.0, s.liquid.toDouble(), 0.001)
    }

    @Test fun `K duplicate rows both count`() {
        val s = snap(listOf(tx(100.0, TransactionType.EXPENSE), tx(100.0, TransactionType.EXPENSE)))
        assertEquals(-200.0, s.liquid.toDouble(), 0.001)
    }

    @Test fun `L negative ledger stays negative with zero flexible`() {
        val s = snap(listOf(tx(50000.0, TransactionType.INCOME), tx(252000.0, TransactionType.EXPENSE)))
        assertEquals(-202000.0, s.liquid.toDouble(), 0.001)
        assertEquals(0.0, s.flexible.toDouble(), 0.001)
        assertEquals(0.0, s.safeToday.toDouble(), 0.001)
    }

    @Test fun `M large values format without overflow`() {
        val s = snap(listOf(tx(5_000_000.0, TransactionType.INCOME)))
        assertEquals(5_000_000.0, s.liquid.toDouble(), 0.001)
        val label = MoneyFormatter.compact(s.liquid)
        assertTrue(label.isNotBlank())
        assertTrue(label.contains("5"))
    }

    @Test fun `samples never count`() {
        val t = tx(99999.0, TransactionType.INCOME).copy(isSample = true)
        val s = snap(listOf(t))
        assertEquals(0.0, s.liquid.toDouble(), 0.001)
    }

    @Test fun `instalments split evenly with remainder last`() {
        val plan = instalmentSchedule(32000.0, 16, 7)
        assertEquals(16, plan.size)
        assertTrue(plan.dropLast(1).all { it.amount == 2000.0 })
        assertEquals(2000.0, plan.last().amount, 0.001)
        assertEquals(32000.0, plan.sumOf { it.amount }, 0.001)
        assertEquals(7, plan.first().dueInDays)
        assertEquals(112, plan.last().dueInDays)
    }

    @Test fun `instalments keep shilling totals exact`() {
        val plan = instalmentSchedule(10000.0, 3, 30)
        assertEquals(3, plan.size)
        assertEquals(10000.0, plan.sumOf { it.amount }, 0.001)
        assertEquals(30, plan.first().dueInDays)
        assertEquals(90, plan.last().dueInDays)
    }

    @Test fun `instalments reject nonsense`() {
        assertTrue(instalmentSchedule(0.0, 4, 7).isEmpty())
        assertTrue(instalmentSchedule(5000.0, 1, 7).isEmpty())
        assertTrue(instalmentSchedule(5000.0, 4, 0).isEmpty())
        assertTrue(instalmentSchedule(5000.0, 53, 7).isEmpty())
    }

    @Test fun `staple catalogue covers comrade quantities`() {        assertTrue(STAPLES.size >= 10)
        val unga = stapleByName("unga")!!
        assertEquals(7.0, unga.daysPerPack(), 0.001)
        assertEquals(500.0, unga.buyAmount(2), 0.001)
        val quarter = stapleByName("CABBAGE")!!
        assertEquals(2.0, quarter.daysPerPack(), 0.001)
        assertTrue(STAPLES.all { it.defaultPrice > 0 && it.dailyUse > 0 })
    }

    @Test fun `food kind inference never guesses`() {
        assertEquals(
            com.pesaflow.app.data.meals.FoodKind.RAW_FOODSTUFF,
            com.pesaflow.app.data.meals.inferFoodKind("unga")
        )
        assertEquals(
            com.pesaflow.app.data.meals.FoodKind.RAW_FOODSTUFF,
            com.pesaflow.app.data.meals.inferFoodKind("Mama Njoroge sukuma")
        )
        assertEquals(
            com.pesaflow.app.data.meals.FoodKind.BOUGHT_PLATE,
            com.pesaflow.app.data.meals.inferFoodKind("Smocha", "University of Nairobi")
        )
        assertEquals(
            com.pesaflow.app.data.meals.FoodKind.UNKNOWN,
            com.pesaflow.app.data.meals.inferFoodKind("Daniel Mayoli")
        )
        assertEquals(
            com.pesaflow.app.data.meals.FoodKind.UNKNOWN,
            com.pesaflow.app.data.meals.inferFoodKind("   ")
        )
    }

    @Test fun `parent daily fare lands every day`() {
        val src = IncomeSource(kind = "PARENT", label = "Parent fare", expectedAmount = 300.0, frequency = "DAILY")
        assertEquals(1, src.daysUntilLanding(now))
        val landings = expectedIncomeLandings(listOf(src), now, 30)
        assertEquals(30, landings.size)
        assertTrue(landings.all { it.second == 300.0 })
        assertEquals(1, nextInflowDay(listOf(src), now))
    }

    @Test fun `parent weekly fare lands four times`() {
        val src = IncomeSource(kind = "PARENT", label = "Parent fare", expectedAmount = 1500.0, frequency = "WEEKLY")
        assertEquals(7, src.daysUntilLanding(now))
        val landings = expectedIncomeLandings(listOf(src), now, 30)
        assertEquals(4, landings.size)
        assertTrue(landings.all { it.second == 1500.0 })
        assertEquals(7, nextInflowDay(listOf(src), now))
    }

    @Test fun `monthly and fuliza landing rules unchanged`() {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val dom = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val dim = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val futureDom = if (dom < dim) dom + 1 else dim
        val monthly = IncomeSource(kind = "JOB", label = "Job", expectedAmount = 20000.0, frequency = "MONTHLY", dayOfMonth = futureDom)
        assertEquals(1, monthly.daysUntilLanding(now))
        assertEquals(1, expectedIncomeLandings(listOf(monthly), now, 30).size)
        val fuliza = IncomeSource(kind = "FULIZA", label = "Fuliza", expectedAmount = 500.0, frequency = "DAILY")
        assertTrue(expectedIncomeLandings(listOf(fuliza), now, 30).isEmpty())
        assertNull(nextInflowDay(emptyList(), now))
        assertNull(nextInflowDay(listOf(fuliza), now))
    }
}
