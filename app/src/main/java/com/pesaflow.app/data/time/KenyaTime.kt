package com.pesaflow.app.data.time

import java.util.Calendar
import java.util.TimeZone

object KenyaTime {
    const val ZONE_ID = "Africa/Nairobi"
    val timeZone: TimeZone = TimeZone.getTimeZone(ZONE_ID)

    fun calendarAt(timestamp: Long): Calendar =
        Calendar.getInstance(timeZone).apply { timeInMillis = timestamp }

    fun installAsDefault() {
        TimeZone.setDefault(timeZone)
    }
}
