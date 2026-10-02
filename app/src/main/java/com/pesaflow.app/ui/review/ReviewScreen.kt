package com.pesaflow.app.ui.review

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.parsers.DEDUCTION_BAR
import com.pesaflow.app.data.parsers.Deduction
import com.pesaflow.app.data.parsers.LedgerRow
import com.pesaflow.app.data.parsers.deduceFare
import com.pesaflow.app.data.parsers.deduceRecurring
import com.pesaflow.app.ui.dashboard.QuickAddDialog
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.ExplainChip
import com.pesaflow.app.ui.theme.PesaEmptyState
import com.pesaflow.app.ui.theme.PesaSectionHeader
import com.pesaflow.app.ui.theme.PesaSpacing
import com.pesaflow.app.ui.theme.TintReports
import com.pesaflow.app.ui.transactions.TransactionRow
import com.pesaflow.app.viewmodels.FinanceViewModel
import java.util.Calendar
import kotlinx.coroutines.launch


private fun dayStartOf(ts: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}


/** Weekly accuracy ritual: uncategorized rows, possible duplicates, one by one.
 *  Fix here and every number downstream gets truer. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReviewScreen(viewModel: FinanceViewModel) {
    val transactions by viewModel.allTransactions.collectAsState()
    val pendings by viewModel.pendingTransactions.collectAsState()
    val reviewPrefs = LocalContext.current.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
    val reviewCtx = LocalContext.current
    var editingTx by remember { mutableStateOf<Transaction?>(null) }
    var confirmDelete by remember { mutableStateOf<Transaction?>(null) }
    var dismissedGroups by remember { mutableStateOf(setOf<String>()) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var queueSort by remember { mutableStateOf("Oldest") }
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val real = remember(transactions) { transactions.filter { !it.isSample } }
    val uncategorized = remember(real) { real.filter { it.category == "Other" } }
    val dupGroups = remember(real, dismissedGroups) {
        real.groupBy {
            "${it.type}|${it.amount}|${it.merchant.trim().lowercase()}|${dayStartOf(it.dateTimestamp)}"
        }.filter { it.value.size > 1 && !dismissedGroups.contains(it.key) }
            .values.toList()
    }
    val openCount = uncategorized.size + dupGroups.sumOf { it.size }
    // Stragglers: pendings older than 7 days rot the queue — surface them
    // with one-tap approve/clear so review week actually clears week.
    val nowMs = System.currentTimeMillis()
    val dayMs = 24L * 60 * 60 * 1000
    val stragglers = remember(pendings, nowMs) {
        pendings.filter { nowMs - it.dateTimestamp > 7 * dayMs }
    }
    // Rhythm evidence: fares/recurrences learned from approved history, keyed
    // by merchant. A pending that matches one shows its evidence ("8 of 8
    // class mornings") so approval is informed, not blind. Amount must sit
    // within 25% of the rhythm's — a 500-bob outlier is not the 50-bob fare.
    val rhythmEvidence = remember(real) {
        val rows = real.map { LedgerRow(it.amount, it.type, it.category, it.merchant, it.dateTimestamp) }
        val map = mutableMapOf<String, Deduction>()
        deduceFare(rows)?.takeIf { it.confidence >= DEDUCTION_BAR }?.let { map[it.merchant.lowercase()] = it }
        deduceRecurring(rows).filter { it.confidence >= DEDUCTION_BAR }.forEach { map[it.merchant.lowercase()] = it }
        map
    }

    fun remove(tx: Transaction) {
        viewModel.deleteTransactionWithUndo(tx)
        scope.launch {
            val r = snackbar.showSnackbar("Deleted ${tx.merchant}.", "Undo", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) viewModel.undoLast()
        }
    }

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintReports, bgRes = R.drawable.bg_reports)
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text("Weekly review ✅", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge.copy(shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 8f))) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { inner ->
            if (openCount == 0) {
                Column(Modifier.fillMaxSize().padding(inner).padding(PesaSpacing.md)) {
                    PesaEmptyState(
                        title = "All clean 🎉",
                        explanation = "Every transaction is categorized and no duplicates found. Come back next week."
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(inner).padding(horizontal = PesaSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(PesaSpacing.xs)
                ) {
                    item { Spacer(Modifier.height(PesaSpacing.xs)) }
                    item {
                        PesaSectionHeader(
                            title = "$openCount to check",
                            subtitle = "Tap a row to fix its category, or delete what isn't real"
                        )
                    }
                    item {
                        // Clean streak: consecutive clean reviews, one count per day.
                        // Streaks reward the ritual without punishing missed weeks.
                        val streakPrefs = LocalContext.current.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                        val todayKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                        var streak by remember { mutableStateOf(streakPrefs.getInt("review_streak", 0)) }
                        var lastClean by remember { mutableStateOf(streakPrefs.getString("review_last_clean", "")) }
                        LaunchedEffect(openCount, todayKey) {
                            if (openCount == 0 && lastClean != todayKey) {
                                val next = if (lastClean == null) 1 else streak + 1
                                streak = next
                                lastClean = todayKey
                                streakPrefs.edit().putInt("review_streak", next).putString("review_last_clean", todayKey).apply()
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (streak > 1) "🔥 $streak clean reviews" else "Review weekly to build a streak",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                val body = "PesaPlanner weekly review: $openCount open (${uncategorized.size} uncategorized, ${dupGroups.size} duplicate groups, ${stragglers.size} stale)."
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, body)
                                }
                                reviewCtx.startActivity(android.content.Intent.createChooser(intent, "Share review"))
                            }) { Text("Share") }
                        }
                    }
                    item {
                        // Ledger health grade: categorized share, duplicates, rot.
                        val total = real.size
                        val catPct = if (total > 0) (total - uncategorized.size) * 100 / total else 100
                        val grade = when {
                            openCount == 0 -> "A+"
                            catPct >= 95 && dupGroups.isEmpty() && stragglers.isEmpty() -> "A"
                            catPct >= 85 && dupGroups.isEmpty() -> "B"
                            catPct >= 70 -> "C"
                            else -> "D"
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = com.pesaflow.app.ui.theme.ppShapes.card,
                            colors = CardDefaults.cardColors(containerColor = com.pesaflow.app.ui.theme.ppColors.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, com.pesaflow.app.ui.theme.ppColors.border)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(PesaSpacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    grade,
                                    style = com.pesaflow.app.ui.theme.ppTypography.financialLarge,
                                    color = when (grade) {
                                        "A+", "A" -> com.pesaflow.app.ui.theme.ppColors.success
                                        "B" -> com.pesaflow.app.ui.theme.ppColors.gold
                                        "C" -> com.pesaflow.app.ui.theme.ppColors.warning
                                        else -> com.pesaflow.app.ui.theme.ppColors.error
                                    }
                                )
                                Spacer(modifier = Modifier.width(PesaSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Ledger health", style = com.pesaflow.app.ui.theme.ppTypography.labelLarge, color = com.pesaflow.app.ui.theme.ppColors.textPrimary)
                                    Text(
                                        "$catPct% categorized · ${dupGroups.size} duplicate groups · ${stragglers.size} stale pendings",
                                        style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                                        color = com.pesaflow.app.ui.theme.ppColors.textTertiary
                                    )
                                }
                                if (grade != "A+") {
                                    TextButton(onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        // Auto-fix the obvious: learned memory first, then the
                                        // trained scorer at high confidence, then keyword
                                        // certainty. Ambiguous rows stay for human eyes.
                                        uncategorized.forEach { tx ->
                                            val fix = com.pesaflow.app.data.ledger.CategoryMemory.lookup(
                                                reviewPrefs, tx.merchant
                                            ) ?: com.pesaflow.app.data.ml.MlCategoryAssist.suggest(reviewCtx, tx.merchant)?.label
                                            ?: com.pesaflow.app.data.parsers.MpesaParser.inferCategory(tx.merchant, tx.type).takeIf { it != "Other" }
                                            if (fix != null) viewModel.replaceTransaction(tx.id, tx.copy(category = fix))
                                        }
                                    }) { Text("Auto-fix", style = com.pesaflow.app.ui.theme.ppTypography.labelMedium, color = com.pesaflow.app.ui.theme.ppColors.gold) }
                                }
                            }
                        }
                    }
                    item {
                        ExplainChip(
                            label = "Why review?",
                            body = "Uncategorized rows blur your charts and duplicates inflate spending. Five minutes here keeps every number honest."
                        )
                    }
                    if (pendings.isNotEmpty()) {
                        // Whole SMS queue lives here now (oldest first) — the old
                        // stragglers-only view hid fresh pendings with no
                        // approve-all in sight.
                        // Plain val, not remember: this runs in LazyColumn content
                        // scope (not a @Composable context). Sorting a queue
                        // is cheap; correctness over memoization here.
                        val queue = if (queueSort == "Surest") pendings.sortedByDescending {
                                com.pesaflow.app.data.ledger.ConfidenceMemory.effective(reviewPrefs, it.merchant, it.confidenceScore)
                            } else pendings.sortedBy { it.dateTimestamp }
                        item {
                            PesaSectionHeader(
                                title = "Pending approvals",
                                subtitle = "${pendings.size} from SMS — approve or clear"
                            )
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                listOf("Oldest", "Surest").forEach { s ->
                                    FilterChip(selected = queueSort == s, onClick = { queueSort = s }, label = { Text(s) })
                                }
                                TextButton(onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Calibrated bar: trusted merchants qualify sooner,
                                    // always-rejected ones stop looking sure.
                                    pendings.filter {
                                        com.pesaflow.app.data.ledger.ConfidenceMemory.effective(reviewPrefs, it.merchant, it.confidenceScore) >= 0.85f
                                    }.forEach {
                                        viewModel.approvePending(it, it.category)
                                    }
                                }) { Text("Approve all sure ones") }
                                TextButton(onClick = { confirmClearAll = true }) {
                                    Text("Clear all", color = MaterialTheme.colorScheme.error)
                                }
                                val stale = com.pesaflow.app.data.parsers.PendingPolicy.stalePendings(pendings, System.currentTimeMillis())
                                if (stale.isNotEmpty()) {
                                    TextButton(onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        stale.forEach { viewModel.rejectPending(it) }
                                    }) { Text("Clear ${stale.size} stale (14d+)") }
                                }
                            }
                        }
                        if (confirmClearAll) {
                            item {
                                AlertDialog(
                                    onDismissRequest = { confirmClearAll = false },
                                    title = { Text("Clear ${pendings.size} pendings?") },
                                    text = { Text("They leave the queue unbooked. A rescan can bring them back.") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            pendings.forEach { viewModel.rejectPending(it) }
                                            confirmClearAll = false
                                        }) { Text("Clear all", color = MaterialTheme.colorScheme.error) }
                                    },
                                    dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text("Keep") } }
                                )
                            }
                        }
                        items(queue, key = { it.id }) { p ->
                            val ageDays = ((nowMs - p.dateTimestamp) / dayMs).toInt()
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(PesaSpacing.sm)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${p.merchant} · KSh ${p.amount.toInt()}", fontWeight = FontWeight.Bold)
                                        Text("$ageDays d old", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                    Text(p.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    // Model second opinion: trained type classifier,
                                    // never an override — a badge that asks for eyes.
                                    val modelSay = remember(p.id, p.rawText) {
                                        com.pesaflow.app.data.ml.MlTypeAssist.suggest(reviewCtx, "", p.rawText)
                                    }
                                    val modelType = when (modelSay?.label) {
                                        "EXPENSE" -> com.pesaflow.app.data.models.TransactionType.EXPENSE
                                        "INCOME" -> com.pesaflow.app.data.models.TransactionType.INCOME
                                        "TRANSFER" -> com.pesaflow.app.data.models.TransactionType.TRANSFER
                                        "SAVING" -> com.pesaflow.app.data.models.TransactionType.SAVING
                                        else -> null
                                    }
                                    if (modelSay != null && modelSay.confidence >= 0.8f && modelType != p.type) {
                                        Text(
                                            if (modelSay.label == "NULL") "🤖 Model flags not-money (${(modelSay.confidence * 100).toInt()}%) — check me"
                                            else "🤖 Model reads ${modelSay.label} (${(modelSay.confidence * 100).toInt()}%) — check me",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                    rhythmEvidence[p.merchant.lowercase()]?.takeIf {
                                        it.amount > 0 && kotlin.math.abs(p.amount - it.amount) / it.amount <= 0.25
                                    }?.let { d ->
                                        Text("🔁 ${d.evidence}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { viewModel.rejectPending(p) }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
                                        Button(onClick = { viewModel.approvePending(p, p.category) }) { Text("Approve") }
                                    }
                                }
                            }
                        }
                    }
                    if (uncategorized.isNotEmpty()) {
                        item {
                            PesaSectionHeader(
                                title = "Uncategorized",
                                subtitle = "${uncategorized.size} row(s) need a category"
                            )
                        }
                        items(uncategorized, key = { it.id }) { tx ->
                            val catSuggest = remember(tx.id, tx.merchant) {
                                com.pesaflow.app.data.ml.MlCategoryAssist.suggest(reviewCtx, tx.merchant)
                            }
                            Column {
                                if (catSuggest != null) {
                                    Text(
                                        "Model: ${catSuggest.label} (${(catSuggest.confidence * 100).toInt()}%)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.padding(start = PesaSpacing.sm)
                                    )
                                }
                                Box(Modifier) {
                                    TransactionRow(
                                        tx = tx,
                                        onEdit = { editingTx = tx },
                                        onDelete = { confirmDelete = tx }
                                    )
                                }
                            }
                        }
                    }
                    dupGroups.forEach { group ->
                        val key = "${group.first().type}|${group.first().amount}|${group.first().merchant.trim().lowercase()}|${dayStartOf(group.first().dateTimestamp)}"
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(PesaSpacing.sm)) {
                                    Text(
                                        "Possible duplicate · ${group.first().merchant} · KSh ${group.first().amount.toInt()} (${group.size}x)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    TextButton(onClick = { dismissedGroups = dismissedGroups + key }) {
                                        Text("Not duplicates — keep all")
                                    }
                                }
                            }
                        }
                        items(group, key = { it.id }) { tx ->
                            Box(Modifier) {
                                TransactionRow(
                                    tx = tx,
                                    onEdit = { editingTx = tx },
                                    onDelete = { confirmDelete = tx }
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    editingTx?.let {
        QuickAddDialog(viewModel = viewModel, defaultType = it.type, onDismiss = { editingTx = null }, existing = it)
    }

    confirmDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this transaction?") },
            text = { Text("${tx.merchant} · KSh ${tx.amount.toInt()} — you can undo right after.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    remove(tx)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } }
        )
    }
}
