package com.pesaflow.app.data.finance

import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.isEarnedIncome
import com.pesaflow.app.data.models.isFulizaBorrowing
import java.util.Calendar

// Canonical Financial State Engine (§2–§3): RAW INPUTS → snapshot. Pure
// Kotlin, no Android. UI must consume FinancialSnapshot, never re-derive.
// Known honest gaps (not masked): opening-upkeep rows are real ledger INCOME
// here (separate opening-equity account arrives with postings, Phase 4);
// partial bill payments reduce the bill's amountRemaining.
private const val DAY_MS = 24L * 60 * 60 * 1000
private val ESSENTIAL_CATEGORIES = setOf("Rent", "School", "Health", "Food", "Transport")

fun fulizaOutstanding(txs: List<Transaction>): Double =
    txs.filter { !it.isSample }.sumOf { tx ->
        when {
            tx.isFulizaBorrowing() -> tx.amount
            tx.type == TransactionType.EXPENSE &&
                (tx.subcategory.equals("Fuliza repayment", ignoreCase = true) ||
                    tx.merchant.contains("fuliza", ignoreCase = true)) -> -tx.amount
            else -> 0.0
        }
    }.coerceAtLeast(0.0)

fun accountKindFor(method: PaymentMethod, merchant: String, type: TransactionType): Account {
    if (merchant.contains("ziidi", ignoreCase = true) &&
        (type == TransactionType.SAVING || type == TransactionType.INCOME)
    ) return Account.ZIIDI
    return when (method) {
        PaymentMethod.MPESA -> Account.M_PESA
        PaymentMethod.CASH -> Account.CASH
        PaymentMethod.BANK_TRANSFER -> Account.BANK
        PaymentMethod.AIRTIME -> Account.M_PESA
        PaymentMethod.OTHER -> Account.OTHER
    }
}

// Stored accountKind wins (stamped at write time, backfilled by migration
// 13→14); derivation is the legacy fallback only.
fun accountOf(tx: Transaction): Account {
    if (tx.accountKind.isNotBlank()) {
        try {
            return Account.valueOf(tx.accountKind)
        } catch (e: Exception) {
            // Corrupt value: fall through to derivation, never crash math.
        }
    }
    return accountKindFor(tx.paymentMethod, tx.merchant, tx.type)
}

fun median(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val s = values.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}

fun percentile(values: List<Double>, pct: Double): Double {
    if (values.isEmpty()) return 0.0
    val s = values.sorted()
    val idx = ((pct / 100.0) * (s.size - 1)).toInt().coerceIn(0, s.size - 1)
    return s[idx]
}

internal fun monthBounds(nowMs: Long): Pair<Long, Long> {
    val c = Calendar.getInstance().apply { timeInMillis = nowMs }
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis to nowMs
}

internal fun daysLeftInMonth(nowMs: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = nowMs }
    return (c.getActualMaximum(Calendar.DAY_OF_MONTH) - c.get(Calendar.DAY_OF_MONTH)).coerceAtLeast(0)
}

