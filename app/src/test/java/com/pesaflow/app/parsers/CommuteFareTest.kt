package com.pesaflow.app.parsers

import com.pesaflow.app.data.schedule.isWithinClassCommuteWindow
import com.pesaflow.app.data.schedule.matchesDeclaredCommuteFare
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CommuteFareTest {
    private fun at(day: Int, hour: Int): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, 0)
    }.timeInMillis

    private val timetable = mapOf("Mon" to (9 to 11))

    @Test
    fun `class commute window accepts two hours either side on scheduled day`() {
        assertTrue(isWithinClassCommuteWindow(at(5, 7), timetable))
        assertTrue(isWithinClassCommuteWindow(at(5, 13), timetable))
        assertFalse(isWithinClassCommuteWindow(at(5, 6), timetable))
        assertFalse(isWithinClassCommuteWindow(at(6, 9), timetable))
    }

    @Test
    fun `declared fare accepts bounded hikes only in commute window`() {
        assertTrue(matchesDeclaredCommuteFare(150.0, at(5, 7), 100.0, timetable))
        assertTrue(matchesDeclaredCommuteFare(50.0, at(5, 13), 100.0, timetable))
        assertFalse(matchesDeclaredCommuteFare(151.0, at(5, 7), 100.0, timetable))
        assertFalse(matchesDeclaredCommuteFare(100.0, at(5, 16), 100.0, timetable))
        assertFalse(matchesDeclaredCommuteFare(100.0, at(6, 9), 100.0, timetable))
    }
}
