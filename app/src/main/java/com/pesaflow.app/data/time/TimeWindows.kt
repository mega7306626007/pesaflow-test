package com.pesaflow.app.data.time

import java.util.Calendar

// Single source of truth for day/week windows. Every figure that says
// "today", "this week" or "last week" derives from here: Nairobi-midnight
// boundaries, Monday-first weeks, strict [start, end) ranges. Pure Kotlin,
// fully unit-tested.
data class TimeRange(val startInclusive: Long, val endExclusive: Long) {
    init {
        require(endExclusive >= startInclusive) { "Range end must not precede start" }
    }

    operator fun contains(ts: Long): Boolean = ts >= startInclusive && ts < endExclusive

    /** Nairobi-midnight starts of each calendar day covered by this range. */
    fun days(): List<Long> {
        val out = mutableListOf<Long>()
        var cursor = startInclusive
        while (cursor < endExclusive) {
            out.add(cursor)
            cursor = addDays(cursor, 1)
        }
        return out
    }
}

/** Nairobi midnight starting the calendar day that contains [ts]. */
fun startOfDay(ts: Long): Long {
    val c = KenyaTime.calendarAt(ts)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** Calendar-day arithmetic (DST-safe): [days] may be negative. */
fun addDays(ts: Long, days: Int): Long {
    val c = KenyaTime.calendarAt(ts)
    c.add(Calendar.DAY_OF_MONTH, days)
    return c.timeInMillis
}

/** Monday-first weekday index 0..6. */
fun mondayIndex(ts: Long): Int {
    val dow = KenyaTime.calendarAt(ts).get(Calendar.DAY_OF_WEEK)
    return (dow + 5) % 7
}

/** Nairobi midnight starting the Monday of the calendar week containing [ts]. */
fun startOfWeek(ts: Long): Long = addDays(startOfDay(ts), -mondayIndex(ts))

/** Today, 00:00 inclusive to tomorrow 00:00 exclusive. */
fun todayRange(now: Long): TimeRange {
    val start = startOfDay(now)
    return TimeRange(start, addDays(start, 1))
}

/** Current calendar month: the 1st 00:00 to the 1st of next month. */
fun monthRange(now: Long): TimeRange {
    val c = KenyaTime.calendarAt(now)
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    val start = c.timeInMillis
    c.add(Calendar.MONTH, 1)
    return TimeRange(start, c.timeInMillis)
}

/** Current calendar year: Jan 1 00:00 to next Jan 1. */
fun yearRange(now: Long): TimeRange {
    val c = KenyaTime.calendarAt(now)
    c.set(Calendar.MONTH, Calendar.JANUARY)
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    val start = c.timeInMillis
    c.add(Calendar.YEAR, 1)
    return TimeRange(start, c.timeInMillis)
}

/** Yesterday as a strict range. */
fun yesterdayRange(now: Long): TimeRange {
    val today = todayRange(now)
    return TimeRange(addDays(today.startInclusive, -1), today.startInclusive)
}

/**
 * Last [days] calendar days including today: [todayStart - (days-1), tomorrow).
 * Use for anything labelled "last 7 days" / "7 days" / "30 days".
 */
fun rollingDays(now: Long, days: Int): TimeRange {
    require(days > 0) { "days must be positive" }
    val end = addDays(startOfDay(now), 1)
    return TimeRange(addDays(end, -days), end)
}

/** The [days]-day block immediately before [rollingDays]: the true "previous". */
fun previousRollingDays(now: Long, days: Int): TimeRange {
    val current = rollingDays(now, days)
    return TimeRange(addDays(current.startInclusive, -days), current.startInclusive)
}

/** Current calendar week, Monday 00:00 to next Monday 00:00. */
fun thisWeekRange(now: Long): TimeRange {
    val start = startOfWeek(now)
    return TimeRange(start, addDays(start, 7))
}

/** The complete calendar week before the current one. */
fun previousWeekRange(now: Long): TimeRange {
    val current = thisWeekRange(now)
    return TimeRange(addDays(current.startInclusive, -7), current.startInclusive)
}

/** Days elapsed in the current calendar week, Monday = 1. */
fun daysElapsedInWeek(now: Long): Int = mondayIndex(now) + 1

/** Guards "to-date" figures against future-dated rows. */
fun inPastOrNow(ts: Long, now: Long): Boolean = ts <= now

/** Whole-percent move vs a previous baseline; null when there is no baseline. */
fun changeVsPrevious(current: Double, previous: Double): Int? =
    if (previous > 0) ((current - previous) / previous * 100).coerceIn(-999.0, 999.0).toInt() else null

/**
 * Baselines under this are dust, not data: KSh 20,000 vs KSh 0.50 is not
 * "up 4000000%", it is "new spending off a dust baseline". Callers show
 * absolutes instead of a percent below this line.
 */
const val DUST_BASELINE = 100.0

/** True when a percent-vs-previous would be noise rather than signal. */
fun isDustBaseline(previous: Double): Boolean = previous in 0.0..DUST_BASELINE
