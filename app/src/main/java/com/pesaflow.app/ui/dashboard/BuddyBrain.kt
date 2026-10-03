package com.pesaflow.app.ui.dashboard

import com.pesaflow.app.data.finance.DataQuality
import com.pesaflow.app.data.finance.FinancialSnapshot
import com.pesaflow.app.data.finance.MetricExplanation
import com.pesaflow.app.data.finance.MoneyFormatter

// PesaBuddy intent layer: scored multilingual intents over the keyword chain.
// Three jobs: (1) synonym expansion (append-only, never rewrites — zero
// regression risk to the existing branches), (2) entity extraction (amounts
// incl. number-words, days), (3) follow-up memory ("and yesterday?" reuses
// the last intent). Ties ask back instead of guessing wrong.
object BuddyMemory {
    var lastIntent: String? = null
}

object BuddyBrain {

    private val FEATURE_GUIDES = listOf(
        listOf("transaction", "ledger", "sms", "mpesa", "history", "import") to
            "Transactions: review parsed SMS, correct categories, add manual entries, and inspect your ledger. SMS history scanning is read-only until rows are queued or confirmed.",
        listOf("budget", "envelope", "daily plan", "weekly plan") to
            "Budgets: set daily, weekly, monthly or semester limits, review category envelopes, and use the calculator for suggestions. Suggestions are plans, not extra money.",
        listOf("income", "helb", "sponsor", "expected money") to
            "Income: record where money comes from and its expected amount/date. Expected income is a forecast; it does not increase cash held until it lands and is logged.",
        listOf("bill", "obligation", "payer") to
            "Bills: record due dates, remaining amounts and who pays. Only bills assigned to you should reduce your own available plan.",
        listOf("debt", "fuliza", "owe") to
            "Debt: track money you owe or are owed. Fuliza draws count as cash received but remain borrowing, not earned income; repayments reduce the tracked balance.",
        listOf("saving", "goal", "net worth", "assets") to
            "Savings and Net Worth: track goals, savings, investments, assets and liabilities. A transfer into savings changes where money is held; it is not everyday spending.",
        listOf("meal", "menu", "recipe", "food plan") to
            "Meal Planner: save meals and prices, use your food budget to generate plans, and account for kitchen stock. It won't assume a food budget when you haven't set one.",
        listOf("stock", "pantry", "kitchen", "unga") to
            "Kitchen Stock: record quantities, prices and use rates to estimate when staples run out and what a refill may cost.",
        listOf("things", "belonging", "need to buy", "own what") to
            "My Things: track what you already own and what you still need, with estimated costs and priorities.",
        listOf("university", "campus", "semester", "timetable", "lecture", "commute") to
            "University and Semester: save campus details, timetable and semester dates, then use the student planner for term cash flow and commute-aware guidance.",
        listOf("analytics", "chart", "report", "insight", "trend") to
            "Analytics and Reports: explore logged spending, category trends, period comparisons and cash-flow explanations. Comparisons need records in both periods; missing history is not treated as zero evidence.",
        listOf("contact", "nancy", "name rule", "categorize people") to
            "Contact Book: save a relationship/category rule and optional alternative names. Parsed messages are matched after parsing, with separate money-in/out scope."
    )

    fun appGuide(question: String): String? {
        val q = question.lowercase()
        val asksOverview = listOf(
            "explain the app", "explain this app", "entire app", "whole app",
            "what can this app", "what can you do", "how does this app work",
            "what can i do", "what does this app do", "how does the app work",
            "show me around", "app guide", "all features"
        ).any(q::contains)
        if (asksOverview) {
            return "PesaFlow brings your student money tools together: Home shows cash and safe-to-spend; Transactions holds your ledger and SMS review; Budgets sets spending limits; Insights and Analytics explain recorded patterns. " +
                "More contains Income, Bills, Debt, Savings/Goals, Meal Planner, Kitchen Stock, My Things, University/Semester, Reports, Net Worth, Contact Book, Settings and data tools. " +
                "I can explain a screen or a figure using your saved records—ask, for example, “why is flexible money KSh…?” or “where do I set who pays this bill?”"
        }
        val asksForGuidance = listOf(
            "how do i", "how to", "where do i", "where can i", "explain",
            "what is", "how does", "how can i use"
        ).any(q::contains)
        if (!asksForGuidance) return null
        val guide = FEATURE_GUIDES.firstOrNull { (terms, _) -> terms.any(q::contains) }
        return guide?.second
    }

