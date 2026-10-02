package com.pesaflow.app.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pesaflow.app.data.database.AppDatabase
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.time.addDays
import com.pesaflow.app.data.time.changeVsPrevious
import com.pesaflow.app.data.time.inPastOrNow
import com.pesaflow.app.data.time.previousRollingDays
import com.pesaflow.app.data.time.previousWeekRange
import com.pesaflow.app.data.time.rollingDays
import com.pesaflow.app.data.time.thisWeekRange
import com.pesaflow.app.data.time.todayRange
import com.pesaflow.app.data.time.yesterdayRange
import com.pesaflow.app.ui.language.langOf
import com.pesaflow.app.ui.language.notifTitle
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit


object NotificationHelper {
    const val CHANNEL_ID = "pesaflow_reminders"

    // Extreme warnings address the user by full name (nickname is for daily talk).
    fun seriousNameOf(context: Context): String {
        val p = context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
        return p.getString("user_name", "").orEmpty().ifBlank { p.getString("user_nickname", "").orEmpty() }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "PesaFlow reminders", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
        }
    }

    // Quiet hours: digest-style pings never fire 22:00–06:30 (a muted channel
    // helps nobody). Urgent money alarms (crossed budgets, overdue debts)
    // always go through.
    fun show(context: Context, id: Int, title: String, body: String, urgent: Boolean = false) {
        if (!urgent) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (hour >= 22 || hour < 6) return
        }
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted — skip silently, app keeps working.
        }
    }
}


object ReminderScheduler {
    private const val DAILY_TAG = "pesaflow_daily_summary"
    private const val WEEKLY_TAG = "pesaflow_weekly_recap"
    private const val DIGEST_TAG = "pesaflow_daily_digest"
    private const val BUDGET_CROSSING_TAG = "pesaflow_budget_crossing"

