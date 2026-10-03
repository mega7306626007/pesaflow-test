package com.pesaflow.app.ui.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.ledger.CategoryMemory
import com.pesaflow.app.data.ledger.MerchantMemory
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.time.addDays
import com.pesaflow.app.data.time.rollingDays
import com.pesaflow.app.data.time.startOfDay
import com.pesaflow.app.viewmodels.exactDuplicateGroups
import com.pesaflow.app.R
import com.pesaflow.app.ui.dashboard.QuickAddDialog
import com.pesaflow.app.ui.theme.CategoryIcon
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.PesaEmptyState
import com.pesaflow.app.ui.theme.PesaSectionHeader
import com.pesaflow.app.ui.theme.PesaSpacing
import com.pesaflow.app.ui.theme.TintTransactionsLedger
import com.pesaflow.app.ui.theme.toKSh
import com.pesaflow.app.viewmodels.FinanceViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun groupLabel(dayStart: Long, now: Long): String {
    val fmt = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
    return when (dayStart) {
        startOfDay(now) -> "TODAY"
        addDays(startOfDay(now), -1) -> "YESTERDAY"
        else -> fmt.format(Date(dayStart)).uppercase(Locale.getDefault())
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    viewModel: FinanceViewModel,
    onQuickAdd: (TransactionType) -> Unit = {},
    onOpenSearch: () -> Unit = {}
) {
    val transactions by viewModel.allTransactions.collectAsState()
    var editingTx by remember { mutableStateOf<Transaction?>(null) }
    var confirmDelete by remember { mutableStateOf<Transaction?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val shareContext = LocalContext.current
    var typeFilter by remember { mutableStateOf<String?>(null) }
    var merchantQuery by remember { mutableStateOf("") }
    var oldestFirst by remember { mutableStateOf(false) }
    // Bulk mode: multi-select rows for share/delete. Range: quick time windows.
    var selecting by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var rangeDays by remember { mutableStateOf<Int?>(null) }
    // SMS scan: today up front, longer ranges behind one disclosure.
    var confirmBulk by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var scanFound by remember { mutableStateOf(0) }
    var scanMsg by remember { mutableStateOf<String?>(null) }
    var showMoreScan by remember { mutableStateOf(false) }
    fun runScan(days: Int, label: String) {
        scanning = true
        scanFound = 0
        scanMsg = null
        viewModel.scanInboxDays(
            daysBack = days,
            maxRows = if (days <= 1) 150 else 500,
            onProgress = { f, _ -> scanFound = f },
            onDone = { found, queued, error ->
                scanning = false
                scanMsg = if (error != null) "Scan failed: $error"
                else "Scanned $found texts ($label): $queued new for review, rest already logged. ✅"
            }
        )
    }
    val now = System.currentTimeMillis()
    val sorted = remember(transactions, typeFilter, merchantQuery, rangeDays) {
        // Range chips cover whole calendar days: the old now - n*24h cutoff
        // sliced the earliest day at the current time-of-day.
        val cutoff = when (rangeDays) {
            0 -> startOfDay(System.currentTimeMillis())
            null -> 0L
            else -> rollingDays(System.currentTimeMillis(), rangeDays!!).startInclusive
        }
        transactions
            .filter { typeFilter == null || it.type.name == typeFilter }
            .filter { merchantQuery.isBlank() || it.merchant.contains(merchantQuery, ignoreCase = true) || it.category.contains(merchantQuery, ignoreCase = true) }
            .filter { it.dateTimestamp >= cutoff }
            .sortedByDescending { it.dateTimestamp }
    }
    val orderedGroups = remember(sorted, oldestFirst) {
        val g = sorted.groupBy { startOfDay(it.dateTimestamp) }.toSortedMap(compareByDescending { it })
        if (oldestFirst) g.toSortedMap(compareBy { it }) else g
    }
    // Pagination: first 100 rows render instantly on big histories (2k SMS
    // imports grouped/sorted on every keystroke); the rest load on tap.
    var visibleLimit by remember(sorted) { mutableStateOf(100) }
    val groups = remember(orderedGroups, visibleLimit, oldestFirst) {
        var shown = 0
        val out = LinkedHashMap<Long, List<Transaction>>()
        for ((day, txs) in orderedGroups) {
            if (shown >= visibleLimit) break
            val take = txs.take((visibleLimit - shown).coerceAtLeast(0))
            if (take.isNotEmpty()) out[day] = take
            shown += take.size
        }
        if (oldestFirst) out.toSortedMap(compareBy { it }) else out
    }
    val visibleCount = groups.values.sumOf { it.size }
    // One share engine: footer shares the view, bulk bar shares the selection.
    fun shareTxs(list: List<Transaction>) {
        if (list.isEmpty()) return
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val rows = list.map { tx ->
            "${fmt.format(java.util.Date(tx.dateTimestamp))},\"${tx.merchant.replace("\"", "")}\",${tx.category},${tx.type},${tx.amount},${tx.paymentMethod}"
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "PesaPlanner transactions")
            putExtra(android.content.Intent.EXTRA_TEXT, (listOf("date,merchant,category,type,amount,method") + rows).joinToString("\n"))
        }
        shareContext.startActivity(android.content.Intent.createChooser(intent, "Share transactions"))
    }
    // Chronological running balance (oldest → newest, samples excluded)
    // regardless of the view's sort direction.
    val runningById = remember(transactions) {
        var run = 0.0
        val map = LinkedHashMap<String, Double>()
        transactions.filter { !it.isSample }.sortedBy { it.dateTimestamp }.forEach { tx ->
            run += when (tx.type) {
                TransactionType.INCOME -> tx.amount
                TransactionType.EXPENSE -> -tx.amount
                TransactionType.SAVING -> -tx.amount
                TransactionType.INVESTMENT -> -tx.amount
                TransactionType.TRANSFER -> 0.0
            }
            map[tx.id] = run
        }
        map
    }

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintTransactionsLedger, bgRes = R.drawable.bg_transactions_ledger)
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text("Transactions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge.copy(shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 8f))) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { inner ->
        if (transactions.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(inner).padding(PesaSpacing.md)) {
                PesaEmptyState(
                    title = "No transactions yet",
                    explanation = "Your spending will appear here as you add transactions or import M-Pesa messages.",
                    actionLabel = "Add your first expense",
                    onAction = { onQuickAdd(TransactionType.EXPENSE) }
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(inner).padding(horizontal = PesaSpacing.md)) {
                OutlinedTextField(
                    value = merchantQuery,
                    onValueChange = { merchantQuery = it },
                    label = { Text("Search merchant or category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onOpenSearch) { Text("Search everything 🔍 → budgets, bills, screens") }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = typeFilter == null,
                        onClick = { typeFilter = null },
                        label = { Text("All") }
                    )
                    listOf("INCOME" to "In", "EXPENSE" to "Out", "SAVING" to "Saved", "INVESTMENT" to "Grown", "TRANSFER" to "Moved").forEach { (v, label) ->
                        FilterChip(
                            selected = typeFilter == v,
                            onClick = { typeFilter = if (typeFilter == v) null else v },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = !oldestFirst, onClick = { oldestFirst = false }, label = { Text("Newest first") })
                    FilterChip(selected = oldestFirst, onClick = { oldestFirst = true }, label = { Text("Oldest first") })
                    FilterChip(
                        selected = selecting,
                        onClick = {
                            selecting = !selecting
                            if (!selecting) selection = emptySet()
                        },
                        label = { Text(if (selecting) "Done" else "Select") }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = rangeDays == null, onClick = { rangeDays = null }, label = { Text("All time") })
                    FilterChip(selected = rangeDays == 0, onClick = { rangeDays = if (rangeDays == 0) null else 0 }, label = { Text("Today") })
                    FilterChip(selected = rangeDays == 7, onClick = { rangeDays = if (rangeDays == 7) null else 7 }, label = { Text("7 days") })
                    FilterChip(selected = rangeDays == 30, onClick = { rangeDays = if (rangeDays == 30) null else 30 }, label = { Text("30 days") })
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Scan row: today is one tap; week/month/history live behind
                // a disclosure with plain info about what each one does.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { runScan(1, "today") }, enabled = !scanning) {
                        Text(if (scanning) "Scanning… $scanFound found" else "Scan today 📥")
                    }
                    TextButton(onClick = { showMoreScan = !showMoreScan }) {
                        Text(if (showMoreScan) "Less ▴" else "More scan options ▾")
                    }
                }
                if (showMoreScan) {
                    Text(
                        "Today checks this morning's texts. Longer scans catch older money — duplicates are skipped automatically by M-Pesa code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { runScan(7, "7 days") }, enabled = !scanning) { Text("Scan 7 days") }
                        TextButton(onClick = { runScan(30, "30 days") }, enabled = !scanning) { Text("Scan 30 days") }
                        TextButton(onClick = { runScan(150, "5 months") }, enabled = !scanning) { Text("Scan 5 months") }
                    }
                }
                scanMsg?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            LazyColumn(
                Modifier.fillMaxSize().weight(1f),
                verticalArrangement = Arrangement.spacedBy(PesaSpacing.xs)
            ) {
                if (sorted.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            PesaEmptyState(
                                title = "No matching transactions",
                                explanation = "Change your search or filters, or clear them to see your full history.",
                                actionLabel = "Clear search and filters",
                                onAction = {
                                    merchantQuery = ""
                                    typeFilter = null
                                    rangeDays = null
                                    oldestFirst = false
                                    selecting = false
                                    selection = emptySet()
                                }
                            )
                        }
                    }
                } else {
                    item { Spacer(Modifier.height(PesaSpacing.xs)) }
                    groups.forEach { (day, txs) ->
                        item(key = "h-$day") {
                            val dayNet = txs.filter { !it.isSample }.sumOf {
                                when (it.type) {
                                    TransactionType.INCOME -> it.amount
                                    TransactionType.EXPENSE -> -it.amount
                                    TransactionType.SAVING -> -it.amount
                                    TransactionType.INVESTMENT -> -it.amount
                                    TransactionType.TRANSFER -> 0.0
                                }
                            }
                            val moved = txs.count { it.type == TransactionType.TRANSFER }
                            PesaSectionHeader(
                                title = groupLabel(day, now),
                                subtitle = "${txs.size} item(s) · " + (if (dayNet >= 0) "+" else "−") + " KSh " + kotlin.math.abs(dayNet).toInt() + (if (moved > 0) " · $moved moved" else "")
                            )
                        }
                        items(txs, key = { it.id }) { tx ->
                            Box(Modifier) {
                                TransactionRow(
                                    tx = tx,
                                    onEdit = { editingTx = tx },
                                    onDelete = { confirmDelete = tx },
                                    runningBalance = runningById[tx.id],
                                    selected = tx.id in selection,
                                    onToggleSelect = if (selecting) ({
                                        selection = if (tx.id in selection) selection - tx.id else selection + tx.id
                                    }) else null
                                )
                            }
                        }
                        item { Spacer(Modifier.height(PesaSpacing.sm)) }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
            val viewIn = sorted.filter { it.type == TransactionType.INCOME && !it.isSample && !it.isOpening }.sumOf { it.amount }
            val viewOut = sorted.filter { it.type == TransactionType.EXPENSE && !it.isSample }.sumOf { it.amount }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Showing $visibleCount of ${sorted.size} · In KSh ${viewIn.toInt()} · Out KSh ${viewOut.toInt()} · Net " + (if (viewIn - viewOut >= 0) "+" else "−") + "KSh ${kotlin.math.abs(viewIn - viewOut).toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                    TextButton(onClick = { shareTxs(sorted) }) { Text("Share") }
                }
                if (visibleCount < sorted.size) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { visibleLimit += 100 }) { Text("Show 100 more") }
                        TextButton(onClick = { visibleLimit = sorted.size }) { Text("Show all ${sorted.size}") }
                    }
                }
                // Bulk bar: share or delete the selection. Deletes confirm as a
                // batch (no per-row undo across N rows) — honest destructive UX.
                if (selecting && selection.isNotEmpty()) {
                    val picked = remember(selection, transactions) { transactions.filter { it.id in selection } }
                    val pickedTotal = picked.sumOf { it.amount }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${selection.size} picked · KSh ${pickedTotal.toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { shareTxs(picked) }) { Text("Share") }
                        TextButton(onClick = { confirmBulk = true }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                // Duplicate hunt: same amount + merchant more than once.
                val dupClusters = remember(sorted) {
                    sorted.filter { !it.isSample }.groupBy {
                        "${it.amount}|${it.merchant.trim().lowercase()}"
                    }.filter { it.value.size > 1 }.values.toList()
                }
                var mergeGroup by remember { mutableStateOf<List<Transaction>?>(null) }
                // Auto-remove: exact matches only (same amount + merchant +
                // day + method, minutes apart). Keeps the earliest, undoable.
                val autoGroups = remember(sorted) { exactDuplicateGroups(sorted) }
                val autoCount = autoGroups.sumOf { it.size - 1 }
                var confirmAuto by remember { mutableStateOf(false) }
                var autoResult by remember { mutableStateOf<String?>(null) }
                if (dupClusters.isNotEmpty()) {
                    com.pesaflow.app.ui.theme.PpCard(kind = com.pesaflow.app.ui.theme.PpCardKind.LARGE) {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(com.pesaflow.app.ui.theme.ppSpacing.xs)
                        ) {
                            com.pesaflow.app.ui.theme.PpSectionHeader(
                                title = "Possible duplicates (${dupClusters.size})",
                                subtitle = "Same amount + merchant. Merge keeps the newest, deletes the rest."
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (autoCount > 0) {
                                TextButton(onClick = { confirmAuto = true }) {
                                    Text(
                                        "Auto-remove $autoCount exact",
                                        style = com.pesaflow.app.ui.theme.ppTypography.labelLarge,
                                        color = com.pesaflow.app.ui.theme.ppColors.gold
                                    )
                                }
                            }
                            autoResult?.let { msg ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        msg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.undoAutoDedupe { n -> autoResult = "Restored $n row(s)" }
                                    }) { Text("Undo") }
                                }
                            }
                            dupClusters.take(5).forEach { g ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${g.first().merchant} · KSh ${g.first().amount.toInt()} (${g.size}×)",
                                        style = com.pesaflow.app.ui.theme.ppTypography.bodyMedium,
                                        color = com.pesaflow.app.ui.theme.ppColors.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { mergeGroup = g }) {
                                        Text(
                                            "Review",
                                            style = com.pesaflow.app.ui.theme.ppTypography.labelMedium,
                                            color = com.pesaflow.app.ui.theme.ppColors.gold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (confirmAuto) {
                    AlertDialog(
                        onDismissRequest = { confirmAuto = false },
                        title = { Text("Remove $autoCount duplicates?") },
                        text = { Text("Same amount, merchant, day and method within minutes. Keeps the earliest of each group — undo brings them back.") },
                        confirmButton = {
                            TextButton(onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.autoRemoveExactDuplicates(autoGroups) { n ->
                                    confirmAuto = false
                                    autoResult = "Removed $n exact duplicate(s)"
                                }
                            }) { Text("Remove") }
                        },
                        dismissButton = { TextButton(onClick = { confirmAuto = false }) { Text("Keep") } }
                    )
                }
                // 14-day spending bars: text bars, zero new imports.
                val dayMs = 24L * 60 * 60 * 1000
                val last14 = remember(sorted) {
                    val now = System.currentTimeMillis()
                    // Strict [day, next-day) buckets: the old ..(d0 + dayMs)
                    // range double-counted any row stamped exactly at midnight.
                    rollingDays(now, 14).days().map { d0 ->
                        sorted.filter {
                            it.type == TransactionType.EXPENSE && !it.isSample &&
                                it.dateTimestamp >= d0 && it.dateTimestamp < d0 + dayMs
                        }.sumOf { it.amount }
                    }
                }
                val peak14 = (last14.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
                com.pesaflow.app.ui.theme.PpCard(kind = com.pesaflow.app.ui.theme.PpCardKind.LARGE) {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(com.pesaflow.app.ui.theme.ppSpacing.xs)
                    ) {
                        com.pesaflow.app.ui.theme.PpSectionHeader(
                            title = "Last 14 days",
                            subtitle = "Daily spend, oldest to newest"
                        )
                        last14.forEachIndexed { i, v ->
                            val bars = "█".repeat(((v / peak14) * 12).toInt().coerceIn(0, 12)).ifEmpty { "·" }
                            Text(
                                "D-${13 - i} $bars KSh ${v.toInt()}",
                                style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                                color = com.pesaflow.app.ui.theme.ppColors.textTertiary,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
                // Top categories in this view.
                val topCats = remember(sorted) {
                    sorted.filter { it.type == TransactionType.EXPENSE && !it.isSample }
                        .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
                        .entries.sortedByDescending { it.value }.take(5)
                }
                if (topCats.isNotEmpty()) {
                    com.pesaflow.app.ui.theme.PpCard(kind = com.pesaflow.app.ui.theme.PpCardKind.LARGE) {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(com.pesaflow.app.ui.theme.ppSpacing.sm)
                        ) {
                            com.pesaflow.app.ui.theme.PpSectionHeader(title = "Top in view")
                            val topMax = topCats.first().value.coerceAtLeast(1.0)
                            topCats.forEach { e ->
                                Column(verticalArrangement = Arrangement.spacedBy(com.pesaflow.app.ui.theme.ppSpacing.xs)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            e.key,
                                            style = com.pesaflow.app.ui.theme.ppTypography.labelLarge,
                                            color = com.pesaflow.app.ui.theme.ppColors.textPrimary
                                        )
                                        Text(
                                            "KSh ${e.value.toInt()}",
                                            style = com.pesaflow.app.ui.theme.ppTypography.financialSmall,
                                            color = com.pesaflow.app.ui.theme.ppColors.textPrimary
                                        )
                                    }
                                    com.pesaflow.app.ui.theme.PpProgress(fraction = (e.value / topMax).toFloat())
                                }
                            }
                        }
                    }
                }
                mergeGroup?.let { g ->
                    AlertDialog(
                        onDismissRequest = { mergeGroup = null },
                        title = { Text("Merge ${g.size} rows?") },
                        text = { Text("Keeps the newest ${g.first().merchant} KSh ${g.first().amount.toInt()}, deletes the other ${g.size - 1}. Batch deletes can't be undone.") },
                        confirmButton = {
                            TextButton(onClick = {
                                val keep = g.maxByOrNull { it.dateTimestamp }?.id
                                // Batched: one statement, one emission — N single
                                // deletes recompose per row and ANR big merges.
                                viewModel.deleteTransactions(g.filter { it.id != keep }.map { it.id }) {
                                    mergeGroup = null
                                }
                            }) { Text("Merge", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = { TextButton(onClick = { mergeGroup = null }) { Text("Keep all") } }
                    )
                }
            }
        }
    }
    }

    editingTx?.let {
        QuickAddDialog(viewModel = viewModel, defaultType = it.type, onDismiss = { editingTx = null }, existing = it)
    }

    if (confirmBulk) {
        val picked = transactions.filter { it.id in selection }
        AlertDialog(
            onDismissRequest = { confirmBulk = false },
            title = { Text("Delete ${picked.size} transactions?") },
            text = { Text("KSh ${picked.sumOf { it.amount }.toInt()} goes away. Batch deletes can't be undone — singles can.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransactions(picked.map { it.id }) {
                        selection = emptySet()
                        selecting = false
                        confirmBulk = false
                    }
                }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmBulk = false }) { Text("Keep") } }
        )
    }

    confirmDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this transaction?") },
            text = { Text("${tx.merchant} · KSh ${tx.amount.toInt()} — you can undo right after.") },
            confirmButton = {
                TextButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.deleteTransactionWithUndo(tx)
                    confirmDelete = null
                    scope.launch {
                        val r = snackbar.showSnackbar("Deleted ${tx.merchant}.", "Undo", duration = SnackbarDuration.Long)
                        if (r == SnackbarResult.ActionPerformed) viewModel.undoLast()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } }
        )
    }
}

