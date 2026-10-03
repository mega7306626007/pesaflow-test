package com.pesaflow.app.data.finance

// Canonical financial vocabulary (Phase 2 of hierarchy transformation).
// Every screen must use these terms with these meanings — never interchange
// balance / available / safe / remaining / projected as synonyms.
object FinancialVocabulary {
    const val CURRENT_BALANCE = "Current balance"
    const val CURRENT_BALANCE_DEF = "Money currently recorded in your ledger across M-Pesa, cash and bank. Transfers move money between pockets without changing this total."

    const val COMMITTED = "Committed money"
    const val COMMITTED_DEF = "Money already expected for known obligations: upcoming bills, debts, fees and goal reservations."

    const val FLEXIBLE = "Flexible money"
    const val FLEXIBLE_DEF = "Current balance minus committed money and safety buffer. This is what you can safely use without endangering upcoming obligations."

    const val BUDGET_REMAINING = "Budget remaining"
    const val BUDGET_REMAINING_DEF = "Amount left inside one spending plan (e.g. Food). A budget is a plan, not cash — it can exceed your balance."

    const val PROJECTED = "Projected balance"
    const val PROJECTED_DEF = "Estimated future balance under stated assumptions. Never a guarantee."

    const val RUNWAY = "Runway"
    const val RUNWAY_DEF = "How long flexible money may last at the stated daily spending pace."
}

// Data provenance: every figure must be traceable to one of these sources.
// UI shows the tag so Recorded is never confused with Projected.
enum class Provenance(val label: String) {
    RECORDED("Recorded"),
    CALCULATED("Calculated"),
    ESTIMATED("Estimated"),
    PROJECTED("Projected"),
    USER_ENTERED("You entered")
}