    fun explainMetric(question: String, snapshot: FinancialSnapshot): String? =
        explainMetric(question, snapshot.explanations, snapshot.liquid)

    fun explainMetric(
        question: String,
        explanations: Map<String, MetricExplanation>,
        liquid: com.pesaflow.app.data.finance.Money,
        monthlyBudget: Double? = null,
        monthExpenses: Double = 0.0
    ): String? {
        val q = question.lowercase()
        val asksWhy = listOf(
            "why", "how come", "where does", "where did", "based on what",
            "what goes into", "explain", "formula", "calculated", "calculation"
        ).any(q::contains)
        if (!asksWhy) return null

        val target = when {
            listOf("safe today", "safe to spend", "safe-to-spend", "spend today", "daily safe", "daily allowance").any(q::contains) -> "safeToday"
            listOf("flexible", "free money", "money left", "available to plan").any(q::contains) -> "flexible"
            listOf("forecast", "projected", "month end", "end of month").any(q::contains) -> "forecast"
            listOf("net worth", "wealth").any(q::contains) -> "netWorth"
            listOf("budget", "envelope").any(q::contains) -> "budget"
            listOf("balance", "cash held", "liquid").any(q::contains) -> "liquid"
            else -> null
        }
        if (target == null) {
            return "Which figure should I explain—cash held, flexible money, safe to spend, month-end forecast, budget progress, or net worth? I won't guess which number you mean."
        }
        if (target == "liquid") {
            return "Cash held is the net of recognized money movements in your ledger: recorded inflows add and outflows subtract. The current ledger does not yet keep opening-upkeep cash in a separate equity account, so imported history can affect this figure. " +
                "It can differ from a current M-Pesa SMS balance if messages are pending, another wallet is missing, or records haven't been reconciled. " +
                "The current ledger figure is ${MoneyFormatter.compact(liquid)}."
        }
        if (target == "budget") {
            if (monthlyBudget == null) return "There is no monthly budget envelope set, so a budget progress figure should not be shown yet. Daily, weekly, semester and annual budgets are separate periods."
            val remaining = monthlyBudget - monthExpenses
            val percent = (monthExpenses / monthlyBudget * 100).toInt()
            return "Monthly budget progress uses the monthly ALL envelope when set; otherwise it uses the sum of monthly category envelopes (not daily or weekly limits). " +
                "The envelope is ${MoneyFormatter.compact(com.pesaflow.app.data.finance.Money.of(monthlyBudget))}; recorded expenses this month are ${MoneyFormatter.compact(com.pesaflow.app.data.finance.Money.of(monthExpenses))}; " +
                "that is $percent% used and ${MoneyFormatter.compact(com.pesaflow.app.data.finance.Money.of(remaining))} remaining. Only expenses recorded in the ledger are counted."
        }
        val explanation: MetricExplanation = explanations[target] ?: return null
        val quality = when (explanation.quality) {
            DataQuality.FULL -> "Evidence: fuller history."
            DataQuality.PARTIAL -> "Evidence: partial history; treat this as an estimate."
            DataQuality.SPARSE -> "Evidence: sparse history; treat this as a rough estimate, not a promise."
        }
        val contributors = explanation.contributors.takeIf { it.isNotEmpty() }
            ?.joinToString("; ")?.let { " Inputs: $it." }.orEmpty()
        val basis = explanation.basis.takeIf { it.isNotBlank() }?.let { " Basis: $it." }.orEmpty()
        return "${explanation.label} is ${MoneyFormatter.compact(explanation.headline)} (${explanation.horizon}). " +
            "${explanation.why}$contributors$basis $quality"
    }

    // Intents whose follow-ups accept a day entity ("and yesterday?").
    private val DAY_INTENTS = setOf("spend", "week", "summary", "balance", "budget", "food", "transport")

