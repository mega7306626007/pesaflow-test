package com.pesaflow.app.data.academic

import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.isEarnedIncome
import com.pesaflow.app.data.time.startOfDay as canonicalDayStart
import com.pesaflow.app.data.time.startOfWeek as canonicalWeekStart
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// University money runs on semesters, not calendar months. The academic year
// runs September -> August: Sem 1 Sep-Dec, Sem 2 Jan-Apr, long break May-Aug.
// Everything that averages, profiles or predicts takes a ScanWindow from
// here, so holiday months never silently poison semester budgets.
//
// Grounded Aug/Sep 2026: UoN first-years reported Aug 18, KU Aug 26,
// JKUAT/Kabarak/KCA Sep 1, lectures ~Sep 7; continuing students resumed
// early September after the May-Aug break.
const val DAY_MS = 24L * 60 * 60 * 1000

enum class SemKind { SEM1, SEM2, BREAK }

data class SemesterWindow(
    val startMs: Long,
    val endMs: Long,
    val label: String,
    val kind: SemKind
)

data class ScanWindow(
    val startMs: Long,
    val endMs: Long,
    val label: String,
    val includesBreak: Boolean,
    val firstYear: Boolean
)

data class SpendProfile(
    val dailyAvg: Double,
    val weeklyAvg: Double,
    val monthlyAvg: Double,
    val byCategoryMonthly: Map<String, Double>,
    val activeDays: Int,
    val windowDays: Long,
    val schoolDays: Set<Int>,
    val fareDailyAvg: Double,
    val fareWindowHint: String?,
    val rentMonthly: Double,
    val rentDay: Int?,
    val paydayAmount: Double?,
    val paydayDay: Int?,
    val paydayWho: String?,
    val skewed: Boolean,
    val skewNote: String?,
    val excludedWeeks: Int = 0,
    val excludedTotal: Double = 0.0
)

fun dayMs(year: Int, month0: Int, day: Int): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.YEAR, year)
    c.set(Calendar.MONTH, month0)
    c.set(Calendar.DAY_OF_MONTH, day)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun tsMs(year: Int, month0: Int, day: Int, hour: Int): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.YEAR, year)
    c.set(Calendar.MONTH, month0)
    c.set(Calendar.DAY_OF_MONTH, day)
    c.set(Calendar.HOUR_OF_DAY, hour)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun academicStartYearFor(ts: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    val y = c.get(Calendar.YEAR)
    return if (c.get(Calendar.MONTH) >= Calendar.SEPTEMBER) y else y - 1
}

fun semesterWindowsFor(academicStartYear: Int): List<SemesterWindow> {
    val sem1Start = dayMs(academicStartYear, Calendar.SEPTEMBER, 1)
    val sem2Start = dayMs(academicStartYear + 1, Calendar.JANUARY, 1)
    val breakStart = dayMs(academicStartYear + 1, Calendar.MAY, 1)
    val nextYear = dayMs(academicStartYear + 1, Calendar.SEPTEMBER, 1)
    return listOf(
        SemesterWindow(sem1Start, sem2Start, "Sem 1 · Sep–Dec $academicStartYear", SemKind.SEM1),
        SemesterWindow(sem2Start, breakStart, "Sem 2 · Jan–Apr ${academicStartYear + 1}", SemKind.SEM2),
        SemesterWindow(breakStart, nextYear, "Long break · May–Aug ${academicStartYear + 1}", SemKind.BREAK)
    )
}

fun windowFor(ts: Long): SemesterWindow =
    semesterWindowsFor(academicStartYearFor(ts)).first { ts in it.startMs until it.endMs }

