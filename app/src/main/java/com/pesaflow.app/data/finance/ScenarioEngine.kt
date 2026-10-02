package com.pesaflow.app.data.finance

// What-if scenarios over VERIFIED numbers. Every scenario returns its
// assumptions and missing inputs alongside the math — hypothetical savings
// are never presented as money saved. Pure Kotlin, fully unit-tested.
// Deterministic engine; ML predictions may feed inputs, never conclusions.

data class ScenarioResult(
    val title: String,
    /** Positive = saves per month, negative = costs per month. */
    val monthlyDelta: Double,
    val newMonthlyTotal: Double,
    val assumptions: List<String>,
    val missingInputs: List<String>
)

private const val WEEKS_PER_MONTH = 4.33

/** Cook [cookDaysPerWeek] dinners instead of buying: grocery cost per home
 *  meal vs current average bought-meal price. */
fun scenarioCookMore(
    boughtMealsPerWeek: Int,
    avgBoughtPrice: Double,
    cookDaysPerWeek: Int,
    groceryPerMeal: Double
): ScenarioResult {
    val missing = mutableListOf<String>()
    if (avgBoughtPrice <= 0) missing.add("average bought-meal price")
    if (groceryPerMeal <= 0) missing.add("grocery cost per home meal")
    val shifted = minOf(cookDaysPerWeek, boughtMealsPerWeek).coerceAtLeast(0)
    val perMealSave = (avgBoughtPrice - groceryPerMeal).coerceAtLeast(0.0)
    val monthly = shifted * perMealSave * WEEKS_PER_MONTH
    return ScenarioResult(
        title = "Cook $shifted day(s) a week",
        monthlyDelta = monthly,
        newMonthlyTotal = -monthly,
        assumptions = listOf(
            "$shifted bought meal(s)/week become home-cooked",
            "KSh ${"%.0f".format(groceryPerMeal)} groceries per home meal"
        ),
        missingInputs = missing
    )
}

/** Walk [walkDaysPerWeek] commute days instead of paying the fare. */
fun scenarioWalkMore(
    fareOneWay: Double,
    commuteDaysPerWeek: Int,
    walkDaysPerWeek: Int
): ScenarioResult {
    val missing = mutableListOf<String>()
    if (fareOneWay <= 0) missing.add("usual one-way fare")
    val shifted = minOf(walkDaysPerWeek, commuteDaysPerWeek).coerceAtLeast(0)
    val monthly = shifted * fareOneWay * 2 * WEEKS_PER_MONTH
    return ScenarioResult(
        title = "Walk $shifted day(s) a week",
        monthlyDelta = monthly,
        newMonthlyTotal = -monthly,
        assumptions = listOf("$shifted return trip(s)/week on foot"),
        missingInputs = missing
    )
}

/** Shave [cutPerLunch] off every lunch. */
fun scenarioCheaperLunch(
    lunchesPerWeek: Int,
    cutPerLunch: Double
): ScenarioResult {
    val monthly = lunchesPerWeek.coerceAtLeast(0) * cutPerLunch.coerceAtLeast(0.0) * WEEKS_PER_MONTH
    return ScenarioResult(
        title = "KSh ${cutPerLunch.toInt()} cheaper lunches",
        monthlyDelta = monthly,
        newMonthlyTotal = -monthly,
        assumptions = listOf("$lunchesPerWeek lunches/week"),
        missingInputs = if (lunchesPerWeek <= 0) listOf("lunches per week") else emptyList()
    )
}

/** Can a [price] goal be afforded in [months] given monthly surplus? */
fun scenarioAffordGoal(
    price: Double,
    months: Int,
    monthlySurplus: Double,
    currentSaved: Double = 0.0
): ScenarioResult {
    val missing = mutableListOf<String>()
    if (monthlySurplus <= 0) missing.add("positive monthly surplus")
    val reachable = currentSaved + monthlySurplus * months.coerceAtLeast(1)
    return ScenarioResult(
        title = "Afford KSh ${price.toInt()} in $months mo",
        monthlyDelta = 0.0,
        newMonthlyTotal = reachable - price,
        assumptions = listOf(
            "KSh ${currentSaved.toInt()} saved already",
            "KSh ${monthlySurplus.toInt()}/mo surplus holds for $months months"
        ),
        missingInputs = missing
    )
}

/** Transport reserve: what a commute really costs per month. */
fun scenarioTransportReserve(
    fareOneWay: Double,
    commuteDaysPerWeek: Int
): ScenarioResult {
    val monthly = fareOneWay.coerceAtLeast(0.0) * 2 * commuteDaysPerWeek.coerceAtLeast(0) * WEEKS_PER_MONTH
    return ScenarioResult(
        title = "Transport reserve",
        monthlyDelta = -monthly,
        newMonthlyTotal = monthly,
        assumptions = listOf("return trip, $commuteDaysPerWeek day(s)/week"),
        missingInputs = if (fareOneWay <= 0) listOf("usual one-way fare") else emptyList()
    )
}

/** Income delayed [daysLate]: held cash vs daily burn until it lands. */
fun scenarioIncomeDelayed(
    heldCash: Double,
    dailyBurn: Double,
    daysLate: Int
): ScenarioResult {
    val need = dailyBurn.coerceAtLeast(0.0) * daysLate.coerceAtLeast(0)
    return ScenarioResult(
        title = "Income $daysLate day(s) late",
        monthlyDelta = 0.0,
        newMonthlyTotal = heldCash - need,
        assumptions = listOf(
            "KSh ${heldCash.toInt()} held now",
            "KSh ${dailyBurn.toInt()}/day burn"
        ),
        missingInputs = if (dailyBurn <= 0) listOf("daily burn rate") else emptyList()
    )
}

/** Rent change: old vs new monthly rent effect on the budget. */
fun scenarioRentChange(
    oldRent: Double,
    newRent: Double
): ScenarioResult {
    val delta = oldRent - newRent
    return ScenarioResult(
        title = "Rent KSh ${oldRent.toInt()} → KSh ${newRent.toInt()}",
        monthlyDelta = delta,
        newMonthlyTotal = -delta,
        assumptions = listOf("other spending unchanged"),
        missingInputs = emptyList()
    )
}
