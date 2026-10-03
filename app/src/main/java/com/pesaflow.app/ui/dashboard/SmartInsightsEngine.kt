package com.pesaflow.app.ui.dashboard

import com.pesaflow.app.data.models.AppLanguage
import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.Debt
import com.pesaflow.app.data.models.SavingsGoal
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.isEarnedIncome
import com.pesaflow.app.data.time.changeVsPrevious
import com.pesaflow.app.data.time.isDustBaseline
import com.pesaflow.app.ui.budgets.Persona
import java.util.Calendar

// Month-end forecast (from PesaFlow main): pace + open bills vs money
// actually held. Names the broke date when one exists — the single most
// forward-looking line here.
data class MonthForecast(val projectedTotal: Double, val brokeDay: Int?, val daysLeft: Int)

fun monthEndForecast(
    monthSpent: Double,
    dailyPace: Double,
    balance: Double,
    openBills: Double,
    nowMs: Long = System.currentTimeMillis()
): MonthForecast {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
    val dim = c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val today = c.get(java.util.Calendar.DAY_OF_MONTH)
    val daysLeft = (dim - today).coerceAtLeast(0)
    val projected = monthSpent + dailyPace * daysLeft + openBills
    var broke: Int? = null
    if (balance > 0 && dailyPace > 0) {
        for (d in today..dim) {
            if (monthSpent + dailyPace * (d - today) + openBills >= balance) {
                broke = d
                break
            }
        }
    }
    return MonthForecast(projected, broke, daysLeft)
}

internal fun ord(day: Int): String = when {
    day in 11..13 -> "${day}th"
    day % 10 == 1 -> "${day}st"
    day % 10 == 2 -> "${day}nd"
    day % 10 == 3 -> "${day}rd"
    else -> "${day}th"
}

