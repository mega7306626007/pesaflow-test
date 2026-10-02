package com.pesaflow.app.parsers

import com.pesaflow.app.data.finance.Account
import com.pesaflow.app.data.finance.Commute
import com.pesaflow.app.data.finance.DataQuality
import com.pesaflow.app.data.finance.DebtLevel
import com.pesaflow.app.data.finance.Horizon
import com.pesaflow.app.data.finance.Housing
import com.pesaflow.app.data.finance.IncomeStability
import com.pesaflow.app.data.finance.Money
import com.pesaflow.app.data.finance.MoneyFormatter
import com.pesaflow.app.data.finance.ProfileSignals
import com.pesaflow.app.data.finance.Reliability
import com.pesaflow.app.data.finance.SnapshotInput
import com.pesaflow.app.data.finance.buildSnapshot
import com.pesaflow.app.data.income.IncomeSource
import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.Debt
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.SavingsGoal
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import org.junit.Assert.*
import org.junit.Test


// Mathematical invariants (§31) + monotonicity (§40) + persona scenarios
// (§25, §39 subset) for the canonical FinancialSnapshot.
class FinancialEngineTest {

    private fun tx(
        amount: Double,
        type: TransactionType,
        category: String = "Food",
        merchant: String = "Test",
        method: PaymentMethod = PaymentMethod.MPESA,
        ts: Long = System.currentTimeMillis(),
        sample: Boolean = false
    ) = Transaction(
        amount = amount, type = type, category = category, dateTimestamp = ts,
        merchant = merchant, description = "", paymentMethod = method,
        source = TransactionSource.MANUAL, isSample = sample
    )

    private fun base(
        txs: List<Transaction> = listOf(
            tx(20000.0, TransactionType.INCOME, "Salary", "Employer", PaymentMethod.BANK_TRANSFER),
            tx(3000.0, TransactionType.EXPENSE, "Food"),
            tx(1500.0, TransactionType.EXPENSE, "Transport")
        ),
        bills: List<Bill> = emptyList(),
        debts: List<Debt> = emptyList(),
        goals: List<SavingsGoal> = emptyList(),
        sources: List<IncomeSource> = emptyList(),
        profile: ProfileSignals = ProfileSignals(),
        budgets: List<Budget> = emptyList()
    ) = SnapshotInput(
        txs = txs, budgets = budgets, bills = bills, debts = debts, goals = goals,
        incomeSources = sources, profile = profile
    )

    private fun bill(
        name: String,
        amount: Double,
        dueInDays: Int,
        status: String = "UNPAID",
        category: String = "Bills"
    ) =
        Bill(
            name = name, amount = amount,
            dueDate = System.currentTimeMillis() + dueInDays * 24L * 60 * 60 * 1000,
            category = category, status = status
        )

    private fun debtOwed(person: String, amount: Double) = Debt(
        person = person, amount = amount,
        dateBorrowed = System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000,
        dueDate = System.currentTimeMillis() + 20L * 24 * 60 * 60 * 1000,
        direction = "I_OWE", status = "OWING"
    )

    // §43 — one formatter, exact backing.
    @Test
    fun `money formatting follows the global rules`() {
        assertEquals("KSh 900", MoneyFormatter.compact(Money.of(900.0)))
        assertEquals("KSh 9.5k", MoneyFormatter.compact(Money.of(9500.0)))
        assertEquals("KSh 90k", MoneyFormatter.compact(Money.of(90000.0)))
        assertEquals("KSh 1.25M", MoneyFormatter.compact(Money.of(1250000.0)))
        assertEquals("KSh 90,000", MoneyFormatter.exact(Money.of(90000.0)))
        assertEquals("KSh 150.90", MoneyFormatter.exact(Money.of(150.90)))
        assertEquals("KSh 10k short", MoneyFormatter.shortfall(Money.of(-10000.0)))
        assertEquals("KSh 4.5k over", MoneyFormatter.shortfall(Money.of(-4500.0), "over"))
        assertEquals(15090L, Money.of(150.90).minorUnits)
    }

    // §31 — transfers move accounts, never wealth/spending/income.
    @Test
    fun `internal transfer is wealth and flow neutral`() {
        val plain = buildSnapshot(base())
        val moved = base(
            txs = base().txs + tx(5000.0, TransactionType.TRANSFER, "Transfer", "M-Pesa to bank")
        )
        val s = buildSnapshot(moved)
        assertEquals(plain.netWorth, s.netWorth)
        assertEquals(plain.monthlyEarnedIncome, s.monthlyEarnedIncome)
        assertEquals(plain.flexible, s.flexible)
        assertEquals(plain.safeToday, s.safeToday)
    }

