package com.pesaflow.app.data.finance

// Instalment splitter: divide one lump obligation (fees, wifi, any bill)
// into weekly/monthly parts. Pure math — the ViewModel replaces the original
// bill with the parts, so commitments are never double-counted.
data class Instalment(val index: Int, val of: Int, val amount: Double, val dueInDays: Int)

fun instalmentSchedule(total: Double, parts: Int, stepDays: Int): List<Instalment> {
    if (total <= 0 || parts < 2 || parts > 52 || stepDays <= 0) return emptyList()
    val base = (total / parts).toInt().toDouble()
    if (base <= 0) return emptyList()
    return (1..parts).map { i ->
        val amount = if (i < parts) base else total - base * (parts - 1)
        Instalment(index = i, of = parts, amount = amount, dueInDays = i * stepDays)
    }
}
