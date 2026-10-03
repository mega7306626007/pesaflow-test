package com.pesaflow.app.parsers

import com.pesaflow.app.ui.dashboard.BuddyBrain
import com.pesaflow.app.data.finance.DataQuality
import com.pesaflow.app.data.finance.Money
import com.pesaflow.app.data.finance.explainFlexible
import com.pesaflow.app.data.finance.explainSafeToday
import org.junit.Assert.*
import org.junit.Test


/** ML assist gating: rules blank + model sure, mapped intents only. */
class BuddyAssistTest {

    @Test
    fun `mapped intents expand to verified branches`() {
        assertEquals("balance", BuddyBrain.mlAssistExpansion("balance_query"))
        assertEquals("can i afford", BuddyBrain.mlAssistExpansion("affordability_check"))
        assertEquals("summary", BuddyBrain.mlAssistExpansion("week_summary"))
        assertEquals("compare vs last", BuddyBrain.mlAssistExpansion("month_compare"))
        assertEquals("hello", BuddyBrain.mlAssistExpansion("greeting"))
    }

    @Test
    fun `unmapped intents stay null`() {
        assertNull(BuddyBrain.mlAssistExpansion("transport_cost_query"))
        assertNull(BuddyBrain.mlAssistExpansion("goals_query"))
        assertNull(BuddyBrain.mlAssistExpansion("no_such_intent"))
    }

    @Test
    fun `assist fires only when rules blank and model sure`() {
        assertTrue(BuddyBrain.shouldMlAssist(0.2f, 0.9f))
        assertTrue(BuddyBrain.shouldMlAssist(0.0f, 0.7f))
        assertFalse(BuddyBrain.shouldMlAssist(0.5f, 0.95f))
        assertFalse(BuddyBrain.shouldMlAssist(0.2f, 0.69f))
        assertFalse(BuddyBrain.shouldMlAssist(0.35f, 0.9f))
    }

    @Test
    fun `app overview explains the main areas without inventing features`() {
        val answer = BuddyBrain.appGuide("explain the entire app")

        assertNotNull(answer)
        assertTrue(answer!!.contains("Transactions"))
        assertTrue(answer.contains("Budgets"))
        assertTrue(answer.contains("Meal Planner"))
        assertTrue(answer.contains("Contact Book"))
        assertTrue(answer.contains("saved records"))
    }

    @Test
    fun `feature guidance answers where and how questions only`() {
        assertTrue(BuddyBrain.appGuide("where do I set who pays this bill")!!.contains("Bills"))
        assertNull(BuddyBrain.appGuide("how much did I spend on bills"))
    }

    @Test
    fun `metric explanation reports canonical value inputs and evidence quality`() {
        val safe = explainSafeToday(
            headline = Money.of(97.0),
            liquid = Money.of(500.0),
            committed = Money.of(200.0),
            essentialDaily = 120.0,
            buffer = Money.of(83.0),
            basis = "3 ledger rows over ~2 days",
            quality = DataQuality.SPARSE
        )
        val flexible = explainFlexible(
            headline = Money.of(300.0),
            liquid = Money.of(500.0),
            bills = Money.of(100.0),
            debts = Money.of(50.0),
            reserved = Money.of(50.0),
            obligationCount = 2,
            reservationCount = 1,
            quality = DataQuality.PARTIAL
        )
        val explanations = mapOf("safeToday" to safe, "flexible" to flexible)

        val answer = BuddyBrain.explainMetric("why safe to spend today?", explanations, Money.of(500.0))

        assertNotNull(answer)
        assertTrue(answer!!.contains("KSh 97"))
        assertTrue(answer.contains("Held KSh 500"))
        assertTrue(answer.contains("sparse history"))
    }

    @Test
    fun `metric explanation asks which value instead of guessing`() {
        val answer = BuddyBrain.explainMetric("why do I see 97?", emptyMap(), Money.of(97.0))

        assertNotNull(answer)
        assertTrue(answer!!.contains("Which figure should I explain"))
    }

    @Test
    fun `budget explanation states envelope precedence and recorded spend`() {
        val answer = BuddyBrain.explainMetric(
            "why does my budget show this amount?",
            emptyMap(),
            Money.of(900.0),
            monthlyBudget = 1_000.0,
            monthExpenses = 250.0
        )

        assertNotNull(answer)
        assertTrue(answer!!.contains("ALL envelope"))
        assertTrue(answer.contains("KSh 1k"))
        assertTrue(answer.contains("KSh 250"))
        assertTrue(answer.contains("KSh 750 remaining"))
        assertTrue(answer.contains("Only expenses recorded"))
    }
}
