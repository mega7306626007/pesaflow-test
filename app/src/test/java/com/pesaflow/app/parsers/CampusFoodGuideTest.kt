package com.pesaflow.app.parsers

import com.pesaflow.app.data.meals.MealTier
import com.pesaflow.app.data.meals.mealTier
import com.pesaflow.app.data.meals.mealTierLabel
import com.pesaflow.app.data.meals.rentHintFor
import com.pesaflow.app.data.meals.spotsFor
import org.junit.Assert.*
import org.junit.Test


class CampusFoodGuideTest {

    @Test
    fun `uon main resolves to uon pack with smocha seventy`() {
        val spots = spotsFor("UoN Main")
        assertTrue(spots.isNotEmpty())
        assertTrue(spots.all { it.university == "UoN" })
        val smocha = spots.firstOrNull { it.item == "Smocha" }
        assertNotNull(smocha)
        assertEquals(70.0, smocha!!.price, 0.001)
        assertTrue(smocha.spot.isNotBlank())
    }

    @Test
    fun `kenyatta resolves to ku before nairobi rule`() {
        val spots = spotsFor("Kenyatta University")
        assertTrue(spots.isNotEmpty())
        assertTrue(spots.all { it.university == "KU" })
    }

    @Test
    fun `jkuat maseno egerton resolve`() {
        assertTrue(spotsFor("JKUAT Juja").all { it.university == "JKUAT" })
        assertTrue(spotsFor("JKUAT Juja").isNotEmpty())
        assertTrue(spotsFor("Maseno").all { it.university == "Maseno" })
        assertTrue(spotsFor("Egerton Njoro").all { it.university == "Egerton" })
    }

    @Test
    fun `unknown or blank school yields no pack`() {
        assertTrue(spotsFor("Oxford").isEmpty())
        assertTrue(spotsFor("").isEmpty())
        assertTrue(spotsFor("   ").isEmpty())
    }

    @Test
    fun `every spot has a priced plate and meal type`() {
        listOf("UoN", "KU", "JKUAT", "Maseno", "Egerton").forEach { uni ->
            val spots = spotsFor(uni)
            assertTrue(spots.isNotEmpty())
            spots.forEach {
                assertTrue(it.item.isNotBlank())
                assertTrue(it.price > 0)
                assertTrue(it.mealType in setOf("Breakfast", "Lunch", "Supper", "Snack"))
            }
        }
    }

    @Test
    fun `coverage is extensive across the country`() {
        val unis = listOf(
            "Moi Eldoret", "Masinde Muliro Kakamega", "Kisii University",
            "Strathmore Madaraka", "Daystar Athi River", "USIU Kasarani",
            "Catholic Karen", "TUK Ngara", "Kabarak Nakuru", "MKU Thika",
            "Dedan Kimathi Nyeri", "Meru University", "Embu", "Chuka",
            "Laikipia Nyahururu", "Maasai Mara Narok", "Pwani Kilifi",
            "Kibabii Bungoma", "Machakos University", "Garissa", "Rongo",
            "KCA Roysambu", "Multimedia Rongai", "Zetech Ruiru",
            "Karatina", "Muranga", "Taita Taveta Voi", "TUM Mombasa"
        )
        unis.forEach { assertTrue("$it should resolve", spotsFor(it).isNotEmpty()) }
        assertTrue(spotsFor("Oxford").isEmpty())
    }

    @Test
    fun `rent hints carry researched bands`() {
        val ku = rentHintFor("Kenyatta University")
        assertNotNull(ku)
        assertTrue(ku!!.contains("5-8k"))
        val maseno = rentHintFor("Maseno")
        assertNotNull(maseno)
        assertTrue(maseno!!.contains("3k"))
        val strath = rentHintFor("Strathmore")
        assertNotNull(strath)
        assertTrue(strath!!.contains("13-50k"))
        assertNull(rentHintFor("Oxford"))
        assertNull(rentHintFor(""))
    }

    @Test
    fun `daily allowance maps to comfort balanced stretch tiers`() {
        assertEquals(MealTier.COMFORT, mealTier(500.0))
        assertEquals(MealTier.COMFORT, mealTier(400.0))
        assertEquals(MealTier.BALANCED, mealTier(399.0))
        assertEquals(MealTier.BALANCED, mealTier(150.0))
        assertEquals(MealTier.STRETCH, mealTier(149.0))
        assertEquals(MealTier.STRETCH, mealTier(0.0))
        assertTrue(mealTierLabel(MealTier.STRETCH).isNotBlank())
    }
}