    // §40 — more money in, safe-to-spend cannot fall.
    @Test
    fun `adding confirmed income cannot reduce safe to spend`() {
        val before = buildSnapshot(base())
        val after = buildSnapshot(base(txs = base().txs + tx(1000.0, TransactionType.INCOME, "Salary", "Employer")))
        assertTrue(after.safeToday >= before.safeToday)
        assertTrue(after.safeUntilIncome >= before.safeUntilIncome)
        assertTrue(after.flexible >= before.flexible)
    }

    // §40 — a new bill cannot raise safe-to-spend or flexible.
    @Test
    fun `adding a bill cannot raise safe to spend`() {
        val before = buildSnapshot(base())
        val after = buildSnapshot(base(bills = listOf(bill("Rent", 8000.0, 5))))
        assertTrue(after.safeToday <= before.safeToday)
        assertTrue(after.flexible <= before.flexible)
        assertTrue(after.upcomingBillsTotal > before.upcomingBillsTotal)
    }

    // §31 — paid bills leave no reserve; only payment events move cash.
    @Test
    fun `paid bills carry no obligation`() {
        val s = buildSnapshot(base(bills = listOf(bill("Rent", 8000.0, -2, "PAID"))))
        assertTrue(s.obligations.none { it.name == "Rent" })
        assertEquals(Money.ZERO, s.upcomingBillsTotal)
    }

    // §40 — goal reservations cannot raise flexible money.
    @Test
    fun `goal reservation cannot raise flexible money`() {
        val before = buildSnapshot(base())
        val goal = SavingsGoal(title = "Laptop", targetAmount = 30000.0, currentAmount = 5000.0, targetTimestamp = System.currentTimeMillis() + 60L * 24 * 60 * 60 * 1000)
        val after = buildSnapshot(base(goals = listOf(goal)))
        assertTrue(after.flexible <= before.flexible)
        assertTrue(after.goalReservations.any { it.label == "Laptop" })
        assertTrue(after.goalReservations.first { it.label == "Laptop" }.projectedNote.contains("Projected"))
    }

    // §31 — uncertain (hustle) income stays out of the cautious number.
    @Test
    fun `possible hustle never enters primary safe to spend`() {
        val hustle = IncomeSource(kind = "HUSTLE", label = "Gig", expectedAmount = 20000.0, frequency = "MONTHLY")
        val without = buildSnapshot(base())
        val with = buildSnapshot(base(sources = listOf(hustle)))
        assertEquals(without.safeToday, with.safeToday)
        assertEquals(without.safeWeek, with.safeWeek)
        assertTrue(with.incomeReliabilities.any { it.reliability == Reliability.POSSIBLE })
    }

    // §31 — samples/demo never contaminate analytics or earned income.
    @Test
    fun `demo data never contaminates real analytics`() {
        val s = buildSnapshot(
            base(txs = base().txs + tx(999999.0, TransactionType.INCOME, "Salary", "Demo", sample = true))
        )
        assertEquals(Money.of(20000.0), s.monthlyEarnedIncome)
        assertTrue(s.netWorth < Money.of(100000.0))
    }

    // §25 — same income, different lives, different answers.
    @Test
    fun `same income different lives give different safe to spend`() {
        val income = listOf(tx(20000.0, TransactionType.INCOME, "Salary", "Employer", PaymentMethod.BANK_TRANSFER))
        val saver = buildSnapshot(
            base(
                txs = income + tx(1500.0, TransactionType.EXPENSE, "Food"),
                profile = ProfileSignals(housing = Housing.PARENTS, commute = Commute.WALK, incomeStability = IncomeStability.FIXED)
            )
        )
        val spender = buildSnapshot(
            base(
                txs = income + listOf(
                    tx(8000.0, TransactionType.EXPENSE, "Rent"),
                    tx(3000.0, TransactionType.EXPENSE, "Transport"),
                    tx(4000.0, TransactionType.EXPENSE, "Food")
                ),
                debts = listOf(debtOwed("Tala", 2000.0)),
                profile = ProfileSignals(housing = Housing.RENTAL, commute = Commute.LONG, debtLevel = DebtLevel.MEDIUM)
            )
        )
        assertTrue(saver.safeToday > spender.safeToday)
        assertTrue(saver.flexible > spender.flexible)
    }

    // §11 — horizon follows the income pattern.
    @Test
    fun `hustle only selects the week horizon`() {
        val s = buildSnapshot(
            base(profile = ProfileSignals(incomeStability = IncomeStability.VARIABLE, incomeKinds = setOf("HUSTLE")))
        )
        assertEquals(Horizon.WEEK, s.primaryHorizon)
    }