    fun scheduleDaily(context: Context) {
        val req = PeriodicWorkRequestBuilder<DailySummaryWorker>(24, TimeUnit.HOURS)
            .setInputData(workDataOf("kind" to "daily"))
            .addTag(DAILY_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY_TAG, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancelDaily(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_TAG)
    }

    fun scheduleWeekly(context: Context) {
        val req = PeriodicWorkRequestBuilder<DailySummaryWorker>(7, TimeUnit.DAYS)
            .setInputData(workDataOf("kind" to "weekly"))
            .addTag(WEEKLY_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WEEKLY_TAG, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancelWeekly(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WEEKLY_TAG)
    }

    // NEW: Schedule daily digest at 9am
    fun scheduleDailyDigest(context: Context) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        var delay = cal.timeInMillis - System.currentTimeMillis()
        if (delay < 60 * 1000) delay += 24L * 60 * 60 * 1000
        val req = androidx.work.OneTimeWorkRequestBuilder<DailyDigestWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(DIGEST_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(DIGEST_TAG, androidx.work.ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelDailyDigest(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DIGEST_TAG)
    }

    // NEW: Budget crossing alert
    fun scheduleBudgetCrossingAlert(context: Context) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 18)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        var delay = cal.timeInMillis - System.currentTimeMillis()
        if (delay < 60 * 1000) delay += 24L * 60 * 60 * 1000
        val req = androidx.work.OneTimeWorkRequestBuilder<BudgetCrossingWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(BUDGET_CROSSING_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(BUDGET_CROSSING_TAG, androidx.work.ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelBudgetCrossingAlert(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(BUDGET_CROSSING_TAG)
    }

    private const val LUNCH_TAG = "pesaflow_lunch_picker"

    fun scheduleLunch(context: Context) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 30)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        var delay = cal.timeInMillis - System.currentTimeMillis()
        if (delay < 60 * 1000) delay += 24L * 60 * 60 * 1000
        val req = androidx.work.OneTimeWorkRequestBuilder<MealLunchWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(LUNCH_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(LUNCH_TAG, androidx.work.ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelLunch(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(LUNCH_TAG)
    }

    // Meal SMS sweeps: breakfast 3:00–11:50, lunch 12:00–16:00, supper 17:00–22:00.
    // Two firings per window; the final 21:20 sweep lands just before the 21:30
    // night report, so supper texts are queued before the report composes.
    // Overlap is safe (pipeline dedupes by M-Pesa code).
    private val MEAL_SWEEP_SLOTS = listOf(
        Triple("breakfast", 7, 0),
        Triple("breakfast", 10, 30),
        Triple("lunch", 12, 30),
        Triple("lunch", 15, 30),
        Triple("supper", 18, 30),
        Triple("supper", 21, 20)
    )

    private fun mealSweepTag(kind: String, hour: Int, min: Int) =
        "pesaflow_meal_scan_${kind}_${hour}_${min}"

    fun scheduleMealScans(context: Context) {
        MEAL_SWEEP_SLOTS.forEach { (kind, hour, min) ->
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, min)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            var delay = cal.timeInMillis - System.currentTimeMillis()
            if (delay < 60 * 1000) delay += 24L * 60 * 60 * 1000
            val req = androidx.work.OneTimeWorkRequestBuilder<MealSmsSweepWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(androidx.work.workDataOf("kind" to kind))
                .addTag(mealSweepTag(kind, hour, min))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                mealSweepTag(kind, hour, min), androidx.work.ExistingWorkPolicy.REPLACE, req
            )
        }
    }

    fun cancelMealScans(context: Context) {
        MEAL_SWEEP_SLOTS.forEach { (kind, hour, min) ->
            WorkManager.getInstance(context).cancelUniqueWork(mealSweepTag(kind, hour, min))
        }
        WorkManager.getInstance(context).cancelUniqueWork(LUNCH_SCAN_TAG)
    }

    private const val LUNCH_SCAN_TAG = "pesaflow_lunch_scan"

    // Kept for existing callers/prefs: now arms all three meal windows.
    fun scheduleLunchScan(context: Context) = scheduleMealScans(context)

    fun cancelLunchScan(context: Context) = cancelMealScans(context)

    private const val BREAKFAST_TAG = "pesaflow_breakfast_picker"

    fun scheduleBreakfast(context: Context) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 7)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        var delay = cal.timeInMillis - System.currentTimeMillis()
        if (delay < 60 * 1000) delay += 24L * 60 * 60 * 1000
        val req = androidx.work.OneTimeWorkRequestBuilder<BreakfastWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(BREAKFAST_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(BREAKFAST_TAG, androidx.work.ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelBreakfast(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(BREAKFAST_TAG)
    }

    private const val SUNDAY_TAG = "pesaflow_sunday_report"

    fun scheduleSundayReport(context: Context) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 20)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        var delay = cal.timeInMillis - System.currentTimeMillis()
        if (delay < 60 * 1000) delay += 7L * 24 * 60 * 60 * 1000
        val req = androidx.work.OneTimeWorkRequestBuilder<SundayReportWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(SUNDAY_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SUNDAY_TAG, androidx.work.ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelSundayReport(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SUNDAY_TAG)
    }

    private const val NIGHT_TAG = "pesaflow_night_report"

    fun scheduleNightReport(context: Context) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 21)
            set(java.util.Calendar.MINUTE, 30)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        var delay = cal.timeInMillis - System.currentTimeMillis()
        if (delay < 60 * 1000) delay += 24L * 60 * 60 * 1000
        val req = androidx.work.OneTimeWorkRequestBuilder<NightReportWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(NIGHT_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NIGHT_TAG, androidx.work.ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelNightReport(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NIGHT_TAG)
    }

    // Tired-tap: the night report returns in one hour (max twice a night,
    // enforced by the receiver). Doesn't disturb the daily chain.
    fun snoozeNightReport(context: Context) {
        val req = androidx.work.OneTimeWorkRequestBuilder<NightReportWorker>()
            .setInitialDelay(1, TimeUnit.HOURS)
            .addTag("pesaflow_night_snooze")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "pesaflow_night_snooze", androidx.work.ExistingWorkPolicy.REPLACE, req
        )
    }

    // Instant previews: fire the real workers right now (used by Settings test
    // buttons). The workers re-chain their normal schedule, so nothing breaks.
    fun previewNightReport(context: Context) {
        val req = androidx.work.OneTimeWorkRequestBuilder<NightReportWorker>()
            .addTag("pesaflow_preview_night")
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }

    fun previewSundayReport(context: Context) {
        val req = androidx.work.OneTimeWorkRequestBuilder<SundayReportWorker>()
            .addTag("pesaflow_preview_sunday")
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }

    fun previewMonthlyReport(context: Context) {
        val req = androidx.work.OneTimeWorkRequestBuilder<MonthlyReportWorker>()
            .addTag("pesaflow_preview_monthly")
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }

    private const val MONTHLY_TAG = "pesaflow_monthly_report"

    // Monthly chain: fires 1st of month 8am, re-chains from the worker itself
    // so no new settings toggle is needed (the digest already recaps on the 1st).
    fun scheduleMonthlyReport(context: Context) {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            add(java.util.Calendar.MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 8)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val delay = cal.timeInMillis - System.currentTimeMillis()
        val req = androidx.work.OneTimeWorkRequestBuilder<MonthlyReportWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(MONTHLY_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(MONTHLY_TAG, androidx.work.ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelMonthlyReport(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(MONTHLY_TAG)
    }
}


class DailySummaryWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val ctx = applicationContext
            ctx.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE).edit().putLong("last_run", System.currentTimeMillis()).apply()
            val db = AppDatabase.getDatabase(ctx)
            val txs = db.transactionDao().getAllTransactions().first()
            val now = System.currentTimeMillis()

            fun isExpense(t: com.pesaflow.app.data.models.Transaction) = t.type == TransactionType.EXPENSE && !t.isSample
            val balance = txs.sumOf {
                when (it.type) {
                    TransactionType.INCOME -> it.amount
                    TransactionType.EXPENSE -> -it.amount
                    TransactionType.SAVING -> -it.amount
                    TransactionType.INVESTMENT -> -it.amount
                    TransactionType.TRANSFER -> 0.0
                }
            }

            when (inputData.getString("kind") ?: "daily") {
                "weekly" -> {
                    // Rolling 7-day blocks, labelled honestly as "7 days": the
                    // copy never claims a calendar week here.
                    val cur = rollingDays(now, 7)
                    val prevRange = previousRollingDays(now, 7)
                    val weekTx = txs.filter { isExpense(it) && it.dateTimestamp in cur && inPastOrNow(it.dateTimestamp, now) }
                    val week = weekTx.sumOf { it.amount }
                    val prev = txs.filter { isExpense(it) && it.dateTimestamp in prevRange }.sumOf { it.amount }
                    val topWeek = weekTx.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.maxByOrNull { it.value }
                    val weeklyBody = buildString {
                        append("Spent KSh ${week.toInt()} in 7 days · Balance KSh ${balance.toInt()}.")
                        topWeek?.let { append(" Top: ${it.key} ${it.value.toInt()}.") }
                        if (prev > 0) {
                            val d = changeVsPrevious(week, prev) ?: 0
                            append(if (d > 0) " Up $d% vs prior week." else " Down ${-d}% vs prior week. 👌")
                        }
                    }
                    NotificationHelper.show(ctx, 2, "Weekly recap 💰", weeklyBody.toString())
                }
                else -> {
                    val todayTx = txs.filter { isExpense(it) && it.dateTimestamp in todayRange(now) }
                    val today = todayTx.sumOf { it.amount }
                    val yesterday = txs.filter { isExpense(it) && it.dateTimestamp in yesterdayRange(now) }.sumOf { it.amount }
                    val topToday = todayTx.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.maxByOrNull { it.value }
                    val dailyBody = buildString {
                        append("Today KSh ${today.toInt()} · Balance KSh ${balance.toInt()}.")
                        topToday?.let { append(" Top: ${it.key} ${it.value.toInt()}.") }
                        if (yesterday > 0) {
                            val d = ((today - yesterday) / yesterday * 100).toInt()
                            append(if (d > 0) " Up $d% vs yesterday." else " Down ${-d}% vs yesterday. 👌")
                        }
                    }
                    NotificationHelper.show(ctx, 1, "Daily summary 💰", dailyBody.toString())
                    val budgets = db.budgetDao().getAllBudgets().first()
                    val all = budgets.firstOrNull { it.category == "ALL" }
                    if (all != null && all.limitAmount > 0) {
                        val m0 = java.util.Calendar.getInstance().apply {
                            timeInMillis = now
                            set(java.util.Calendar.DAY_OF_MONTH, 1)
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        val monthSpent = txs.filter { isExpense(it) && it.dateTimestamp >= m0 }.sumOf { it.amount }
                        val pct = (monthSpent / all.limitAmount * 100).toInt()
                        if (pct >= 80) {
                            val sn = NotificationHelper.seriousNameOf(ctx)
                            val who = if (sn.isNotBlank()) "$sn, " else ""
                            NotificationHelper.show(ctx, 3, "Budget watch ⚠️", "${who}you've used $pct% of your KSh ${all.limitAmount.toInt()} monthly budget.")
                        }
                    }
                    // Debt nudges: overdue balances named plainly
                    val overdue = db.debtDao().getAllDebts().first()
                        .filter { it.status != "PAID" && it.dueDate < now }
                    if (overdue.isNotEmpty()) {
                        val total = overdue.sumOf { it.amount }.toInt()
                        val sn = NotificationHelper.seriousNameOf(ctx)
                        val who = if (sn.isNotBlank()) "$sn, " else ""
                        NotificationHelper.show(ctx, 5, "Debts nudging you 🧹", "${who}${overdue.size} overdue totalling KSh $total — clear the smallest first: ${overdue.minByOrNull { it.amount }?.person ?: ""}.", urgent = true)
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}


class MealLunchWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Chain tomorrow's lunch ping first so one missed run never kills the rhythm.
        ReminderScheduler.scheduleLunch(applicationContext)
        return try {
            val ctx = applicationContext
            val db = AppDatabase.getDatabase(ctx)
            val lunches = db.mealDao().getMealsByType("Lunch").first()
                .sortedBy { it.price }.take(3)
            if (lunches.isEmpty()) {
                NotificationHelper.show(ctx, 4, "Lunch time 🍲", "What are you eating? Add foods in Meal Planner first.")
            } else {
                val builder = androidx.core.app.NotificationCompat.Builder(ctx, NotificationHelper.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Lunch time 🍲 — what are you eating?")
                    .setContentText("Tap your plate, expense logs itself.")
                    .setAutoCancel(true)
                lunches.forEachIndexed { i, food ->
                    val intent = Intent(ctx, MealLogReceiver::class.java).apply {
                        action = MealLogReceiver.ACTION_LOG_MEAL
                        putExtra("name", food.name)
                        putExtra("price", food.price)
                    }
                    val pending = android.app.PendingIntent.getBroadcast(
                        ctx, i, intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    builder.addAction(
                        androidx.core.app.NotificationCompat.Action.Builder(
                            android.R.drawable.ic_dialog_info,
                            "${food.name} ${food.price.toInt()}",
                            pending
                        ).build()
                    )
                }
                NotificationHelper.ensureChannel(ctx)
                val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                try {
                    manager.notify(4, builder.build())
                } catch (e: SecurityException) {
                    // Permission missing — rhythm continues, app keeps working.
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}


class BreakfastWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Chain tomorrow's breakfast ping first so one missed run never kills the rhythm.
        ReminderScheduler.scheduleBreakfast(applicationContext)
        return try {
            val ctx = applicationContext
            val db = AppDatabase.getDatabase(ctx)
            val breakfasts = db.mealDao().getMealsByType("Breakfast").first()
                .sortedBy { it.price }.take(3)
            if (breakfasts.isEmpty()) {
                NotificationHelper.show(ctx, 14, "Breakfast time 🍳", "What are you eating? Add foods in Meal Planner first.")
            } else {
                val builder = androidx.core.app.NotificationCompat.Builder(ctx, NotificationHelper.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Breakfast time 🍳 — what are you eating?")
                    .setContentText("Tap your plate, expense logs itself.")
                    .setAutoCancel(true)
                breakfasts.forEachIndexed { i, food ->
                    val intent = Intent(ctx, MealLogReceiver::class.java).apply {
                        action = MealLogReceiver.ACTION_LOG_MEAL
                        putExtra("name", food.name)
                        putExtra("price", food.price)
                    }
                    val pending = android.app.PendingIntent.getBroadcast(
                        ctx, 100 + i, intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    builder.addAction(
                        androidx.core.app.NotificationCompat.Action.Builder(
                            android.R.drawable.ic_dialog_info,
                            "${food.name} ${food.price.toInt()}",
                            pending
                        ).build()
                    )
                }
                NotificationHelper.ensureChannel(ctx)
                val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                try {
                    manager.notify(14, builder.build())
                } catch (e: SecurityException) {
                    // Permission missing — rhythm continues, app keeps working.
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}


// Meal-window SMS sweep: breakfast (3:00–11:50), lunch (12:00–16:00), supper
// (17:00–22:00). Scans today's inbox; the pipeline dedupes by M-Pesa code, so
// overlapping windows never double-post — food texts become confirms.
class MealSmsSweepWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Re-chain all six slots first so one missed run never kills the rhythm.
        ReminderScheduler.scheduleMealScans(applicationContext)
        return try {
            val ctx = applicationContext
            val kind = inputData.getString("kind") ?: "meal"
            val scanned = com.pesaflow.app.data.parsers.scanRecentSms(ctx, 1, 150)
            var food = 0
            scanned.parsed.forEach { p ->
                handleDetectedTransaction(ctx, p)
                if (p.category == "Food") food++
            }
            if (food > 0) {
                ctx.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE).edit()
                    .putLong("last_${kind}_scan_food", System.currentTimeMillis()).apply()
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}


class LunchScanWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Legacy single slot: hand over to the full meal-window chain.
        ReminderScheduler.scheduleMealScans(applicationContext)
        return try {
            val ctx = applicationContext
            val scanned = com.pesaflow.app.data.parsers.scanRecentSms(ctx, 1, 100)
            scanned.parsed.forEach { handleDetectedTransaction(ctx, it) }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}


class MealLogReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_LOG_MEAL = "com.pesaflow.app.LOG_MEAL"
        const val ACTION_COOKED_DAY = "com.pesaflow.app.COOKED_DAY"
        const val ACTION_ATE_OUT = "com.pesaflow.app.ATE_OUT"
        const val ACTION_ATE_PLANNED = "com.pesaflow.app.ATE_PLANNED"
        const val ACTION_SNOOZE_NIGHT = "com.pesaflow.app.SNOOZE_NIGHT"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        scope.launch {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                when (intent.action) {
                    ACTION_LOG_MEAL -> {
                        val name = intent.getStringExtra("name") ?: "Lunch"
                        val price = intent.getDoubleExtra("price", 0.0)
                        if (price > 0) {
                            val db = AppDatabase.getDatabase(context)
                            db.transactionDao().insertTransaction(
                                com.pesaflow.app.data.models.Transaction(
                                    amount = price,
                                    type = TransactionType.EXPENSE,
                                    category = "Food",
                                    dateTimestamp = System.currentTimeMillis(),
                                    merchant = name,
                                    description = "Logged from meal picker",
                                    paymentMethod = com.pesaflow.app.data.models.PaymentMethod.CASH,
                                    source = com.pesaflow.app.data.models.TransactionSource.MANUAL
                                )
                            )
                        }
                        manager.cancel(4)
                        manager.cancel(14)
                    }
                    ACTION_COOKED_DAY -> {
                        // "I cooked" from the night report: burn a day off every
                        // stock item so all bars move with zero typing.
                        val db = AppDatabase.getDatabase(context)
                        val now = System.currentTimeMillis()
                        val items = db.kitchenStockDao().getAllStock().first()
                        items.forEach {
                            db.kitchenStockDao().updateStock(
                                it.copy(qtyLeft = (it.qtyLeft - it.dailyUse).coerceAtLeast(0.0), updatedAt = now)
                            )
                        }
                        manager.cancel(8)
                        if (items.isNotEmpty()) {
                            NotificationHelper.show(
                                context, 17, "Kitchen updated 🍳",
                                "Burned a cooking day off ${items.size} item(s) — nice, no typing needed."
                            )
                        }
                    }
                    ACTION_ATE_OUT -> {
                        // Ate out: nothing to deduct — acknowledge so the tap feels heard.
                        manager.cancel(8)
                        NotificationHelper.show(context, 19, "Noted 🍲", "Ate out — nothing deducted, report stood down.")
                    }
                    ACTION_ATE_PLANNED -> {
                        // Ate what the planner said: nothing to deduct — close the loop kindly.
                        manager.cancel(8)
                        val planned = intent.getStringExtra("planned").orEmpty()
                        NotificationHelper.show(
                            context, 22, "As planned ✅",
                            (if (planned.isNotBlank()) "$planned stood. " else "") + "Nice — no typing, no guessing."
                        )
                    }
                    ACTION_SNOOZE_NIGHT -> {
                        // Tired? +1 hour, max twice a night, then it lets you sleep.
                        val prefs = context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
                        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            .format(java.util.Date(System.currentTimeMillis()))
                        val parts = (prefs.getString("night_snooze", "") ?: "").split("|")
                        val count = if (parts.getOrNull(0) == today) parts.getOrNull(1)?.toIntOrNull() ?: 0 else 0
                        manager.cancel(8)
                        if (count >= 2) {
                            NotificationHelper.show(context, 18, "Rest well 😴", "No more pings tonight — see you tomorrow.")
                        } else {
                            prefs.edit().putString("night_snooze", "$today|${count + 1}").apply()
                            ReminderScheduler.snoozeNightReport(context)
                            NotificationHelper.show(context, 8, "Snoozed ⏰", "Back in an hour (${count + 1}/2 tonight).")
                        }
                    }
                }
            } catch (e: Exception) {
                // Never crash the receiver; user can log manually.
            } finally {
                result.finish()
            }
        }
    }
}


class SundayReportWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ReminderScheduler.scheduleSundayReport(applicationContext)
        return try {
            val ctx = applicationContext
            val lang = langOf(ctx.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE).getString("app_language", "MIXED") ?: "MIXED")
            val db = AppDatabase.getDatabase(ctx)
            val txs = db.transactionDao().getAllTransactions().first()
            val now = System.currentTimeMillis()
            // Calendar weeks (Mon-start), never mid-day rolling windows: the old
            // now - 7d boundary sliced the same local date across both weeks.
            val week = thisWeekRange(now)
            val prevWeek = previousWeekRange(now)

            fun isExpense(t: com.pesaflow.app.data.models.Transaction) = t.type == TransactionType.EXPENSE && !t.isSample
            fun inWeek(t: com.pesaflow.app.data.models.Transaction) = t.dateTimestamp in week && inPastOrNow(t.dateTimestamp, now)

            val weekSpent = txs.filter { isExpense(it) && inWeek(it) }.sumOf { it.amount }
            val weekCount = txs.filter { isExpense(it) && inWeek(it) }.size
            val prevWeekSpent = txs.filter { isExpense(it) && it.dateTimestamp in prevWeek }.sumOf { it.amount }
            val weekIncome = txs.filter { it.type == TransactionType.INCOME && !it.isOpening && inWeek(it) }.sumOf { it.amount }

            val mpesaWeek = txs.filter { isExpense(it) && inWeek(it) && it.source == com.pesaflow.app.data.models.TransactionSource.MPESA_SMS }.sumOf { it.amount }
            val manualWeek = weekSpent - mpesaWeek

            val topMpesa = txs.filter { isExpense(it) && inWeek(it) && it.source == com.pesaflow.app.data.models.TransactionSource.MPESA_SMS }
                .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
                .maxByOrNull { it.value }

            // Top-3 categories of the week
            val top3 = txs.filter { isExpense(it) && inWeek(it) }
                .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
                .entries.sortedByDescending { it.value }.take(3)

            // Priciest weekday of the calendar week (Mon-first, local days)
            val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val priciestDay = week.days().filter { it <= now }.map { d0 ->
                val sum = txs.filter { isExpense(it) && it.dateTimestamp >= d0 && it.dateTimestamp < addDays(d0, 1) }.sumOf { it.amount }
                dayNames[java.util.Calendar.getInstance().apply { timeInMillis = d0 }.get(java.util.Calendar.DAY_OF_WEEK).let { (it + 5) % 7 }] to sum
            }.maxByOrNull { it.second }

            // Week savings rate
            val savedRate = if (weekIncome > 0) ((weekIncome - weekSpent) / weekIncome * 100).toInt() else null

            // Bills landing in the next 7 days
            val dueWeek = db.billDao().getAllBills().first()
                .filter { it.status != "PAID" && it.dueDate in now..addDays(now, 7) }
            val dueWeekTotal = dueWeek.sumOf { it.amount }

            val pending = db.pendingTransactionDao().getAllPendingTransactions().first().size

            val body = buildString {
                append("This week: KSh ${weekSpent.toInt()} ($weekCount items)")
                if (weekIncome > 0) append(", income KSh ${weekIncome.toInt()}")
                append(".")
                if (prevWeekSpent > 0) {
                    // Dust baselines get absolutes, never a fantasy percent:
                    // KSh 20,000 vs KSh 0.50 is not "up 4000000%".
                    if (com.pesaflow.app.data.time.isDustBaseline(prevWeekSpent)) {
                        append(" KSh ${weekSpent.toInt()} vs KSh ${prevWeekSpent.toInt()} last week.")
                    } else {
                        val change = changeVsPrevious(weekSpent, prevWeekSpent) ?: 0
                        append(if (change > 0) " Up $change% vs last week." else " Down ${-change}% vs last week. 👌")
                    }
                }
                if (top3.isNotEmpty()) append(" Top: " + top3.joinToString(", ") { "${it.key} ${it.value.toInt()}" } + ".")
                priciestDay?.let { if (it.second > 0) append(" Priciest day: ${it.first}.") }
                savedRate?.let { append(if (it >= 20) " Kept $it% — solid 💪." else " Kept $it% (target 20%).") }
                if (mpesaWeek > 0) append(" M-Pesa: KSh ${mpesaWeek.toInt()}, manual: KSh ${manualWeek.toInt()}.")
                topMpesa?.let { append(" M-Pesa top: ${it.key}.") }
                if (dueWeek.isNotEmpty()) append(" Bills this week: ${dueWeek.size} totalling KSh ${dueWeekTotal.toInt()}.")
                if (pending > 0) append(" $pending SMS pending.")
            }
            NotificationHelper.show(ctx, 6, "${notifTitle("sunday", lang)} 📊", body)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}


class BudgetCrossingWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ReminderScheduler.scheduleBudgetCrossingAlert(applicationContext)
        return try {
            val ctx = applicationContext
            val db = AppDatabase.getDatabase(ctx)
            val txs = db.transactionDao().getAllTransactions().first()
            val budgets = db.budgetDao().getAllBudgets().first()
            val now = System.currentTimeMillis()
            val all = budgets.firstOrNull { it.category == "ALL" }
            if (all != null && all.limitAmount > 0) {
                val monthStart = java.util.Calendar.getInstance().apply {
                    timeInMillis = now
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                val monthSpent = txs.filter { it.type == TransactionType.EXPENSE && it.dateTimestamp >= monthStart }.sumOf { it.amount }
                val pct = (monthSpent / all.limitAmount * 100).toInt()
                if (pct >= 100) {
                    val sn = NotificationHelper.seriousNameOf(ctx)
                    val who = if (sn.isNotBlank()) "$sn, " else ""
                    NotificationHelper.show(ctx, 9, "Budget crossed ⛔", "${who}you've spent KSh ${monthSpent.toInt()} of KSh ${all.limitAmount.toInt()} this month. Essentials only!", urgent = true)
                } else if (pct >= 80) {
                    val sn = NotificationHelper.seriousNameOf(ctx)
                    val who = if (sn.isNotBlank()) "$sn, " else ""
                    NotificationHelper.show(ctx, 9, "Budget warning ⚠️", "${who}you've used $pct% of your KSh ${all.limitAmount.toInt()} monthly budget. Slow down!", urgent = true)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}


class DailyDigestWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ReminderScheduler.scheduleDailyDigest(applicationContext)
        return try {
            val ctx = applicationContext
            val db = AppDatabase.getDatabase(ctx)
            val txs = db.transactionDao().getAllTransactions().first()
            val now = System.currentTimeMillis()
            val yesterday = yesterdayRange(now)
            val yesterdaySpent = txs.filter { it.type == TransactionType.EXPENSE && it.dateTimestamp in yesterday }.sumOf { it.amount }
            val day = 24L * 60 * 60 * 1000
            // Bills AND debts landing inside each item's own lead window
            // (falls back to 3 days) — overdue ones always included.
            val dueBills = db.billDao().getAllBills().first()
                .filter {
                    it.status != "PAID" &&
                        it.dueDate <= now + maxOf(1, it.reminderLeadDays) * day
                }
                .sortedBy { it.dueDate }
            val dueDebts = db.debtDao().getAllDebts().first()
                .filter {
                    it.status != "PAID" &&
                        it.dueDate <= now + maxOf(1, it.reminderLeadDays) * day
                }
                .sortedBy { it.dueDate }
            val overdueDebts = dueDebts.filter { it.dueDate < now }
            val digest = buildString {
                append("Yesterday: KSh ${yesterdaySpent.toInt()} spent.")
                if (dueBills.isNotEmpty()) append(" Bills due soon: ${dueBills.size} (KSh ${dueBills.sumOf { it.amount }.toInt()}).")
                if (dueDebts.isNotEmpty()) append(" Debts due soon: ${dueDebts.size} (KSh ${dueDebts.sumOf { it.amount }.toInt()}).")
                if (overdueDebts.isNotEmpty()) append(" Overdue: ${overdueDebts.size} 🧹.")
                if (dueBills.isEmpty() && dueDebts.isEmpty()) append(" Nothing due — clean slate. 🎉")
            }
            NotificationHelper.show(ctx, 7, "Daily Digest ☀️", digest.toString())
            // Monthly recap fires from the digest on the 1st of each month
            if (java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.DAY_OF_MONTH) == 1) {
                val monthStart = java.util.Calendar.getInstance().apply {
                    timeInMillis = now
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                val prevStart = java.util.Calendar.getInstance().apply {
                    timeInMillis = monthStart
                    add(java.util.Calendar.MONTH, -1)
                }.timeInMillis
                fun isExp(t: com.pesaflow.app.data.models.Transaction) = t.type == TransactionType.EXPENSE && !t.isSample
                val lastSpent = txs.filter { isExp(it) && it.dateTimestamp >= prevStart && it.dateTimestamp < monthStart }.sumOf { it.amount }
                val lastIncome = txs.filter { it.type == TransactionType.INCOME && !it.isOpening && it.dateTimestamp >= prevStart && it.dateTimestamp < monthStart }.sumOf { it.amount }
                val lastTop = txs.filter { isExp(it) && it.dateTimestamp >= prevStart && it.dateTimestamp < monthStart }
                    .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
                    .maxByOrNull { it.value }
                val monthName = java.text.SimpleDateFormat("MMMM", java.util.Locale.US).format(java.util.Date(prevStart))
                NotificationHelper.show(
                    ctx, 21, "Monthly report 🗓️ — $monthName",
                    "Spent KSh ${lastSpent.toInt()}, in KSh ${lastIncome.toInt()}." +
                        (lastTop?.let { " Top: ${it.key} KSh ${it.value.toInt()}." } ?: " No spending logged.") +
                        if (lastIncome > 0) " Kept ${((lastIncome - lastSpent) / lastIncome * 100).toInt()}%." else ""
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}


class NightReportWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ReminderScheduler.scheduleNightReport(applicationContext)
        return try {
            val ctx = applicationContext
            val lang = langOf(ctx.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE).getString("app_language", "MIXED") ?: "MIXED")
            val db = AppDatabase.getDatabase(ctx)
            val txs = db.transactionDao().getAllTransactions().first()
            val now = System.currentTimeMillis()
            val day = 24L * 60 * 60 * 1000
            val today = todayRange(now)
            val yesterday = yesterdayRange(now)
            // Rolling last-7-days, labelled as such in the body — never "week",
            // which now means the Monday-start calendar week everywhere.
            val last7 = rollingDays(now, 7)
            val dayStart = today.startInclusive

            fun isExpense(t: com.pesaflow.app.data.models.Transaction) = t.type == TransactionType.EXPENSE && !t.isSample

            val todaySpent = txs.filter { isExpense(it) && it.dateTimestamp in today }.sumOf { it.amount }
            val todayCount = txs.filter { isExpense(it) && it.dateTimestamp in today }.size
            val yesterdaySpent = txs.filter { isExpense(it) && it.dateTimestamp in yesterday }.sumOf { it.amount }
            val weekSpent = txs.filter { isExpense(it) && it.dateTimestamp in last7 && inPastOrNow(it.dateTimestamp, now) }.sumOf { it.amount }
            val weekCount = txs.filter { isExpense(it) && it.dateTimestamp in last7 && inPastOrNow(it.dateTimestamp, now) }.size
            val monthStart = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val monthSpent = txs.filter { isExpense(it) && it.dateTimestamp >= monthStart }.sumOf { it.amount }
            val balance = txs.sumOf { when (it.type) { TransactionType.INCOME -> it.amount; TransactionType.EXPENSE -> -it.amount; TransactionType.SAVING -> -it.amount; TransactionType.INVESTMENT -> -it.amount; TransactionType.TRANSFER -> 0.0 } }

            val topCatToday = txs.filter { isExpense(it) && it.dateTimestamp in today }
                .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
                .maxByOrNull { it.value }
            val biggestSingle = txs.filter { isExpense(it) && it.dateTimestamp in today }
                .maxByOrNull { it.amount }

            val mpesaToday = txs.filter { isExpense(it) && it.dateTimestamp in today && it.source == com.pesaflow.app.data.models.TransactionSource.MPESA_SMS }.sumOf { it.amount }
            val manualToday = todaySpent - mpesaToday
            // M-Pesa summarizer: today's parsed texts, counted and grouped
            val mpesaTodayTxns = txs.filter { isExpense(it) && it.dateTimestamp in today && it.source == com.pesaflow.app.data.models.TransactionSource.MPESA_SMS }
            val mpesaSummary = mpesaTodayTxns.groupBy { it.category }
                .mapValues { e -> e.value.sumOf { it.amount } }
                .entries.sortedByDescending { it.value }.take(3)
            val pending = db.pendingTransactionDao().getAllPendingTransactions().first().size

            // Tonight's question needs tonight's plan: persisted by the menu generator.
            // Diet check reads the ledger, not a questionnaire: greens anywhere today?
            val menuToday = ctx.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE).getString("menu_today", "").orEmpty()
            fun plannedSlot(slot: String): String? {
                return menuToday.split("|").firstOrNull { it.startsWith("$slot:") }
                    ?.removePrefix("$slot:")?.replace("+", " + ")?.takeIf { it.isNotBlank() }
            }
            val plannedLunch = plannedSlot("Lunch")
            val plannedSupper = plannedSlot("Supper")
            val ateFoodToday = txs.any { it.type == TransactionType.EXPENSE && it.category == "Food" && it.dateTimestamp in today }
            val greensToday = txs.any {
                it.type == TransactionType.EXPENSE && it.dateTimestamp in today &&
                    (it.merchant + " " + it.category).contains(Regex("sukuma|mboga|mchicha|spinach|veg|cabbage", RegexOption.IGNORE_CASE))
            }
            val dietLine = if (ateFoodToday && !greensToday) {
                "No greens today — throw sukuma in supper? Cheap + filling. 🌱"
            } else null

            // Cupboard ↔ night loop: scarcest staple rides along when critical.
            val lowStock = db.kitchenStockDao().getAllStock().first()
                .filter { it.dailyUse > 0 && it.qtyLeft > 0 }
                .minByOrNull { it.qtyLeft / it.dailyUse }
            val stockLine = if (lowStock != null && lowStock.qtyLeft / lowStock.dailyUse <= 3) {
                " 🫙 ${lowStock.name} ~${(lowStock.qtyLeft / lowStock.dailyUse).toInt()}d left."
            } else null

            // Month pace vs the ALL budget (spent vs expected-by-today)
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
            val dom = cal.get(java.util.Calendar.DAY_OF_MONTH)
            val dim = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            val allBudget = db.budgetDao().getAllBudgets().first().firstOrNull { it.category == "ALL" }
            val paceLine = if (allBudget != null && allBudget.limitAmount > 0 && dim > 0) {
                val expected = allBudget.limitAmount * dom / dim
                when {
                    monthSpent > expected * 1.15 -> " Month pace: KSh ${monthSpent.toInt()} vs KSh ${expected.toInt()} expected — slow down 🐢."
                    monthSpent < expected * 0.7 -> " Month pace: KSh ${monthSpent.toInt()} vs KSh ${expected.toInt()} expected — nicely under 🐖."
                    else -> null
                }
            } else null

            // Bills landing in the next 3 days
            val dueSoon = db.billDao().getAllBills().first()
                .filter { it.status != "PAID" && it.dueDate in now..(now + 3 * day) }
                .sortedBy { it.dueDate }
            val billsLine = if (dueSoon.isNotEmpty()) {
                val b = dueSoon.first()
                val days = ((b.dueDate - now) / day).coerceAtLeast(0)
                " ⚠️ ${b.name} KSh ${b.amount.toInt()} due in ${days}d" +
                    (if (dueSoon.size > 1) " (+${dueSoon.size - 1} more)" else "") + "."
            } else null

            val body = buildString {
                append("Today: KSh ${todaySpent.toInt()} ($todayCount items")
                if (mpesaToday > 0) append(", KSh ${mpesaToday.toInt()} via M-Pesa")
                append(")")
                if (yesterdaySpent > 0) {
                    val change = ((todaySpent - yesterdaySpent) / yesterdaySpent * 100).toInt()
                    append(if (change > 0) " (up $change% vs yesterday)" else " (down ${-change}%)")
                }
                append(". Last 7 days: KSh ${weekSpent.toInt()} ($weekCount items).")
                topCatToday?.let { top ->
                    append(" Top today: ${top.key} KSh ${top.value.toInt()}")
                    val mOfTop = mpesaSummary.firstOrNull { e -> e.key == top.key }?.value?.toInt() ?: 0
                    if (mOfTop > 0) append(" (KSh $mOfTop of it M-Pesa)")
                    append(".")
                }
                // Any other M-Pesa categories ride on the same sentence, never alone.
                val rest = if (topCatToday != null) mpesaSummary.drop(1) else mpesaSummary
                if (mpesaToday > 0 && rest.isNotEmpty()) {
                    append(" Also on M-Pesa: " + rest.joinToString(", ") { "${it.key} ${it.value.toInt()}" } + ".")
                }
                biggestSingle?.let {
                    append(" Biggest hit: ${it.merchant.take(20)} KSh ${it.amount.toInt()}")
                    append(if (it.source == com.pesaflow.app.data.models.TransactionSource.MPESA_SMS) " (M-Pesa)." else " (manual).")
                }
                if (mpesaToday > 0 && manualToday > 0) append(" Rest KSh ${manualToday.toInt()} manual.")
                // All-manual day (cash life): name the top hand-logged category instead of silence.
                if (mpesaToday <= 0 && manualToday > 0) {
                    val topManual = txs.filter {
                        isExpense(it) && it.dateTimestamp in today &&
                            it.source != com.pesaflow.app.data.models.TransactionSource.MPESA_SMS
                    }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
                        .maxByOrNull { it.value }
                    topManual?.let { append(" All hand-logged — top: ${it.key} KSh ${it.value.toInt()}. Respect. ✍️") }
                }
                paceLine?.let { append(it) }
                billsLine?.let { append(it) }
                stockLine?.let { append(it) }
                if (!ateFoodToday && (plannedSupper != null || plannedLunch != null)) {
                    append(" Supposed: ${(plannedSupper ?: plannedLunch)} — did you?")
                }
                dietLine?.let { append(" $it") }
                if (pending > 0) append(" $pending SMS pending.")
            }
            // A question, not a verdict: what did you eat, or postpone if tired.
            // Tapping the body opens the app; the buttons answer from the shade.
            NotificationHelper.ensureChannel(ctx)
            fun nightAction(action: String, requestCode: Int, label: String): androidx.core.app.NotificationCompat.Action {
                val intent = Intent(ctx, MealLogReceiver::class.java).apply { this.action = action }
                val pending = android.app.PendingIntent.getBroadcast(
                    ctx, requestCode, intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                return androidx.core.app.NotificationCompat.Action.Builder(
                    android.R.drawable.ic_dialog_info, label, pending
                ).build()
            }
            val openApp = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let { launch ->
                android.app.PendingIntent.getActivity(
                    ctx, 50, launch,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            val plannedName = plannedSupper ?: plannedLunch
            val plannedAction = plannedName?.let { name ->
                androidx.core.app.NotificationCompat.Action.Builder(
                    android.R.drawable.ic_dialog_info, "Planned ✅",
                    android.app.PendingIntent.getBroadcast(
                        ctx, 203,
                        Intent(ctx, MealLogReceiver::class.java).apply {
                            action = MealLogReceiver.ACTION_ATE_PLANNED
                            putExtra("planned", name)
                        },
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    )
                ).build()
            }
            val nightNote = androidx.core.app.NotificationCompat.Builder(ctx, NotificationHelper.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("${notifTitle("night", lang)} 🌙")
                .setContentText(body.toString().take(120))
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body.toString()))
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .addAction(nightAction(MealLogReceiver.ACTION_COOKED_DAY, 200, "Cooked 🍳"))
                .addAction(nightAction(MealLogReceiver.ACTION_ATE_OUT, 201, "Ate out 🍲"))
                .addAction(nightAction(MealLogReceiver.ACTION_SNOOZE_NIGHT, 202, "Later ⏰"))
                .apply { plannedAction?.let { addAction(it) } }
                .build()
            try {
                (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(8, nightNote)
            } catch (e: SecurityException) {
                // Permission missing — tomorrow's chain already re-armed above.
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}


class MonthlyReportWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        ReminderScheduler.scheduleMonthlyReport(applicationContext)
        return try {
            val ctx = applicationContext
            val lang = langOf(ctx.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE).getString("app_language", "MIXED") ?: "MIXED")
            val db = AppDatabase.getDatabase(ctx)
            val txs = db.transactionDao().getAllTransactions().first()
            val now = System.currentTimeMillis()
            val monthStart = java.util.Calendar.getInstance().apply {
                timeInMillis = now
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            fun isExp(t: com.pesaflow.app.data.models.Transaction) = t.type == TransactionType.EXPENSE && !t.isSample
            val spent = txs.filter { isExp(it) && it.dateTimestamp >= monthStart }.sumOf { it.amount }
            val income = txs.filter { it.type == TransactionType.INCOME && !it.isOpening && it.dateTimestamp >= monthStart }.sumOf { it.amount }
            val top3 = txs.filter { isExp(it) && it.dateTimestamp >= monthStart }
                .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
                .entries.sortedByDescending { it.value }.take(3)
            val mpesa = txs.filter { isExp(it) && it.dateTimestamp >= monthStart && it.source == com.pesaflow.app.data.models.TransactionSource.MPESA_SMS }.sumOf { it.amount }
            val monthName = java.text.SimpleDateFormat("MMMM", java.util.Locale.US).format(java.util.Date(now))
            val body = buildString {
                append("$monthName: spent KSh ${spent.toInt()}, in KSh ${income.toInt()}.")
                if (top3.isNotEmpty()) append(" Top: " + top3.joinToString(", ") { "${it.key} ${it.value.toInt()}" } + ".")
                if (income > 0) append(" Kept ${((income - spent) / income * 100).toInt()}%.")
                if (mpesa > 0) append(" M-Pesa KSh ${mpesa.toInt()}, manual KSh ${(spent - mpesa).toInt()}.")
            }
            NotificationHelper.show(ctx, 20, "${notifTitle("monthly", lang)} 🗓️ — $monthName", body)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
