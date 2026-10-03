package com.pesaflow.app.ui.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.R
import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.viewmodels.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.ui.theme.AtmosphereBand
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintBillsSteel
import java.util.Calendar


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(viewModel: FinanceViewModel) {
    val bills by viewModel.bills.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    // Proof ticks: brief ✓ that reverts so actions stay tappable.
    val ackScope = rememberCoroutineScope()
    var acked by remember { mutableStateOf(setOf<String>()) }
    fun ack(key: String) {
        acked = acked + key
        ackScope.launch { kotlinx.coroutines.delay(2000); acked = acked - key }
    }
    // Single repeats computation (was duplicated with a shadow warning):
    // detected rhythms minus names already tracked as open bills.
    val repeats = remember(transactions, bills) {
        detectRepeats(transactions).filter { hit ->
            bills.none { it.status != "PAID" && it.name.equals(hit.label, ignoreCase = true) }
        }.take(5)
    }

    val upcoming = bills.filter { it.status != "PAID" }
    val recurring = bills.filter { it.frequency != "ONE_TIME" && it.status != "PAID" }
    val settled = bills.filter { it.status == "PAID" }.sortedByDescending { it.dueDate }
    var billQuery by remember { mutableStateOf("") }
    // Search applies to every section below.
    fun matchesBill(b: Bill): Boolean =
        billQuery.isBlank() || b.name.contains(billQuery, ignoreCase = true) || b.category.contains(billQuery, ignoreCase = true)
    var confirmDelete by remember { mutableStateOf<Bill?>(null) }
    var editingBill by remember { mutableStateOf<Bill?>(null) }
    var splittingBill by remember { mutableStateOf<Bill?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun paidWithUndo(bill: Bill) {
        viewModel.markBillPaid(bill.id)
        scope.launch {
            val r = snackbar.showSnackbar("Paid ${bill.name}.", "Undo", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) viewModel.reopenBill(bill.id)
        }
    }

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintBillsSteel, bgRes = R.drawable.bg_bills_steel)
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Bills & Reminders", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge.copy(shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 8f))) },
                actions = {
                    TextButton(onClick = { showAddDialog = true }) {
                        Text("Add Bill", color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            AtmosphereBand(
                workspace = AtmoWorkspace.BILLS,
                title = "Never surprised",
                subtitle = "Due · repeats · paid"
            )
            // Detected repeats: same charge on a rhythm — one tap to track as a bill
            if (repeats.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Detected Repeats 🔁", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Same charge, regular rhythm — track it so it never surprises you.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        repeats.take(5).forEach { r ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(r.label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                    Text(
                                        "KSh ${r.amount.toInt()} · ~every ${r.intervalDays}d · ${r.times}× seen",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Normalized key (toInt kills 99.999-vs-100 dupes) + disabled
                                // while acked: double-taps can't mint twin bills.
                                val billKey = "${r.label}|${r.amount.toInt()}|${r.intervalDays}"
                                TextButton(
                                    onClick = {
                                        viewModel.addBill(
                                            r.label,
                                            r.amount,
                                            System.currentTimeMillis() + r.intervalDays * 24L * 60 * 60 * 1000,
                                            r.category,
                                            if (r.monthly) "MONTHLY" else "WEEKLY"
                                        )
                                        ack(billKey)
                                    },
                                    enabled = billKey !in acked
                                ) { Text(if (billKey in acked) "Tracked ✓" else "+ Bill", color = MaterialTheme.colorScheme.primary) }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
            // Spotted payments: confirmed ledger rows that look like open bills
            // (amount + window + name). Suggest-only — tapping links the row so
            // projections stop reserving it. Undo reopens, keeps your row.
            val spotted = remember(transactions, bills) {
                com.pesaflow.app.data.finance.matchBillPayments(
                    bills.filter { matchesBill(it) }, transactions
                ).take(3)
            }
            if (spotted.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Spotted payments 💡", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("These ledger rows look like open bills — link one and the reserve releases.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        spotted.forEach { m ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(m.bill.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                    Text(
                                        "KSh ${m.tx.amount.toInt()} · ${m.tx.merchant} — looks paid",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val spotKey = "${m.bill.id}|${m.tx.id}"
                                TextButton(
                                    onClick = {
                                        viewModel.linkBillPayment(m.bill.id, m.tx.id)
                                        ack(spotKey)
                                        scope.launch {
                                            val r = snackbar.showSnackbar("Linked — ${m.bill.name} settled.", "Undo", duration = SnackbarDuration.Short)
                                            if (r == SnackbarResult.ActionPerformed) viewModel.reopenBill(m.bill.id)
                                        }
                                    },
                                    enabled = spotKey !in acked
                                ) { Text(if (spotKey in acked) "Linked ✓" else "Mark paid", color = MaterialTheme.colorScheme.primary) }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }


            OutlinedTextField(
                value = billQuery,
                onValueChange = { billQuery = it },
                label = { Text("Search bills") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            // Section: Upcoming Bills
            // Detected repeats: the orphaned detector, now wired — repeating
            // ledger charges become one-tap bills (skips names already billed).
            val repeats = remember(transactions) {
                detectRepeats(transactions).filter { hit ->
                    bills.none { it.status != "PAID" && it.name.equals(hit.label, ignoreCase = true) }
                }.take(5)
            }
            if (repeats.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Looks recurring (${repeats.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Same charge, steady rhythm — tap to track as a bill.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        repeats.forEach { hit ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${hit.label} · KSh ${hit.amount.toInt()} · every ~${hit.intervalDays}d (${hit.times}×)",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    viewModel.addBill(
                                        hit.label,
                                        hit.amount,
                                        System.currentTimeMillis() + hit.intervalDays * 24L * 60 * 60 * 1000,
                                        hit.category.ifBlank { "Other" },
                                        if (hit.monthly) "MONTHLY" else "ONE_TIME"
                                    )
                                }) { Text("Add bill") }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Upcoming Bills (${upcoming.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    // Wallet cover check: can M-Pesa clear what's due?
                    val billsCtx = LocalContext.current
                    val upTotal = upcoming.sumOf { it.amount }
                    com.pesaflow.app.data.parsers.readMpesaBalance(billsCtx)?.let { (wamt, wat) ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (upcoming.isEmpty()) "Nothing due — wallet untouched. 🎉"
                            else if (wamt >= upTotal) "Wallet covers all KSh ${upTotal.toInt()} ✓ (" + com.pesaflow.app.data.parsers.balanceAgeText(wat, System.currentTimeMillis()) + ")"
                            else "Wallet holds KSh ${wamt.toInt()} of KSh ${upTotal.toInt()} due — gap KSh ${(upTotal - wamt).toInt()}.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (upcoming.isNotEmpty() && wamt < upTotal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (upcoming.isEmpty()) {
                        Text(
                            "No upcoming bills. Add rent, subscriptions or repayments.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        upcoming.filter { matchesBill(it) }.forEach { bill ->
                            BillCard(
                                bill = bill,
                                onPaid = { paidWithUndo(bill) },
                                onDelete = { confirmDelete = bill },
                                onEdit = { editingBill = bill },
                                onSplit = { splittingBill = bill }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }


            // Section: Recurring Bills
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Recurring Bills (${recurring.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (recurring.isEmpty()) {
                        Text(
                            "No recurring bills.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        recurring.filter { matchesBill(it) }.forEach { bill ->
                            BillCard(
                                bill = bill,
                                onPaid = { paidWithUndo(bill) },
                                onDelete = { confirmDelete = bill },
                                onEdit = { editingBill = bill },
                                onSplit = { splittingBill = bill }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }


            // Section: Settled history — paid stays visible and reopens.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Settled (${settled.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val nowMs = System.currentTimeMillis()
                    val monthStart = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.DAY_OF_MONTH, 1)
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    // Bills carry no paid-stamp; month heuristic uses due date.
                    val mtdPaid = settled.filter { it.dueDate >= monthStart - 31L * 24 * 60 * 60 * 1000 }.sumOf { it.amount }
                    Text(
                        if (settled.isEmpty()) "Nothing settled yet — paid bills land here instead of vanishing."
                        else "KSh ${mtdPaid.toInt()} cleared in the last ~30 days.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    settled.filter { matchesBill(it) }.take(10).forEach { bill ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bill.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text("KSh ${bill.amount.toInt()} · paid ✓", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { viewModel.reopenBill(bill.id) }) { Text("Reopen") }
                        }
                    }
                }
            }


            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Add New Bill", color = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    }

    confirmDelete?.let { bill ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this bill?") },
            text = { Text("${bill.name} · KSh ${bill.amount.toInt()} will stop reminding you.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBill(bill.id)
                    confirmDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } }
        )
    }

    editingBill?.let { bill ->
        var editName by remember(bill.id) { mutableStateOf(bill.name) }
        var editAmount by remember(bill.id) { mutableStateOf(if (bill.amount % 1.0 == 0.0) bill.amount.toInt().toString() else bill.amount.toString()) }
        var editCategory by remember(bill.id) { mutableStateOf(bill.category) }
        var editFrequency by remember(bill.id) { mutableStateOf(bill.frequency) }
        var editPaybill by remember(bill.id) { mutableStateOf(bill.paybill) }
        var editPaidBy by remember(bill.id) { mutableStateOf(bill.paidBy) }
        val editValid = editName.isNotBlank() && (editAmount.toDoubleOrNull() ?: 0.0) > 0
        AlertDialog(
            onDismissRequest = { editingBill = null },
            title = { Text("Edit bill", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Bill name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editAmount, onValueChange = { editAmount = it }, label = { Text("Amount (KSh)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editCategory, onValueChange = { editCategory = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = editPaybill, onValueChange = { editPaybill = it }, label = { Text("Paybill / till (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    BillPayerPicker(editPaidBy) { editPaidBy = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ONE_TIME", "WEEKLY", "MONTHLY").forEach { f ->
                            FilterChip(selected = editFrequency == f, onClick = { editFrequency = f }, label = { Text(f.take(5)) })
                        }
                    }
                    if (!editValid) {
                        Text("Name it and set an amount above zero.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = editValid,
                    onClick = {
                        val amt = editAmount.toDoubleOrNull() ?: return@Button
                        viewModel.updateBillDetails(bill, editName.trim(), amt, editCategory.trim().ifEmpty { "Other" }, editFrequency, editPaybill, editPaidBy)
                        editingBill = null
                    }
                ) { Text("Save changes") }
            },
            dismissButton = { TextButton(onClick = { editingBill = null }) { Text("Cancel") } }
        )
    }

    splittingBill?.let { bill ->
        var parts by remember(bill.id) { mutableStateOf("4") }
        var weekly by remember(bill.id) { mutableStateOf(true) }
        val n = parts.toIntOrNull() ?: 0
        val plan = remember(bill.id, n, weekly) {
            com.pesaflow.app.data.finance.instalmentSchedule(bill.amount, n, if (weekly) 7 else 30)
        }
        AlertDialog(
            onDismissRequest = { splittingBill = null },
            title = { Text("Split \"${bill.name}\"", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Divide KSh ${bill.amount.toInt()} into dated parts (fees, wifi). The original is replaced — never doubled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(value = parts, onValueChange = { parts = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Parts (2–52)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = weekly, onClick = { weekly = true }, label = { Text("Weekly") })
                        FilterChip(selected = !weekly, onClick = { weekly = false }, label = { Text("Monthly") })
                    }
                    if (plan.isNotEmpty()) {
                        Text(
                            "${plan.size} × KSh ${plan.first().amount.toInt()}" +
                                (if (plan.size > 1) " (last KSh ${plan.last().amount.toInt()})" else "") +
                                " · first due in ${if (weekly) 7 else 30} days",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text("Enter 2–52 parts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = plan.isNotEmpty(),
                    onClick = {
                        viewModel.splitBillIntoInstalments(bill.id, plan.size, if (weekly) 7 else 30)
                        splittingBill = null
                    }
                ) { Text("Split") }
            },
            dismissButton = { TextButton(onClick = { splittingBill = null }) { Text("Cancel") } }
        )
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Rent") }
        var daysUntilDue by remember { mutableStateOf("7") }
        var paybill by remember { mutableStateOf("") }
        var frequency by remember { mutableStateOf("ONE_TIME") }
        var paidBy by remember { mutableStateOf("ME") }
        val frequencies = listOf("ONE_TIME", "WEEKLY", "MONTHLY")

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Bill") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Bill name") })
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (KSh)") })
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") })
                    OutlinedTextField(value = daysUntilDue, onValueChange = { daysUntilDue = it }, label = { Text("Due in (days)") })
                    OutlinedTextField(value = paybill, onValueChange = { paybill = it }, label = { Text("Paybill / till (optional)") })
                    BillPayerPicker(paidBy) { paidBy = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        frequencies.forEach { f ->
                            FilterChip(
                                selected = frequency == f,
                                onClick = { frequency = f },
                                label = { Text(if (f == "ONE_TIME") "Once" else f.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amount.toDoubleOrNull()
                    val days = daysUntilDue.toIntOrNull()
                    if (name.isNotBlank() && amt != null && amt > 0 && days != null) {
                        val due = System.currentTimeMillis() + days.coerceAtLeast(0) * 24L * 60 * 60 * 1000
                        viewModel.addBill(name.trim(), amt, due, category.trim().ifEmpty { "Other" }, frequency, paybill, paidBy)
                        showAddDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}

private val billPayerOptions = listOf(
    "ME" to "Me",
    "PARENTS" to "Parents",
    "SPONSOR" to "Sponsor",
    "HELB" to "HELB",
    "OTHER" to "Other"
)

private fun billPayerLabel(paidBy: String): String =
    billPayerOptions.firstOrNull { it.first == paidBy }?.second ?: "Me"

@Composable
private fun BillPayerPicker(selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Who pays this bill?", style = MaterialTheme.typography.labelMedium)
        Text(
            "Only bills marked Me reduce your safe-to-spend amount.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        billPayerOptions.chunked(2).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = selected == value,
                        onClick = { onSelected(value) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowOptions.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}


@Composable
fun BillCard(bill: Bill, onPaid: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit = {}, onSplit: () -> Unit = {}) {
    var isExpanded by remember { mutableStateOf(false) }
    val nowMs = System.currentTimeMillis()
    val dayMs = 24L * 60 * 60 * 1000
    val overdueDays = if (bill.status != "PAID" && bill.dueDate < nowMs) ((nowMs - bill.dueDate) / dayMs).toInt() else -1
    val dueSoon = bill.status != "PAID" && overdueDays < 0 && bill.dueDate - nowMs < 3 * dayMs

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = com.pesaflow.app.ui.theme.ppShapes.card,
        colors = CardDefaults.cardColors(containerColor = com.pesaflow.app.ui.theme.ppColors.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (overdueDays >= 0) com.pesaflow.app.ui.theme.ppColors.error else com.pesaflow.app.ui.theme.ppColors.border
        ),
        onClick = { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = if (isExpanded) Arrangement.spacedBy(8.dp) else Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        bill.name,
                        style = com.pesaflow.app.ui.theme.ppTypography.labelLarge,
                        color = com.pesaflow.app.ui.theme.ppColors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "KSh ${bill.amount.toInt()}",
                        style = com.pesaflow.app.ui.theme.ppTypography.financialSmall,
                        color = if (overdueDays >= 0) com.pesaflow.app.ui.theme.ppColors.error
                        else com.pesaflow.app.ui.theme.ppColors.textPrimary
                    )
                    Text(
                        "Paid by ${billPayerLabel(bill.paidBy)}",
                        style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                        color = com.pesaflow.app.ui.theme.ppColors.textTertiary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        when {
                            bill.status == "PAID" -> "Due: ${formatDate(bill.dueDate)}"
                            overdueDays >= 0 -> "OVERDUE · ${overdueDays}d"
                            dueSoon -> "Due in ${((bill.dueDate - nowMs) / dayMs).toInt()}d"
                            else -> "Due: ${formatDate(bill.dueDate)}"
                        },
                        style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                        color = when {
                            bill.status == "PAID" -> com.pesaflow.app.ui.theme.ppColors.textTertiary
                            overdueDays >= 0 -> com.pesaflow.app.ui.theme.ppColors.error
                            dueSoon -> com.pesaflow.app.ui.theme.ppColors.warning
                            else -> com.pesaflow.app.ui.theme.ppColors.textTertiary
                        }
                    )
                    if (bill.status == "PAID") {
                        Text("PAID ✓", style = com.pesaflow.app.ui.theme.ppTypography.bodySmall, color = com.pesaflow.app.ui.theme.ppColors.success)
                    } else {
                        TextButton(onClick = onPaid) {
                            Text("Mark Paid", style = com.pesaflow.app.ui.theme.ppTypography.labelMedium, color = com.pesaflow.app.ui.theme.ppColors.gold)
                        }
                    }
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (bill.paybill.isNotBlank()) {
                    Text(
                        "Paybill ${bill.paybill} — pay this number via M-Pesa",
                        style = com.pesaflow.app.ui.theme.ppTypography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = com.pesaflow.app.ui.theme.ppColors.brightBlue,
                        modifier = Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 0.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DetailRowItem(
                        icon = Icons.Default.DateRange,
                        label = "Due Date",
                        value = formatDate(bill.dueDate)
                    )
                    DetailRowItem(
                        icon = Icons.Default.Person,
                        label = "Category",
                        value = bill.category
                    )
                    DetailRowItem(
                        icon = Icons.Default.Refresh,
                        label = "Frequency",
                        value = bill.frequency
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(0.dp, 0.dp, 8.dp, 8.dp), horizontalArrangement = Arrangement.End) {
                    if (bill.status != "PAID") {
                        TextButton(onClick = onSplit) {
                            Text("Split", style = com.pesaflow.app.ui.theme.ppTypography.labelMedium, color = com.pesaflow.app.ui.theme.ppColors.gold)
                        }
                    }
                    TextButton(onClick = onEdit) {
                        Text("Edit", style = com.pesaflow.app.ui.theme.ppTypography.labelMedium, color = com.pesaflow.app.ui.theme.ppColors.brightBlue)
                    }
                    TextButton(onClick = onDelete) {
                        Text("Delete", style = com.pesaflow.app.ui.theme.ppTypography.labelMedium, color = com.pesaflow.app.ui.theme.ppColors.error)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRowItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = com.pesaflow.app.ui.theme.ppColors.textTertiary, modifier = Modifier.size(20.dp))
        Text(label, style = com.pesaflow.app.ui.theme.ppTypography.labelSmall, color = com.pesaflow.app.ui.theme.ppColors.textTertiary)
        Text(value, style = com.pesaflow.app.ui.theme.ppTypography.bodyMedium, color = com.pesaflow.app.ui.theme.ppColors.textPrimary)
    }
}

private fun formatDate(millis: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = millis
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val month = calendar.get(Calendar.MONTH) + 1
    val year = calendar.get(Calendar.YEAR)
    return "$day/$month/$year"
}


private data class RecurringHit(
    val label: String,
    val amount: Double,
    val category: String,
    val intervalDays: Long,
    val times: Int,
    val monthly: Boolean
)


private fun detectRepeats(txs: List<Transaction>): List<RecurringHit> {
    val day = 24L * 60 * 60 * 1000
    return txs.filter { it.type == TransactionType.EXPENSE && !it.isSample }
        .groupBy { it.merchant.trim().lowercase() to it.category }
        .mapNotNull { (key, list) ->
            if (list.size < 3) return@mapNotNull null
            val sorted = list.map { it.dateTimestamp }.sorted()
            val gaps = sorted.zipWithNext { a, b -> (b - a) / day }
            if (gaps.isEmpty()) return@mapNotNull null
            val median = gaps.sorted()[gaps.size / 2]
            if (median < 5 || median > 40) return@mapNotNull null
            val avg = list.map { it.amount }.average()
            if (list.any { kotlin.math.abs(it.amount - avg) > avg * 0.35 + 1 }) return@mapNotNull null
            RecurringHit(
                label = list.maxByOrNull { it.dateTimestamp }?.merchant ?: key.first,
                amount = avg,
                category = key.second,
                intervalDays = median,
                times = list.size,
                monthly = median >= 25
            )
        }
        .sortedByDescending { it.times }
}
