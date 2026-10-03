package com.pesaflow.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.analytics.detectRecurring
import com.pesaflow.app.data.analytics.predictPaydays
import com.pesaflow.app.data.finance.projectCashFlow
import com.pesaflow.app.data.income.IncomeSource
import com.pesaflow.app.data.income.expectedIncomeLandings
import com.pesaflow.app.data.money.ledgerBalance
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.theme.ExplainChip
import com.pesaflow.app.ui.theme.PpCard
import com.pesaflow.app.ui.theme.PpCardKind
import com.pesaflow.app.ui.theme.PpProgress
import com.pesaflow.app.ui.theme.PpProgressKind
import com.pesaflow.app.ui.theme.PpSectionHeader
import com.pesaflow.app.ui.theme.ppColors
import com.pesaflow.app.ui.theme.ppSpacing
import com.pesaflow.app.ui.theme.ppTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


// "Next 30 days": the app's eyes forward — paydays, open bills and monthly
// commitments projected against money held. Low point and broke date, not
// just rear-view stats.
@Composable
fun Next30DaysCard(
    transactions: List<Transaction>,
    bills: List<com.pesaflow.app.data.models.Bill>,
    hide: Boolean = false,
    // Dynamic horizon: nearest dated inflow (HELB day, allowance day) from
    // the Income tab — the forecast counts down to money, not just month-end.
    inflowDays: Int? = null,
    incomeSources: List<IncomeSource> = emptyList(),
    // Canonical held cash from FinancialSnapshot.liquid. Falls back to local
    // ledger only for unmigrated callers — pass snapshot.liquid going forward.
    heldBalance: Double? = null
) {
    // now captured inside: keying remember on a fresh timestamp recomputed
    // the whole 30-day projection on every recomposition (scroll jank).
    val projection = remember(transactions, bills, incomeSources, heldBalance) {
        val now = System.currentTimeMillis()
        val held = heldBalance ?: ledgerBalance(transactions)
        val declaredInflows = expectedIncomeLandings(incomeSources, now, 30)
        val inferredInflows = predictPaydays(transactions, now).filterNot { inferred ->
            declaredInflows.any { declared ->
                val sameDate = kotlin.math.abs(declared.third - inferred.third) < 4L * 24 * 60 * 60 * 1000
                val scale = maxOf(declared.second, inferred.second, 1.0)
                sameDate && kotlin.math.abs(declared.second - inferred.second) / scale <= 0.15
            }
        }
        projectCashFlow(
            balance = held,
            now = now,
            horizonDays = 30,
            paydays = declaredInflows + inferredInflows,
            bills = bills.filter { it.status != "PAID" && it.paidBy == "ME" },
            recurring = detectRecurring(transactions)
        )
    }
    val heldForLabel = heldBalance ?: ledgerBalance(transactions)
    val fmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val low = projection.lowest
    val lowDate = fmt.format(Date(low.dayStart))
    val lowText = if (hide) "••••" else "KSh ${low.balance.toInt()}"
    val maxBal = projection.days.maxOf { it.balance }.coerceAtLeast(1.0)

    PpCard(kind = PpCardKind.LARGE) {
        Column(verticalArrangement = Arrangement.spacedBy(ppSpacing.sm)) {
            PpSectionHeader(
                title = "Next 30 days 🔮",
                subtitle = "Paydays + bills + subscriptions vs KSh ${if (hide) "••••" else heldForLabel.toInt()} held (Calculated)" +
                    (if (!hide && inflowDays != null) " · inflow in $inflowDays day${if (inflowDays == 1) "" else "s"} 📥" else "")
            )
            // Expected endpoint, always labelled as projection — e.g.
            // "Expected by 2 Nov: -KSh 12,000 (Projected)".
            if (!hide) {
                Text(
                    "Expected by $lowDate: $lowText (Projected)",
                    style = ppTypography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (low.balance < 0) ppColors.error else ppColors.success
                )
            }
            ExplainChip(
                label = "Why this projection?",
                body = "Expected paydays plus known bills and subscriptions, counted against money held, at your recent daily pace. " +
                    "A projection, never a guarantee — irregular income is not assumed."
            )
            val expectedIncome = projection.days
                .flatMap { day -> day.events.filter { it.amount > 0 }.map { day.dayStart to it } }
                .take(3)
            if (expectedIncome.isNotEmpty()) {
                Text(
                    "Expected (not guaranteed): " + expectedIncome.joinToString(" · ") { (date, event) ->
                        val amount = if (hide) "••••" else "KSh ${event.amount.toInt()}"
                        "${event.label} $amount · ${fmt.format(Date(date))}"
                    },
                    style = ppTypography.bodySmall,
                    color = ppColors.textTertiary
                )
            } else {
                Text(
                    "No dated income expected in this window; irregular support is not assumed.",
                    style = ppTypography.bodySmall,
                    color = ppColors.textTertiary
                )
            }
            if (projection.brokeDate != null) {
                Text(
                    "Goes negative around ${fmt.format(Date(projection.brokeDate))} — low $lowText. Move money or delay spending. ⚠️",
                    style = ppTypography.bodyMedium,
                    color = ppColors.error
                )
            } else {
                Text(
                    "Stays positive — low $lowText around $lowDate.",
                    style = ppTypography.bodyMedium,
                    color = ppColors.success
                )
            }
            // Mini projection bars on canvas, today → day 30. Red hangs BELOW
            // the zero line, gold stands above it — the old code drew both
            // upward (red streaks over gold) in raw px, which also shrank the
            // bars on dense screens. Everything scales from size.height.
            val hasNeg = projection.days.any { it.balance < 0 }
            val minBal = if (hasNeg) projection.days.minOf { it.balance } else 0.0
            val denom = (maxBal - minBal).coerceAtLeast(1.0)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(vertical = ppSpacing.xs)
            ) {
                val fullH = size.height
                val zeroY = if (hasNeg) ((maxBal / denom) * fullH).toFloat() else fullH
                val n = projection.days.size
                val barW = size.width / (n * 1.5f)
                projection.days.forEachIndexed { i, day ->
                    val h = ((kotlin.math.abs(day.balance) / denom) * fullH).toFloat()
                    val x = i * (size.width / n.toFloat()) + barW / 4f
                    val color = if (day.balance < 0) ppColors.error else ppColors.gold
                    drawRect(
                        color = color,
                        topLeft = Offset(x, if (day.balance < 0) zeroY else zeroY - h),
                        size = Size(barW, h)
                    )
                }
                drawLine(
                    color = ppColors.border,
                    start = Offset(0f, zeroY),
                    end = Offset(size.width, zeroY),
                    strokeWidth = 1.5f
                )
            }
            Text(
                "D-${projection.days.size - 1} → D-0 · " + when {
                    projection.days.count { it.events.isNotEmpty() } == 0 -> "no known events — quiet month ahead"
                    else -> "${projection.days.count { it.events.isNotEmpty() }} event days (paydays, bills, subscriptions)"
                },
                style = ppTypography.bodySmall,
                color = ppColors.textTertiary
            )
        }
    }
}
