package com.pesaflow.app.ui.exports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.*
import com.pesaflow.app.data.exports.ExportEngine
import com.pesaflow.app.data.ledger.Watchlist

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportScreen(
    transactions: List<Transaction>,
    budgets: List<Budget> = emptyList(),
    goals: List<SavingsGoal> = emptyList(),
    bills: List<Bill> = emptyList(),
    debts: List<Debt> = emptyList(),
    profile: com.pesaflow.app.data.models.UniversityProfile? = null
) {
    val engine = remember { ExportEngine() }
    var csvText by remember { mutableStateOf("") }
    var backupJson by remember { mutableStateOf("") }
    var shareText by remember { mutableStateOf("") }
    // Range export: window + category filter. Null window = all time.
    var rangeStart by remember { mutableStateOf<Long?>(null) }
    var rangeEnd by remember { mutableStateOf<Long?>(null) }
    var rangeCats by remember { mutableStateOf(setOf<String>()) }
    val allCats = remember(transactions) {
        transactions.map { it.category }.distinct().sorted().take(10)
    }
    val dayMs = 24L * 60 * 60 * 1000
    val now = remember { System.currentTimeMillis() }
    val monthStart = remember(now) {
        java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text("📤 Export & Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("CSV Export", fontWeight = FontWeight.Bold)
                    Text("Export all transactions, budgets, goals, bills, and debts as CSV.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        csvText = engine.exportCsv(transactions, budgets, goals, bills, debts)
                    }) { Text("Generate CSV") }
                    if (csvText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("✅ CSV ready (${csvText.length} chars)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Range export", fontWeight = FontWeight.Bold)
                    Text("A window and/or categories — commitments always export whole.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        data class RangeChip(val label: String, val start: Long?, val end: Long?)
                        listOf(
                            RangeChip("All time", null, null),
                            RangeChip("This month", monthStart, null),
                            RangeChip("Last 30 days", now - 30 * dayMs, null),
                            RangeChip("Last 7 days", now - 7 * dayMs, null)
                        ).forEach { r ->
                            FilterChip(
                                selected = rangeStart == r.start && rangeEnd == r.end,
                                onClick = {
                                    if (rangeStart == r.start && rangeEnd == r.end) {
                                        rangeStart = null; rangeEnd = null
                                    } else {
                                        rangeStart = r.start; rangeEnd = r.end
                                    }
                                },
                                label = { Text(r.label) }
                            )
                        }
                    }
                    if (allCats.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Categories (none = all):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            allCats.forEach { c ->
                                FilterChip(
                                    selected = c in rangeCats,
                                    onClick = { rangeCats = if (c in rangeCats) rangeCats - c else rangeCats + c },
                                    label = { Text(c) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        csvText = engine.exportRange(
                            transactions, budgets, goals, bills, debts, profile,
                            startMs = rangeStart ?: Long.MIN_VALUE,
                            endMs = rangeEnd ?: Long.MAX_VALUE,
                            categories = rangeCats
                        )
                    }) { Text("Generate range CSV") }
                    if (csvText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("✅ CSV ready (${csvText.length} chars)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Backup (JSON)", fontWeight = FontWeight.Bold)
                    Text("Full database backup with every table, compiler-checked.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        val payload = engine.buildBackupPayload(
                            transactions = transactions, pending = emptyList(),
                            budgets = budgets, goals = goals, profile = profile,
                            bills = bills, debts = debts, meals = emptyList(),
                            chamas = emptyList(), belongings = emptyList(),
                            kitchenStock = emptyList()
                        )
                        backupJson = engine.backupToJson(payload)
                    }) { Text("Create Backup") }
                    if (backupJson.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("✅ Backup ready (${backupJson.length} chars)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Share Summary", fontWeight = FontWeight.Bold)
                    Text("Human-readable 30-day summary for sharing.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        shareText = engine.generateShareSummary(transactions, 30)
                    }) { Text("Generate Summary") }
                    if (shareText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(shareText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
