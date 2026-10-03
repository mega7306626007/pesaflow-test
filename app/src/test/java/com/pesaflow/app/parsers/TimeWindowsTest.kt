package com.pesaflow.app.parsers

import com.pesaflow.app.data.time.addDays
import com.pesaflow.app.data.time.changeVsPrevious
import com.pesaflow.app.data.time.daysElapsedInWeek
import com.pesaflow.app.data.time.inPastOrNow
import com.pesaflow.app.data.time.KenyaTime
import com.pesaflow.app.data.time.mondayIndex
import com.pesaflow.app.data.time.monthRange
import com.pesaflow.app.data.time.previousRollingDays
import com.pesaflow.app.data.time.previousWeekRange
import com.pesaflow.app.data.time.rollingDays
import com.pesaflow.app.data.time.startOfDay
import com.pesaflow.app.data.time.startOfWeek
import com.pesaflow.app.data.time.thisWeekRange
import com.pesaflow.app.data.time.todayRange
import com.pesaflow.app.data.time.yesterdayRange
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import java.time.Instant


/** Canonical week/day windows: midnight boundaries, Monday weeks, no leaks. */
class TimeWindowsTest {

    /** Build wall-clock dates in Kenya time, independent of the test host. */
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long {
        return KenyaTime.calendarAt(0L).apply {
            clear()
            set(y, m, d, h, min, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `time windows stay in Nairobi when device timezone differs`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            val instant = Instant.parse("2026-09-07T21:30:00Z").toEpochMilli()
            val today = todayRange(instant)

            assertEquals(Instant.parse("2026-09-07T21:00:00Z").toEpochMilli(), today.startInclusive)
            assertEquals(Instant.parse("2026-09-08T21:00:00Z").toEpochMilli(), today.endExclusive)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `startOfDay pins any time to local midnight`() {
        val noon = at(2026, Calendar.SEPTEMBER, 9, 12, 30)
        val midnight = at(2026, Calendar.SEPTEMBER, 9, 0, 0)
        assertEquals(midnight, startOfDay(noon))
        assertEquals(midnight, startOfDay(midnight))
        assertEquals(midnight, startOfDay(at(2026, Calendar.SEPTEMBER, 9, 23, 59)))
    }

    @Test
    fun `addDays crosses month boundary`() {
        val jan31 = at(2026, Calendar.JANUARY, 31, 10, 0)
        assertEquals(at(2026, Calendar.FEBRUARY, 1, 10, 0), addDays(jan31, 1))
        assertEquals(at(2026, Calendar.JANUARY, 30, 10, 0), addDays(jan31, -1))
    }

    @Test
    fun `monday indexes first sunday last`() {
        assertEquals(0, mondayIndex(at(2026, Calendar.SEPTEMBER, 7)))
        assertEquals(2, mondayIndex(at(2026, Calendar.SEPTEMBER, 9)))
        assertEquals(5, mondayIndex(at(2026, Calendar.SEPTEMBER, 12)))
        assertEquals(6, mondayIndex(at(2026, Calendar.SEPTEMBER, 13)))
    }

    @Test
    fun `startOfWeek pins any day to monday midnight`() {
        val mondayMidnight = at(2026, Calendar.SEPTEMBER, 7, 0, 0)
        assertEquals(mondayMidnight, startOfWeek(at(2026, Calendar.SEPTEMBER, 7, 0, 0)))
        assertEquals(mondayMidnight, startOfWeek(at(2026, Calendar.SEPTEMBER, 9, 18, 45)))
        assertEquals(mondayMidnight, startOfWeek(at(2026, Calendar.SEPTEMBER, 13, 23, 59)))
    }

    @Test
    fun `calendar weeks are contiguous and non overlapping`() {
        val now = at(2026, Calendar.SEPTEMBER, 13, 15, 0) // a Sunday
        val cur = thisWeekRange(now)
        val prev = previousWeekRange(now)
        assertEquals(at(2026, Calendar.SEPTEMBER, 7, 0, 0), cur.startInclusive)
        assertEquals(at(2026, Calendar.SEPTEMBER, 14, 0, 0), cur.endExclusive)
        assertEquals(at(2026, Calendar.AUGUST, 31, 0, 0), prev.startInclusive)
        assertEquals(cur.startInclusive, prev.endExclusive)
        assertEquals(7, cur.days().size)
    }

    @Test
    fun `midnight boundary rows never leak across weeks`() {
        val now = at(2026, Calendar.SEPTEMBER, 13, 15, 0)
        val cur = thisWeekRange(now)
        val prev = previousWeekRange(now)
        val sundayEdge = at(2026, Calendar.SEPTEMBER, 6, 23, 59)
        val mondayEdge = at(2026, Calendar.SEPTEMBER, 7, 0, 0)
        assertTrue(sundayEdge in prev)
        assertFalse(sundayEdge in cur)
        assertTrue(mondayEdge in cur)
        assertFalse(mondayEdge in prev)
    }

    @Test
    fun `same local date never splits across rolling comparison`() {
        // Sunday 09:00 run: the old now - 7d window put last Sunday 08:00 in
        // the PREVIOUS week and last Sunday 10:00 in THIS week. Calendar days
        // keep the whole date together.
        val now = at(2026, Calendar.SEPTEMBER, 13, 9, 0)
        val prev = previousWeekRange(now)
        assertTrue(at(2026, Calendar.SEPTEMBER, 6, 8, 0) in prev)
        assertTrue(at(2026, Calendar.SEPTEMBER, 6, 22, 0) in prev)
    }

    @Test
    fun `rolling days cover whole calendar days including today`() {
        val now = at(2026, Calendar.SEPTEMBER, 13, 9, 30)
        val r = rollingDays(now, 7)
        assertEquals(7, r.days().size)
        assertEquals(at(2026, Calendar.SEPTEMBER, 7, 0, 0), r.startInclusive)
        assertEquals(at(2026, Calendar.SEPTEMBER, 14, 0, 0), r.endExclusive)
        assertTrue(at(2026, Calendar.SEPTEMBER, 13, 9, 30) in r)
        assertTrue(r.startInclusive in r)
        assertFalse(r.endExclusive in r)
        assertFalse(at(2026, Calendar.SEPTEMBER, 6, 23, 59) in r)
    }

    @Test
    fun `previous rolling block ends exactly where current starts`() {
        val now = at(2026, Calendar.SEPTEMBER, 13, 9, 30)
        val cur = rollingDays(now, 7)
        val prev = previousRollingDays(now, 7)
        assertEquals(cur.startInclusive, prev.endExclusive)
        assertEquals(at(2026, Calendar.AUGUST, 31, 0, 0), prev.startInclusive)
    }

    @Test
    fun `today and yesterday ranges tile without gaps`() {
        val now = at(2026, Calendar.SEPTEMBER, 13, 9, 30)
        val today = todayRange(now)
        val yesterday = yesterdayRange(now)
        assertEquals(today.startInclusive, yesterday.endExclusive)
        assertTrue(at(2026, Calendar.SEPTEMBER, 13, 0, 0) in today)
        assertTrue(at(2026, Calendar.SEPTEMBER, 12, 23, 59) in yesterday)
        assertFalse(at(2026, Calendar.SEPTEMBER, 12, 23, 59) in today)
    }

    @Test
    fun `changeVsPrevious handles zero baseline`() {
        assertEquals(20, changeVsPrevious(1200.0, 1000.0))
        assertEquals(-20, changeVsPrevious(800.0, 1000.0))
        assertEquals(0, changeVsPrevious(500.0, 500.0))
        assertNull(changeVsPrevious(500.0, 0.0))
    }

    @Test
    fun `changeVsPrevious caps fantasy percents`() {
        // KSh 20,000 vs a KSh 0.50 baseline is not "up 4000000%".
        assertEquals(999, changeVsPrevious(20000.0, 0.5))
        assertEquals(999, changeVsPrevious(1_000_000.0, 1.0))
        assertEquals(-100, changeVsPrevious(0.0, 1000.0))
    }

    @Test
    fun `dust baselines are detected`() {
        assertTrue(com.pesaflow.app.data.time.isDustBaseline(0.5))
        assertTrue(com.pesaflow.app.data.time.isDustBaseline(100.0))
        assertFalse(com.pesaflow.app.data.time.isDustBaseline(100.01))
        assertFalse(com.pesaflow.app.data.time.isDustBaseline(5000.0))
    }

    @Test
    fun `days elapsed counts monday as one`() {
        assertEquals(1, daysElapsedInWeek(at(2026, Calendar.SEPTEMBER, 7, 0, 1)))
        assertEquals(3, daysElapsedInWeek(at(2026, Calendar.SEPTEMBER, 9, 12, 0)))
        assertEquals(7, daysElapsedInWeek(at(2026, Calendar.SEPTEMBER, 13, 23, 59)))
    }

    @Test
    fun `inPastOrNow excludes future dated rows`() {
        val now = at(2026, Calendar.SEPTEMBER, 13, 9, 0)
        assertTrue(inPastOrNow(now, now))
        assertTrue(inPastOrNow(now - 1, now))
        assertFalse(inPastOrNow(now + 1, now))
    }

    @Test
    fun `monthRange spans the calendar month only`() {
        val now = at(2026, Calendar.SEPTEMBER, 13, 9, 0)
        val m = monthRange(now)
        assertEquals(at(2026, Calendar.SEPTEMBER, 1, 0, 0), m.startInclusive)
        assertEquals(at(2026, Calendar.OCTOBER, 1, 0, 0), m.endExclusive)
        assertTrue(at(2026, Calendar.SEPTEMBER, 30, 23, 59) in m)
        assertFalse(at(2026, Calendar.AUGUST, 31, 23, 59) in m)
        assertFalse(at(2026, Calendar.OCTOBER, 1, 0, 0) in m)
    }

    @Test
    fun `yearRange spans january to january`() {
        val now = at(2026, Calendar.SEPTEMBER, 13, 9, 0)
        val y = com.pesaflow.app.data.time.yearRange(now)
        assertEquals(at(2026, Calendar.JANUARY, 1, 0, 0), y.startInclusive)
        assertEquals(at(2027, Calendar.JANUARY, 1, 0, 0), y.endExclusive)
        assertFalse(at(2025, Calendar.DECEMBER, 31, 23, 59) in y)
    }
}
