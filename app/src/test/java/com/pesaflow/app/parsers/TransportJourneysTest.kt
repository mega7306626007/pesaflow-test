package com.pesaflow.app.parsers

import com.pesaflow.app.data.context.ContextSources
import com.pesaflow.app.data.finance.Journey
import com.pesaflow.app.data.finance.journeyFacts
import com.pesaflow.app.data.finance.journeyMonthly
import com.pesaflow.app.data.finance.journeyWeekly
import org.junit.Assert.*
import org.junit.Test


/** Journey fare math: return trips × days, month = 4.33 weeks. */
class TransportJourneysTest {

    private val commute = Journey("home-campus", "Ruiru", "CBD", "MATATU", fareOneWay = 80.0, daysPerWeek = 5, verified = true)

    @Test
    fun `weekly is return fare times days`() {
        assertEquals(800.0, journeyWeekly(commute)!!, 0.001)
    }

    @Test
    fun `monthly scales weekly by 4_33`() {
        assertEquals(800.0 * 4.33, journeyMonthly(commute)!!, 0.001)
    }

    @Test
    fun `unknown fare yields null not zero`() {
        val unknown = commute.copy(fareOneWay = 0.0)
        assertNull(journeyWeekly(unknown))
        assertNull(journeyMonthly(unknown))
        assertNull(journeyWeekly(commute.copy(daysPerWeek = 0)))
    }

    @Test
    fun `facts roundtrip all five fields`() {
        val facts = journeyFacts(commute, ContextSources.USER_CONFIRMED)
        assertEquals(5, facts.size)
        val byKey = facts.associateBy { it.key }
        assertEquals("Ruiru", byKey["journey.home-campus.from"]!!.value)
        assertEquals("80.0", byKey["journey.home-campus.fare"]!!.value)
        assertTrue(byKey["journey.home-campus.fare"]!!.userConfirmed)
        assertEquals("5", byKey["journey.home-campus.days"]!!.value)
    }
}