// 2026/27 first-year reporting dates. Matched by containment (longest key
// first so "JKUAT" beats "KU"); anything unknown falls back to Sep 1.
private fun reportingDates(): Map<String, Long> = mapOf(
    "TECHNICAL UNIVERSITY" to dayMs(2026, Calendar.SEPTEMBER, 1),
    "MASINDE MULIRO" to dayMs(2026, Calendar.AUGUST, 20),
    "MOUNT KENYA" to dayMs(2026, Calendar.AUGUST, 24),
    "KABARAK" to dayMs(2026, Calendar.SEPTEMBER, 1),
    "NAIROBI" to dayMs(2026, Calendar.AUGUST, 18),
    "KENYATTA" to dayMs(2026, Calendar.AUGUST, 26),
    "EGERTON" to dayMs(2026, Calendar.AUGUST, 17),
    "KIBABII" to dayMs(2026, Calendar.AUGUST, 17),
    "MMUST" to dayMs(2026, Calendar.AUGUST, 20),
    "JKUAT" to dayMs(2026, Calendar.SEPTEMBER, 1),
    "RONGO" to dayMs(2026, Calendar.AUGUST, 24),
    "MASENO" to dayMs(2026, Calendar.AUGUST, 18),
    "KCA" to dayMs(2026, Calendar.SEPTEMBER, 1),
    "TUK" to dayMs(2026, Calendar.SEPTEMBER, 1),
    "MKU" to dayMs(2026, Calendar.AUGUST, 24),
    "MOI" to dayMs(2026, Calendar.AUGUST, 24),
    "UON" to dayMs(2026, Calendar.AUGUST, 18),
    "KU" to dayMs(2026, Calendar.AUGUST, 26)
)

fun uniStartMs(university: String, nowMs: Long = System.currentTimeMillis()): Long {
    val u = university.uppercase(Locale.US)
    if (academicStartYearFor(nowMs) == 2026) {
        reportingDates().entries
            .sortedByDescending { it.key.length }
            .firstOrNull { u.contains(it.key) }
            ?.let { return it.value }
    }
    return dayMs(academicStartYearFor(nowMs), Calendar.SEPTEMBER, 1)
}

// Any May–Jul day, or Aug 1–16, inside [startMs, endMs) means holiday
// spending is mixed in — averages are skewed until the user confirms
// term-time life. Reporting starts ~Aug 17, so late August is term time.
fun overlapsBreak(startMs: Long, endMs: Long): Boolean {
    if (endMs <= startMs) return false
    var t = startMs
    var guard = 0
    while (t < endMs && guard++ < 400) {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        val m = c.get(Calendar.MONTH)
        if (m in Calendar.MAY..Calendar.JULY) return true
        if (m == Calendar.AUGUST && c.get(Calendar.DAY_OF_MONTH) < 17) return true
        t += DAY_MS
    }
    return false
}

private fun fmtDay(ts: Long): String =
    SimpleDateFormat("d MMM", Locale.US).format(java.util.Date(ts))

// First-years: from reporting day (never before uni life began), capped at
// ~5 months back. Returning: this semester from Sep 1, same cap.
fun scanWindow(yearOfStudy: Int, university: String, nowMs: Long = System.currentTimeMillis()): ScanWindow {
    val cap = nowMs - 150 * DAY_MS
    return if (yearOfStudy <= 1) {
        val start = maxOf(uniStartMs(university, nowMs), cap)
        ScanWindow(start, nowMs, "Since reporting · ${fmtDay(start)}", overlapsBreak(start, nowMs), true)
    } else {
        val sept1 = dayMs(academicStartYearFor(nowMs), Calendar.SEPTEMBER, 1)
        val start = maxOf(sept1, cap)
        ScanWindow(start, nowMs, "This semester · Sep–now", overlapsBreak(start, nowMs), false)
    }
}

// Last academic year, Sep 1 -> May 1 (Sem 1 + Sem 2, never the break): the
// Sept–April comparison base for returning students.
fun priorAcademicWindow(nowMs: Long = System.currentTimeMillis()): ScanWindow {
    val startYear = academicStartYearFor(nowMs) - 1
    val start = dayMs(startYear, Calendar.SEPTEMBER, 1)
    val end = dayMs(startYear + 1, Calendar.MAY, 1)
    return ScanWindow(start, end, "Last year · Sep–Apr", false, false)
}

