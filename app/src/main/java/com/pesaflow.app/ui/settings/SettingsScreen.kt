package com.pesaflow.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.app.Activity
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.pesaflow.app.data.notifications.NotificationHelper
import com.pesaflow.app.data.notifications.ReminderScheduler
import com.pesaflow.app.data.parsers.MpesaParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.AppLanguage
import com.pesaflow.app.data.models.AppTheme
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.parsers.CsvImporter
import com.pesaflow.app.viewmodels.FinanceViewModel
import com.pesaflow.app.ui.theme.AtmoWorkspace
import com.pesaflow.app.ui.theme.AtmosphereBand
import com.pesaflow.app.ui.theme.glass
import com.pesaflow.app.R
import com.pesaflow.app.ui.theme.CinematicBackdrop
import com.pesaflow.app.ui.theme.TintSettingsNeutral
import com.pesaflow.app.ui.theme.SkinSectionHeader
import com.pesaflow.app.ui.budgets.Persona
import com.pesaflow.app.ui.budgets.parsePersona


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: FinanceViewModel) {
    val context = LocalContext.current
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val transactions by viewModel.allTransactions.collectAsState()
    val hiddenSections by viewModel.hiddenSections.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val savingsGoals by viewModel.savingsGoals.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val mealItems by viewModel.mealItems.collectAsState()
    val chamas by viewModel.chamaGroups.collectAsState()
    val universityProfile by viewModel.universityProfile.collectAsState()
    val belongings by viewModel.belongings.collectAsState()
    val kitchenStock by viewModel.kitchenStock.collectAsState()
    val pendingTransactions by viewModel.pendingTransactions.collectAsState()

    val prefs = remember { context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE) }
    var lastRun by remember { mutableStateOf(prefs.getLong("last_run", 0)) }
    var notifTransactions by remember { mutableStateOf(prefs.getBoolean("daily_summary", false)) }
    var notifWeekly by remember { mutableStateOf(prefs.getBoolean("weekly_recap", false)) }
    var sundayReport by remember { mutableStateOf(prefs.getBoolean("sunday_report", false)) }
    var nightReport by remember { mutableStateOf(prefs.getBoolean("night_report", true)) }
    var smsGranted by remember { mutableStateOf(hasSmsPermission(context)) }
    var smsAsked by remember { mutableStateOf(false) }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        smsGranted = granted
        smsAsked = true
    }
    val inboxScope = rememberCoroutineScope()
    var inboxResult by remember { mutableStateOf<String?>(null) }
    var inboxScanning by remember { mutableStateOf(false) }
    var scanRange by remember { mutableStateOf("5 months") }
    var customScanDays by remember {
        mutableStateOf(prefs.getInt("sms_scan_custom_days", 150).toString())
    }
    fun scanInbox(range: String = scanRange) {
        val customDays = customScanDays.toIntOrNull()
        if (range == "Custom" && (customDays == null || customDays !in 1..3650)) {
            inboxResult = "Enter a custom lookback between 1 and 3,650 days."
            return
        }
        inboxScanning = true
        inboxScope.launch(Dispatchers.IO) {
            try {
                // Presets use calendar boundaries; Custom accepts any lookback
                // up to ten years. All preserves the full-inbox option.
                val now = System.currentTimeMillis()
                val day = 24L * 60 * 60 * 1000
                val dayStart = java.util.Calendar.getInstance().apply {
                    timeInMillis = now
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                val rangeStart = when (range) {
                    "Today" -> dayStart
                    "Week" -> dayStart - 6 * day
                    "Month" -> java.util.Calendar.getInstance().apply {
                        timeInMillis = now
                        set(java.util.Calendar.DAY_OF_MONTH, 1)
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    "5 months" -> java.util.Calendar.getInstance().apply {
                        timeInMillis = now
                        add(java.util.Calendar.MONTH, -5)
                    }.timeInMillis
                    "Custom" -> (now - customDays!!.toLong() * 24L * 60 * 60 * 1000)
                    else -> 0L
                }
                var found = 0
                var queued = 0
                var dupes = 0
                var unreadable = 0
                var ads = 0
                var capped = false
                var newestBal: Double? = null
                val samples = mutableListOf<String>()
                val badSenders = mutableMapOf<String, Int>()
                val sourceFilter = "(address LIKE ? OR address LIKE ? OR address LIKE ? OR address LIKE ? OR " +
                    "address LIKE ? OR address LIKE ? OR address LIKE ? OR body LIKE ? OR body LIKE ?)"
                val sourceArgs = arrayOf(
                    "%MPESA%", "%Safaricom%", "%AIRTEL%", "%TELKOM%", "%EQUITEL%",
                    "%HELB%", "%SACCO%", "%M-PESA%", "%KES%"
                )
                val selection = if (rangeStart > 0) "$sourceFilter AND date >= ?" else sourceFilter
                val args = if (rangeStart > 0) sourceArgs + rangeStart.toString() else sourceArgs
                val pageSize = 250
                val maxRows = 5000
                var offset = 0
                var exhausted = false
                while (!exhausted && !capped) {
                    // Fetch one extra row at the limit so exactly 5,000 rows
                    // are not mislabeled as truncated.
                    val requested = minOf(pageSize, maxRows + 1 - offset)
                    var pageRows = 0
                    context.contentResolver.query(
                        android.provider.Telephony.Sms.Inbox.CONTENT_URI,
                        arrayOf("_id", "address", "body", "date"),
                        selection,
                        args,
                        "date DESC LIMIT $requested OFFSET $offset"
                    )?.use { c ->
                        val bodyIdx = c.getColumnIndexOrThrow("body")
                        val addrIdx = c.getColumnIndexOrThrow("address")
                        val dateIdx = c.getColumnIndexOrThrow("date")
                        while (c.moveToNext()) {
                            pageRows++
                            if (offset + pageRows > maxRows) {
                                capped = true
                                break
                            }
                            val body = c.getString(bodyIdx) ?: ""
                            val sender = c.getString(addrIdx) ?: ""
                            // Body text can mention M-Pesa; never parse a
                            // friend's message as an official transaction.
                            if (sender.isNotBlank() && !MpesaParser.isOfficialSender(sender)) continue
                            found++
                            val smsTimestamp = c.getLong(dateIdx).takeIf { it > 0L } ?: now
                            // Newest-first order: first balance tail found is the latest wallet figure.
                            if (newestBal == null) com.pesaflow.app.data.parsers.parseBalance(body)?.let { newestBal = it }
                            val pending = MpesaParser.parseMessage(body, sender, smsTimestamp)
                            if (pending == null) {
                                if (MpesaParser.isPromoAd(body)) {
                                    ads++
                                } else {
                                    unreadable++
                                    badSenders[sender] = (badSenders[sender] ?: 0) + 1
                                    // Keep the first 3 failures (sender + opening words) so the
                                    // exact format can be taught to the parser next update.
                                    if (samples.size < 3) {
                                        val head = body.replace("\n", " ").trim().take(90)
                                        samples.add("$sender: $head")
                                    }
                                }
                            } else if (viewModel.tryQueuePending(pending)) {
                                queued++
                            } else {
                                dupes++
                            }
                        }
                    }
                    offset += pageRows
                    if (pageRows < requested || offset > maxRows) exhausted = true
                }
                // Wallet display: newest balance tail seen in this scan.
                newestBal?.let { com.pesaflow.app.data.parsers.saveMpesaBalance(context, it) }
                val scopeLabel = if (range == "All") "" else " ($range)"
                val scanSummary = if (found == 0) {
                    "No M-Pesa/Safaricom texts found$scopeLabel — nothing to parse."
                } else {
                    buildString {
                        append("Scanned ${if (capped) "at least " else ""}$found official text(s)$scopeLabel: $queued new pending — approve them on Home. ✅")
                        if (ads > 0) append(" $ads promo text(s) skipped (ads, not money).")
                        if (dupes > 0) append(" $dupes already in your ledger (skipped, no doubles).")
                        if (capped) append(" Scan cap reached; choose a shorter date window to continue.")
                        if (unreadable > 0) {
                            append(" $unreadable I can't read yet")
                            val topSenders = badSenders.entries.sortedByDescending { it.value }.take(2)
                            if (topSenders.isNotEmpty()) {
                                append(" [" + topSenders.joinToString(", ") { "${it.key}×${it.value}" } + "]")
                            }
                            append(".")
                            samples.forEach { append("\n• $it…") }
                        }
                    }
                }
                withContext(Dispatchers.Main) { inboxResult = scanSummary }
            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) { inboxResult = "SMS permission needed — tap Enable first." }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { inboxResult = "Scan failed: ${e.message}" }
            } finally {
                withContext(Dispatchers.Main) { inboxScanning = false }
            }
        }
    }
    var autoApprove by remember { mutableStateOf(prefs.getBoolean("auto_approve_mpesa", false)) }
    var autoFaces by remember { mutableStateOf(prefs.getBoolean("auto_confirm_faces", false)) }
    var notifGranted by remember { mutableStateOf(hasNotifPermission(context)) }
    var notifAsked by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifGranted = granted
        notifAsked = true
    }
    fun askNotif() {
        notifGranted = hasNotifPermission(context)
        if (notifGranted) return
        if (Build.VERSION.SDK_INT < 33) {
            notifGranted = true
            return
        }
        val activity = context as? Activity
        if (notifAsked && activity != null && !activity.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            context.startActivity(i)
        } else {
            notifAsked = true
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<List<Transaction>?>(null) }

    var backupMsg by remember { mutableStateOf<String?>(null) }
    // Backup v2: every table, every field, via the typed payload — parity by
    // construction. One line per table; new columns join automatically.
    fun buildBackupJson(): String {
        val payload = com.pesaflow.app.data.backup.BackupPayload(
            exportedAt = System.currentTimeMillis(),
            transactions = transactions,
            pending = pendingTransactions,
            budgets = budgets,
            goals = savingsGoals,
            profile = universityProfile,
            bills = bills,
            debts = debts,
            meals = mealItems,
            chamas = chamas,
            belongings = belongings,
            kitchenStock = kitchenStock
        )
        return com.pesaflow.app.data.backup.BackupJson.encodeToString(
            com.pesaflow.app.data.backup.BackupPayload.serializer(), payload
        )
    }
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(buildBackupJson().toByteArray()) }
                backupMsg = "Backup saved. ✅"
            } catch (e: Exception) {
                backupMsg = "Backup failed: ${e.message}"
            }
        }
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                viewModel.restoreBackup(text) { ok -> backupMsg = if (ok) "Restore complete. ✅" else "Not a PesaPlanner backup." }
            } catch (e: Exception) {
                backupMsg = "Restore failed: ${e.message}"
            }
        }
    }
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importPreview = try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                CsvImporter.parseCsvDataAuto(text)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        CinematicBackdrop(workspaceTint = TintSettingsNeutral, bgRes = R.drawable.bg_settings_neutral)
        Scaffold(
            containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // How it all fits together (plain words first, toggles after)
            item {
                AtmosphereBand(
                    workspace = AtmoWorkspace.STAGE,
                    title = "Control deck",
                    subtitle = "Detection · alerts · data"
                )
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glass(shape = RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("How PesaFlow works 🔄", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("1️⃣ We spot your M-Pesa texts the moment they arrive.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        Text("2️⃣ You confirm each one — right in the notification, or on Home → Pending.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        Text("3️⃣ Budgets, insights and reports update themselves. That's the whole app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Everything below tunes that flow. Nothing here can break your data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                SkinSectionHeader(title = "APPEARANCE", subtitle = "Language, theme, home sections")
            }


            // Language Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Language", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            LanguageOptionChip(
                                language = AppLanguage.ENGLISH,
                                isSelected = currentLanguage == AppLanguage.ENGLISH,
                                onSelect = { viewModel.setLanguage(AppLanguage.ENGLISH) }
                            ) {
                                Text("English", color = if (currentLanguage == AppLanguage.ENGLISH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                            LanguageOptionChip(
                                language = AppLanguage.KISWAHILI,
                                isSelected = currentLanguage == AppLanguage.KISWAHILI,
                                onSelect = { viewModel.setLanguage(AppLanguage.KISWAHILI) }
                            ) {
                                Text("Kiswahili", color = if (currentLanguage == AppLanguage.KISWAHILI) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                            LanguageOptionChip(
                                language = AppLanguage.SHENG,
                                isSelected = currentLanguage == AppLanguage.SHENG,
                                onSelect = { viewModel.setLanguage(AppLanguage.SHENG) }
                            ) {
                                Text("Sheng", color = if (currentLanguage == AppLanguage.SHENG) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                            LanguageOptionChip(
                                language = AppLanguage.MIXED,
                                isSelected = currentLanguage == AppLanguage.MIXED,
                                onSelect = { viewModel.setLanguage(AppLanguage.MIXED) }
                            ) {
                                Text("Mixed", color = if (currentLanguage == AppLanguage.MIXED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }


            // Theme Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Theme", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = themeMode == AppTheme.SYSTEM,
                                onClick = { viewModel.setThemeMode(AppTheme.SYSTEM) },
                                label = { Text("📱 System") }
                            )
                            FilterChip(
                                selected = themeMode == AppTheme.LIGHT,
                                onClick = { viewModel.setThemeMode(AppTheme.LIGHT) },
                                label = { Text("☀️ Light") }
                            )
                            FilterChip(
                                selected = themeMode == AppTheme.DARK,
                                onClick = { viewModel.setThemeMode(AppTheme.DARK) },
                                label = { Text("🌙 Dark") }
                            )
                            FilterChip(
                                selected = themeMode == AppTheme.AMOLED,
                                onClick = { viewModel.setThemeMode(AppTheme.AMOLED) },
                                label = { Text("⬛ AMOLED") }
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Dark saves battery, Light wins in sunlight. Instant switch. ✨", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemePreviewSwatch(
                                name = "Light",
                                bg = Color(0xFFFFEFD5),
                                selected = themeMode == AppTheme.LIGHT,
                                onClick = { viewModel.setThemeMode(AppTheme.LIGHT) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemePreviewSwatch(
                                name = "Dark",
                                bg = Color(0xFF121212),
                                selected = themeMode == AppTheme.DARK,
                                onClick = { viewModel.setThemeMode(AppTheme.DARK) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemePreviewSwatch(
                                name = "AMOLED",
                                bg = Color.Black,
                                selected = themeMode == AppTheme.AMOLED,
                                onClick = { viewModel.setThemeMode(AppTheme.AMOLED) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }


            // Home sections visibility
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Home Sections", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf("safe" to "Safe-to-spend", "pending" to "Pending approvals", "recent" to "Recent ledgers").forEach { (key, label) ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Switch(checked = key !in hiddenSections, onCheckedChange = { viewModel.toggleSection(key) })
                            }
                        }
                    }
                }
            }


            item {
                SkinSectionHeader(title = "NOTIFICATIONS AND DETECTION", subtitle = "Alerts, M-Pesa scan, listeners")
            }


            // Notification Settings
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Notifications", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("System permission", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (notifGranted) "ON ✓ — reminders can appear."
                                    else "Off — reminders are scheduled but silent.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (notifGranted) {
                                Text("ON ✓", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            } else {
                                TextButton(onClick = { askNotif() }) { Text(if (notifAsked) "Open Settings" else "Enable") }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Hear one now", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                NotificationHelper.show(context, 99, "Test ✓", "PesaFlow notifications work on this phone.")
                            }) { Text("Send test") }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Preview real reports", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { ReminderScheduler.previewNightReport(context) }) { Text("Night now") }
                            TextButton(onClick = { ReminderScheduler.previewSundayReport(context) }) { Text("Sunday now") }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Last background check", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (lastRun == 0L) "never yet"
                                    else java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastRun)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { lastRun = prefs.getLong("last_run", 0) }) { Text("Refresh") }
                            }
                        }
                        Text(
                            "If scheduled pings never arrive, exempt PesaFlow from battery optimization in system settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Battery exemption", style = MaterialTheme.typography.bodySmall)
                                val powerManager = remember {
                                    context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                                }
                                var batteryClean by remember {
                                    mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
                                }
                                Text(
                                    if (batteryClean) "ON ✓ — Android won't pause alerts."
                                    else "Off — alerts may pause when you leave the app.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                try {
                                    context.startActivity(
                                        android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = android.net.Uri.parse("package:" + context.packageName)
                                        }
                                    )
                                } catch (e: Exception) {
                                    try {
                                        context.startActivity(
                                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = android.net.Uri.parse("package:" + context.packageName)
                                            }
                                        )
                                    } catch (_: Exception) {
                                    }
                                }
                            }) { Text("Keep alive") }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Daily spending summary", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Switch(checked = notifTransactions, onCheckedChange = {
                                notifTransactions = it
                                prefs.edit().putBoolean("daily_summary", it).apply()
                                if (it) {
                                    ReminderScheduler.scheduleDaily(context)
                                    NotificationHelper.show(context, 10, "Reminders on ✓", "You'll get a daily spending summary here.")
                                } else ReminderScheduler.cancelDaily(context)
                            })
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Weekly summaries", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Switch(checked = notifWeekly, onCheckedChange = {
                                notifWeekly = it
                                prefs.edit().putBoolean("weekly_recap", it).apply()
                                if (it) {
                                    ReminderScheduler.scheduleWeekly(context)
                                    NotificationHelper.show(context, 11, "Weekly recap on ✓", "You'll get a 7-day spending recap every week.")
                                } else ReminderScheduler.cancelWeekly(context)
                            })
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sunday 8pm report", style = MaterialTheme.typography.bodySmall)
                                Text("Week summary, every Sunday evening", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = sundayReport, onCheckedChange = {
                                sundayReport = it
                                prefs.edit().putBoolean("sunday_report", it).apply()
                                if (it) {
                                    ReminderScheduler.scheduleSundayReport(context)
                                    NotificationHelper.show(context, 12, "Sunday report on ✓", "See you Sunday 8pm. 🌙")
                                } else ReminderScheduler.cancelSundayReport(context)
                            })
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Night report 9:30pm", style = MaterialTheme.typography.bodySmall)
                                Text("Today's total, biggest item, pending SMS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = nightReport, onCheckedChange = {
                                nightReport = it
                                prefs.edit().putBoolean("night_report", it).apply()
                                if (it) {
                                    ReminderScheduler.scheduleNightReport(context)
                                    NotificationHelper.show(context, 13, "Night report on ✓", "See you tonight 9:30pm. 🌙")
                                } else ReminderScheduler.cancelNightReport(context)
                            })
                        }
                    }
                }
            }


            // M-Pesa Detection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("M-Pesa Detection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "We read M-Pesa texts so you don't type them. Approve each in the notification or on Home → Pending. No permission? Paste texts in the 🧪 box below instead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Today", "Week", "Month").forEach { r ->
                                FilterChip(selected = scanRange == r, onClick = { scanRange = r }, label = { Text(r) })
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("5 months", "Custom", "All").forEach { r ->
                                FilterChip(selected = scanRange == r, onClick = { scanRange = r }, label = { Text(r) })
                            }
                        }
                        if (scanRange == "Custom") {
                            OutlinedTextField(
                                value = customScanDays,
                                onValueChange = {
                                    customScanDays = it.filter { ch -> ch in '0'..'9' }.take(4)
                                    prefs.edit().putInt("sms_scan_custom_days", customScanDays.toIntOrNull() ?: 150).apply()
                                },
                                label = { Text("Look back (days, 1–3,650)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Scan SMS inbox now", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { scanInbox(scanRange) }, enabled = !inboxScanning) {
                                Text(if (inboxScanning) "Scanning…" else "Scan $scanRange")
                            }
                        }
                        inboxResult?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("SMS-based M-Pesa parsing", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (smsGranted) "ON ✓ — new M-Pesa texts auto-log."
                                    else "Needs SMS access, or share texts to PesaFlow instead.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (smsGranted) {
                                Text("ON ✓", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            } else {
                                TextButton(onClick = {
                                    smsGranted = hasSmsPermission(context)
                                    if (smsGranted) return@TextButton
                                    val activity = context as? Activity
                                    if (smsAsked && activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS)) {
                                        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                        context.startActivity(i)
                                    } else {
                                        smsAsked = true
                                        smsLauncher.launch(Manifest.permission.READ_SMS)
                                    }
                                }) { Text(if (smsAsked) "Open Settings" else "Enable") }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-log M-Pesa 🤖", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (autoApprove) "ON — new texts skip Pending, straight to ledger."
                                    else "Off — every text waits in Pending for your tap.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = autoApprove, onCheckedChange = {
                                autoApprove = it
                                prefs.edit().putBoolean("auto_approve_mpesa", it).apply()
                            })
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-confirm familiar faces 🤝", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (autoFaces) "ON — remembered people with a usual category skip Pending."
                                    else "Off — even remembered faces wait for your tap.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = autoFaces, onCheckedChange = {
                                autoFaces = it
                                prefs.edit().putBoolean("auto_confirm_faces", it).apply()
                            })
                        }
                    }
                }
            }


            // SMS parse tester: paste any M-Pesa text, see instantly what the app reads
            item {
                var testSms by remember { mutableStateOf("") }
                var testParsed by remember { mutableStateOf<com.pesaflow.app.data.models.PendingTransaction?>(null) }
                var testFailed by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Test SMS Parsing 🧪", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Paste one M-Pesa text below — see instantly what the app reads from it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = testSms,
                            onValueChange = { testSms = it; testParsed = null; testFailed = false },
                            label = { Text("Paste M-Pesa SMS here") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val p = MpesaParser.parseMessage(testSms)
                                    testParsed = p
                                    testFailed = p == null
                                },
                                enabled = testSms.isNotBlank(),
                                shape = RoundedCornerShape(16.dp)
                            ) { Text("Parse this message") }
                            if (testParsed != null) {
                                OutlinedButton(
                                    onClick = {
                                        testParsed?.let { viewModel.queueSharedTransaction(it) }
                                        testSms = ""
                                        testParsed = null
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                ) { Text("Send to Pending") }
                            }
                        }
                        testParsed?.let { p ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "✅ KSh ${p.amount.toInt()} · ${p.type} · ${p.category} · ${p.merchant} (${(p.confidenceScore * 100).toInt()}% sure). Approve it on Home → Pending.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (testFailed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "❌ Couldn't read this one. Copy the exact text to the developer so this format gets added in the next update.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }


            // Notification Detection (real wiring: system Notification Access)
            item {
                var notifAccess by remember { mutableStateOf(hasNotifAccess(context)) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Notification Detection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (notifAccess) "ON ✓ — transaction notifications auto-log (or wait in Pending)."
                            else "For phones where SMS access is denied: reads transaction notifications instead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Notification-based tracking", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            if (notifAccess) {
                                Text("ON ✓", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            } else {
                                TextButton(onClick = {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                }) { Text("Enable") }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { notifAccess = hasNotifAccess(context) }) { Text("Refresh status") }
                        }
                    }
                }
            }


            item {
                SkinSectionHeader(title = "TRANSACTIONS AND DATA", subtitle = "Import, export, backup, delete")
            }


            // Data Management
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Data Management (${transactions.size} transactions)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { shareCsvExport(context, transactions) },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Text("Export CSV", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = { csvPicker.launch("text/*") },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Text("Import CSV", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { backupSaver.launch("pesaplanner-backup-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) + ".json") },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Text("Backup JSON", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = { backupPicker.launch(arrayOf("application/json")) },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Text("Restore JSON", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        backupMsg?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = {
                                    viewModel.purgeSamples { n ->
                                        prefs.edit().putBoolean("demo_mode", false).apply()
                                        backupMsg = if (n == 0) "No sample data found. ✅" else "Cleared $n sample row(s). ✅"
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Text("Clear samples", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = {
                                    viewModel.undoLastImport { n ->
                                        backupMsg = "Undid last import ($n row(s)). ✅"
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Text("Undo import", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "CSV columns: date (yyyy-MM-dd), description, amount, category. Minus amounts come in as spending.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val staleCount = com.pesaflow.app.data.parsers.PendingPolicy.stalePendings(
                            viewModel.pendingTransactions.collectAsState().value,
                            System.currentTimeMillis()
                        ).size
                        if (staleCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    com.pesaflow.app.data.parsers.PendingPolicy.stalePendings(
                                        viewModel.pendingTransactions.value,
                                        System.currentTimeMillis()
                                    ).forEach { viewModel.rejectPending(it) }
                                    backupMsg = "Cleared $staleCount stale pending row(s). ✅"
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Text("Clear $staleCount stale pending", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showDeleteConfirm = true },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text("Delete All Data", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }


            item {
                SkinSectionHeader(title = "UNIVERSITY", subtitle = "School, campus, allowance")
            }


            // University Profile
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("University Profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Set your university, campus and semester allowance under More → University.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }


            // Your setup: switch persona post-onboarding (moved rooms,
            // learned to cook, new commute). Rewrites the home/commute/
            // cooking answers; budgets, meals and buddy follow on next open.
            item {
                var personaSel by remember { mutableStateOf(parsePersona(viewModel.getOnboardingAnswers())) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Your setup", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(personaSel.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Persona.entries.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { p ->
                                    FilterChip(
                                        selected = personaSel == p,
                                        onClick = {
                                            personaSel = p
                                            val cur = viewModel.getOnboardingAnswers()
                                            val keepCooking = cur.split("|").firstOrNull { it.startsWith("cooking=") }?.substringAfter("=")
                                            val cooking = when (p) {
                                                Persona.HOSTEL_NOCOOK -> "NO"
                                                Persona.HOSTEL_COOK -> "YES"
                                                else -> keepCooking ?: "YES"
                                            }
                                            val (home, commute) = when (p) {
                                                Persona.PARENTS_FAR -> "PARENTS" to "FAR"
                                                Persona.PARENTS_NEAR -> "PARENTS" to "NEAR"
                                                Persona.RENT_WALK -> "RENTAL" to "NEAR"
                                                Persona.RENT_COMMUTE -> "RENTAL" to "FAR"
                                                else -> "HOSTEL" to "NEAR"
                                            }
                                            // Drop the old explicit persona= too — otherwise the
                                            // onboarding pick wins forever and this tap looks ignored.
                                            val parts = cur.split("|").filterNot {
                                                it.startsWith("home=") || it.startsWith("commute=") || it.startsWith("cooking=") || it.startsWith("persona=")
                                            }.toMutableList()
                                            parts += listOf("home=$home", "commute=$commute", "cooking=$cooking", "persona=${p.name}")
                                            viewModel.saveOnboardingAnswers(parts.joinToString("|"))
                                        },
                                        label = { Text(p.label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }


            item {
                SkinSectionHeader(title = "ABOUT", subtitle = "Version, privacy, support")
            }


            // About
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("About", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("PesaPlanner - Free Personal Finance App for Kenyan University Students", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        val appVersion = remember {
                            try {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.1"
                            } catch (e: Exception) { "1.1" }
                        }
                        Text("Version $appVersion. All data stays on this phone. No ads, no accounts, no cloud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete all data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This permanently removes all ${transactions.size} transactions. Budgets, bills, debts, meals and goals stay — only the ledger is wiped. This cannot be undone — there is no recovery.")
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("Type DELETE to confirm") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllTransactions()
                        showDeleteConfirm = false
                    },
                    enabled = typed == "DELETE",
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                ) { Text("Delete Everything") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Keep My Data") } }
        )
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { importPreview = null },
            title = { Text("Import CSV") },
            text = {
                Text(
                    if (preview.isEmpty()) "No valid transaction rows found. Expected columns: date, description, amount, category."
                    else "Found ${preview.size} transactions. Import them now?"
                )
            },
            confirmButton = {
                if (preview.isNotEmpty()) {
                    Button(onClick = {
                        viewModel.importTransactions(preview) { added, skipped ->
                            backupMsg = "Imported $added row(s)" +
                                (if (skipped > 0) " · $skipped duplicate(s) skipped" else "") +
                                ". Wrong file? Undo import above reverts it."
                        }
                        importPreview = null
                    }) { Text("Import ${preview.size}") }
                } else {
                    TextButton(onClick = { importPreview = null }) { Text("OK") }
                }
            },
            dismissButton = { TextButton(onClick = { importPreview = null }) { Text("Cancel") } }
        )
    }
}
    }


@Composable
fun LanguageOptionChip(
    language: AppLanguage,
    isSelected: Boolean,
    onSelect: () -> Unit,
    content: @Composable () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onSelect,
        label = content
    )
}


@Composable
fun ThemePreviewSwatch(
    name: String,
    bg: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(
                color = bg,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            (if (selected) "✓ " else "") + name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (bg == Color.Black || bg == Color(0xFF121212)) Color.White else Color.Black
        )
    }
}


@Composable
fun ThemeChip(    theme: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onSelect,
        enabled = enabled,
        label = content
    )
}


private fun shareCsvExport(context: Context, transactions: List<Transaction>) {
    val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    val sb = StringBuilder("date,description,amount,category\n")
    transactions.forEach { tx ->
        sb.append("${format.format(java.util.Date(tx.dateTimestamp))},\"${tx.merchant}\",${tx.amount},\"${tx.category}\"\n")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "PesaFlow transactions export")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(intent, "Export transactions"))
}


private fun hasNotifAccess(context: Context): Boolean {
    val flat = android.provider.Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners"
    ).orEmpty()
    return flat.contains(context.packageName)
}


private fun hasSmsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED


private fun hasNotifPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT < 33) true
    else ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