    // §15 — HELB splits fees vs upkeep only with real figures.
    @Test
    fun `helb splits only with real figures`() {
        val s = buildSnapshot(base().copy(helbExpected = 20000.0, feesAmount = 8000.0))
        assertEquals(Money.of(8000.0), s.helbFeesCovered)
        assertEquals(Money.of(12000.0), s.helbUpkeep)
        val bare = buildSnapshot(base())
        assertEquals(Money.ZERO, bare.helbFeesCovered)
        assertEquals(Money.ZERO, bare.helbUpkeep)
    }

    @Test
    fun `tracked semester fee is not reserved again from profile`() {
        val s = buildSnapshot(
            base(bills = listOf(bill("Semester fees", 32000.0, 60, category = "School")))
                .copy(helbExpected = 25100.0, feesAmount = 32000.0)
        )
        assertEquals(Money.of(32000.0), s.upcomingBillsTotal)
        assertEquals(Money.ZERO, s.upcomingFees)
        assertEquals(Money.of(32000.0), s.committed)
    }

    @Test
    fun `coffee bill does not replace the profile fee reserve`() {
        val s = buildSnapshot(
            base(bills = listOf(bill("Coffee", 500.0, 5)))
                .copy(helbExpected = 25100.0, feesAmount = 32000.0)
        )
        assertEquals(Money.of(6900.0), s.upcomingFees)
    }

    @Test
    fun `safe semester does not subtract commitments a second time`() {
        val s = buildSnapshot(
            base(
                txs = listOf(tx(100000.0, TransactionType.INCOME, "Salary", "Employer")),
                bills = listOf(bill("Rent", 5000.0, 10)),
                goals = listOf(
                    SavingsGoal(
                        title = "Laptop",
                        targetAmount = 20000.0,
                        currentAmount = 0.0,
                        targetTimestamp = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
                    )
                )
            ).copy(feesAmount = 10000.0)
        )

        assertEquals(Money.of(65000.0), s.flexible)
        assertEquals(s.flexible, s.safeSemester)
    }

    // §19 — scenarios stay ordered favourable >= typical >= cautious.
    @Test
    fun `forecast scenarios stay ordered`() {
        val txs = (1..20).map { tx(400.0 + it * 10, TransactionType.EXPENSE, "Food") } +
            listOf(tx(20000.0, TransactionType.INCOME, "Salary", "Employer"))
        val s = buildSnapshot(base(txs = txs))
        assertTrue(s.forecast.favourable >= s.forecast.typical)
        assertTrue(s.forecast.typical >= s.forecast.cautious)
    }

    // §9 — a bill due tomorrow reserves harder than one due in 28 days.
    @Test
    fun `urgent bills reserve harder`() {
        val s = buildSnapshot(
            base(bills = listOf(bill("Tomorrow", 10000.0, 1), bill("Later", 10000.0, 28)))
        )
        val soon = s.obligations.first { it.name == "Tomorrow" }
        val late = s.obligations.first { it.name == "Later" }
        assertTrue(soon.urgency > late.urgency)
        assertTrue(soon.dailyReserve > late.dailyReserve)
    }

    // §3 — every snapshot explains itself at three levels.
    @Test
    fun `every hero number carries an explanation`() {
        val s = buildSnapshot(base())
        listOf("safeToday", "flexible", "forecast", "netWorth").forEach { key ->
            val e = s.explanations.getValue(key)
            assertTrue(e.why.isNotBlank())
            assertTrue(e.contributors.isNotEmpty())
            assertTrue(e.basis.isNotBlank())
        }
    }

    // §5 — account map covers every shilling exactly once.
    @Test
    fun `accounts partition liquid exactly`() {
        val s = buildSnapshot(base())
        assertEquals(s.liquid, s.accounts.values.fold(Money.ZERO) { a, m -> a + m })
        assertTrue(s.accounts.getValue(Account.BANK) > Money.ZERO)
    }

    // §17 — a KSh 25,000 phone cannot move an established baseline. Twenty
    // calm days quarantine the shock; a one-day history is fragile by nature
    // (any median of one value moves), so the guarantee is pinned on history.
    @Test
    fun `one off purchase cannot distort baselines`() {
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val calmTxs = (1..20).map { tx(225.0, TransactionType.EXPENSE, "Food", ts = now - it * day) } +
            tx(20000.0, TransactionType.INCOME, "Salary", "Employer", PaymentMethod.BANK_TRANSFER)
        val calm = buildSnapshot(base(txs = calmTxs))
        val shock = buildSnapshot(
            base(txs = calmTxs + tx(25000.0, TransactionType.EXPENSE, "Shopping", "Phone shop"))
        )
        assertEquals(calm.essentialAhead, shock.essentialAhead)
    }