// Onboarding answers are pipe-delimited: year=1, classdays=Mon,Tue,...
fun parseYearOfStudy(answers: String): Int =
    Regex("year=(\\d)").find(answers)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 6) ?: 1

private val CLASS_DAY_NAMES = setOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun parseClassDays(answers: String): Set<String> {
    val v = Regex("classdays=([^|]*)").find(answers)?.groupValues?.get(1).orEmpty()
    val days = v.split(",").map { it.trim() }.filter { it in CLASS_DAY_NAMES }.toSet()
    return if (days.isEmpty()) setOf("Mon", "Tue", "Wed", "Thu", "Fri") else days
}

// Re-learn window: clamp the scan window to the cutoff so daily math stays
// honest after the user drops an old route. Pure — the button owns prefs.
fun relearnWindow(win: ScanWindow, cutoffMs: Long): ScanWindow {
    if (cutoffMs <= win.startMs) return win
    return win.copy(
        startMs = cutoffMs,
        label = win.label + " · re-learned"
    )
}

// One-time September/January nudge for existing users whose answers predate
// class-day capture: new term, no class days on file — ask once, in context,
// never for fresh onboardings (they always carry classdays=) or blanks.
fun needsSemesterReset(answers: String, nowMs: Long = System.currentTimeMillis()): Boolean {
    if (answers.isBlank() || answers.contains("classdays=")) return false
    val m = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.MONTH)
    return m == Calendar.SEPTEMBER || m == Calendar.JANUARY
}

data class FreeDayDividend(
    val freeDows: List<Int>,
    val perDay: Double,
    val total: Double
)

// Free-day dividends: school days in the last 7 complete days with zero
// spend at all. Each one kept its fare home — name the days and the total
// so the savings are visible instead of invisible.
fun freeDayDividends(
    expenses: List<Transaction>,
    schoolDays: Set<Int>,
    farePerDay: Double,
    nowMs: Long = System.currentTimeMillis()
): FreeDayDividend? {
    if (schoolDays.isEmpty() || farePerDay <= 0) return null
    val today = dayStart(nowMs)
    val free = (1..7).mapNotNull { back ->
        val day = today - back * DAY_MS
        val c = Calendar.getInstance().apply { timeInMillis = day }
        val dow = c.get(Calendar.DAY_OF_WEEK)
        if (dow !in schoolDays) return@mapNotNull null
        val spent = expenses.any {
            it.type == TransactionType.EXPENSE && dayStart(it.dateTimestamp) == day
        }
        if (spent) null else dow
    }
    if (free.isEmpty()) return null
    return FreeDayDividend(free, farePerDay, farePerDay * free.size)
}

data class ExamSignal(
    val printingRecent: Double,
    val printingPrior: Double,
    val nightRecent: Int,
    val nightPrior: Int
)

// CAT/exam weeks smell like printing + late-night M-Pesa. Compare the last
// 14 days against the prior 14: printing doubled (off a real base) together
// with busier nights means study season — offer to pause fare guards instead
// of misreading the fortnight as a new routine.
fun detectExamMode(expenses: List<Transaction>, nowMs: Long = System.currentTimeMillis()): ExamSignal? {
    fun side(startMs: Long, endMs: Long): Pair<Double, Int> {
        val inSide = expenses.filter {
            it.type == TransactionType.EXPENSE && it.dateTimestamp in startMs until endMs
        }
        val printing = inSide.filter { it.category.equals("Printing", ignoreCase = true) }
            .sumOf { it.amount }
        val night = inSide.count {
            val h = Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }.get(Calendar.HOUR_OF_DAY)
            h >= 21 || h <= 4
        }
        return printing to night
    }
    val (newPrint, newNight) = side(nowMs - 14 * DAY_MS, nowMs)
    val (oldPrint, oldNight) = side(nowMs - 28 * DAY_MS, nowMs - 14 * DAY_MS)
    if (oldPrint <= 0 || newPrint < 2 * oldPrint) return null
    if (newNight < 3 || newNight <= oldNight) return null
    return ExamSignal(newPrint, oldPrint, newNight, oldNight)
}

