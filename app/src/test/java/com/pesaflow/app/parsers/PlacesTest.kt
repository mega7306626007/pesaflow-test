package com.pesaflow.app.parsers

import com.pesaflow.app.data.places.Place
import com.pesaflow.app.data.places.rankFoodOptions
import org.junit.Assert.*
import org.junit.Test


/** User-reported spots rank first; over-budget plates are excluded, counted. */
class PlacesTest {

    private fun place(
        name: String,
        price: Double,
        source: String = "USER_ENTERED",
        ageDays: Long = 5
    ) = Place(
        name = name,
        kind = "FOOD_OUTLET",
        area = "Roysambu",
        priceMin = price,
        priceMax = price,
        source = source,
        verifiedAt = System.currentTimeMillis() - ageDays * 24 * 3_600_000L
    )

    @Test
    fun `user report beats dataset at same price`() {
        val mine = place("Mama Njoroge", 80.0, "USER_ENTERED")
        val theirs = place("Canteen", 80.0, "PUBLIC_DATASET")
        val (ranked, excluded) = rankFoodOptions(listOf(theirs, mine), mealBudget = 500.0)
        assertEquals(0, excluded)
        assertEquals("Mama Njoroge", ranked.first().place.name)
        assertEquals("USER_REPORTED", ranked.first().evidence)
    }

    @Test
    fun `over budget plates excluded and counted`() {
        val cheap = place("Kibanda", 50.0)
        val pricey = place("Java", 800.0)
        val (ranked, excluded) = rankFoodOptions(listOf(cheap, pricey), mealBudget = 100.0)
        assertEquals(1, excluded)
        assertEquals(listOf("Kibanda"), ranked.map { it.place.name })
    }

    @Test
    fun `stale user report flagged not trusted`() {
        val old = place("Old spot", 60.0, "USER_ENTERED", ageDays = 200)
        val (ranked, _) = rankFoodOptions(listOf(old), mealBudget = 500.0)
        assertEquals("STALE", ranked.single().evidence)
    }

    @Test
    fun `non-food kinds ignored`() {
        val stage = place("Odeon stage", 50.0).copy(kind = "STAGE")
        val (ranked, excluded) = rankFoodOptions(listOf(stage), mealBudget = 500.0)
        assertTrue(ranked.isEmpty())
        assertEquals(0, excluded)
    }

    @Test
    fun `transport both ways counts against budget`() {
        val p = place("Far kibanda", 90.0)
        val (_, excluded) = rankFoodOptions(listOf(p), mealBudget = 100.0, transportEachWay = 10.0)
        assertEquals(1, excluded)
    }
}
