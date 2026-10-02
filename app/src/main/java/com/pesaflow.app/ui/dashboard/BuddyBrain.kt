package com.pesaflow.app.ui.dashboard

// PesaBuddy intent layer: scored multilingual intents over the keyword chain.
// Three jobs: (1) synonym expansion (append-only, never rewrites — zero
// regression risk to the existing branches), (2) entity extraction (amounts
// incl. number-words, days), (3) follow-up memory ("and yesterday?" reuses
// the last intent). Ties ask back instead of guessing wrong.
object BuddyMemory {
    var lastIntent: String? = null
}

object BuddyBrain {

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