// Sunday check-in bookkeeping: one prefs key per calendar week, card gated
// to Sundays so it asks at most once a week and never nags mid-week.
fun weekKey(nowMs: Long = System.currentTimeMillis()): String {
    val c = Calendar.getInstance().apply { timeInMillis = nowMs }
    return "${c.get(Calendar.YEAR)}-W${c.get(Calendar.WEEK_OF_YEAR)}"
}

fun isSunday(nowMs: Long = System.currentTimeMillis()): Boolean =
    Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY

fun PendingTransaction.asTransaction(): Transaction = Transaction(
    amount = amount,
    type = type,
    category = category,
    dateTimestamp = dateTimestamp,
    merchant = merchant,
    paymentMethod = paymentMethod,
    source = source,
    sourceTransactionId = sourceTransactionId,
    confirmed = false
)

private fun dayStart(ts: Long): Long = canonicalDayStart(ts)

private fun weekStartMonday(ts: Long): Long = canonicalWeekStart(ts)

// School days are observed, not assumed: days of the week with Transport
// spend in at least half the observed weeks (min 2). Saturday counts if the
// ledger says so; Sunday never does.
fun inferSchoolDays(expenses: List<Transaction>, window: ScanWindow): Set<Int> {
    val weeks = mutableSetOf<Long>()
    var cursor = weekStartMonday(window.startMs)
    while (cursor < window.endMs) {
        weeks.add(cursor)
        cursor += 7 * DAY_MS
    }
    if (weeks.isEmpty()) return emptySet()
    val need = maxOf(2, weeks.size / 2)
    val transport = expenses.filter {
        it.type == TransactionType.EXPENSE &&
            it.category.equals("Transport", ignoreCase = true) &&
            it.dateTimestamp in window.startMs until window.endMs
    }
    return (Calendar.MONDAY..Calendar.SATURDAY).filter { dow ->
        val activeWeeks = transport
            .filter { Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }.get(Calendar.DAY_OF_WEEK) == dow }
            .map { weekStartMonday(it.dateTimestamp) }
            .toSet().size
        activeWeeks >= need
    }.toSet()
}

// Given current observed school days and a proposed new set (from a Sunday
// one‑tap check‑in), return the merged set as day labels. Only days the user
// confirms are kept — this is the single source of truth for school-day
// scheduling.
fun updateSchoolDays(current: Set<String>, proposed: Set<String>): Set<String> {
    // Labels "Mon".."Sun" -> dow integers 2..1 (Sun=2..Mon=2...Sun=1).
    val labelToDow = mapOf("Sun" to 1, "Mon" to 2, "Tue" to 3, "Wed" to 4, "Thu" to 5, "Fri" to 6, "Sat" to 7)
    val dowToLabel = mapOf(1 to "Sun", 2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat")
    // Convert proposed labels to dow integers, silently dropping unknown labels
    val proposedDows = proposed.mapNotNull { labelToDow[it] }
    // Convert dow integers back to day labels; unknown ints become null and are dropped
    val proposedLabels = proposedDows.mapNotNull { dowToLabel[it] }.toSet()
    // Keep current as base, add any newly confirmed labels, never remove without
    // an explicit "remove" signal (absent here).
    return current + proposedLabels
}

// Average fare per weekday (Calendar.DAY_OF_WEEK -> KSh), observed only:
// total on that weekday divided by the distinct days it was paid. Basis
// for per-day fare thinking with zero new storage.
fun weekdayFareAverages(expenses: List<Transaction>, window: ScanWindow): Map<Int, Double> {
    val transport = expenses.filter {
        it.type == TransactionType.EXPENSE &&
            it.category.equals("Transport", ignoreCase = true) &&
            it.dateTimestamp in window.startMs until window.endMs
    }
    return transport.groupBy {
        Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }.get(Calendar.DAY_OF_WEEK)
    }.mapValues { (_, l) ->
        l.sumOf { it.amount } / l.map { dayStart(it.dateTimestamp) }.toSet().size.coerceAtLeast(1)
    }
}

