package com.pesaflow.app.parsers

import com.pesaflow.app.data.context.ContextFact
import com.pesaflow.app.data.context.ContextSources
import com.pesaflow.app.data.finance.Journey
import com.pesaflow.app.data.finance.journeyFacts
import com.pesaflow.app.data.finance.journeyMonthly
import com.pesaflow.app.data.finance.journeyWeekly
import com.pesaflow.app.data.finance.readJourney
import com.pesaflow.app.data.finance.scenarioAffordGoal
import com.pesaflow.app.data.finance.scenarioCheaperLunch
import com.pesaflow.app.data.finance.scenarioCookMore
import com.pesaflow.app.data.finance.scenarioIncomeDelayed
import com.pesaflow.app.data.finance.scenarioRentChange
import com.pesaflow.app.data.finance.scenarioTransportReserve
import com.pesaflow.app.data.finance.scenarioWalkMore
import com.pesaflow.app.data.places.Place
import com.pesaflow.app.data.places.rankFoodOptions
import org.junit.Assert.*
import org.junit.Test


/** Scenario math, food ranking, journey round-trips. */
class ScenarioEngineTest {

    @Test
    fun `cooking shifts bought meals at grocery cost`() {
        val r = scenarioCookMore(
            boughtMealsPerWeek = 5, avgBoughtPrice = 150.0,
            cookDaysPerWeek = 3, groceryPerMeal = 60.0
        )
        // 3 × (150 − 60) × 4.33.
        assertEquals(1169.1, r.monthlyDelta, 0.5)
        assertTrue(r.assumptions.isNotEmpty())
        assertTrue(r.missingInputs.isEmpty())
    }

    @Test
    fun `cooking caps at bought meals and flags missing prices`() {
        val r = scenarioCookMore(2, 0.0, 5, 0.0)
        assertEquals(0.0, r.monthlyDelta, 0.001)
        assertEquals(2, r.missingInputs.size)
    }

    @Test
    fun `walking saves return fares`() {
        val r = scenarioWalkMore(fareOneWay = 80.0, commuteDaysPerWeek = 5, walkDaysPerWeek = 2)
        assertEquals(2 * 80.0 * 2 * 4.33, r.monthlyDelta, 0.5)
    }

    @Test
    fun `cheaper lunch scales by frequency`() {
        val r = scenarioCheaperLunch(lunchesPerWeek = 5, cutPerLunch = 100.0)
        assertEquals(5 * 100.0 * 4.33, r.monthlyDelta, 0.5)
    }

    @Test
    fun `laptop affordability compares reachable vs price`() {
        val r = scenarioAffordGoal(price = 45000.0, months = 6, monthlySurplus = 8000.0, currentSaved = 5000.0)
        assertEquals(53000.0 - 45000.0, r.newMonthlyTotal, 0.001)
        assertTrue(r.missingInputs.isEmpty())
        val broke = scenarioAffordGoal(price = 45000.0, months = 6, monthlySurplus = 0.0)
        assertTrue(broke.missingInputs.isNotEmpty())
    }

    @Test
    fun `transport reserve is return trips by month`() {
        val r = scenarioTransportReserve(fareOneWay = 80.0, commuteDaysPerWeek = 5)
        // A reserve is money spoken for: negative delta, positive set-aside.
        assertEquals(-80.0 * 2 * 5 * 4.33, r.monthlyDelta, 0.5)
        assertEquals(80.0 * 2 * 5 * 4.33, r.newMonthlyTotal, 0.5)
    }

    @Test
    fun `delayed income projects the shortfall`() {
        val r = scenarioIncomeDelayed(heldCash = 2000.0, dailyBurn = 500.0, daysLate = 5)
        assertEquals(-500.0, r.newMonthlyTotal, 0.001)
    }

    @Test
    fun `rent change signs correctly`() {
        val cheaper = scenarioRentChange(oldRent = 8000.0, newRent = 5000.0)
        assertEquals(3000.0, cheaper.monthlyDelta, 0.001)
        val dearer = scenarioRentChange(oldRent = 5000.0, newRent = 8000.0)
        assertEquals(-3000.0, dearer.monthlyDelta, 0.001)
    }

    private fun place(name: String, min: Double, area: String = "", source: String = "USER_ENTERED", verifiedAt: Long = System.currentTimeMillis()) =
        Place(name = name, kind = "FOOD_OUTLET", area = area, priceMin = min, source = source, verifiedAt = verifiedAt)

    @Test
    fun `food ranking respects budget and prefers cheap`() {
        val options = listOf(
            place("Kibanda", 120.0, "Gate A"),
            place("Java", 450.0, "Mall"),
            place("Mama Mboga", 80.0, "Stage")
        )
        val (ranked, excluded) = rankFoodOptions(options, mealBudget = 200.0)
        assertEquals(2, ranked.size)
        assertEquals(1, excluded)
        assertEquals("Mama Mboga", ranked[0].place.name)
        assertEquals("Kibanda", ranked[1].place.name)
        assertEquals("USER_REPORTED", ranked[0].evidence)
    }

    @Test
    fun `food ranking adds transport and marks stale`() {
        val old = System.currentTimeMillis() - 200L * 24 * 60 * 60 * 1000
        val options = listOf(place("Old Spot", 100.0, source = "USER_ENTERED", verifiedAt = old))
        val (ranked, excluded) = rankFoodOptions(options, mealBudget = 500.0, transportEachWay = 50.0)
        assertEquals(1, ranked.size)
        assertEquals(0, excluded)
        assertEquals(200.0, ranked[0].expectedCost, 0.001)
        assertEquals("STALE", ranked[0].evidence)
    }

    @Test
    fun `non food places never rank`() {
        val options = listOf(Place(name = "Stage", kind = "STAGE"))
        val (ranked, excluded) = rankFoodOptions(options, mealBudget = 500.0)
        assertTrue(ranked.isEmpty())
        assertEquals(0, excluded)
    }

    private fun journey() = Journey(
        id = "home-campus", from = "Roysambu", to = "UoN",
        mode = "MATATU", fareOneWay = 80.0, daysPerWeek = 5, verified = true
    )

    @Test
    fun `journey week and month math`() {
        assertEquals(800.0, journeyWeekly(journey())!!, 0.001)
        assertEquals(800.0 * 4.33, journeyMonthly(journey())!!, 0.5)
    }

    @Test
    fun `unknown fare yields null never zero`() {
        assertNull(journeyWeekly(journey().copy(fareOneWay = 0.0)))
        assertNull(journeyMonthly(journey().copy(daysPerWeek = 0)))
    }

    @Test
    fun `journey round trips through facts`() {
        val facts = journeyFacts(journey()).associateBy { it.key }
        val back = readJourney("home-campus", facts)!!
        assertEquals("Roysambu", back.from)
        assertEquals(80.0, back.fareOneWay, 0.001)
        assertTrue(back.verified == (facts["journey.home-campus.fare"]?.userConfirmed == true))
        assertNull(readJourney("missing", facts))
        val partial = mapOf("journey.x.from" to ContextFact(key = "journey.x.from", value = "A", source = ContextSources.USER_ENTERED))
        assertNull(readJourney("x", partial))
    }
}
