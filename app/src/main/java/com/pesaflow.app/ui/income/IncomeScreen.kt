package com.pesaflow.app.ui.income

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R
import com.pesaflow.app.data.income.IncomeSourceStore
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.isEarnedIncome
import com.pesaflow.app.ui.dashboard.QuickAddDialog
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.ExplainChip
import com.pesaflow.app.ui.theme.PesaEmptyState
import com.pesaflow.app.ui.theme.TintIncome
import com.pesaflow.app.viewmodels.FinanceViewModel


// More → Income: the one page for "where does my money come from".
// Declared sources set expectations; the ledger proves what landed.
// Anything set here auto-updates the budget calculator base, Buddy's income
// answers and the onboarding review — single store, whole system.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeScreen(viewModel: FinanceViewModel) {
    val transactions by viewModel.allTransactions.collectAsState()
    val sources by viewModel.incomeSources.collectAsState()
    var showQuickAdd by remember { mutableStateOf(false) }

    val monthStart = remember {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.DAY_OF_MONTH, 1)
        c.set(java.util.Calendar.HOUR_OF_DAY, 0)
        c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        c.timeInMillis
    }
    val todayDay = remember { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH) }
    val real = remember(transactions) { transactions.filter { !it.isSample } }
    val mtdIn = remember(real, monthStart) {
        real.filter { it.isEarnedIncome() && it.dateTimestamp >= monthStart }.sumOf { it.amount }
    }
    val expected = remember(sources) { sources.sumOf { IncomeSourceStore.budgetedMonthly(it) } }
    val pace = if (expected > 0) (mtdIn / expected).toFloat().coerceIn(0f, 1f) else 0f
    val recentIn = remember(real) {
        real.filter { it.isEarnedIncome() }.sortedByDescending { it.dateTimestamp }.take(5)
    }

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintIncome, bgRes = R.drawable.bg_income)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Income", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge.copy(shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 8f))) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ExplainChip(
                    label = "Expected vs received?",
                    body = "Expected is what you declared below. Received is what actually landed in the ledger this month — M-Pesa arrivals still confirm via pending first."
                )
                // Expectations vs reality, one glance.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("This month", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Expected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("KSh ${expected.toInt()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Received", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "KSh ${mtdIn.toInt()}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (expected > 0 && mtdIn >= expected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        // Dynamic horizon: nearest dated inflow counts the days.
                        val nextSrc = remember(sources) {
                            sources.filter { it.frequency == "MONTHLY" && it.dayOfMonth in 1..31 }
                                .minByOrNull { it.daysUntilLanding() ?: Int.MAX_VALUE }
                        }
                        nextSrc?.daysUntilLanding()?.let { days ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Next: ${nextSrc.label.ifBlank { nextSrc.displayKind() }} in $days day${if (days == 1) "" else "s"} 📥",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { pace },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            when {
                                expected <= 0 -> "Declare sources below and this bar starts tracking."
                                mtdIn >= expected -> "Fully landed 🎉 — surplus is safe to save or spend."
                                else -> "KSh ${(expected - mtdIn).toInt()} still expected. Payday coming?"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Still waiting: payday passed, nothing logged. Catches
                        // late HELB tranches and missing salary rows early.
                        val waiting = remember(real, sources, monthStart, todayDay) {
                            sources.filter { s ->
                                s.label.isNotBlank() && s.dayOfMonth in 1..todayDay &&
                                    real.none {
                                        it.isEarnedIncome() && it.dateTimestamp >= monthStart &&
                                            it.merchant.contains(s.label, ignoreCase = true)
                                    }
                            }
                        }
                        if (waiting.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Still waiting: " + waiting.joinToString(", ") { it.label } + " — payday passed, nothing landed. Check M-Pesa or log it.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                // Per-source expectations.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Sources", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (sources.isEmpty()) {
                            Text(
                                "No sources yet — HELB, parents, hustle, whatever lands. Add one to set expectations.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            sources.forEach { s ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            (s.label.ifBlank { s.displayKind() }),
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val gotMtd = if (s.label.isBlank()) -1.0 else real.filter {
                                            it.isEarnedIncome() && it.dateTimestamp >= monthStart &&
                                                it.merchant.contains(s.label, ignoreCase = true)
                                        }.sumOf { it.amount }
                                        if (gotMtd >= 0) {
                                            Text(
                                                "Received KSh ${gotMtd.toInt()} this month",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (gotMtd > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            s.displayKind() + (if (s.dayOfMonth in 1..31) " · day ${s.dayOfMonth}" else ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (s.dayOfMonth in 1..31) {
                                            val dueIn = (s.dayOfMonth - todayDay + 30) % 30
                                            Text(
                                                if (dueIn == 0) "Expected today 👀" else "Expected in $dueIn day(s)",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Text(
                                        "KSh ${IncomeSourceStore.budgetedMonthly(s).toInt()}/mo",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = { showQuickAdd = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Log income received", color = MaterialTheme.colorScheme.onPrimary) }
                // What actually landed.
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Recently landed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (recentIn.isEmpty()) {
                            Text(
                                "Nothing received yet — M-Pesa arrivals appear here after you confirm them as pending.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            recentIn.forEach { tx ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(tx.merchant.ifBlank { tx.category }, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                        Text(tx.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        "+ KSh ${tx.amount.toInt()}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00C853)
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    "HELB, parents, hustle, job — declare each above. HELB on M-Pesa? It parses from SMS automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IncomeSetupBlock(
                    viewModel = viewModel,
                    highlightSelfSponsored = false
                )
                if (sources.isEmpty() && recentIn.isEmpty()) {
                    PesaEmptyState(
                        title = "No income picture yet",
                        explanation = "Add a source above or log your first income — expectations and reality meet here."
                    )
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (showQuickAdd) {
        QuickAddDialog(viewModel = viewModel, defaultType = TransactionType.INCOME, onDismiss = { showQuickAdd = false })
    }
}