// Extension: drop the day-of-week wrapper and return plain Int->Double for spread logic.
fun faresByDay(avgs: Map<Int, Double>): Map<Int, Double> = avgs

// School-day-aware monthly transport pace: each observed weekday keeps its
// own average, summed to a school week, scaled to a month. Beats a flat
// 90-day average that lets break weeks water down term-time fares.
fun observedTransportMonthly(expenses: List<Transaction>, window: ScanWindow): Double {
    val avgs = weekdayFareAverages(expenses, window)
    if (avgs.isEmpty()) return 0.0
    return avgs.values.sum() * 30.0 / 7.0
}

data class RoutineChange(
    val recentDaily: Double,
    val priorDaily: Double,
    val recentDays: Int,
    val priorDays: Int
)

// Fare routine shift: last 14 days vs the 14 before. Both sides need 3+
// active fare days; a 40%+ move in daily fare (or a collapsed school week)
// means new route, new timetable or a strike — ask, never average it in.
fun detectRoutineChange(expenses: List<Transaction>, nowMs: Long = System.currentTimeMillis()): RoutineChange? {
    val transport = expenses.filter {
        it.type == TransactionType.EXPENSE &&
            it.category.equals("Transport", ignoreCase = true)
    }
    fun side(startMs: Long, endMs: Long): Pair<Double, Int> {
        val days = transport.filter { it.dateTimestamp in startMs until endMs }
            .groupBy { dayStart(it.dateTimestamp) }
        if (days.size < 3) return 0.0 to 0
        return days.values.sumOf { l -> l.sumOf { it.amount } } / days.size to days.size
    }
    val (newDaily, newDays) = side(nowMs - 14 * DAY_MS, nowMs)
    val (oldDaily, oldDays) = side(nowMs - 28 * DAY_MS, nowMs - 14 * DAY_MS)
    if (newDays < 3 || oldDays < 3 || oldDaily <= 0) return null
    val ratio = newDaily / oldDaily
    if (ratio in 0.6..1.4 && newDays >= oldDays - 1) return null
    return RoutineChange(newDaily, oldDaily, newDays, oldDays)
}

private fun ampm(h: Int): String = when {
    h == 0 -> "12am"
    h < 12 -> "${h}am"
    h == 12 -> "12pm"
    else -> "${h - 12}pm"
}

// Spike weeks: weeks spending over 2x the median non-zero week, with at
// least 3 weeks observed. Trip weeks, strike splurges, funerals — real life,
// but they must not set the daily base. Announced, never silent.
// Rent is excluded from detection (not from the base): it lands as a lump
// by design and has its own modal-day logic — a rent week is not an anomaly.
fun anomalyWeeks(expenses: List<Transaction>, window: ScanWindow): Set<Long> {
    val byWeek = expenses.filter {
        it.type == TransactionType.EXPENSE &&
            !it.category.equals("Rent", ignoreCase = true) &&
            it.dateTimestamp in window.startMs until window.endMs
    }.groupBy { weekStartMonday(it.dateTimestamp) }
    if (byWeek.size < 3) return emptySet()
    val totals = byWeek.values.map { l -> l.sumOf { it.amount } }.sorted()
    val median = totals[totals.size / 2]
    if (median <= 0) return emptySet()
    return byWeek.filter { it.value.sumOf { t -> t.amount } > 2 * median }.keys
}