@Composable
fun TransactionRow(
    tx: Transaction,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    showActions: Boolean = true,
    runningBalance: Double? = null,
    selected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null
) {
    val isIncome = tx.type == TransactionType.INCOME
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE) }
    var aliasTick by remember { mutableStateOf(0) }
    val alias = remember(tx.merchant, aliasTick) { MerchantMemory.lookup(prefs, tx.merchant) }
    var showAlias by remember { mutableStateOf(false) }
    var aliasInput by remember(tx.merchant, showAlias) { mutableStateOf(alias?.label.orEmpty()) }
    Card(
        onClick = { onToggleSelect?.invoke() },
        enabled = onToggleSelect != null,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp),
        shape = com.pesaflow.app.ui.theme.ppShapes.cardCompact,
        colors = CardDefaults.cardColors(containerColor = com.pesaflow.app.ui.theme.ppColors.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) com.pesaflow.app.ui.theme.ppColors.borderGold else com.pesaflow.app.ui.theme.ppColors.border
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(
                horizontal = com.pesaflow.app.ui.theme.ppSpacing.lg,
                vertical = com.pesaflow.app.ui.theme.ppSpacing.md
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(com.pesaflow.app.ui.theme.ppSpacing.md)
        ) {
            CategoryIcon(category = tx.category)
            Column(Modifier.weight(1f)) {
                Text(
                    alias?.label ?: tx.merchant.ifBlank { tx.category },
                    style = com.pesaflow.app.ui.theme.ppTypography.labelLarge,
                    color = com.pesaflow.app.ui.theme.ppColors.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    (if (alias != null && tx.merchant.isNotBlank()) tx.merchant + " · " else "") + "${tx.category} · ${tx.paymentMethod.name.lowercase().replace('_', ' ').replaceFirstChar { c -> c.uppercase() }}",
                    style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                    color = com.pesaflow.app.ui.theme.ppColors.textTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (when (tx.type) {
                        TransactionType.INCOME -> "+ "
                        TransactionType.TRANSFER -> "↔ "
                        else -> "− "
                    }) + tx.amount.toKSh().removePrefix("KSh "),
                    style = com.pesaflow.app.ui.theme.ppTypography.financialSmall,
                    color = when (tx.type) {
                        TransactionType.INCOME -> com.pesaflow.app.ui.theme.ppColors.income
                        TransactionType.SAVING -> com.pesaflow.app.ui.theme.ppColors.gold
                        TransactionType.INVESTMENT -> com.pesaflow.app.ui.theme.ppColors.brightBlue
                        TransactionType.TRANSFER -> com.pesaflow.app.ui.theme.ppColors.textTertiary
                        else -> com.pesaflow.app.ui.theme.ppColors.expense
                    },
                    maxLines = 1
                )
                if (runningBalance != null) {
                    Text(
                        "Bal " + (if (runningBalance < 0) "−" else "") + "KSh " + kotlin.math.abs(runningBalance).toInt(),
                        style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                        color = com.pesaflow.app.ui.theme.ppColors.textTertiary,
                        maxLines = 1
                    )
                }
            }
            if (showActions) {
                IconButton(onClick = { showAlias = true }) { Icon(Icons.Filled.Person, contentDescription = "Name this sender", tint = com.pesaflow.app.ui.theme.ppColors.textTertiary) }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit transaction", tint = com.pesaflow.app.ui.theme.ppColors.textTertiary) }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete transaction", tint = com.pesaflow.app.ui.theme.ppColors.error)
                }
            }
        }
    }
    if (showAlias) {
        AlertDialog(
            onDismissRequest = { showAlias = false },
            title = { Text("Who is ${tx.merchant.ifBlank { tx.category }}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Give them a name once — every future row from this sender shows it, and their category is remembered too.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        label = { Text("Name (e.g. Mom)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (alias != null) {
                        Text(
                            "Now showing as “${alias.label}”.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (MerchantMemory.learnAlias(prefs, tx.merchant, aliasInput)) {
                        CategoryMemory.learn(prefs, tx.merchant, tx.category)
                        aliasTick++
                    }
                    showAlias = false
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (alias != null) {
                        TextButton(onClick = {
                            MerchantMemory.clear(prefs, tx.merchant)
                            aliasTick++
                            showAlias = false
                        }) { Text("Forget") }
                    }
                    TextButton(onClick = { showAlias = false }) { Text("Cancel") }
                }
            }
        )
    }
}

private fun Double.toKShTrim(): String = this.toKSh()
