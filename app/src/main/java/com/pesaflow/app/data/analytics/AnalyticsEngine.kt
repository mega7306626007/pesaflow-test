package com.pesaflow.app.data.analytics

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.isEarnedIncome
import com.pesaflow.app.data.time.addDays
import com.pesaflow.app.data.time.mondayIndex
import com.pesaflow.app.data.time.rollingDays
import com.pesaflow.app.data.time.startOfWeek
import java.util.Calendar

/**
 * Deep analytics engine: pure Kotlin, no Android imports, fully unit-tested.
 *
 * Categories are bucketed into the 6 canonical PesaFlow groups
 * regardless of the raw label. All amounts are in KSh.
 */
enum class ExpenseCategory(val label: String) {
    FOOD("Food"),
    TRANSPORT("Transport"),
    SHOPPING("Shopping"),
    ENTERTAINMENT("Entertainment"),
    BILLS("Bills"),
    OTHER("Other")
}

data class CategorySummary(
    val category: ExpenseCategory,
    val total: Double,
    val count: Int,
    val avg: Double,
    val max: Double,
    val sharePercent: Int,
    var trendPercent: Double
)

data class MonthlyTrend(
    val monthId: String,
    val total: Double,
    val byCategory: Map<ExpenseCategory, Double>
)

data class SpendingHeatmap(
    /** Rows = weeks in the window, columns = 7 weekdays (Mon-first). Values are daily spend. */
    val grid: Array<DoubleArray>,
    val weekLabels: List<String>
)

data class AnalyticsReport(
    val periodDays: Int,
    val activityDays: Int,
    val totalSpent: Double,
    val totalIncome: Double,
    val netFlow: Double,
    val categorySummaries: List<CategorySummary>,
    val monthlyTrends: List<MonthlyTrend>,
    val heatmap: SpendingHeatmap,
    val topSpender: Pair<ExpenseCategory, Double>,
    val savingsRate: Int,
    val dailyAvg: Double,
    val weekdayProfile: DoubleArray
) {
    companion object {
        internal const val MAX_WEEKS = 8
        internal const val HEATMAP_WEEKS = 8
    }
}

/** Canonical category classification — no raw labels leak out. */
fun classifyCategory(raw: String): ExpenseCategory {
    val low = raw.lowercase().trim()
    return when {
        low.contains("food") || low.contains("uzingo") || low.contains("choma") || low.contains("ugali") || low.contains("mboga") || low.contains("omoko") || low.contains("chai") || low.contains("kahawa") -> ExpenseCategory.FOOD
        low.contains("transport") || low.contains("matatu") || low.contains("boda") || low.contains("uber") || low.contains("taxi") || low.contains("fuel") || low.contains("petrol") || low.contains("diesel") || low.contains("tolla") || low.contains("washer") || low.contains("carwash") -> ExpenseCategory.TRANSPORT
        low.contains("shopping") || low.contains("mall") || low.contains("shop") || low.contains("naivas") || low.contains("quickmart") || low.contains("choppies") || low.contains("clothes") || low.contains("shoes") || low.contains("electronics") || low.contains("phone") -> ExpenseCategory.SHOPPING
        low.contains("entertain") || low.contains("movie") || low.contains("game") || low.contains("club") || low.contains("bar") || low.contains("pub") || low.contains("party") || low.contains("concert") || low.contains("sport") || low.contains("football") -> ExpenseCategory.ENTERTAINMENT
        low.contains("bill") || low.contains("rent") || low.contains("helb") || low.contains("electric") || low.contains("water") || low.contains("internet") || low.contains("wifi") || low.contains("insurance") || low.contains("school") || low.contains("fees") || low.contains("kura") -> ExpenseCategory.BILLS
        else -> ExpenseCategory.OTHER
    }
}

