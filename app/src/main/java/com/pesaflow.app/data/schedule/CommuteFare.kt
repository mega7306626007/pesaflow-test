package com.pesaflow.app.data.schedule

import java.util.Calendar

private val weekdayKeys = mapOf(
    Calendar.MONDAY to "Mon",
    Calendar.TUESDAY to "Tue",
    Calendar.WEDNESDAY to "Wed",
    Calendar.THURSDAY to "Thu",
    Calendar.FRIDAY to "Fri",
    Calendar.SATURDAY to "Sat",
    Calendar.SUNDAY to "Sun"
)

fun timetableDay(timestamp: Long): String? =
    weekdayKeys[Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.DAY_OF_WEEK)]

fun isWithinClassCommuteWindow(
    timestamp: Long,
    classTimes: Map<String, Pair<Int, Int>>,
    toleranceHours: Int = 2
): Boolean {
    if (toleranceHours < 0) return false
    val bounds = classTimes[timetableDay(timestamp)] ?: return false
    val hour = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)
    return hour in (bounds.first - toleranceHours).coerceAtLeast(0)..(bounds.second + toleranceHours).coerceAtMost(23)
}

fun matchesDeclaredCommuteFare(
    amount: Double,
    timestamp: Long,
    oneWayFare: Double,
    classTimes: Map<String, Pair<Int, Int>>,
    tolerance: Double = 50.0
): Boolean =
    amount > 0 &&
        oneWayFare > 0 &&
        tolerance >= 0 &&
        kotlin.math.abs(amount - oneWayFare) <= tolerance &&
        isWithinClassCommuteWindow(timestamp, classTimes)