internal fun buildInsights(
    txs: List<Transaction>,

    budgets: List<Budget>,
    lang: AppLanguage,
    name: String,
    bills: List<Bill>,
    debts: List<Debt>,
    goals: List<SavingsGoal>,
weekPlan: Map<String, Set<String>> = emptyMap(),
persona: Persona = Persona.HOSTEL_COOK,
// Declared-but-not-landed income (HELB tranches, seeded sources): the app
// already knows payday, so it waits instead of nagging to add income.
expectedMonthlyIncome: Double = 0.0,
// M-Pesa fee bleed this month (tracked separately, never in spending).
monthFees: Double = 0.0,
// Hustle lens: declared hustle expectation vs landed this month.
hustleExpected: Double = 0.0,
hustleLanded: Double = 0.0,
// Money actually held (ledger balance): pace + open bills are judged
// against it, naming the dry date when one exists.
heldBalance: Double = 0.0,
// Pinned watchlist categories: report first, budget or not.
watched: Set<String> = emptySet()
): List<String> {
    fun t(en: String, sh: String, sw: String, mix: String): String =
        when (lang) {
            AppLanguage.SHENG -> sh
            AppLanguage.KISWAHILI -> sw
            AppLanguage.MIXED -> mix
            else -> en
        }
    val nn = if (name.isNotBlank()) "$name, " else ""
    if (txs.isEmpty()) return listOf(t(
        "Add transactions and I'll spot patterns. 👀",
        "Weka transactions ni-spot patterns. 👀",
        "Weka miamala nianze kuchambua. 👀",
        "Weka transactions ni-spot patterns. 👀"
    ))
    val out = mutableListOf<String>()
    val cal = Calendar.getInstance()
    fun inMonth(ts: Long, offset: Int = 0): Boolean {
        val ref = (cal.clone() as Calendar).apply { add(Calendar.MONTH, offset) }
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return c.get(Calendar.YEAR) == ref.get(Calendar.YEAR) &&
            c.get(Calendar.MONTH) == ref.get(Calendar.MONTH)
    }
    val monthExp = txs.filter { it.type == TransactionType.EXPENSE && !it.isSample && inMonth(it.dateTimestamp) }
    val monthTotal = monthExp.sumOf { it.amount }
    // Earned income only — onboarding opening rows are held cash, and counting
    // them here broke the overspend alarm + savings rate every onboarding month.
    val monthIncome = txs.filter { it.isEarnedIncome() && !it.isSample && inMonth(it.dateTimestamp) }.sumOf { it.amount }
    if (monthTotal <= 0) return listOf(t(
        "No spending this month yet.",
        "Hujaspend this month.",
        "Hakuna matumizi mwezi huu.",
        "No spending this month."
    ))

    // Overspend alarm: outgo already past income with month still running.
    if (monthIncome > 0 && monthTotal > monthIncome) {
        val gap = monthTotal - monthIncome
        out.add(t(
            "Danger: spent KSh ${monthTotal.toInt()} against KSh ${monthIncome.toInt()} income — KSh ${gap.toInt()} over. Freeze non-food spending. 🛑",
            "Danger: umespend KSh ${monthTotal.toInt()} na income ni KSh ${monthIncome.toInt()} — KSh ${gap.toInt()} juu. Freeze vitu si food. 🛑",
            "Hatari: umetumia KSh ${monthTotal.toInt()} dhidi ya mapato KSh ${monthIncome.toInt()} — KSh ${gap.toInt()} zaidi. Sitisha matumizi yasiyo ya chakula. 🛑",
            "Danger: spent KSh ${monthTotal.toInt()} vs KSh ${monthIncome.toInt()} income — KSh ${gap.toInt()} over. Freeze non-food. 🛑"
        ))
    }

    // Hourly pattern: which 3-hour window owns the outflow? Thin or flat
    // data stays silent — the engine only speaks with 5+ rows and 35%+.
    com.pesaflow.app.data.analytics.hourlyPeak(
        txs.filter { !it.isSample }.map {
            com.pesaflow.app.data.parsers.LedgerRow(
                it.amount, it.type, it.category, it.merchant, it.dateTimestamp,
                it.isSample, it.isOpening, it.isEarnedIncome()
            )
        }
    )?.let { hp ->
        val h = com.pesaflow.app.data.analytics.hourLabel(hp.peakStartHour)
        out.add(t(
            "Rhythm: most spending lands around $h (~${hp.sharePct}% of outflows). Big buys outside that window stand out. 🕐",
            "Rhythm: spending mingi hu-land around $h (~${hp.sharePct}% ya outflows). Nunua kubwa nje ya io window ina-stand out. 🕐",
            "Mzunguko: matumizi mengi hutokea karibu na $h (~asilimia ${hp.sharePct} ya matumizi). Ununuzi mkubwa nje ya muda huo unajitokeza. 🕐",
            "Rhythm: spending most hu-land around $h (~${hp.sharePct}% ya outflows). Big buys outside that window hu-stand out. 🕐"
        ))
    }
    // the spending until the next one — no double-counting.
    com.pesaflow.app.data.analytics.paydaySplurge(
        txs.filter { !it.isSample }.map {
            com.pesaflow.app.data.parsers.LedgerRow(
                it.amount, it.type, it.category, it.merchant, it.dateTimestamp,
                it.isSample, it.isOpening, it.isEarnedIncome()
            )
        }
    )?.let { splurge ->
        if (splurge.paydays >= 2 && splurge.avgPctSpent7d >= 60) {
            out.add(t(
                "Payday splurge: ~${splurge.avgPctSpent7d}% of each income is gone within a week of landing (${splurge.paydays} paydays). Move savings out on day one. 💸",
                "Payday splurge: ~${splurge.avgPctSpent7d}% ya income inaisha wiki moja after kuland (${splurge.paydays} paydays). Toa savings day one. 💸",
                "Matumizi ya siku ya mshahara: ~${splurge.avgPctSpent7d}% ya mapato yanaisha wiki moja baada ya kuingia (siku ${splurge.paydays} za malipo). Toa akiba siku ya kwanza. 💸",
                "Payday splurge: ~${splurge.avgPctSpent7d}% ya income inaisha within a week of landing (${splurge.paydays} paydays). Move savings day one. 💸"
            ))
        }
    }

    // Month-end forecast: current burn projected out, judged vs ALL budget.
    run {
        val dim = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dom = cal.get(Calendar.DAY_OF_MONTH)
        val activeDays = monthExp.map { tx ->
            Calendar.getInstance().apply { timeInMillis = tx.dateTimestamp }.get(Calendar.DAY_OF_MONTH)
        }.toSet().size
        if (dom >= 7 && activeDays >= 4 && dim > dom) {
            val projected = monthTotal / dom * dim
            val allLimit = budgets.firstOrNull { it.category == "ALL" }?.limitAmount ?: 0.0
            if (allLimit > 0) {
                val over = projected - allLimit
                out.add(if (over > 0) t(
                    "Pace check: heading for KSh ${projected.toInt()} by month-end — KSh ${over.toInt()} over budget. Ease off. 📉",
                    "Pace check: unaelekea KSh ${projected.toInt()} mwisho wa mwezi — KSh ${over.toInt()} juu ya budget. Tuliza. 📉",
                    "Ukaguzi wa mwendo: unaelekea KSh ${projected.toInt()} mwisho wa mwezi — KSh ${over.toInt()} juu ya bajeti. Punguza. 📉",
                    "Pace check: KSh ${projected.toInt()} by month-end — KSh ${over.toInt()} over. Ease off. 📉"
                ) else t(
                    "Pace check: heading for KSh ${projected.toInt()} — inside your KSh ${allLimit.toInt()} budget. ✅",
                    "Pace check: unaelekea KSh ${projected.toInt()} — ndani ya budget yako ya KSh ${allLimit.toInt()}. ✅",
                    "Ukaguzi wa mwendo: unaelekea KSh ${projected.toInt()} — ndani ya bajeti yako ya KSh ${allLimit.toInt()}. ✅",
                    "Pace check: KSh ${projected.toInt()} — inside budget. ✅"
                ))
            } else {
                out.add(t(
                    "Pace check: heading for KSh ${projected.toInt()} by month-end at this burn. 📉",
                    "Pace check: unaelekea KSh ${projected.toInt()} mwisho wa mwezi kwa mwendo huu. 📉",
                    "Ukaguzi wa mwendo: unaelekea KSh ${projected.toInt()} mwisho wa mwezi. 📉",
                    "Pace check: KSh ${projected.toInt()} by month-end. 📉"
                ))
            }
        }
    }

    // Anomaly day: today vs your usual for this weekday (4-week average).
    run {
        val now = cal.timeInMillis
        val dayStart = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todaySpend = txs.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp >= dayStart }.sumOf { it.amount }
        if (todaySpend > 0) {
            val c2 = Calendar.getInstance()
            val sums = DoubleArray(7)
            txs.filter {
                it.type == TransactionType.EXPENSE && !it.isSample &&
                    it.dateTimestamp >= now - 28L * 24 * 60 * 60 * 1000 && it.dateTimestamp < dayStart
            }.forEach {
                c2.timeInMillis = it.dateTimestamp
                sums[(c2.get(Calendar.DAY_OF_WEEK) + 5) % 7] += it.amount
            }
            c2.timeInMillis = now
            val idx = (c2.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val usual = sums[idx] / 4
            val dayName = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[idx]
            if (usual > 0 && todaySpend > usual * 2 && todaySpend - usual >= 200) {
                out.add(t(
                    "Unusual $dayName: KSh ${todaySpend.toInt()} today vs usual KSh ${usual.toInt()}. Big day or double-log? 👀",
                    "Siku weird: KSh ${todaySpend.toInt()} leo vs kawaida KSh ${usual.toInt()} $dayName. Siku kubwa ama double-log? 👀",
                    "Siku ya ajabu: KSh ${todaySpend.toInt()} leo dhidi ya kawaida KSh ${usual.toInt()}. 👀",
                    "Unusual $dayName: KSh ${todaySpend.toInt()} vs usual KSh ${usual.toInt()}. 👀"
                ))
            }
        }
    }

    // Top category share
    monthExp.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.maxByOrNull { it.value }?.let {
        val amt = "KSh ${it.value.toInt()}"
        val pct = (it.value / monthTotal * 100).toInt()
        out.add(t(
            "Most spending: ${it.key} — $amt ($pct%).",
            "Mullah mingi: ${it.key} — $amt ($pct%).",
            "Matumizi makubwa: ${it.key} — $amt ($pct%).",
            "Spending kubwa: ${it.key} — $amt ($pct%)."
        ))
        // Uncategorized bulk: a donut that reads "Other 100%" is a filing
        // problem, not a spending fact — one Review pass teaches it.
        if (it.key.equals("Other", ignoreCase = true) && pct >= 50) {
            out.add(t(
                "$amt sits uncategorized — open Review and teach each row once; every insight sharpens after. 🏷️",
                "KSh ${it.value.toInt()} haina category — ingia Review ufundishe kila row mara moja; insights zote zinakali after. 🏷️",
                "KSh ${it.value.toInt()} hazina kategoria — ingia Review ufundishe kila safu mara moja. 🏷️",
                "KSh ${it.value.toInt()} bado Other — Review once, insights sharpen after. 🏷️"
            ))
        }
    }

    // Weekend vs weekday pace
    var weekendSum = 0.0
    var weekdaySum = 0.0
    monthExp.forEach { tx ->
        val d = Calendar.getInstance().apply { timeInMillis = tx.dateTimestamp }.get(Calendar.DAY_OF_WEEK)
        if (d == Calendar.SATURDAY || d == Calendar.SUNDAY) weekendSum += tx.amount else weekdaySum += tx.amount
    }
    var weekendDays = 0
    var weekdayDays = 0
    val cursor = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    while (!cursor.after(cal)) {
        val d = cursor.get(Calendar.DAY_OF_WEEK)
        if (d == Calendar.SATURDAY || d == Calendar.SUNDAY) weekendDays++ else weekdayDays++
        cursor.add(Calendar.DAY_OF_MONTH, 1)
    }
    if (weekendDays > 0 && weekdayDays > 0) {
        val weAvg = weekendSum / weekendDays
        val wdAvg = weekdaySum / weekdayDays
        if (weAvg > wdAvg * 1.2) out.add(t(
            "${nn}weekends cost more: KSh ${weAvg.toInt()}/day vs KSh ${wdAvg.toInt()} weekdays.",
            "${nn}weekend inaburn: KSh ${weAvg.toInt()}/day vs KSh ${wdAvg.toInt()} weekdays.",
            "${nn}wikendi inagharimu: KSh ${weAvg.toInt()}/siku vs KSh ${wdAvg.toInt()} siku za kazi.",
            "${nn}weekend pricey: KSh ${weAvg.toInt()}/day vs KSh ${wdAvg.toInt()} weekdays."
        ))
    }

    // Month-over-month change. Dust baselines get absolutes — a percent off
    // KSh 12 last month is noise ("up 4000000%"), never insight.
    val currentDay = cal.get(Calendar.DAY_OF_MONTH)
    val lastTotal = txs.filter {
        it.type == TransactionType.EXPENSE && !it.isSample && inMonth(it.dateTimestamp, -1) &&
            Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }.get(Calendar.DAY_OF_MONTH) <= currentDay
    }.sumOf { it.amount }
    if (lastTotal > 0) {
        val top = monthExp.groupBy { it.category }.maxByOrNull { e -> e.value.sumOf { it.amount } }?.key ?: "spending"
        if (isDustBaseline(lastTotal)) {
            out.add(t(
                "KSh ${monthTotal.toInt()} month-to-date vs KSh ${lastTotal.toInt()} for the same days last month — too little history for a percent. Watch $top. 📊",
                "KSh ${monthTotal.toInt()} month-to-date vs KSh ${lastTotal.toInt()} for the same days last month — history kidogo sana kwa percent. Watch $top. 📊",
                "KSh ${monthTotal.toInt()} hadi sasa dhidi ya KSh ${lastTotal.toInt()} kwa siku hizo mwezi uliopita — historia kidogo mno kwa asilimia. Angalia $top. 📊",
                "KSh ${monthTotal.toInt()} month-to-date vs KSh ${lastTotal.toInt()} for those same days — history kidogo for %. Watch $top. 📊"
            ))
        } else {
            val change = changeVsPrevious(monthTotal, lastTotal) ?: 0
            out.add(
                if (change > 0) t(
                    "Up $change% vs the same days last month. Watch $top. 📈",
                    "Ime Panda $change% vs siku hizo mwezi uliopita. Watch $top. 📈",
                    "Juu $change% dhidi ya siku hizo mwezi uliopita. Angalia $top. 📈",
                    "Up $change% vs the same days last month. Watch $top. 📈"
                ) else t(
                    "Down ${-change}% vs the same days last month. Good job! 📉",
                    "Imeshuka ${-change}% dhidi ya siku hizo mwezi uliopita. Poa sana! 📉",
                    "Chini ${-change}% dhidi ya siku hizo mwezi uliopita. Kazi nzuri! 📉",
                    "Down ${-change}% vs those same days last month. Poa! 📉"
                )
            )
        }
    }

    // Income vs spending verdict + savings rate
    val topName = monthExp.groupBy { it.category }.maxByOrNull { e -> e.value.sumOf { it.amount } }?.key ?: "top categories"
    if (monthIncome > 0) {
        val diff = monthIncome - monthTotal
        if (diff < 0) {
            out.add(t(
                "${nn}danger: KSh ${-diff.toInt()} more spent than earned. Cut $topName first. ⚠️",
                "${nn}uko red: KSh ${-diff.toInt()} zaidi. Kata $topName. ⚠️",
                "${nn}hatari: KSh ${-diff.toInt()} zaidi. Punguza $topName. ⚠️",
                "${nn}danger: KSh ${-diff.toInt()} over income. Cut $topName. ⚠️"
            ))
        } else {
            val rate = (diff / monthIncome * 100).toInt()
            if (rate >= 20) out.add(t(
                "Great: you saved $rate% (KSh ${diff.toInt()}). 💪",
                "Poa: umesave $rate% (KSh ${diff.toInt()}). 💪",
                "Vizuri: umeweka $rate% (KSh ${diff.toInt()}). 💪",
                "Poa: saved $rate% (KSh ${diff.toInt()}). 💪"
            ))
            else out.add(t(
                "Only $rate% saved (KSh ${diff.toInt()}). Target 20% — cut $topName.",
                "Ish $rate% tu (KSh ${diff.toInt()}). Target 20% — kata $topName.",
                "$rate% tu (KSh ${diff.toInt()}). Lenga 20% — punguza $topName.",
                "Only $rate% saved (KSh ${diff.toInt()}). Target 20% — cut $topName."
            ))
        }
    } else if (monthTotal > 0) {
        if (expectedMonthlyIncome > 0) {
            out.add(t(
                "No income landed yet — waiting on KSh ${expectedMonthlyIncome.toInt()} expected. Log it when it lands.",
                "Hakuna income bado — tunangoja KSh ${expectedMonthlyIncome.toInt()} expected. I-log ikifika.",
                "Hakuna kipato bado — tunasubiri KSh ${expectedMonthlyIncome.toInt()} kinachotarajiwa.",
                "No income yet — waiting on KSh ${expectedMonthlyIncome.toInt()} expected."
            ))
        } else {
            out.add(t(
                "Add income (+ Income) to compare in vs out.",
                "Weka income (+ Income) tu-compare.",
                "Weka kipato (+ Income) kulinganisha.",
                "Add income (+ Income) to compare."
            ))
        }
    }

    // M-Pesa fee bleed: visible here, never mixed into spending.
    if (monthFees > 0) {
        out.add(t(
            "M-Pesa fees ate KSh ${monthFees.toInt()} this month — batch withdrawals to cut it. 💸",
            "Fee za M-Pesa zimekula KSh ${monthFees.toInt()} this month — batch withdrawals ukata. 💸",
            "Tozo za M-Pesa zimetumia KSh ${monthFees.toInt()} mwezi huu — unganisha miamala kupunguza. 💸",
            "M-Pesa fees: KSh ${monthFees.toInt()} this month — batch withdrawals. 💸"
        ))
    }

    // Hustle lens: declared hustle expectation vs landed this month.
    if (hustleExpected > 0) {
        val hpct = (hustleLanded / hustleExpected * 100).toInt().coerceIn(0, 100)
        out.add(if (hustleLanded >= hustleExpected) t(
            "Hustle lens: KSh ${hustleLanded.toInt()} landed of KSh ${hustleExpected.toInt()} expected ($hpct%) — fully stacked. 💪",
            "Hustle lens: KSh ${hustleLanded.toInt()} imeingia of KSh ${hustleExpected.toInt()} expected ($hpct%) — ume-stack fiti. 💪",
            "Mtazamo wa hustle: KSh ${hustleLanded.toInt()} zimeingia kati ya KSh ${hustleExpected.toInt()} ($hpct%) — umejaza. 💪",
            "Hustle lens: KSh ${hustleLanded.toInt()}/${hustleExpected.toInt()} ($hpct%) — fully stacked. 💪"
        ) else t(
            "Hustle lens: KSh ${hustleLanded.toInt()} of KSh ${hustleExpected.toInt()} expected ($hpct%) — one gig covers the gap. 💪",
            "Hustle lens: KSh ${hustleLanded.toInt()} of KSh ${hustleExpected.toInt()} ($hpct%) — gig moja inatosha. 💪",
            "Mtazamo wa hustle: KSh ${hustleLanded.toInt()} kati ya KSh ${hustleExpected.toInt()} ($hpct%) — kibarua kimoja kinatosha. 💪",
            "Hustle lens: KSh ${hustleLanded.toInt()}/${hustleExpected.toInt()} ($hpct%) — one gig covers it. 💪"
        ))
    }

    // Timetable validation: declared busy days vs ledger movement.
    // Nudges when the lecture grid looks stale — never edits it.
    run {
        if (txs.isNotEmpty()) {
            val nowMsT = System.currentTimeMillis()
            val winT = com.pesaflow.app.data.academic.ScanWindow(
                nowMsT - 30L * 24 * 60 * 60 * 1000, nowMsT, "30d", false, false
            )
            val ledgerT = txs.filter { !it.isSample }
            val transportDays = ledgerT.filter {
                it.type == TransactionType.EXPENSE && it.category.equals("Transport", ignoreCase = true) &&
                    it.dateTimestamp >= winT.startMs
            }.map {
                java.util.Calendar.getInstance().apply { timeInMillis = it.dateTimestamp }.get(java.util.Calendar.DAY_OF_WEEK)
            }.toSet()
            val active = com.pesaflow.app.data.academic.inferSchoolDays(ledgerT, winT)
            val dowOf = mapOf("Sun" to 1, "Mon" to 2, "Tue" to 3, "Wed" to 4, "Thu" to 5, "Fri" to 6, "Sat" to 7)
            val labelOf = mapOf(1 to "Sun", 2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat")
            val order = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val declared = weekPlan.filterValues { it.isNotEmpty() }.keys.mapNotNull { dowOf[it] }.toSet()
            if (declared.isNotEmpty()) {
                val stale = (declared - transportDays - setOf(1)).mapNotNull { labelOf[it] }.sortedBy { order.indexOf(it) }
                val undeclared = (active - declared).mapNotNull { labelOf[it] }.sortedBy { order.indexOf(it) }
                if (stale.isNotEmpty()) {
                    out.add(t(
                        "Timetable check: ${stale.joinToString(", ")} marked busy but no movement in 30 days — grid stale? 🗓️",
                        "Timetable check: ${stale.joinToString(", ")} ime-mark busy lakini hakuna movement siku 30 — grid imepitwa? 🗓️",
                        "Ukaguzi wa ratiba: ${stale.joinToString(", ")} zimeorodheshwa busy lakini hakuna muamala siku 30 — ratiba imepitwa? 🗓️",
                        "Timetable check: ${stale.joinToString(", ")} busy but silent 30 days — stale grid? 🗓️"
                    ))
                } else if (undeclared.isNotEmpty()) {
                    out.add(t(
                        "Timetable check: spending on ${undeclared.joinToString(", ")} which isn't marked busy — add it to the grid? 🗓️",
                        "Timetable check: spending iko ${undeclared.joinToString(", ")} ambayo si busy — i-add kwa grid? 🗓️",
                        "Ukaguzi wa ratiba: matumizi yapo ${undeclared.joinToString(", ")} isiyo busy — iongezwe? 🗓️",
                        "Timetable check: spend on ${undeclared.joinToString(", ")}, not marked busy — add it? 🗓️"
                    ))
                }
            }
        }
    }

    // Broke-date forecast: pace + open bills vs money actually held.
    run {
        if (heldBalance > 0 && monthTotal > 0) {
            val calB = java.util.Calendar.getInstance()
            val domB = calB.get(java.util.Calendar.DAY_OF_MONTH)
            val paceB = monthTotal / domB.coerceAtLeast(1)
            val billsB = bills.filter { it.status != "PAID" && it.paidBy == "ME" }.sumOf { it.amount }
            val fc = monthEndForecast(monthTotal, paceB, heldBalance, billsB)
            val broke = fc.brokeDay
            if (broke != null && broke > domB) {
                out.add(t(
                    "${nn}at this pace you run dry around the ${ord(broke)} (~KSh ${fc.projectedTotal.toInt()} projected vs KSh ${heldBalance.toInt()} at hand). ⚠️",
                    "${nn}hii pace uta-dry around ${ord(broke)} (~KSh ${fc.projectedTotal.toInt()} vs KSh ${heldBalance.toInt()} mkononi). ⚠️",
                    "${nn}kwa mwendo huu utakauka karibu tarehe $broke (~KSh ${fc.projectedTotal.toInt()} dhidi ya KSh ${heldBalance.toInt()}). ⚠️",
                    "${nn}this pace you run dry around ${ord(broke)} (~KSh ${fc.projectedTotal.toInt()} vs KSh ${heldBalance.toInt()}). ⚠️"
                ))
            } else if (broke != null) {
                out.add(t(
                    "${nn}already over: KSh ${monthTotal.toInt()} gone + KSh ${billsB.toInt()} bills vs KSh ${heldBalance.toInt()} held. Essentials only. 🔴",
                    "${nn}tayari over: KSh ${monthTotal.toInt()} gone + KSh ${billsB.toInt()} bills vs KSh ${heldBalance.toInt()}. Essentials tu. 🔴",
                    "${nn}tayari umezidi: KSh ${monthTotal.toInt()} + bili KSh ${billsB.toInt()} dhidi ya KSh ${heldBalance.toInt()}. Muhimu tu. 🔴",
                    "${nn}already over: KSh ${monthTotal.toInt()} + KSh ${billsB.toInt()} bills vs KSh ${heldBalance.toInt()}. Essentials only. 🔴"
                ))
            } else {
                out.add(t(
                    "${nn}pace projects ~KSh ${fc.projectedTotal.toInt()} by month end — inside your KSh ${heldBalance.toInt()}. ✅",
                    "${nn}pace ina-project ~KSh ${fc.projectedTotal.toInt()} mwisho wa mwezi — ndani ya KSh ${heldBalance.toInt()}. ✅",
                    "${nn}mwendo unaonyesha ~KSh ${fc.projectedTotal.toInt()} mwisho wa mwezi — ndani ya KSh ${heldBalance.toInt()}. ✅",
                    "${nn}pace projects ~KSh ${fc.projectedTotal.toInt()} month end — inside KSh ${heldBalance.toInt()}. ✅"
                ))
            }
        }
    }

    // Forward projection: paydays + open bills + monthly commitments vs money
    // held — the app's eyes forward, not just the rear-view.
    run {
        val balance = txs.filter { !it.isSample }.sumOf {
            when (it.type) {
                TransactionType.INCOME -> it.amount
                TransactionType.EXPENSE -> -it.amount
                TransactionType.SAVING -> -it.amount
                TransactionType.INVESTMENT -> -it.amount
                TransactionType.TRANSFER -> 0.0
            }
        }
        val nowMs = System.currentTimeMillis()
        val projection = com.pesaflow.app.data.finance.projectCashFlow(
            balance, nowMs, 30,
            paydays = com.pesaflow.app.data.analytics.predictPaydays(txs, nowMs),
            bills = bills.filter { it.status != "PAID" && it.paidBy == "ME" },
            recurring = com.pesaflow.app.data.analytics.detectRecurring(txs)
        )
        val fmt = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
        if (projection.brokeDate != null) {
            out.add(t(
                "Forward: balance goes negative around ${fmt.format(java.util.Date(projection.brokeDate))} (low KSh ${projection.lowest.balance.toInt()}). Move money or delay spending. 🔮",
                "Forward: salio linaenda negative karibu ${fmt.format(java.util.Date(projection.brokeDate))} (low KSh ${projection.lowest.balance.toInt()}). Hamisha money au uahirishe matumizi. 🔮",
                "Kusonga: salio litaingia chini ya sifuri karibu ${fmt.format(java.util.Date(projection.brokeDate))} (KSh ${projection.lowest.balance.toInt()} chini). 🔮",
                "Forward: balance goes negative around ${fmt.format(java.util.Date(projection.brokeDate))} (low KSh ${projection.lowest.balance.toInt()}). 🔮"
            ))
        } else if (projection.lowest.balance < balance * 0.2 && balance > 0) {
            out.add(t(
                "Forward: gets tight — low KSh ${projection.lowest.balance.toInt()} around ${fmt.format(java.util.Date(projection.lowest.dayStart))}. 🔮",
                "Forward: hupata tight — low KSh ${projection.lowest.balance.toInt()} karibu ${fmt.format(java.util.Date(projection.lowest.dayStart))}. 🔮",
                "Kusonga: huzidi — KSh ${projection.lowest.balance.toInt()} chini karibu ${fmt.format(java.util.Date(projection.lowest.dayStart))}. 🔮",
                "Forward: gets tight — low KSh ${projection.lowest.balance.toInt()} around ${fmt.format(java.util.Date(projection.lowest.dayStart))}. 🔮"
            ))
        } else {
            out.add(t(
                "Forward: stays positive — low KSh ${projection.lowest.balance.toInt()} around ${fmt.format(java.util.Date(projection.lowest.dayStart))}. 🔮",
                "Forward: salio linabaki positive — low KSh ${projection.lowest.balance.toInt()} karibu ${fmt.format(java.util.Date(projection.lowest.dayStart))}. 🔮",
                "Kusonga: salio halitashuka — KSh ${projection.lowest.balance.toInt()} chini karibu ${fmt.format(java.util.Date(projection.lowest.dayStart))}. 🔮",
                "Forward: stays positive — low KSh ${projection.lowest.balance.toInt()} around ${fmt.format(java.util.Date(projection.lowest.dayStart))}. 🔮"
            ))
        }
    }

    // Budget pace check
    val dom = cal.get(Calendar.DAY_OF_MONTH)
    val dim = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val paceBudget = budgets.firstOrNull { it.category == "ALL" }
    if (paceBudget != null && paceBudget.limitAmount > 0 && monthTotal > 0 && dim > 0) {
        val expected = paceBudget.limitAmount * dom / dim
        if (monthTotal > expected * 1.15) out.add(t(
            "${nn}too fast: KSh ${monthTotal.toInt()} spent, KSh ${expected.toInt()} expected. Slow down. 🐢",
            "${nn}haraka sana: KSh ${monthTotal.toInt()} vs KSh ${expected.toInt()} expected. Tulia. 🐢",
            "${nn}haraka sana: KSh ${monthTotal.toInt()} badala ya KSh ${expected.toInt()}. Punguza. 🐢",
            "${nn}too fast: KSh ${monthTotal.toInt()} vs KSh ${expected.toInt()}. Slow down. 🐢"
        ))
        else if (monthTotal < expected * 0.7) out.add(t(
            "Good pace: KSh ${monthTotal.toInt()} vs KSh ${expected.toInt()} expected. Save the extra. 🐖",
            "Pace poa: KSh ${monthTotal.toInt()} vs KSh ${expected.toInt()}. Save extra. 🐖",
            "Mwendo mzuri: KSh ${monthTotal.toInt()} vs KSh ${expected.toInt()}. Weka ziada. 🐖",
            "Good pace: KSh ${monthTotal.toInt()} vs KSh ${expected.toInt()}. Save extra. 🐖"
        ))
    }

    // Budget watch
    val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val global = budgets.firstOrNull { it.category == "ALL" }
    if (global != null && global.limitAmount > 0) {
        val pct = (monthTotal / global.limitAmount * 100).toInt()
        if (pct >= 100) out.add(t(
            "${nn}budget finished (KSh ${global.limitAmount.toInt()}). Essentials only. ⚠️",
            "${nn}budget imeisha (KSh ${global.limitAmount.toInt()}). Essentials tu. ⚠️",
            "${nn}bajeti imekwisha (KSh ${global.limitAmount.toInt()}). Muhimu tu. ⚠️",
            "${nn}budget done (KSh ${global.limitAmount.toInt()}). Essentials only. ⚠️"
        ))
        else if (pct >= 80) out.add(t(
            "Budget $pct% used, ${daysInMonth - dayOfMonth + 1} days left.",
            "Budget $pct% used, siku ${daysInMonth - dayOfMonth + 1} zimebaki.",
            "Bajeti $pct% imetumika, siku ${daysInMonth - dayOfMonth + 1} zimebaki.",
            "Budget $pct% used, ${daysInMonth - dayOfMonth + 1} days left."
        ))
        else if (dayOfMonth > 1) {
            val projected = (monthTotal / dayOfMonth * daysInMonth).toInt()
            val inside = projected <= global.limitAmount
            out.add(t(
                "${nn}may end near KSh $projected (${if (inside) "inside" else "above"} budget). Just a guess. 🔮",
                "${nn}uta-end near KSh $projected (${if (inside) "inside" else "above"} budget). Guess tu. 🔮",
                "${nn}huenda ukafikia KSh $projected. Kadirio tu. 🔮",
                "${nn}may end near KSh $projected. Guess tu. 🔮"
            ))
        }
    }

    // Open bills — nearest due date first, overdue flagged by days.
    val openBills = bills.filter { it.status != "PAID" && it.paidBy == "ME" }
    if (openBills.isNotEmpty()) {
        val nowMsB = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        val byDue = openBills.sortedBy { it.dueDate }
        val overdue = byDue.filter { it.dueDate < nowMsB }
        val nearest = byDue.firstOrNull()
        val total = openBills.sumOf { it.amount }.toInt()
        val urgency = when {
            overdue.isNotEmpty() -> {
                val d = ((nowMsB - overdue.minOf { it.dueDate }) / dayMs).toInt()
                " OVERDUE by $d day(s) — lipa sai. 🔴"
            }
            nearest != null && nearest.dueDate - nowMsB < 3 * dayMs -> " Due in ${((nearest.dueDate - nowMsB) / dayMs).toInt()}d: ${nearest.name}. 🟡"
            nearest != null -> " Next: ${nearest.name} KSh ${nearest.amount.toInt()}."
            else -> ""
        }
        out.add(t(
            "${openBills.size} open bill(s): KSh $total.$urgency",
            "Bills ${openBills.size} open: KSh $total.$urgency",
            "Bili ${openBills.size} wazi: KSh $total.$urgency",
            "${openBills.size} open bills: KSh $total.$urgency"
        ))
    }

    // Open debts — split who owes whom, name the smallest you owe.
    val openDebts = debts.filter { it.status != "PAID" }
    if (openDebts.isNotEmpty()) {
        val iOwe = openDebts.filter { it.direction == "I_OWE" }
        val theyOwe = openDebts.filter { it.direction != "I_OWE" }
        val smallest = iOwe.minByOrNull { it.amount }
        val total = openDebts.sumOf { it.amount }.toInt()
        out.add(t(
            "Open debts: KSh $total (you owe KSh ${iOwe.sumOf { it.amount }.toInt()}, owed KSh ${theyOwe.sumOf { it.amount }.toInt()})." +
                (smallest?.let { " Clear ${it.person} KSh ${it.amount.toInt()} first. 🧹" } ?: " Clear the smallest first. 🧹"),
            "Madeni open: KSh $total (unadaiwa KSh ${iOwe.sumOf { it.amount }.toInt()})." +
                (smallest?.let { " Maliza ${it.person} KSh ${it.amount.toInt()} first. 🧹" } ?: " Maliza ndogo first. 🧹"),
            "Madeni wazi: KSh $total." +
                (smallest?.let { " Lipa ${it.person} KSh ${it.amount.toInt()} kwanza. 🧹" } ?: " Lipa ndogo kwanza. 🧹"),
            "Open debts: KSh $total (you owe KSh ${iOwe.sumOf { it.amount }.toInt()})." +
                (smallest?.let { " Clear ${it.person} KSh ${it.amount.toInt()} first. 🧹" } ?: " Clear smallest first. 🧹")
        ))
    }

    // Most urgent goal
    val nowMs = System.currentTimeMillis()
    val urgent = goals
        .map { g -> g to ((g.targetAmount - g.currentAmount).coerceAtLeast(0.0)) }
        .filter { it.second > 0 }
        .minByOrNull { it.first.targetTimestamp }
    if (urgent != null) {
        val days = ((urgent.first.targetTimestamp - nowMs) / (24L * 60 * 60 * 1000)).coerceAtLeast(0)
        val perDay = if (days > 0) urgent.second / days else urgent.second
        if (urgent.first.targetTimestamp < nowMs) {
            val od = ((nowMs - urgent.first.targetTimestamp) / (24L * 60 * 60 * 1000)).toInt()
            out.add(t(
                "${urgent.first.title} OVERDUE by $od day(s) — KSh ${urgent.second.toInt()} still needed. 🔴",
                "${urgent.first.title} ili-pitwa na $od day(s) — KSh ${urgent.second.toInt()} bado. 🔴",
                "${urgent.first.title} imechelewa siku $od — KSh ${urgent.second.toInt()} bado. 🔴",
                "${urgent.first.title} overdue $od days — KSh ${urgent.second.toInt()} remaining. 🔴"
            ))
        }
        out.add(t(
            "${urgent.first.title} needs KSh ${urgent.second.toInt()} in $days day(s) — KSh ${perDay.toInt()}/day.",
            "${urgent.first.title} inahitaji KSh ${urgent.second.toInt()} in $days day(s) — KSh ${perDay.toInt()}/day.",
            "${urgent.first.title} inahitaji KSh ${urgent.second.toInt()} kwa siku $days — KSh ${perDay.toInt()}/siku.",
            "${urgent.first.title} needs KSh ${urgent.second.toInt()} in $days days — KSh ${perDay.toInt()}/day."
        ))
    }

    // Punguza Spending: Category-specific overspending tips
    val categorySpend = monthExp
        .groupBy { it.category }
        .mapValues { e -> e.value.sumOf { it.amount } }
    val topOverspend = categorySpend.entries.maxByOrNull { it.value }
    if (topOverspend != null && monthTotal > 0) {
        val pct = (topOverspend.value / monthTotal * 100).toInt()
        val topCat = topOverspend.key
        if (pct >= 25) {
            val (tipEn, tipSh, tipSw, tipMix) = getPunguzaTipsForCategory(topCat, persona)
            out.add(t(
                "${nn}Punguza spending: $topCat is $pct% of expenses. $tipEn",
                "${nn}Punguza spending: $topCat ni $pct% ya expenses. $tipSh",
                "${nn}Punguza matumizi: $topCat ni $pct% ya matumizi. $tipSw",
                "${nn}Punguza spending: $topCat is $pct% of total. $tipMix"
            ))
        }
    }
    // Per-category envelopes: breach >100%, watch >80% (two loudest only).
    run {
        val monthByCat = monthExp.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
        budgets.filter { it.limitAmount > 0 && !it.category.equals("ALL", ignoreCase = true) }
            .mapNotNull { b ->
                val spent = monthByCat.entries.firstOrNull { it.key.equals(b.category, ignoreCase = true) }?.value ?: 0.0
                val pct = if (b.limitAmount > 0) (spent / b.limitAmount * 100).toInt() else 0
                if (pct >= 100) "${b.category} blown: KSh ${spent.toInt()} of KSh ${b.limitAmount.toInt()} ($pct%). Kata. ⚠️" to pct
                else if (pct >= 80) "${b.category} at $pct%: KSh ${spent.toInt()} of KSh ${b.limitAmount.toInt()}. Tulia. 🟡" to pct
                else null
            }
            .sortedByDescending { it.second }.take(2)
            .forEach { (msg, _) -> out.add(t(msg, msg, msg, msg)) }
    }
    // Watched categories: pinned envelopes report first, budget or not.
    if (watched.isNotEmpty()) {
        val monthByCatLower = monthExp.groupBy { it.category.lowercase() }.mapValues { e -> e.value.sumOf { it.amount } }
        watched.forEach { w ->
            val spent = monthByCatLower[w.lowercase()] ?: 0.0
            if (spent > 0) {
                val limit = budgets.firstOrNull { it.category.equals(w, ignoreCase = true) }?.limitAmount
                out.add(t(
                    "Watching $w: KSh ${spent.toInt()}" + (if (limit != null && limit > 0) " of KSh ${limit.toInt()} budget. 👀" else " this month. 👀"),
                    "Watching $w: KSh ${spent.toInt()}" + (if (limit != null && limit > 0) " of KSh ${limit.toInt()} budget. 👀" else " this month. 👀"),
                    "Unafuatilia $w: KSh ${spent.toInt()}" + (if (limit != null && limit > 0) " kati ya KSh ${limit.toInt()}. 👀" else " mwezi huu. 👀"),
                    "Watching $w: KSh ${spent.toInt()}" + (if (limit != null && limit > 0) " of KSh ${limit.toInt()} budget. 👀" else " this month. 👀")
                ))
            }
        }
    }
    if (monthTotal > 0 && out.size == 1) {
        out.add(t(
            "All clear — spending sits inside every line. Keep the rhythm. ✅",
            "Rada safi — spending iko chini ya kila line. Keep it ivo. ✅",
            "Shwari — matumizi yako chini ya kila kikomo. Endelea. ✅",
            "All clear — kila line iko sawa. Keep the rhythm. ✅"
        ))
    }
    // Timetable × ledger: do busy days actually cost more? Your week says
    // what's busy; your money says what it cost. Guiding, not law.
    run {
        val busyNames = weekPlan.filterValues { it.isNotEmpty() }.keys
        if (busyNames.isNotEmpty()) {
            val nameToDow = mapOf("Sun" to 1, "Mon" to 2, "Tue" to 3, "Wed" to 4, "Thu" to 5, "Fri" to 6, "Sat" to 7)
            val busyDows = busyNames.mapNotNull { nameToDow[it] }.toSet()
            if (busyDows.isNotEmpty()) {
                val nowMsH = System.currentTimeMillis()
                val dayMsH = 24L * 60 * 60 * 1000
                fun dayStart(ts: Long): Long {
                    val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
                    c.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    c.set(java.util.Calendar.MINUTE, 0)
                    c.set(java.util.Calendar.SECOND, 0)
                    c.set(java.util.Calendar.MILLISECOND, 0)
                    return c.timeInMillis
                }
                val busySums = mutableListOf<Double>()
                val freeSums = mutableListOf<Double>()
                (0 until 30).forEach { i ->
                    val ts = nowMsH - i * dayMsH
                    val start = dayStart(ts)
                    val sum = txs.filter {
                        it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp >= start && it.dateTimestamp < start + dayMsH
                    }.sumOf { it.amount }
                    val dow = java.util.Calendar.getInstance().apply { timeInMillis = ts }.get(java.util.Calendar.DAY_OF_WEEK)
                    if (dow in busyDows) busySums.add(sum) else freeSums.add(sum)
                }
                if (busySums.size >= 3 && freeSums.size >= 3) {
                    val busyAvg = busySums.average()
                    val freeAvg = freeSums.average()
                    if (freeAvg > 0 && busyAvg > freeAvg * 1.3) {
                        val ratio = String.format(java.util.Locale.US, "%.1f", busyAvg / freeAvg)
                        out.add(t(
                            "Busy days avg KSh ${busyAvg.toInt()} vs free KSh ${freeAvg.toInt()} (×$ratio) — carry lunch those days. Guide, not law 🙂",
                            "Siku busy avg KSh ${busyAvg.toInt()} vs free KSh ${freeAvg.toInt()} (×$ratio) — beba lunch siku hizo. Mwongozo tu 🙂",
                            "Busy days avg KSh ${busyAvg.toInt()} vs free KSh ${freeAvg.toInt()} (×$ratio) — carry lunch hizo days. Guide, not law 🙂",
                            "Busy days ~KSh ${busyAvg.toInt()} vs free KSh ${freeAvg.toInt()} (×$ratio) — carry lunch those days 🙂"
                        ))
                    }
                }
            }
        }
    }
    return out.take(6)
}





