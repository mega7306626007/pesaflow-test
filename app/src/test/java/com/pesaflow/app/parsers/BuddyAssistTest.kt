package com.pesaflow.app.parsers

import com.pesaflow.app.ui.dashboard.BuddyBrain
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
}