    // Quality tracks evidence (§33).
    @Test
    fun `sparse data is labeled sparse`() {
        val s = buildSnapshot(base(txs = listOf(tx(100.0, TransactionType.EXPENSE, "Food"))))
        assertEquals(DataQuality.SPARSE, s.quality)
    }

    // §4 — paired legs move accounts with zero net wealth.
    @Test
    fun `paired transfer legs move accounts wealth neutrally`() {
        val group = "g1"
        val outLeg = tx(4000.0, TransactionType.TRANSFER, "Transfer", "Transfer to Bank")
        val inLeg = tx(4000.0, TransactionType.TRANSFER, "Transfer", "Transfer from M-Pesa")
        val legs = listOf(
            outLeg.copy(accountKind = "M_PESA", transferGroupId = group, transferSide = "OUT"),
            inLeg.copy(accountKind = "BANK", transferGroupId = group, transferSide = "IN")
        )
        val plain = buildSnapshot(base())
        val moved = buildSnapshot(base(txs = base().txs + legs))
        assertEquals(plain.netWorth, moved.netWorth)
        assertEquals(plain.liquid, moved.liquid)
        assertEquals(plain.flexible, moved.flexible)
        assertEquals(Money.of(4000.0), moved.accounts.getValue(Account.BANK) - plain.accounts.getValue(Account.BANK))
        assertTrue(moved.accounts.getValue(Account.M_PESA) < plain.accounts.getValue(Account.M_PESA))
    }

    // §8 — remainder owed drives reserves, never the headline amount.
    @Test
    fun `bill remainder drives reserves`() {
        val full = buildSnapshot(base(bills = listOf(bill("Rent", 8000.0, 5))))
        val part = buildSnapshot(
            base(bills = listOf(
                Bill(
                    name = "Rent", amount = 8000.0,
                    dueDate = System.currentTimeMillis() + 5L * 24 * 60 * 60 * 1000,
                    category = "Bills", amountRemaining = 3000.0
                )
            ))
        )
        assertEquals(Money.of(3000.0), part.upcomingBillsTotal)
        assertTrue(part.flexible > full.flexible)
        assertEquals(Money.of(3000.0), part.obligations.first { it.name == "Rent" }.remaining)
    }

    // §5/§7 — stored account wins; opening equity is held cash, never salary.
    @Test
    fun `stored account kind wins over derivation`() {
        val s = buildSnapshot(
            base(txs = listOf(tx(5000.0, TransactionType.INCOME, "Salary", "Employer", PaymentMethod.CASH)))
        )
        assertTrue(s.accounts.getValue(Account.CASH) > Money.ZERO)
        val stamped = buildSnapshot(
            base(txs = listOf(
                Transaction(
                    amount = 5000.0, type = TransactionType.INCOME, category = "Salary",
                    dateTimestamp = System.currentTimeMillis(), merchant = "Employer",
                    description = "", paymentMethod = PaymentMethod.CASH,
                    source = TransactionSource.MANUAL, accountKind = "BANK"
                )
            ))
        )
        assertEquals(Money.ZERO, stamped.accounts.getValue(Account.CASH))
        assertTrue(stamped.accounts.getValue(Account.BANK) > Money.ZERO)
        assertEquals(s.liquid, stamped.liquid)
    }

    @Test
    fun `opening equity is held but never earned income`() {
        val s = buildSnapshot(
            base(txs = listOf(
                Transaction(
                    amount = 10000.0, type = TransactionType.INCOME, category = "Income",
                    dateTimestamp = System.currentTimeMillis(), merchant = "Opening balance",
                    description = "", paymentMethod = PaymentMethod.CASH,
                    source = TransactionSource.MANUAL, accountKind = "CASH", isOpening = true
                )
            ))
        )
        assertEquals(Money.ZERO, s.monthlyEarnedIncome)
        assertEquals(Money.of(10000.0), s.liquid)
        assertEquals(Money.of(10000.0), s.flexible)
    }

    // §5 — corrupt stored kinds fall back to derivation, never crash math.
    @Test
    fun `corrupt account kind falls back safely`() {
        val s = buildSnapshot(
            base(txs = listOf(
                Transaction(
                    amount = 2000.0, type = TransactionType.EXPENSE, category = "Food",
                    dateTimestamp = System.currentTimeMillis(), merchant = "Kibanda",
                    description = "", paymentMethod = PaymentMethod.MPESA,
                    source = TransactionSource.MANUAL, accountKind = "NOPE"
                )
            ))
        )
        assertTrue(s.accounts.getValue(Account.M_PESA) < Money.ZERO)
    }
}
