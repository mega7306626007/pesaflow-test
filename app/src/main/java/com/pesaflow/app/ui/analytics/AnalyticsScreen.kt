package com.pesaflow.app.ui.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.analytics.*
import com.pesaflow.app.ui.theme.PpCard
import com.pesaflow.app.ui.theme.PpCardKind
import com.pesaflow.app.ui.theme.PpSectionHeader
import com.pesaflow.app.ui.theme.ppColors
import com.pesaflow.app.ui.theme.ppSpacing
import com.pesaflow.app.ui.theme.ppTypography

@Composable
fun AnalyticsScreen(
    transactions: List<Transaction>,
    periodDays: Int = 30
) {
    val report = remember(transactions, periodDays) {
        buildAnalyticsReport(transactions, periodDays)
    }
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            PpCard(kind = PpCardKind.LARGE) {
                Column(verticalArrangement = Arrangement.spacedBy(ppSpacing.sm)) {
                    PpSectionHeader(
                        title = "$periodDays-day overview",
                        subtitle = "Activity logged on ${report.activityDays} of $periodDays days · missing records can change these insights"
                    )
                    AnalyticsFigure(label = "Total spent", value = "KSh ${report.totalSpent.toInt()}", primary = true)
                    AnalyticsFigure(label = "Total income", value = "KSh ${report.totalIncome.toInt()}")
                    AnalyticsFigure(label = "Net flow", value = "KSh ${report.netFlow.toInt()}")
                    AnalyticsFigure(label = "Savings rate", value = "${report.savingsRate}%")
                    AnalyticsFigure(label = "Daily average", value = "KSh ${report.dailyAvg.toInt()}")
                }
            }
        }
        item {
            PpCard(kind = PpCardKind.LARGE) {
                Column(verticalArrangement = Arrangement.spacedBy(ppSpacing.sm)) {
                    PpSectionHeader(title = "Top category")
                    report.categorySummaries.sortedByDescending { it.total }.take(5).forEach { cat ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${cat.category.label}: KSh ${cat.total.toInt()}",
                                style = ppTypography.labelLarge,
                                color = ppColors.textPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text("${cat.sharePercent}%", style = ppTypography.financialSmall, color = ppColors.gold)
                        }
                    }
                }
            }
        }
        item {
            PpCard(kind = PpCardKind.LARGE) {
                Column(verticalArrangement = Arrangement.spacedBy(ppSpacing.sm)) {
                    PpSectionHeader(title = "Category trends")
                    report.categorySummaries.filter { it.trendPercent != 0.0 }.take(5).forEach { cat ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${cat.category.label}: ${cat.trendPercent.toInt()}%",
                                style = ppTypography.labelLarge,
                                color = ppColors.textPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (cat.trendPercent > 0) "📈" else "📉",
                                style = ppTypography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
        item {
            PpCard(kind = PpCardKind.LARGE) {
                Column(verticalArrangement = Arrangement.spacedBy(ppSpacing.sm)) {
                    PpSectionHeader(title = "Monthly trends")
                    report.monthlyTrends.takeLast(6).forEach { month ->
                        Column(verticalArrangement = Arrangement.spacedBy(ppSpacing.xs)) {
                            Text(month.monthId, style = ppTypography.labelLarge, color = ppColors.gold)
                            Text("Total: KSh ${month.total.toInt()}", style = ppTypography.financialSmall, color = ppColors.textPrimary)
                            ExpenseCategory.entries.forEach { cat ->
                                val v = month.byCategory[cat] ?: 0.0
                                if (v > 0) {
                                    Text(
                                        "  ${cat.label}: KSh ${v.toInt()}",
                                        style = ppTypography.bodySmall,
                                        color = ppColors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            PpCard(kind = PpCardKind.LARGE) {
                Column(verticalArrangement = Arrangement.spacedBy(ppSpacing.sm)) {
                    PpSectionHeader(
                        title = "Spending heatmap",
                        subtitle = "${report.heatmap.weekLabels.size} weeks · rows newest first"
                    )
                    report.heatmap.grid.forEach { week ->
                        Row { week.forEach { v ->
                            Text(if (v > 500) "🔴" else if (v > 200) "🟡" else "🟢")
                        } }
                    }
                }
            }
        }
        item {
            PpCard(kind = PpCardKind.INFO) {
                Column(verticalArrangement = Arrangement.spacedBy(ppSpacing.sm)) {
                    PpSectionHeader(title = "💡 Insights")
                    val growing = fastestGrowingCategory(report)
                    if (growing != null) {
                        Text(
                            "⚠️ ${growing.label} is growing fastest",
                            style = ppTypography.bodyMedium,
                            color = ppColors.textPrimary
                        )
                    }
                    val over = overBudgetCategories(report)
                    if (over.isNotEmpty()) {
                        over.forEach { cat ->
                            Text(
                                "🔥 ${cat.category.label} exceeding budget",
                                style = ppTypography.bodyMedium,
                                color = ppColors.textPrimary
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun AnalyticsFigure(label: String, value: String, primary: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = ppTypography.bodySmall, color = ppColors.textTertiary)
        Text(
            value,
            style = if (primary) ppTypography.financialMedium else ppTypography.financialSmall,
            color = if (primary) ppColors.gold else ppColors.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}