    private val INTENTS: List<Pair<String, List<String>>> = listOf(
        "greeting" to listOf("hello", "hey", "habari", "sasa", "mambo", "niaje", "wassup"),
        "help" to listOf("help", "unaeza", "nisaidie", "saidia", "how do i", "what can you"),
        "thanks" to listOf("thank", "asante", "shukran"),
        "balance" to listOf("balance", "baki", "niko na", "remaining", "left", "nisalio", "salio"),
        "spend" to listOf("spend", "burn", "expense", "matumizi", "tumia", "gharama", "nimetumia"),
        "today" to listOf("today", "leo"),
        "yesterday" to listOf("yesterday", "jana"),
        "week" to listOf("week", "wiki"),
        "budget" to listOf("budget", "bajeti"),
        "safe" to listOf("safe", "kila siku", "can i spend", "per day"),
        "afford" to listOf("afford", "naeza", "can i buy", "nunua"),
        "runout" to listOf("run out", "nitakwisha", "how long will"),
        "bills" to listOf("bill", "lipia", "inadaiwa", "due"),
        "debts" to listOf("debt", "madeni", "deni", "owe", "borrow", "kopa", "daiwa"),
        "meals" to listOf("menu", "meal", "food budget"),
        "food" to listOf("food", "kaini", "chakula", "munch", "kula", "lunch", "supper", "breakfast"),
        "transport" to listOf("transport", "boda", "matatu", "nauli", "fare", "mathree"),
        "summary" to listOf("summary", "breakdown", "report", "overview", "muhtasari"),
        "compare" to listOf("compare", "vs last", "difference", "last month"),
        "savings" to listOf("saving", "saved", "akiba"),
        "goals" to listOf("goal", "target", "laptop"),
        "helb" to listOf("helb", "fees", "ada", "upkeep"),
        "income" to listOf("salary", "income", "mshahara", "paycheck"),
        "busy" to listOf("busy", "lecture", "timetable", "darasa"),
        "free" to listOf("free evening", "free to cook", "when free"),
        "survival" to listOf("surviv", "stretch", "make it to"),
        "stock" to listOf("stock", "kitchen", "cupboard", "unga"),
        "belongings" to listOf("lack", "need to buy", "things", "vitu", "ninahitaji"),
        "cut" to listOf("cut", "reduce", "punguza", "what can i save"),
        "track" to listOf("on track", "progress", "am i ok"),
        "bye" to listOf("bye", "tutaonana")
    )

    private val LABELS = mapOf(
        "balance" to "your balance", "spend" to "your spending",
        "budget" to "your budget", "bills" to "bills due",
        "debts" to "madeni", "week" to "this week",
        "summary" to "a full summary", "food" to "food spending",
        "transport" to "transport spending"
    )

    // Append-only expansion: original words stay, so old branches keep matching.
    fun normalize(q: String): String {
        val sb = StringBuilder(q)
        fun has(vararg ws: String) = ws.any { q.contains(it) }
        if (has("mullah", "doh", "chapaa", "ganji", "cheddar", "bob", "soo", "ksh", "pesa")) sb.append(" money")
        if (has("nime", "nita", "naeza", "nataka", "niko")) sb.append(" i")
        if (has("chakula", "kula", "munch", "kibanda", "ugali", "githeri")) sb.append(" food")
        if (has("mathree", "matatu", "boda", "nauli", "fare")) sb.append(" transport")
        if (has("bajeti", "matumizi", "gharama")) sb.append(" spending")
        if (has("mzazi", "wazazi", "helb", "upkeep")) sb.append(" income")
        if (has("deni", "madeni", "kopa", "daiwa")) sb.append(" debt")
        if (has("darasa", "somo", "lecturer")) sb.append(" lecture")
        return sb.toString()
    }

    private val ONES = mapOf(
        "moja" to 1, "mbili" to 2, "tatu" to 3, "nne" to 4, "tano" to 5,
        "sita" to 6, "saba" to 7, "nane" to 8, "tisa" to 9, "kumi" to 10
    )