fun buildSnapshot(input: SnapshotInput): FinancialSnapshot {
    val now = input.nowMs
    val real = input.txs.filter { !it.isSample && it.dateTimestamp <= now }
    val flows = real.filter { it.type != TransactionType.TRANSFER }
    val (monthStart, _) = monthBounds(now)

    // Quality from evidence, never assumed.
    val spanDays = if (real.isEmpty()) 0 else ((now - real.minOf { it.dateTimestamp }) / DAY_MS).toInt() + 1
    val quality = when {
        real.size >= 30 && spanDays >= 21 -> DataQuality.FULL
        real.size >= 10 -> DataQuality.PARTIAL
        else -> DataQuality.SPARSE
    }

    // Accounts + liquid + assets. Transfers move between accounts with zero
    // net wealth effect; savings/investments are assets, never spending.
    val accounts = Account.values().associateWith { Money.ZERO }.toMutableMap()
    var spendableDelta = Money.ZERO
    var savedWealth = Money.ZERO
    flows.forEach { tx ->
        val m = Money.of(tx.amount)
        val acct = accountOf(tx)
        when (tx.type) {
            TransactionType.INCOME -> {
                val ziidiMove = tx.subcategory.equals("Ziidi transfer", ignoreCase = true)
                if (ziidiMove) {
                    accounts[Account.M_PESA] = accounts.getValue(Account.M_PESA) + m
                    accounts[Account.ZIIDI] = accounts.getValue(Account.ZIIDI) - m
                } else {
                    accounts[acct] = accounts.getValue(acct) + m
                }
                spendableDelta += m
                if (ziidiMove) savedWealth -= m
            }
            TransactionType.EXPENSE -> {
                accounts[acct] = accounts.getValue(acct) - m
                spendableDelta -= m
            }
            TransactionType.SAVING, TransactionType.INVESTMENT -> {
                val ziidiDeposit = tx.type == TransactionType.SAVING &&
                    tx.subcategory.equals("Ziidi transfer", ignoreCase = true)
                if (ziidiDeposit) {
                    accounts[Account.M_PESA] = accounts.getValue(Account.M_PESA) - m
                    accounts[Account.ZIIDI] = accounts.getValue(Account.ZIIDI) + m
                } else {
                    accounts[acct] = accounts.getValue(acct) - m
                }
                spendableDelta -= m
                savedWealth += m
            }
            TransactionType.TRANSFER -> Unit // unpaired legacy: fully excluded; paired legs handled below
        }
    }
    // Paired internal moves: OUT leaves the source account, IN arrives in the
    // destination — zero net wealth, but each account shows its truth.
    real.filter { it.type == TransactionType.TRANSFER && it.transferGroupId != null }.forEach { tx ->
        val m = Money.of(tx.amount)
        val acct = accountOf(tx)
        when (tx.transferSide) {
            "OUT" -> {
                accounts[acct] = accounts.getValue(acct) - m
                spendableDelta -= m
            }
            "IN" -> {
                accounts[acct] = accounts.getValue(acct) + m
                spendableDelta += m
            }
            else -> Unit
        }
    }
    // Liquid is NEVER floored: flooring printed "Held KSh 0" next to a
    // "Ledger -KSh 32,800" on the same screen. A negative ledger is real
    // (more confirmed out than in) — the reconcile flow explains and fixes
    // it; hiding it breaks trust in every number. Downstream guards
    // (flexible, safe-figures) floor their own outputs where spending
    // advice, not accounting truth, is shown.
    val liquid = spendableDelta
    val totalAssets = liquid + savedWealth
    val debtsOwed = input.debts.filter { it.status != "PAID" && it.direction == "I_OWE" }
    val trackedFuliza = fulizaOutstanding(real)
    val totalLiabilities = Money.of(debtsOwed.sumOf { it.amount } + trackedFuliza)
    val netWorth = totalAssets - totalLiabilities

    // Monthly EARNED income: real income events in-period. Samples excluded,
    // transfers excluded, opening equity excluded (§7) — cash is still cash
    // in liquid, it is simply never called salary.
    val monthlyEarnedIncome = Money.of(
        flows.filter {
            it.isEarnedIncome() && it.dateTimestamp >= monthStart
        }.sumOf { it.amount }
    )

    // Obligations: time-aware reserves + urgency (§8–§9). Paid bills are gone;
    // only real payment events move cash (bill rows alone never book money).
    val openBills = input.bills.filter { it.status != "PAID" && it.paidBy == "ME" }
    val obligations = openBills.map { b ->
        val owed = b.amountRemaining.takeIf { it > 0 } ?: b.amount
        val days = ((b.dueDate - now) / DAY_MS).toInt()
        val overdue = b.dueDate < now
        val essential = b.category in ESSENTIAL_CATEGORIES ||
            (input.profile.debtLevel != DebtLevel.NONE && b.category == "Bills")
        val urgency = (if (essential) 2.0 else 1.0) * (if (overdue) 3.0 else 1.0) *
            owed / maxOf(days, 1).toDouble()
        ObligationView(
            name = b.name,
            amount = Money.of(b.amount),
            remaining = Money.of(owed),
            dueInDays = days,
            essential = essential,
            overdue = overdue,
            urgency = urgency,
            dailyReserve = Money.of(owed / maxOf(days, 1).toDouble())
        )
    }.sortedByDescending { it.urgency }
    val upcomingBillsTotal = Money.of(openBills.sumOf { b -> b.amountRemaining.takeIf { it > 0 } ?: b.amount })
    val upcomingDebtTotal = Money.of(debtsOwed.sumOf { it.amount } + trackedFuliza)

    // HELB semester treatment (§15): split fees vs upkeep only when the user
    // gave both figures. Never invent a split.
    val helb = Money.of(input.helbExpected)
    val fees = Money.of(input.feesAmount)
    val feesCovered = if (input.helbExpected > 0 && input.feesAmount > 0) minOf(helb, fees) else Money.ZERO
    val upkeep = if (input.helbExpected > 0) (helb - feesCovered).coerceAtLeast(Money.ZERO) else Money.ZERO
    // A fee bill is the authoritative outstanding amount when present. Do not
    // reserve the profile's fee estimate again on top of that same obligation.
    val hasTrackedFeeBill = openBills.any { bill ->
        listOf(bill.name, bill.category).any { text ->
            text.lowercase().split(Regex("[^a-z0-9]+")).any {
                it == "fee" || it == "fees" || it == "tuition"
            }
        }
    }
    val upcomingFees = if (hasTrackedFeeBill) Money.ZERO else (fees - feesCovered).coerceAtLeast(Money.ZERO)

    // Goal reservations (§10): remaining + recommended pace, labeled projected.
    val goalReservations = input.goals.map { g ->
        val remaining = (g.targetAmount - g.currentAmount).coerceAtLeast(0.0)
        val days = ((g.targetTimestamp - now) / DAY_MS).toInt().coerceAtLeast(1)
        val perDay = remaining / days
        ReservationView(
            label = g.title,
            amount = Money.of(remaining),
            projectedNote = "Projected pace ≈ ${MoneyFormatter.compact(Money.of(perDay))}/day to stay on schedule."
        )
    }
    val reservedGoals = goalReservations.fold(Money.ZERO) { acc, r -> acc + r.amount }
    val reserved = reservedGoals + upcomingFees
    val committed = upcomingBillsTotal + upcomingDebtTotal + reserved
    val flexible = (liquid - committed).coerceAtLeast(Money.ZERO)

    // Income reliability (§14–§16): observed cadence over the last 4 month
    // slots. Hustle is capped at VARIABLE — never salary. No calibrated
    // percentages are shown, only honest bands (§42).
    val incomeReliabilities = input.incomeSources.map { s ->
        val landed = monthlyLandedFor(s.label, s.kind, real, monthStart)
        val slots = (0..3).count { back ->
            val c = Calendar.getInstance().apply { timeInMillis = now }
            c.add(Calendar.MONTH, -back)
            val y = c.get(Calendar.YEAR)
            val m = c.get(Calendar.MONTH)
            real.any {
                it.isEarnedIncome() && matchesSource(it, s.label, s.kind) &&
                    Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }.let {
                        it.get(Calendar.YEAR) == y && it.get(Calendar.MONTH) == m
                    }
            }
        }
        val band = when {
            s.kind == "HUSTLE" && slots >= 3 -> Reliability.VARIABLE
            s.kind == "HUSTLE" -> if (slots >= 1) Reliability.VARIABLE else Reliability.POSSIBLE
            s.kind == "OTHER" && slots == 0 -> Reliability.UNKNOWN
            slots >= 4 -> Reliability.CONFIRMED
            slots == 3 -> Reliability.LIKELY
            slots >= 1 -> Reliability.VARIABLE
            else -> Reliability.POSSIBLE
        }
        IncomeReliability(
            label = s.label.ifBlank { s.kind },
            reliability = if (s.kind.startsWith("HELB")) {
                if (slots >= 1) Reliability.LIKELY else Reliability.POSSIBLE
            } else band,
            expectedMonthly = Money.of(com.pesaflow.app.data.income.IncomeSourceStore.budgetedMonthly(s)),
            landedThisMonth = Money.of(landed)
        )
    }
    val reliableMonthly = incomeReliabilities
        .filter { it.reliability == Reliability.CONFIRMED }
        .fold(Money.ZERO) { acc, r -> acc + r.expectedMonthly }
    val expectedMonthly = incomeReliabilities
        .filter { it.reliability == Reliability.CONFIRMED || it.reliability == Reliability.LIKELY }
        .fold(Money.ZERO) { acc, r -> acc + r.expectedMonthly }

    // Daily needs (§17 rewritten): YOUR burn first, YOUR word second, zero
    // invention third. burn = robust 28-day median of your own daily spend
    // (one-offs quarantined — one phone can't move it). declaredDaily = your
    // stated monthly envelopes / 30 (ALL master wins when set, else category
    // sum). needsDaily = the bigger of the two, so an 800/day life and a
    // 200/day life each get honest math — no fixed "truths" about what a day
    // should cost. Cold start (no habit, no statement): needs 0 and quality
    // says SPARSE instead of inventing a number.
    val paces = dailyPaces(flows, now)
    val burn = paces.typical
    // Declared daily shares one rule with every budget surface
    // (masterOrCategoryTotal: latest active ALL wins, else active category
    // sum). firstOrNull froze the oldest ALL forever — expired rows kept
    // capping the hero figure.
    val declaredDaily = masterOrCategoryTotal(input.budgets, BudgetType.MONTHLY, now) / 30.0
    val needsDaily = maxOf(burn, declaredDaily)

    // Horizons (§11, §26): each figure states its own window. Primary horizon
    // follows the income pattern: salary → next income, HELB → semester-aware
    // month, hustle-only (nothing confirmed/likely) → week, else month.
    val hasSalary = incomeReliabilities.any {
        (it.reliability == Reliability.CONFIRMED || it.reliability == Reliability.LIKELY) &&
            (it.label.contains("salary", true) || it.label.contains("job", true))
    }
    val hasHelb = input.profile.incomeKinds.any { it.contains("HELB", true) } || input.helbExpected > 0
    val hasSteady = incomeReliabilities.any {
        it.reliability == Reliability.CONFIRMED || it.reliability == Reliability.LIKELY
    }
    val primaryHorizon = when {
        hasSalary -> Horizon.UNTIL_NEXT_INCOME
        hasHelb -> Horizon.SEMESTER
        !hasSteady -> Horizon.WEEK
        else -> Horizon.MONTH
    }
    // Assurance-priced buffer (§14): the buffer buys days of YOUR burn, sized
    // by how assured income is. assurance = confirmed share of expected
    // monthly income. Fully assured salary → 1 shelter day; nothing assured →
    // 7. Long commutes (+2: fares can't flex) and conservative stomachs (+2)
    // widen it; flexible shrinks it (−1, min 0). Policy knobs, disclosed —
    // the point is the buffer derives from your income profile, never a flat %.
    val assurance = run {
        val assured = reliableMonthly.toDouble()
        val expected = expectedMonthly.toDouble()
        when {
            expected > 0 -> (assured / expected).coerceIn(0.0, 1.0)
            assured > 0 -> 1.0
            else -> 0.0
        }
    }
    var bufferDays = 1 + 6 * (1 - assurance)
    if (input.profile.commute == Commute.LONG) bufferDays += 2
    when (input.profile.risk) {
        RiskPreference.CONSERVATIVE -> bufferDays += 2
        RiskPreference.FLEXIBLE -> bufferDays -= 1
        else -> Unit
    }
    bufferDays = bufferDays.coerceAtLeast(0.0)
    val riskBuffer = Money.of(needsDaily * bufferDays)
    fun horizonValue(days: Int, incomeAhead: Money, obligs: List<ObligationView>): Money {
        val due = obligs.filter { it.dueInDays <= days }.fold(Money.ZERO) { acc, o -> acc + o.remaining }
        return (liquid + incomeAhead - due - reserved - Money.of(needsDaily * days) - riskBuffer)
            .coerceAtLeast(Money.ZERO)
    }
    val monthDaysLeft = daysLeftInMonth(now)
    val untilIncomeDays = if (hasSteady) 30 else 7
    val safeToday = horizonValue(1, Money.ZERO, obligations)
    val safeWeek = horizonValue(7, reliableMonthly * (7.0 / 30), obligations)
    val safeUntilIncome = horizonValue(untilIncomeDays, reliableMonthly * (untilIncomeDays / 30.0), obligations)
    val safeMonth = horizonValue(monthDaysLeft, reliableMonthly * (monthDaysLeft / 30.0), obligations)
    // flexible already subtracts bills, debts, fee reserves, and goal reserves.
    val safeSemester = flexible

    // Forecast scenarios (§19, Phase 9 engine): robust daily paces, one-offs
    // quarantined. Output is month-end FLEXIBLE money.
    val billsDue = obligations.filter { it.dueInDays <= monthDaysLeft }.fold(Money.ZERO) { acc, o -> acc + o.remaining }
    val paceTypical = paces.typical
    val forecast = ForecastRange(
        cautious = projectMonthEnd(flexible, paces.cautious, reliableMonthly * (monthDaysLeft / 30.0), billsDue, monthDaysLeft),
        typical = projectMonthEnd(flexible, paces.typical, expectedMonthly * (monthDaysLeft / 30.0), billsDue, monthDaysLeft),
        favourable = projectMonthEnd(flexible, paces.favourable, expectedMonthly * (monthDaysLeft / 30.0), billsDue, monthDaysLeft)
    )

    val explanations = mapOf(
        "safeToday" to explainSafeToday(
            safeToday, liquid, committed, needsDaily, riskBuffer,
            "${real.size} ledger rows over ~$spanDays days", quality
        ),
        "flexible" to explainFlexible(
            flexible, liquid, upcomingBillsTotal, upcomingDebtTotal, reserved,
            obligations.size, goalReservations.size, quality
        ),
        "forecast" to explainForecast(
            forecast.typical, paceTypical, forecast.cautious, forecast.favourable, quality
        ),
        "netWorth" to explainNetWorth(netWorth, totalAssets, totalLiabilities, quality)
    )

    return FinancialSnapshot(
        liquid = liquid,
        accounts = accounts,
        committed = committed,
        reserved = reserved,
        flexible = flexible,
        safeToday = safeToday,
        safeWeek = safeWeek,
        safeUntilIncome = safeUntilIncome,
        safeMonth = safeMonth,
        safeSemester = safeSemester,
        primaryHorizon = primaryHorizon,
        reliableIncomeAhead = reliableMonthly,
        expectedIncomeAhead = expectedMonthly,
        essentialAhead = Money.of(needsDaily * 30),
        obligations = obligations,
        upcomingBillsTotal = upcomingBillsTotal,
        upcomingDebtTotal = upcomingDebtTotal,
        upcomingFees = upcomingFees,
        goalReservations = goalReservations,
        riskBuffer = riskBuffer,
        totalAssets = totalAssets,
        totalLiabilities = totalLiabilities,
        netWorth = netWorth,
        monthlyEarnedIncome = monthlyEarnedIncome,
        forecast = forecast,
        projectedEndFlexible = forecast.typical,
        incomeReliabilities = incomeReliabilities,
        helbExpected = helb,
        helbFeesCovered = feesCovered,
        helbUpkeep = upkeep,
        quality = quality,
        explanations = explanations
    )
}

private fun monthlyLandedFor(
    label: String,
    kind: String,
    txs: List<Transaction>,
    monthStart: Long
): Double {
    return txs.filter {
        it.isEarnedIncome() && it.dateTimestamp >= monthStart && matchesSource(it, label, kind)
    }.sumOf { it.amount }
}

private fun matchesSource(tx: Transaction, label: String, kind: String): Boolean {
    if (tx.isOpening || tx.isSample) return false
    if (label.isNotBlank() && tx.merchant.contains(label, ignoreCase = true)) return true
    val keys = when {
        kind.contains("HELB") -> listOf("helb")
        kind == "JOB" -> listOf("salary", "wage", "pay")
        kind == "HUSTLE" -> listOf("freelance", "gig", "kibarua", "hustle")
        kind == "SCHOLARSHIP" -> listOf("scholarship", "bursary")
        kind == "PARENT" || kind == "GUARDIAN" -> listOf("allowance", "upkeep", "support")
        else -> emptyList()
    }
    return keys.any { tx.merchant.contains(it, ignoreCase = true) || tx.category.contains(it, ignoreCase = true) }
}
