package com.pesaflow.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.isEarnedIncome
import com.pesaflow.app.data.schedule.WeekPlan
import com.pesaflow.app.ui.budgets.Persona
import com.pesaflow.app.ui.budgets.parsePersona
import com.pesaflow.app.ui.theme.ppSpacing


@Composable
fun SmartInsightsCard(
    transactions: List<com.pesaflow.app.data.models.Transaction>,
    budgets: List<com.pesaflow.app.data.models.Budget>,
    lang: com.pesaflow.app.data.models.AppLanguage,
    name: String,
    bills: List<com.pesaflow.app.data.models.Bill>,
    debts: List<com.pesaflow.app.data.models.Debt>,
    goals: List<com.pesaflow.app.data.models.SavingsGoal>,
    incomeSources: List<com.pesaflow.app.data.income.IncomeSource> = emptyList(),
    persona: Persona = Persona.HOSTEL_COOK
) {
    val appCtx = LocalContext.current
    val weekPlan = WeekPlan.load(appCtx)
    val expectedIncome = remember(incomeSources) { incomeSources.sumOf { com.pesaflow.app.data.income.IncomeSourceStore.budgetedMonthly(it) } }
    // Keyed on transactions: fee detections land alongside ledger writes, so
    // a refresh that adds spending also refreshes the fee bleed line.
    val monthFees = remember(transactions) { com.pesaflow.app.data.parsers.readMonthFees(appCtx) }
    val hustleSrcs = remember(incomeSources) { incomeSources.filter { it.kind == "HUSTLE" } }
    val hustleExp = remember(hustleSrcs) { hustleSrcs.sumOf { com.pesaflow.app.data.income.IncomeSourceStore.budgetedMonthly(it) } }
    val hustleGot = remember(transactions, hustleSrcs) {
        val labels = hustleSrcs.map { it.label }.filter { it.isNotBlank() }
        if (labels.isEmpty()) 0.0 else {
            val monthStartH = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            transactions.filter {
                it.isEarnedIncome() && !it.isSample &&
                    it.dateTimestamp >= monthStartH && labels.any { l -> it.merchant.contains(l, ignoreCase = true) }
            }.sumOf { it.amount }
        }
    }
    val held = remember(transactions) { com.pesaflow.app.data.money.ledgerBalance(transactions) }
    val watched = remember(transactions) {
        com.pesaflow.app.data.ledger.Watchlist.read(
            appCtx.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
        )
    }
    val insights = remember(transactions, budgets, lang, name, bills, debts, goals, weekPlan, persona, expectedIncome, monthFees, hustleExp, hustleGot, held, watched) {
        buildInsights(transactions, budgets, lang, name, bills, debts, goals, weekPlan, persona, expectedIncome, monthFees, hustleExp, hustleGot, held, watched)
    }
    // Dismissal learning: muted tip families stay muted (prefs-backed).
    // "Show muted" brings them back — nothing is ever lost.
    val insightPrefs = LocalContext.current.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
    var muted by remember {
        mutableStateOf(insightPrefs.getStringSet("muted_insights", emptySet()) ?: emptySet())
    }
    val shown = remember(insights, muted) { insights.filter { insightTag(it) !in muted } }
    if (shown.isEmpty() && muted.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ppSpacing.sm)
    ) {
        com.pesaflow.app.ui.theme.PpSectionHeader(title = "Smart Insights 💡")
        shown.forEach { insight ->
            com.pesaflow.app.ui.theme.PpInsightCard(
                text = insight,
                onDismiss = {
                    val next = muted + insightTag(insight)
                    muted = next
                    insightPrefs.edit().putStringSet("muted_insights", next).apply()
                }
            )
        }
        if (muted.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    muted = emptySet()
                    insightPrefs.edit().remove("muted_insights").apply()
                }) { Text("Show muted (${muted.size})", style = com.pesaflow.app.ui.theme.ppTypography.bodySmall) }
            }
        }
    }
}


private fun insightTag(s: String): String {
    val l = s.lowercase()
    return when {
        l.contains("punguza") -> "punguza"
        l.contains("tight mode") -> "tight"
        l.contains("most spending") || l.contains("mullah mingi") -> "topcat"
        l.contains("streak") -> "streak"
        l.contains("runway") || l.contains("days at this burn") -> "runway"
        l.contains("bill") -> "bills"
        l.contains("debt") || l.contains("madeni") || l.contains("daiwa") -> "debts"
        l.contains("saving") || l.contains("goal") || l.contains("akiba") -> "savings"
        l.contains("busy") || l.contains("free") -> "schedule"
        l.contains("envelope") || l.contains("breach") || l.contains("blown") -> "envelopes"
        else -> "other"
    }
}
