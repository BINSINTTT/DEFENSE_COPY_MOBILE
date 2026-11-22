package com.tomatoinsight

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tomatoinsight.data.DueScanReminder
import com.tomatoinsight.data.ScanCalendar
import com.tomatoinsight.data.TileScanResult
import com.tomatoinsight.data.asOfIso
import com.tomatoinsight.data.dueScanReminders
import com.tomatoinsight.data.formatScanDueLabel
import com.tomatoinsight.data.todayIsoLocal
import java.util.Calendar

private const val REMINDER_REQUEST = 41
private const val PREFS = "scan_reminders"
private const val SHOWN_SIGNATURE = "shown_signature"

/** Clears shared reminder prefs so a new account never inherits another user's due signature. */
fun clearScanReminderCache(context: Context) {
    val alarm = context.getSystemService(AlarmManager::class.java)
    runCatching {
        alarm?.cancel(reminderPending(context, "", "", ""))
    }
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}

/**
 * Shows or schedules the “this week needs a scan” phone notification.
 * Due tiles use the same per-plant rules as the website (`reminderWeekForPlant`).
 */
fun refreshScanReminder(
    context: Context,
    calendar: ScanCalendar,
    rows: Int,
    columns: Int,
    history: Map<Int, List<TileScanResult>>,
    plantNames: Map<Int, String> = emptyMap()
) {
    val alarm = context.getSystemService(AlarmManager::class.java) ?: return
    val pending = reminderPending(context, "", "", "")
    alarm.cancel(pending)

    if (!calendar.enabled || calendar.anchor.isBlank() || rows <= 0 || columns <= 0) return

    val dues = dueScanReminders(calendar, history)
    if (dues.isEmpty()) return

    val asOf = calendar.asOfIso()
    val dueLabel = formatScanDueLabel(asOf)
    val title = "Plant scan reminder"
    val summary = buildNextScanNotificationSummary(dues, plantNames)
    val body = buildNextScanNotificationBody(dues, plantNames)

    val signature = buildString {
        append(asOf)
        append('|')
        append(
            dues.sortedBy { it.tileNumber }.joinToString(",") { due ->
                val name = plantNames[due.tileNumber]?.trim().orEmpty()
                "${due.tileNumber}:${due.nextWeek}:$name"
            }
        )
        append('|')
        append(body)
    }

    val ready = reminderPending(context, title, body, dueLabel)
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (prefs.getString(SHOWN_SIGNATURE, "") == signature) return

    // Website shows reminders as soon as the calendar "as of" day makes them due.
    val posted = showFarmAlert(
        context = context,
        title = title,
        body = body,
        summary = summary,
        dueDateLabel = dueLabel
    )
    if (posted) {
        prefs.edit().putString(SHOWN_SIGNATURE, signature).apply()
        return
    }

    // Permission may still be denied — retry next local morning while dues remain.
    scheduleReminderAlarm(alarm, nextMorningMillis(), ready)
}

internal fun plantDueLabel(due: DueScanReminder, plantNames: Map<Int, String>): String {
    val name = plantNames[due.tileNumber]?.trim().orEmpty().ifBlank { "Tile ${due.tileNumber}" }
    return if (name.equals("Tile ${due.tileNumber}", ignoreCase = true)) {
        name
    } else {
        "$name (Tile ${due.tileNumber})"
    }
}

/** Compact one-line preview for the collapsed notification shade. */
internal fun buildNextScanNotificationSummary(
    dues: List<DueScanReminder>,
    plantNames: Map<Int, String>
): String {
    val due = dues.singleOrNull()
    return when {
        due != null -> "Time for the week ${due.nextWeek} scan of ${plantDueLabel(due, plantNames)}."
        dues.isEmpty() -> "Check your farm for upcoming scans."
        else -> "${dues.size} plants are due for a scan."
    }
}

internal fun buildNextScanNotificationBody(
    dues: List<DueScanReminder>,
    plantNames: Map<Int, String>
): String = buildString {
    if (dues.size <= 1) {
        append(buildNextScanNotificationSummary(dues, plantNames))
    } else {
        append("${dues.size} plants are due for a scan.")
        dues.sortedBy { it.tileNumber }.forEach { due ->
            append("\n• ${plantDueLabel(due, plantNames)} · Week ${due.nextWeek}")
        }
    }
    append("\n\nTap to open your farm.")
}

class ScanReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Plant scan reminder"
        val body = intent.getStringExtra("body").orEmpty()
        if (body.isBlank()) return
        val dueLabel = intent.getStringExtra("dueLabel").orEmpty()
        val posted = showFarmAlert(
            context = context,
            title = title,
            body = body,
            dueDateLabel = dueLabel.ifBlank { formatScanDueLabel(todayIsoLocal()) }
        )
        if (posted) {
            // Keep alarm from looping every morning with the same payload until dues change.
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(SHOWN_SIGNATURE, "alarm:${title.hashCode()}:${body.hashCode()}")
                .apply()
        }
    }
}

private fun scheduleReminderAlarm(
    alarm: AlarmManager,
    triggerAt: Long,
    pending: PendingIntent
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        return
    }
    runCatching {
        alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }.onFailure {
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }
}

private fun reminderPending(
    context: Context,
    title: String,
    body: String,
    dueLabel: String
): PendingIntent {
    val intent = Intent(context, ScanReminderReceiver::class.java).apply {
        putExtra("title", title)
        putExtra("body", body)
        putExtra("dueLabel", dueLabel)
    }
    return PendingIntent.getBroadcast(
        context,
        REMINDER_REQUEST,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun nextMorningMillis(): Long {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 8)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    return cal.timeInMillis
}
