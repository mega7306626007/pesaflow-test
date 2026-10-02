package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.ui.budgets.BudgetRule
import com.pesaflow.app.ui.budgets.Persona
import com.pesaflow.app.ui.budgets.monthlyBillReserve
import com.pesaflow.app.ui.budgets.smartBudget
import org.junit.Assert.*
import org.junit.Test


// Budgets follow the profile: home-fed commuters get fares first and small
// plate money; stated envelopes are promises the plan keeps.
class SmartBudgetTest {

    private fun suggestion(persona: Persona, base: Double, declared: Map<String, Int> = emptyMap(), category: String) =
        smartBudget(
            monthlyBase = base, rule = BudgetRule.CAMPUS, period = BudgetType.MONTHLY,
            persona = persona, declaredByCategory = declared
        ).suggestions.firstOrNull { it.category == category }

    @Test
    fun `parents far home-fed food is small and unshielded`() {
        val food = suggestion(Persona.PARENTS_FAR, 20000.0, category = "Food")!!
        assertTrue("food ${food.amount} should be home-plate small", food.amount <= 2500)
        assertFalse(food.shielded)
        assertTrue(food.reason.contains("Home-fed"))
    }

    @Test
    fun `parents far transport floor holds at sixty five hundred`() {
        val transport = suggestion(Persona.PARENTS_FAR, 20000.0, category = "Transport")!!
        assertTrue("transport ${transport.amount} below fare floor", transport.amount >= 6500)
        assertTrue(transport.reason.contains("Non-negotiable"))
    }

    @Test
    fun `parents near food is small too`() {
        val food = suggestion(Persona.PARENTS_NEAR, 20000.0, category = "Food")!!
        assertTrue("food ${food.amount} should be home-plate small", food.amount <= 3000)
        assertFalse(food.shielded)
    }

    @Test
    fun `declared matatu value becomes the plan floor`() {
        // 12000 binds above the natural ~10350 share, so the floor must hold.
        val transport = suggestion(
            Persona.PARENTS_FAR, 20000.0,
            declared = mapOf("transport" to 12000), category = "Transport"
        )!!
        assertTrue("transport ${transport.amount} ignored the stated 12000", transport.amount >= 12000)
        assertTrue(transport.reason.contains("your set"))
    }

    @Test
    fun `hostel buyer food protection untouched`() {
        val food = suggestion(Persona.HOSTEL_NOCOOK, 20000.0, category = "Food")!!
        assertTrue(food.amount >= 9000)
        assertTrue(food.shielded)
    }

    @Test
    fun `tight mode still funds the essential commute`() {
        val result = smartBudget(
            monthlyBase = 8000.0, rule = BudgetRule.CAMPUS,
            period = BudgetType.MONTHLY, persona = Persona.PARENTS_FAR
        )
        assertTrue(result.tightMode)
        val transport = result.suggestions.first { it.category == "Transport" }
        assertTrue(transport.amount >= 6500)
    }

    @Test
    fun `one time bill is paced to its due date and outstanding remainder`() {
        val now = System.currentTimeMillis()
        val fee = Bill(
            name = "Semester fees",
            amount = 32000.0,
            amountRemaining = 16000.0,
            dueDate = now + 90L * 24 * 60 * 60 * 1000,
            category = "School"
        )
        assertEquals(5334, monthlyBillReserve(fee, now))
    }

    @Test
    fun `overdue one time bill reserves no more than its remainder`() {
        val now = System.currentTimeMillis()
        val fee = Bill(
            name = "Semester fees",
            amount = 32000.0,
            amountRemaining = 16000.0,
            dueDate = now - 2L * 24 * 60 * 60 * 1000,
            category = "School"
        )
        assertEquals(16000, monthlyBillReserve(fee, now))
    }
}
