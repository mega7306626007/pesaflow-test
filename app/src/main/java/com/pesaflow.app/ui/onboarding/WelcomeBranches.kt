package com.pesaflow.app.ui.onboarding

// Adaptive welcome: ~20 possible question branches exist, but each student
// sees only the ones their case earns. Answers are a plain map
// (home/commute/fundSource...) so the engine stays pure and testable; the
// screens render exactly visibleBranches() — no hidden boxes, no gaps.
// Purpose: the welcome screen's job is enough data, never interrogation.
data class BranchQ(
    val id: String,
    val askIf: (Map<String, String>) -> Boolean = { true }
)

val WELCOME_BRANCHES: List<BranchQ> = listOf(
    BranchQ("name"),
    BranchQ("nickname"),
    BranchQ("university"),
    BranchQ("semester"),
    BranchQ("home"),
    BranchQ("commute"),
    BranchQ("cooks"),
    // Costs: parents' roof has no rent; walkers have no fare and no peak run.
    BranchQ("rent", askIf = { it["home"] != "Parents" }),
    BranchQ("fare", askIf = { it["commute"] != "Walk" || it["walkOk"] == "No" }),
    BranchQ("classTimes", askIf = { it["commute"] != "Walk" || it["walkOk"] == "No" }),
    // If walking is not practical most class days, ask for the fare/timetable.
    BranchQ("walkOk", askIf = { it["commute"] == "Walk" }),
    // Cooks: grocery spot feeds the food engine's price context.
    BranchQ("grocery", askIf = { it["cooks"] == "Yes" }),
    // Far commuters: usual stages anchor fare estimates to real routes.
    BranchQ("stages", askIf = { it["commute"] == "Far" }),
    BranchQ("fundSource"),
    // HELB lands once a semester — one figure, never tranches.
    BranchQ("helb", askIf = { it["fundSource"] != "SELF" }),
    BranchQ("pocket"),
    BranchQ("upkeep"),
    BranchQ("foodBudget"),
    BranchQ("fees"),
    BranchQ("monthlyBudget"),
    BranchQ("airtime"),
    BranchQ("save"),
    BranchQ("semesterEnd")
)

fun visibleBranches(answers: Map<String, String>): List<String> =
    WELCOME_BRANCHES.filter { it.askIf(answers) }.map { it.id }