// Daily / weekly / monthly from ONE consistent base (calendar-day average),
// categories as monthly pace, plus fare window, rent day and payday reads —
// every figure an observed fact with evidence, never a declaration.
// Anomaly weeks leave the money base (averages + categories) but stay in
// inference evidence: fares, rent day and payday want all the data.
fun buildSpendProfile(rows: List<Transaction>, window: ScanWindow): SpendProfile {
    val inWin = rows.filter { it.dateTimestamp in window.startMs until window.endMs }
    val expenses = inWin.filter { it.type == TransactionType.EXPENSE }
    val badWeeks = anomalyWeeks(expenses, window)
    val badDays = badWeeks
        .flatMap { ws -> (0..6).map { ws + it * DAY_MS } }
        .filter { it in window.startMs until window.endMs }
        .map { dayStart(it) }.toSet()
    val baseExpenses = expenses.filter { dayStart(it.dateTimestamp) !in badDays }
    val excludedTotal = expenses.filter { dayStart(it.dateTimestamp) in badDays }.sumOf { it.amount }
    val windowDays = maxOf(1L, (window.endMs - window.startMs) / DAY_MS - badDays.size)
    val months = windowDays / 30.0
    val expenseTotal = baseExpenses.sumOf { it.amount }
    val daily = expenseTotal / windowDays
    val byCat = baseExpenses.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } / months }

    val schoolDays = inferSchoolDays(expenses, window)

    val transport = expenses.filter { it.category.equals("Transport", ignoreCase = true) }
    val transportDays = transport.map { dayStart(it.dateTimestamp) }.toSet()
    var fareDaily = 0.0
    var fareHint: String? = null
    if (transportDays.size >= 5) {
        fareDaily = transport.sumOf { it.amount } / transportDays.size
        val hours = transport.groupingBy {
            Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }.get(Calendar.HOUR_OF_DAY)
        }.eachCount()
        val peak = hours.maxByOrNull { it.value }?.key ?: 8
        val startH = maxOf(0, peak - 2)
        val endH = minOf(23, peak + 2)
        fareHint = "around ${ampm(peak)} (mostly ${ampm(startH)}–${ampm(endH)}, ${transportDays.size} days seen)"
    }

    val rent = expenses.filter { it.category.equals("Rent", ignoreCase = true) }
    val rentByDom = rent.groupBy {
        Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }.get(Calendar.DAY_OF_MONTH)
    }
    val rentDom = rentByDom.maxByOrNull { it.value.size }
    val rentDay = rentDom?.key
    val rentMonthly = if (rent.isEmpty()) 0.0 else rent.sumOf { it.amount } / months

    var paydayAmount: Double? = null
    var paydayDay: Int? = null
    var paydayWho: String? = null
    inWin.filter { it.isEarnedIncome() && !it.isSample }
        .groupBy { it.merchant.trim().lowercase() }
        .mapNotNull { (_, list) ->
            if (list.size < 2) return@mapNotNull null
            val sorted = list.map { it.dateTimestamp }.sorted()
            val gaps = sorted.zipWithNext { a, b -> (b - a) / DAY_MS }
            if (gaps.isEmpty()) return@mapNotNull null
            val median = gaps.sorted()[gaps.size / 2]
            if (median !in 25..35) return@mapNotNull null
            Triple(list, list.map { it.amount }.average(), median)
        }
        .maxByOrNull { it.second }?.let { (list, avg, _) ->
            paydayAmount = avg
            paydayWho = list.maxByOrNull { it.dateTimestamp }?.merchant?.takeIf { it.isNotBlank() }
            paydayDay = Calendar.getInstance().apply {
                timeInMillis = list.map { it.dateTimestamp }.maxOrNull() ?: window.startMs
            }.get(Calendar.DAY_OF_MONTH)
        }

    val skewed = window.includesBreak
    return SpendProfile(
        dailyAvg = daily,
        weeklyAvg = daily * 7,
        monthlyAvg = daily * 30,
        byCategoryMonthly = byCat,
        activeDays = inWin.map { dayStart(it.dateTimestamp) }.toSet().size,
        windowDays = windowDays,
        schoolDays = schoolDays,
        fareDailyAvg = fareDaily,
        fareWindowHint = fareHint,
        rentMonthly = rentMonthly,
        rentDay = rentDay,
        paydayAmount = paydayAmount,
        paydayDay = paydayDay,
        paydayWho = paydayWho,
        skewed = skewed,
        skewNote = if (skewed) "Includes May–Aug break months — holiday spending is mixed in. Confirm term-time life." else null,
        excludedWeeks = badWeeks.size,
        excludedTotal = excludedTotal
    )
}