/** Build a full analytics report from all transactions over [periodDays]. */
fun buildAnalyticsReport(
    allTxs: List<Transaction>,
    periodDays: Int = 30,
    now: Long = System.currentTimeMillis()
): AnalyticsReport {
    val window = rollingDays(now, periodDays.coerceAtLeast(1))
    val inWindow = allTxs.filter { it.dateTimestamp in window && it.dateTimestamp <= now }
    val expenses = inWindow.filter { it.type == TransactionType.EXPENSE && !it.isSample }
    val incomes = inWindow.filter { it.isEarnedIncome() && !it.isSample }
    val activityDays = inWindow.filter { !it.isSample && !it.isOpening }.map { tx ->
        Calendar.getInstance().apply { timeInMillis = tx.dateTimestamp }.let {
            "%04d-%02d-%02d".format(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH))
        }
    }.distinct().size
    val totalSpent = expenses.sumOf { it.amount }
    val totalIncome = incomes.sumOf { it.amount }

    val byCat = ExpenseCategory.entries.associateWith { cat ->
        expenses.filter { classifyCategory(it.category) == cat }
    }
    val summaries = ExpenseCategory.entries.map { cat ->
        val rows = byCat[cat]!!
        val total = rows.sumOf { it.amount }
        val count = rows.size
        val avg = if (count > 0) total / count else 0.0
        val max = rows.maxOfOrNull { it.amount } ?: 0.0
        CategorySummary(
            category = cat,
            total = total,
            count = count,
            avg = avg,
            max = max,
            sharePercent = if (totalSpent > 0) ((total / totalSpent) * 100).toInt() else 0,
            trendPercent = 0.0
        )
    }.filter { it.count > 0 }
    // Trend: compare last half vs first half of the period.
    val mid = addDays(window.startInclusive, periodDays / 2)
    val (recent, older) = expenses.partition { it.dateTimestamp >= mid }
    summaries.forEach { s ->
        val recentTotal = byCat[s.category]!!.filter { it.dateTimestamp >= mid }.sumOf { it.amount }
        val olderTotal = byCat[s.category]!!.filter { it.dateTimestamp < mid }.sumOf { it.amount }
        // Dust baselines grow "4000000%": under KSh 100 of history a percent
        // is noise, not signal — report flat instead of a fantasy number.
        s.trendPercent = if (olderTotal < com.pesaflow.app.data.time.DUST_BASELINE) 0.0
        else ((recentTotal - olderTotal) / olderTotal * 100).coerceIn(-999.0, 999.0)
    }
    val topSpender = summaries.maxByOrNull { it.total }?.let { Pair(it.category, it.total) } ?: Pair(ExpenseCategory.OTHER, 0.0)
    val savingsRate = if (totalIncome > 0) ((totalIncome - totalSpent) / totalIncome * 100).toInt() else 0
    val dailyAvg = if (periodDays > 0) totalSpent / periodDays else 0.0

    // Monthly trends.
    val monthMap = mutableMapOf<String, MutableList<Transaction>>()
    expenses.forEach { tx ->
        val cal = Calendar.getInstance().apply { timeInMillis = tx.dateTimestamp }
        val id = "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        monthMap.getOrPut(id) { mutableListOf() }.add(tx)
    }
    val monthlyTrends = monthMap.map { (id, rows) ->
        MonthlyTrend(id, rows.sumOf { it.amount }, ExpenseCategory.entries.associateWith { cat ->
            rows.filter { classifyCategory(it.category) == cat }.sumOf { it.amount }
        })
    }.sortedBy { it.monthId }

    // Heatmap: calendar weeks × 7 weekdays (Mon-first). Rows run newest
    // week first and labels match row-for-row — the old rolling-week rows
    // mixed mid-day buckets with true weekdays and mislabelled every row.
    val heatmapWeeks = AnalyticsReport.Companion.HEATMAP_WEEKS.coerceAtMost(if (periodDays > 0) (periodDays / 7) else 8)
    val grid = Array(heatmapWeeks) { DoubleArray(7) { 0.0 } }
    val weekStarts = (0 until heatmapWeeks).map { addDays(startOfWeek(now), -7 * it) }
    val weekLabels = weekStarts.map { start ->
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        "%02d-%02d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
    }
    expenses.forEach { tx ->
        val weekIdx = weekStarts.indexOf(startOfWeek(tx.dateTimestamp))
        if (weekIdx >= 0) {
            grid[weekIdx][mondayIndex(tx.dateTimestamp)] += tx.amount
        }
    }

    // Weekday profile (Mon-first, average spend per weekday).
    val weekdaySpend = DoubleArray(7)
    val weekdayCount = IntArray(7)
    expenses.forEach { tx ->
        val dow = mondayIndex(tx.dateTimestamp)
        weekdaySpend[dow] += tx.amount
        weekdayCount[dow]++
    }
    val weekdayProfile = DoubleArray(7) { d ->
        if (weekdayCount[d] > 0) weekdaySpend[d] / weekdayCount[d] else 0.0
    }

    return AnalyticsReport(
        periodDays = periodDays,
        activityDays = activityDays,
        totalSpent = totalSpent,
        totalIncome = totalIncome,
        netFlow = totalIncome - totalSpent,
        categorySummaries = summaries,
        monthlyTrends = monthlyTrends,
        heatmap = SpendingHeatmap(grid, weekLabels),
        topSpender = topSpender,
        savingsRate = savingsRate,
        dailyAvg = dailyAvg,
        weekdayProfile = weekdayProfile
    )
}

/** Returns the category that grew fastest over the period (≥20%). */
fun fastestGrowingCategory(report: AnalyticsReport): ExpenseCategory? {
    val growing = report.categorySummaries.filter { it.trendPercent >= 20.0 }
    return growing.maxByOrNull { it.trendPercent }?.category
}

/** Returns categories spending above their own 30-day average (alarm bells). */
fun overBudgetCategories(report: AnalyticsReport, threshold: Double = 1.3): List<CategorySummary> {
    if (report.monthlyTrends.size < 2) return emptyList()
    val prevTotal = report.monthlyTrends.dropLast(1).sumOf { it.total }
    val prevAvg = if (report.monthlyTrends.size > 1) prevTotal / (report.monthlyTrends.size - 1) else 0.0
    if (prevAvg <= 0) return emptyList()
    return report.categorySummaries.filter { cat ->
        val prevCatAvg = report.monthlyTrends.dropLast(1).sumOf { mt -> mt.byCategory[cat.category] ?: 0.0 } / (report.monthlyTrends.size - 1)
        val currentMonthlyProjected = cat.total * (30.0 / report.periodDays)
        prevCatAvg > 0 && currentMonthlyProjected > prevCatAvg * threshold
    }
}