    // Amounts: digits first, then Sheng/Swahili number words, then Xk shorthand.
    fun extractAmount(q: String): Double? {
        Regex("(\\d[\\d,]*)").find(q)?.value?.replace(",", "")?.toDoubleOrNull()?.let { return it }
        Regex("(\\d+(?:\\.\\d+)?)\\s*k\\b").find(q)?.let { return it.groupValues[1].toDoubleOrNull()?.times(1000) }
        ONES.forEach { (w, n) ->
            if (q.contains("elfu $w")) return n * 1000.0
            if (q.contains("soo $w") || q.contains("mia $w")) return n * 100.0
        }
        if (q.contains("elfu")) return 1000.0
        if (q.contains("soo") || q.contains("mia")) return 100.0
        return null
    }

    private val DAY_WORDS = mapOf(
        "monday" to "monday", "tuesday" to "tuesday", "wednesday" to "wednesday",
        "thursday" to "thursday", "friday" to "friday", "saturday" to "saturday",
        "sunday" to "sunday", "yesterday" to "yesterday", "jana" to "yesterday",
        "today" to "today", "leo" to "today", "tomorrow" to "tomorrow", "kesho" to "tomorrow"
    )

    fun extractDay(q: String): String? =
        DAY_WORDS.entries.firstOrNull { q.contains(it.key) }?.value

    data class Scored(val name: String, val conf: Float)

    fun classify(q: String): List<Scored> {
        return INTENTS.map { (name, keys) ->
            val hits = keys.count { q.contains(it) }
            val conf = if (hits == 0) 0f else (0.35f + 0.15f * hits).coerceAtMost(1f)
            Scored(name, conf)
        }.sortedByDescending { it.conf }
    }

    // Entity-only follow-up + live memory → explicit rewritten query. Null = handle normally.
    fun rewriteFollowUp(raw: String, q: String): String? {
        val mem = BuddyMemory.lastIntent ?: return null
        if (classify(q).firstOrNull()?.conf ?: 0f >= 0.5f) return null
        val day = extractDay(raw)
        val amt = extractAmount(raw)
        val isBare = day != null || amt != null ||
            raw.trim().matches(Regex("^(and|na|what about|hiyo|hii|that|it)\\b.*"))
        if (!isBare) return null
        return when {
            day != null && mem in DAY_INTENTS -> "how much did i spend $day"
            amt != null && mem == "afford" -> "can i afford ${amt.toInt()}"
            day != null && mem == "busy" -> "am i busy $day"
            else -> null
        }
    }

    // Close tie between two known intents → ask back with examples, never guess.
    fun disambiguate(q: String): String? {
        val top = classify(q).take(2)
        if (top.size < 2) return null
        val (a, b) = top
        if (a.conf < 0.35f || a.conf >= 0.6f || a.conf - b.conf >= 0.2f) return null
        val la = LABELS[a.name] ?: return null
        val lb = LABELS[b.name] ?: return null
        return "Do you mean $la or $lb? Add one more word — e.g. 'budget left?' or 'food spending?'."
    }

    // Trained-model assist: expansion phrases routing an ML intent into its
    // VERIFIED keyword branch. Only mapped intents resolve; anything else
    // returns null and the generic fallback stays. Pure, unit-tested.
    private val ML_INTENT_EXPANSION = mapOf(
        "balance_query" to "balance",
        "affordability_check" to "can i afford",
        "financial_constraint_update" to "survive till month end",
        "week_summary" to "summary",
        "month_compare" to "compare vs last",
        "food_query" to "food how much spend",
        "bills_query" to "bill due",
        "budget_query" to "budget",
        "savings_query" to "saved",
        "runout_query" to "run out",
        "safe_spend_query" to "can i spend",
        "greeting" to "hello"
    )

    fun mlAssistExpansion(mlLabel: String): String? = ML_INTENT_EXPANSION[mlLabel]

    /** The model may speak only when rules are blank and it is sure. */
    fun shouldMlAssist(ruleTopConf: Float, mlConf: Float): Boolean =
        ruleTopConf < 0.35f && mlConf >= 0.7f
}
