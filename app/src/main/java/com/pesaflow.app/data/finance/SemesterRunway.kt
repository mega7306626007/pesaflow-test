package com.pesaflow.app.data.finance

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.money.openingBasis
import kotlin.math.ceil

data class SemesterRunway(
    val openingFunds: Double,
    val income: Double,
    val outflows: Double,
    val remainingBeforeCommitments: Double,
    val committed: Double,
    val availableAfterCommitments: Double,
    val dailyPace: Double,
    val weeklyAllowance: Double,
    val projectedEndAfterCommitments: Double,
    val daysElapsed: Int,
    val daysUntilStart: Int,
    val daysRemaining: Int,
    val isUpcoming: Boolean,
    val isEnded: Boolean
)

fun calculateSemesterRunway(
    transactions: List<Transaction>,
    startTimestamp: Long,
    endTimestamp: Long,
    startingFunding: Double,
    committed: Double,
    now: Long
): SemesterRunway? {
    if (startTimestamp <= 0L || endTimestamp <= startTimestamp) return null

    val rows = transactions.filter {
        !it.isSample &&
            it.dateTimestamp >= startTimestamp &&
            it.dateTimestamp < endTimestamp &&
            it.dateTimestamp <= now
    }
    val openingRows = rows.filter { it.type == TransactionType.INCOME && it.isOpening }
    val openingFunds = openingBasis(
        openingRows = openingRows.sumOf { it.amount },
        profileFunding = startingFunding.coerceAtLeast(0.0)
    )
    val income = rows
        .filter { it.type == TransactionType.INCOME && !it.isOpening }
        .sumOf { it.amount }
    val outflows = rows.filter {
        it.type == TransactionType.EXPENSE ||
            it.type == TransactionType.SAVING ||
            it.type == TransactionType.INVESTMENT
    }.sumOf { it.amount }

    val remaining = openingFunds + income - outflows
    val available = remaining - committed.coerceAtLeast(0.0)
    val isUpcoming = now < startTimestamp
    val isEnded = now >= endTimestamp
    val daysElapsed = if (isUpcoming) 0 else
        ((now.coerceAtMost(endTimestamp) - startTimestamp) / DAY_MS).toInt().coerceAtLeast(1)
    val daysUntilStart = if (isUpcoming) {
        ceil((startTimestamp - now).toDouble() / DAY_MS).toInt().coerceAtLeast(0)
    } else 0
    val daysRemaining = when {
        isEnded -> 0
        isUpcoming -> ceil((endTimestamp - startTimestamp).toDouble() / DAY_MS).toInt().coerceAtLeast(0)
        else -> ceil((endTimestamp - now).toDouble() / DAY_MS).toInt().coerceAtLeast(0)
    }
    val dailyPace = if (daysElapsed > 0) outflows / daysElapsed else 0.0
    val weeksRemaining = if (daysRemaining > 0) ceil(daysRemaining / 7.0).toInt() else 0
    val weeklyAllowance = if (weeksRemaining > 0) available / weeksRemaining else available
    val projectedEnd = remaining - dailyPace * daysRemaining - committed.coerceAtLeast(0.0)

    return SemesterRunway(
        openingFunds = openingFunds,
        income = income,
        outflows = outflows,
        remainingBeforeCommitments = remaining,
        committed = committed.coerceAtLeast(0.0),
        availableAfterCommitments = available,
        dailyPace = dailyPace,
        weeklyAllowance = weeklyAllowance,
        projectedEndAfterCommitments = projectedEnd,
        daysElapsed = daysElapsed,
        daysUntilStart = daysUntilStart,
        daysRemaining = daysRemaining,
        isUpcoming = isUpcoming,
        isEnded = isEnded
    )
}

private const val DAY_MS = 24L * 60 * 60 * 1000
