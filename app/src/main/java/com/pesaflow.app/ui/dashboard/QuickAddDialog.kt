package com.pesaflow.app.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.ledger.AmountParser
import com.pesaflow.app.data.ledger.CategoryMemory
import com.pesaflow.app.data.parsers.MpesaParser
import com.pesaflow.app.data.parsers.NaturalLanguageParser
import com.pesaflow.app.ui.motion.SuccessBurst
import com.pesaflow.app.ui.theme.PesaSpacing
import com.pesaflow.app.viewmodels.FinanceViewModel

private val QuickCategories = listOf("Food", "Transport", "Rent", "Airtime", "Data", "Shopping", "Health", "School", "Entertainment", "Savings", "Other")
private val QuickIncomeSources = listOf("Salary", "HELB", "Allowance", "Freelance", "Gift", "Other")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddDialog(
    viewModel: FinanceViewModel,
    defaultType: TransactionType,
    onDismiss: () -> Unit,
    existing: Transaction? = null
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showSuccess by remember { mutableStateOf(false) }
    // Save-duplicate guard: same merchant + amount within a day asks first.
    var dupWarn by remember { mutableStateOf<Transaction?>(null) }
    val qScope = rememberCoroutineScope()
    var inputAmount by remember { mutableStateOf(existing?.amount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var inputMerchant by remember { mutableStateOf(existing?.merchant ?: "") }
    var selectedCategory by remember { mutableStateOf(existing?.category ?: if (defaultType == TransactionType.INCOME) "Salary" else "Food") }
    var entryMode by remember {
        mutableStateOf(
            if (existing != null) {
                when (existing.type) {
                    TransactionType.INCOME -> "Received"
                    TransactionType.SAVING -> "Saved"
                    else -> "Spent"
                }
            } else if (defaultType == TransactionType.INCOME) "Received"
            else if (defaultType == TransactionType.SAVING) "Saved"
            else "Spent"
        )
    }
    // Backdate without a calendar: yesterday covers ~all real backdating.
    var dayOffset by remember { mutableStateOf(0) }
    // Any-day backdate for the rest (receipts found late, month-end catch-up).
    var showDatePick by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<Long?>(null) }
    var inputNotes by remember { mutableStateOf("") }
    // Smart line: "kibanda 250" fills amount + category (+ backdate words).
    var smartLine by remember { mutableStateOf("") }
    // Manual category touch: auto-apply runs until the user picks — then hands off.
    var categoryTouched by remember { mutableStateOf(existing != null) }
    // One function for tap-to-fill AND keyboard-Done: same fill, one less tap.
    fun applySmart(p: com.pesaflow.app.data.models.PendingTransaction) {
        inputAmount = if (p.amount % 1.0 == 0.0) p.amount.toInt().toString() else p.amount.toString()
        selectedCategory = p.category
        categoryTouched = true
        entryMode = when (p.type) {
            TransactionType.INCOME -> "Received"
            TransactionType.SAVING -> "Saved"
            else -> "Spent"
        }
        // Backdate words ("jana", "juzi") land on their day.
        val dayMs = 24L * 60 * 60 * 1000
        if (p.dateTimestamp < System.currentTimeMillis() - dayMs / 2) {
            pickedDate = p.dateTimestamp
            dayOffset = 0
        }
        smartLine = ""
    }
    fun suggestFor(m: String, type: TransactionType): String? {
        if (m.isBlank()) return null
        val prefs = context.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
        return CategoryMemory.lookup(prefs, m)
            ?: MpesaParser.inferCategory(m, type).takeIf { it != "Other" }
    }
    // Repeat + merchant memory: the fastest log is one you barely type.
    val recentTx by viewModel.allTransactions.collectAsState()
    val repeatCandidate = remember(recentTx) { if (existing == null) recentTx.firstOrNull() else null }
    // Match-as-you-type across every ledger merchant (blank = last 3) —
    // tapping one fills merchant, usual price AND learned category.
    val matchMerchants = remember(recentTx, inputMerchant) {
        val q = inputMerchant.trim()
        val all = recentTx.map { it.merchant }.distinct().filter { it.isNotBlank() }
        if (q.isEmpty()) all.take(3)
        else (all.filter { it.contains(q, ignoreCase = true) && !it.equals(q, ignoreCase = true) } +
            all.filter { !it.contains(q, ignoreCase = true) }).take(5)
    }
    var selectedMethod by remember {
        val last = context
            .getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
            .getString("last_method", "MPESA")
        mutableStateOf(existing?.paymentMethod ?: runCatching { PaymentMethod.valueOf(last ?: "MPESA") }.getOrDefault(PaymentMethod.MPESA))
    }
    val entryType = when (entryMode) {
        "Received" -> TransactionType.INCOME
        "Saved" -> TransactionType.SAVING
        else -> TransactionType.EXPENSE
    }
    val amountValid = (AmountParser.parseExpression(inputAmount) ?: 0.0) > 0
    // Keyboard up immediately on the amount: the common path is amount-first.
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { amountFocus.requestFocus() }

    // Single save path shared by fresh saves, forced re-saves and edits.
    fun doSave(amt: Double, stamp: Long) {
        val fallbackCategory = when (entryType) {
            TransactionType.INCOME -> "Salary"
            TransactionType.SAVING -> "Savings"
            else -> "Food"
        }
                    if (existing == null) {
                        viewModel.addManualTransaction(
                            amt,
                            entryType,
                            selectedCategory.ifBlank { fallbackCategory },
                            inputMerchant.ifBlank { "General" },
                            selectedMethod,
                            dateTimestamp = stamp,
                            notes = inputNotes.trim()
                        )
            // Sticky method: cash users stay on Cash, M-Pesa users on M-Pesa.
            context
                .getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putString("last_method", selectedMethod.name).apply()
            // Celebration beats the close: check + stars, then out.
            showSuccess = true
        } else {
                        viewModel.replaceTransaction(
                            existing.id,
                            existing.copy(
                                amount = amt,
                                // Chips cover Spent/Received/Saved: preserve any other
                                // stored type (TRANSFER, INVESTMENT) instead of
                                // silently converting it into an expense.
                                type = if (existing.type == TransactionType.TRANSFER || existing.type == TransactionType.INVESTMENT) existing.type else entryType,
                                category = selectedCategory.ifBlank { fallbackCategory },
                                merchant = inputMerchant.ifBlank { "General" },
                                paymentMethod = selectedMethod,
                                notes = inputNotes.ifBlank { existing.notes },
                                updatedAt = System.currentTimeMillis()
                            )
                        )
            onDismiss()
        }
    }
    // Voice: system recognizer (no permission needed), parsed like typed text.
    var voiceHint by remember { mutableStateOf<String?>(null) }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (spoken.isNullOrBlank()) {
            voiceHint = "Didn't catch that — try again 🎤"
            return@rememberLauncherForActivityResult
        }
        val p = NaturalLanguageParser.parse(spoken)
        if (p == null) {
            voiceHint = "Hiyo sijaelewa — try 'lunch 250'"
            return@rememberLauncherForActivityResult
        }
        inputAmount = if (p.amount % 1.0 == 0.0) p.amount.toInt().toString() else p.amount.toString()
        inputMerchant = if (p.merchant == "General Merchant") "" else p.merchant
        selectedCategory = p.category
        entryMode = when (p.type) {
            TransactionType.INCOME -> "Received"
            TransactionType.SAVING -> "Saved"
            else -> "Spent"
        }
        voiceHint = "Heard: \"$spoken\" ✓ — check and save"
    }
    fun askVoice() {
        try {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "sw-KE")
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Sema: lunch 250")
            }
            voiceLauncher.launch(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            voiceHint = "No voice app on this phone — type it instead ⌨️"
        }
    }

    // Pill taps dismiss the keyboard: typing a merchant then tapping a
    // category left the keyboard shoving the whole dialog upward.
    val keyboard = LocalSoftwareKeyboardController.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add transaction" else "Edit transaction", fontWeight = FontWeight.Bold) },
        text = {
            // Scrollable: small screens + open keyboard no longer bury Save.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PesaSpacing.sm)
            ) {
                // Smart line: one line in, whole form filled. Enter applies
                // directly — the suggestion tap is the same fill, not a step.
                if (existing == null) {
                    OutlinedTextField(
                        value = smartLine,
                        onValueChange = { smartLine = it },
                        label = { Text("⚡ Smart line — \"kibanda 250\", \"jana fare 100\"") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            NaturalLanguageParser.parse(smartLine)?.let { applySmart(it) }
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    remember(smartLine) { if (smartLine.isBlank()) null else NaturalLanguageParser.parse(smartLine) }?.let { p ->
                        TextButton(onClick = { applySmart(p) }) { Text("⚡ ${p.category} · KSh ${p.amount.toInt()} — tap to fill") }
                    }
                }
                // Spent / Received / Saved — smart defaults follow the mode.
                com.pesaflow.app.ui.theme.SegChoice(
                    options = listOf(
                        com.pesaflow.app.ui.theme.SegOption("Spent", "− Spent"),
                        com.pesaflow.app.ui.theme.SegOption("Received", "+ Received"),
                        com.pesaflow.app.ui.theme.SegOption("Saved", "◉ Saved")
                    ),
                    selected = entryMode,
                    onSelect = { v ->
                        keyboard?.hide()
                        entryMode = v
                        categoryTouched = true
                        if (v == "Spent" && (selectedCategory == "Salary" || selectedCategory == "Savings")) selectedCategory = "Food"
                        if (v == "Received" && (selectedCategory == "Food" || selectedCategory == "Savings")) selectedCategory = "Salary"
                        if (v == "Saved" && (selectedCategory == "Food" || selectedCategory == "Salary")) selectedCategory = "Savings"
                    }
                )
                // One-tap repeat: same as last time, editable before saving.
                repeatCandidate?.let { last ->
                    TextButton(onClick = {
                        inputAmount = if (last.amount % 1.0 == 0.0) last.amount.toInt().toString() else last.amount.toString()
                        inputMerchant = last.merchant
                        selectedCategory = last.category
                        selectedMethod = last.paymentMethod
                        entryMode = when (last.type) {
                            TransactionType.INCOME -> "Received"
                            TransactionType.SAVING -> "Saved"
                            else -> "Spent"
                        }
                    }) { Text("↻ Repeat ${last.merchant} · ${last.amount.toInt()}") }
                }
                // Today / yesterday — covers real backdating without a calendar.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PesaSpacing.xs)) {
                    FilterChip(selected = dayOffset == 0 && pickedDate == null, onClick = { dayOffset = 0; pickedDate = null }, label = { Text("Today") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = dayOffset == 1 && pickedDate == null, onClick = { dayOffset = 1; pickedDate = null }, label = { Text("Yesterday") }, modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = pickedDate != null,
                        onClick = { showDatePick = true },
                        label = {
                            Text(
                                pickedDate?.let { "📅 " + java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(it)) }
                                    ?: "Pick date"
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Amount dominates — calculator-style, numeric first.
                // Accepts 250, 1,250, 2k and sums like 250+80.
                // Speak it or tap it: voice fills everything, chips fill the number.
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { askVoice() }) { Text("🎤 Speak it") }
                    voiceHint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                // Accepts 250, 1,250, 2k: AmountParser validates on save.
                OutlinedTextField(
                    value = inputAmount,
                    onValueChange = { v -> if (v.matches(Regex("^[0-9.,kK+\\- ]*\$"))) inputAmount = v },
                    label = { Text("Amount · KSh") },
                    placeholder = { Text("0", style = MaterialTheme.typography.displaySmall) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Start),
                    modifier = Modifier.fillMaxWidth().focusRequester(amountFocus)
                )
                if ((AmountParser.parseExpression(inputAmount) ?: 0.0) >= 1_000_000) {
                    Text(
                        "Large amount — double-check before saving.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PesaSpacing.xs)) {
                    listOf("50", "100", "200", "500", "1000").forEach { q ->
                        FilterChip(selected = inputAmount == q, onClick = { inputAmount = q }, label = { Text(q) })
                    }
                }
                // Your amounts: the last distinct sums you actually logged.
                val recentAmounts = remember(recentTx, entryType) {
                    recentTx.filter { it.type == entryType && !it.isSample }
                        .map { it.amount }.distinct().take(5)
                }
                if (recentAmounts.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(PesaSpacing.xs)) {
                        recentAmounts.forEach { a ->
                            val label = if (a % 1.0 == 0.0) a.toInt().toString() else a.toString()
                            FilterChip(
                                selected = inputAmount == label,
                                onClick = { inputAmount = label },
                                label = { Text("↻ $label") }
                            )
                        }
                    }
                }
                run {
                    val dayStart = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val todayOut = recentTx.filter { it.type == TransactionType.EXPENSE && !it.isSample && it.dateTimestamp >= dayStart }.sumOf { it.amount }
                    if (todayOut > 0) {
                        Text(
                            "Out today so far: KSh ${todayOut.toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedTextField(
                    value = inputMerchant,
                    onValueChange = { inputMerchant = it },
                    label = { Text(if (entryMode == "Received") "From who? (e.g. HELB)" else "Where? (e.g. Kibanda, Java House)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Type-to-prefill: typed merchant with history offers its
                // median as one tap (chip-tap path below does the same).
                val typedMedian = remember(inputMerchant, recentTx) {
                    val m = inputMerchant.trim()
                    if (m.isEmpty() || inputAmount.isNotBlank()) null
                    else recentTx.filter { it.merchant.equals(m, ignoreCase = true) }
                        .map { it.amount }.sorted()
                        .let { if (it.isEmpty()) null else it[it.size / 2] }
                }
                if (typedMedian != null) {
                    TextButton(onClick = {
                        inputAmount = if (typedMedian % 1.0 == 0.0) typedMedian.toInt().toString() else typedMedian.toString()
                        if (!categoryTouched) suggestFor(inputMerchant, entryType)?.let { selectedCategory = it }
                    }) { Text("Usual: KSh ${typedMedian.toInt()} — tap to fill") }
                }
                if (matchMerchants.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(PesaSpacing.xs)) {
                        matchMerchants.forEach { m ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    inputMerchant = m
                                    // Smart assumption: blank amount inherits this
                                    // merchant's median — the usual price, editable.
                                    if (inputAmount.isBlank()) {
                                        val amts = recentTx
                                            .filter { it.merchant.equals(m, ignoreCase = true) }
                                            .map { it.amount }.sorted()
                                        if (amts.isNotEmpty()) {
                                            val med = amts[amts.size / 2]
                                            inputAmount = if (med % 1.0 == 0.0) med.toInt().toString() else med.toString()
                                        }
                                    }
                                    // Learned category applies until you pick one.
                                    if (!categoryTouched) suggestFor(m, entryType)?.let { selectedCategory = it }
                                },
                                label = { Text(m) }
                            )
                        }
                    }
                }
                // Templates: snapshot the whole form, replay in one tap. Kept
                // tiny (6 max) so the row stays glanceable, not a second menu.
                val tmplPrefs = context.getSharedPreferences("pesaflow_prefs", android.content.Context.MODE_PRIVATE)
                var templates by remember {
                    mutableStateOf(
                        tmplPrefs.getString("quick_templates", "").orEmpty().split(";;")
                            .mapNotNull { row ->
                                val p = row.split("|")
                                if (p.size == 6) p else null
                            }.takeLast(6)
                    )
                }
                fun persistTemplates(next: List<List<String>>) {
                    templates = next.takeLast(6)
                    tmplPrefs.edit().putString("quick_templates", next.takeLast(6).joinToString(";;") { it.joinToString("|") }).apply()
                }
                if (templates.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(PesaSpacing.xs)) {
                        templates.forEach { t ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    inputAmount = t[1]
                                    inputMerchant = t[2]
                                    selectedCategory = t[3]
                                    selectedMethod = runCatching { PaymentMethod.valueOf(t[5]) }.getOrDefault(selectedMethod)
                                    entryMode = when (t[4]) {
                                        "INCOME" -> "Received"
                                        "SAVING" -> "Saved"
                                        else -> "Spent"
                                    }
                                },
                                label = { Text("★ " + t[0]) }
                            )
                        }
                    }
                }
                TextButton(onClick = {
                    val name = inputMerchant.ifBlank { selectedCategory }.ifBlank { "Template" }
                    val amt = AmountParser.parse(inputAmount) ?: 0.0
                    if (amt > 0) persistTemplates(templates.filter { it[0] != name } + listOf(listOf(name, inputAmount, inputMerchant, selectedCategory, entryType.name, selectedMethod.name)))
                }) { Text("★ Save current as template") }
                // Elegant category chips — consistent treatment, no wall of fields
                Text(if (entryMode == "Received") "Source" else "Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PesaSpacing.xs), verticalArrangement = Arrangement.spacedBy(PesaSpacing.xs)) {
                    (if (entryMode == "Received") QuickIncomeSources else QuickCategories).forEach { c ->
                        FilterChip(selected = selectedCategory == c, onClick = { keyboard?.hide(); selectedCategory = c; categoryTouched = true }, label = { Text(c) })
                    }
                }
                val suggestedCat = remember(inputMerchant, entryMode) { suggestFor(inputMerchant, entryType) }
                // Hands-off display: untouched forms auto-apply, so this only
                // appears after a manual pick disagrees with memory.
                if (categoryTouched && suggestedCat != null && selectedCategory != suggestedCat) {
                    TextButton(onClick = { selectedCategory = suggestedCat }) {
                        Text("Memory says: $suggestedCat?")
                    }
                }
                Text("Payment method", style = MaterialTheme.typography.labelLarge)
                com.pesaflow.app.ui.theme.SegChoice(
                    options = listOf(
                        com.pesaflow.app.ui.theme.SegOption("MPESA", "M-Pesa", "📲"),
                        com.pesaflow.app.ui.theme.SegOption("CASH", "Cash", "💵"),
                        com.pesaflow.app.ui.theme.SegOption("BANK_TRANSFER", "Bank", "🏦"),
                        com.pesaflow.app.ui.theme.SegOption("AIRTIME", "Airtime", "📶")
                    ),
                    selected = selectedMethod.name,
                    onSelect = {
                        keyboard?.hide()
                        selectedMethod = runCatching { PaymentMethod.valueOf(it) }.getOrDefault(selectedMethod)
                    }
                )
                if (existing == null) {
                    OutlinedTextField(
                        value = inputNotes,
                        onValueChange = { inputNotes = it },
                        label = { Text("Note (optional — receipt no., reason)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Tip: type \"nimebuy lunch 250\" in Insights → Parse and confirm — Food KSh 250.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!amountValid) {
                    Text(
                        "Enter an amount above zero to save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = amountValid && !showSuccess,
                onClick = {
                    val amt = AmountParser.parseExpression(inputAmount) ?: return@Button
                    val dayMs = 24L * 60 * 60 * 1000
                    val stamp = pickedDate ?: (System.currentTimeMillis() - dayOffset * dayMs)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // New saves pass the duplicate guard first; edits save direct.
                    qScope.launch {
                        val dup = if (existing == null) {
                            viewModel.hasConfirmedDuplicate(amt, inputMerchant.ifBlank { "General" }, stamp)
                        } else null
                        if (dup != null) dupWarn = dup else doSave(amt, stamp)
                    }
                }
            ) {
                Text(if (existing == null) "Save" else "Save changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
    // "Already logged?" — same merchant + amount within a day.
    dupWarn?.let { d ->
        AlertDialog(
            onDismissRequest = { dupWarn = null },
            title = { Text("Already logged?", fontWeight = FontWeight.Bold) },
            text = { Text("${d.merchant} · KSh ${d.amount.toInt()} is already in your ledger. Save anyway?") },
            confirmButton = {
                TextButton(onClick = {
                    val amt = AmountParser.parseExpression(inputAmount) ?: return@TextButton
                    val stamp = pickedDate ?: (System.currentTimeMillis() - dayOffset * 24L * 60 * 60 * 1000)
                    dupWarn = null
                    doSave(amt, stamp)
                }) { Text("Save anyway") }
            },
            dismissButton = { TextButton(onClick = { dupWarn = null }) { Text("Cancel") } }
        )
    }
    // Any-day picker backing the "Pick date" chip.
    @OptIn(ExperimentalMaterial3Api::class)
    if (showDatePick) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = pickedDate ?: System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePick = false },
            confirmButton = {
                @OptIn(ExperimentalMaterial3Api::class)
                TextButton(onClick = {
                    pickedDate = pickerState.selectedDateMillis
                    dayOffset = 0
                    showDatePick = false
                }) { Text("Set date") }
            },
            dismissButton = { @OptIn(ExperimentalMaterial3Api::class)
                TextButton(onClick = { showDatePick = false }) { Text("Cancel") } }
        ) {
            @OptIn(ExperimentalMaterial3Api::class)
            DatePicker(state = pickerState)
        }
    }
    // Celebration overlay: plays once over the dimmed dialog, then closes it.
    if (showSuccess) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            SuccessBurst(onDone = onDismiss)
        }
    }
}