private fun getPunguzaTipsForCategory(category: String, persona: Persona): Quadruple<String, String, String, String> {
    val farCommute = persona == Persona.PARENTS_FAR || persona == Persona.RENT_COMMUTE
    val noCook = persona == Persona.HOSTEL_NOCOOK
    val atHome = persona == Persona.PARENTS_FAR || persona == Persona.PARENTS_NEAR
    return when (category.lowercase()) {
        "food" -> if (noCook) Quadruple(
            "Kibanda lunch plates beat fast food — same full stomach, half the price. 🍛",
            "Plate ya kibanda inashinda fast food — shibe same, bei nusu. 🍛",
            "Sahani ya kibandani inashinda vyakula vya haraka — shibe ile ile, bei nusu. 🍛",
            "Kibanda plates over fast food — same shibe, half price. 🍛"
        ) else Quadruple(
            "Try cooking at home or eating at campus mess — saves up to KSh 2,000/month. 🍳",
            "Try kucook kibanda/mess instead of high-end joints — inasave mullah kibao. 🍳",
            "Pika nyumbani au kula mess ya chuo — utaokoa hadi KSh 2,000 mwezi huu. 🍳",
            "Try cooking at home — saves up to KSh 2,000 monthly. 🍳"
        )
        "shopping" -> Quadruple(
            "Use the 24-hour rule: wait a day before non-essential purchases. 🛒",
            "Tumia 24-hr rule: tulia siku moja kabla kubuy vitu zisizohitajika. 🛒",
            "Subiri masaa 24 kabla ya kununua vitu visivyo vya lazima. 🛒",
            "Wait 24 hours before non-essential shopping buys. 🛒"
        )
        "airtime" -> Quadruple(
            "Buy data/airtime bundles in bulk or use campus Wi-Fi to lower daily spend. 📱",
            "Tumia Wi-Fi ya campus na obuy bundles za mwezi usimwage coin kila siku. 📱",
            "Tumia Wi-Fi ya chuo na nunua vifurushi vya mwezi ili kupunguza matumizi. 📱",
            "Use campus Wi-Fi & buy weekly/monthly airtime bundles. 📱"
        )
        "transport" -> if (farCommute) Quadruple(
            "Long route — travel off-peak or catch the early bus, peak fares run ~2x. 🚌",
            "Route ndefu — travel off-peak ama shika early bus, peak fare ni karibu double. 🚌",
            "Njia ndefu — safiri nje ya msongamano au panda basi la mapema, nauli ya peak ni karibu mara mbili. 🚌",
            "Long route — off-peak or early bus, peak fares run ~2x. 🚌"
        ) else Quadruple(
            "Walk short distances or walk with friends to cut matatu fare. 🚌",
            "Rauka mapema u-walk short distances kucut fare za matatu. 🚌",
            "Mtembee umbali mfupi ili kupunguza nauli za matatu. 🚌",
            "Walk short distances to reduce daily matatu fare. 🚌"
        )
        "rent" -> if (atHome) Quadruple(
            "No rent — protect that small Home upkeep envelope so it stays that way. 🏠",
            "Huna rent — linda hiyo kakitu ya home upkeep ibaki hivyo. 🏠",
            "Huna kodi — linda fedha ndogo ya matunzo ya nyumbani ibaki hivyo. 🏠",
            "No rent — protect the Home upkeep envelope. 🏠"
        ) else Quadruple(
            "Consider splitting rent/hostel with a roommate to share costs. 🏠",
            "Tafuta roommate m-share rent na hostel bills. 🏠",
            "Fikiria kushiriki pango na mwanafunzi mwenzako ili kupunguza gharama. 🏠",
            "Share hostel/rent expenses with a roommate. 🏠"
        )
        else -> Quadruple(
            "Set a strict weekly limit for $category and track every coin. 💡",
            "Weka limit ya weekly kwa $category u-track kila coin. 💡",
            "Weka kikomo cha kila wiki kwa $category ufuatilie kila sarafu. 💡",
            "Set a weekly limit for $category to stay within budget. 💡"
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
