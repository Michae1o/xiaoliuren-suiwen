package com.xlr.k

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar

internal object RecallSystemReminder {
    private const val CHANNEL = "suiwen_recall"
    private const val PREF = "recall_system_reminder"
    internal const val FIRE = "com.xlr.k.RECALL_SYSTEM_REMINDER"
    const val PERMISSION_REQUEST = 8041
    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun enabled(c: Context) = prefs(c).getBoolean("enabled", false)
    private fun permitted(c: Context) = NotificationManagerCompat.from(c).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 ||
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun isActive(c: Context) = enabled(c) && permitted(c)

    fun setEnabled(a: MainActivity, value: Boolean) {
        val store = prefs(a)
        store.edit().putBoolean("enabled", value).apply()
        if (value && !permitted(a)) {
            if (Build.VERSION.SDK_INT >= 33 &&
                a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                (!store.getBoolean("requested", false) ||
                    a.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                store.edit().putBoolean("requested", true).apply()
                a.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_REQUEST)
            } else {
                val intent = if (Build.VERSION.SDK_INT >= 26)
                    Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, a.packageName)
                else Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${a.packageName}"))
                runCatching { a.startActivity(intent) }
            }
        }
        refresh(a)
        a.syncRecallNotificationSwitch()
    }

    private fun alarmIntent(c: Context, at: Long = 0L) = PendingIntent.getBroadcast(c, 921,
        Intent(c, RecallSystemReminderReceiver::class.java).setAction(FIRE).putExtra("at", at),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    internal fun refresh(c: Context) {
        val alarms = c.getSystemService(AlarmManager::class.java)
        if (!enabled(c) || !permitted(c)) {
            alarms.cancel(alarmIntent(c))
            return
        }
        val now = System.currentTimeMillis()
        // Preserve a just-due (possibly deferred) alarm when the app resumes during its evening.
        val stored = prefs(c).getLong("nextAt", 0)
        val previous = Calendar.getInstance().apply { timeInMillis = stored }
        val reuse = stored in (now - 3 * 60 * 60 * 1000L)..now &&
            previous.get(Calendar.HOUR_OF_DAY) == 21 && previous.get(Calendar.MINUTE) == 0
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            while (timeInMillis <= now || !(get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ||
                    get(Calendar.DAY_OF_MONTH) == getActualMaximum(Calendar.DAY_OF_MONTH))) add(Calendar.DAY_OF_MONTH, 1)
        }
        val at = if (reuse) stored else next.timeInMillis
        prefs(c).edit().putLong("nextAt", at).apply()
        val pending = alarmIntent(c, at)
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                return
            } catch (_: SecurityException) { /* Permission can change after the check. */ }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    internal fun fire(c: Context, at: Long) {
        if (at == 0L || prefs(c).getLong("nextAt", 0) != at) return
        prefs(c).edit().remove("nextAt").apply()
        refresh(c)
        if (System.currentTimeMillis() - at !in 0..(3 * 60 * 60 * 1000L) || !enabled(c) || !permitted(c)) return
        val date = Calendar.getInstance().apply { timeInMillis = at }
        if (date.get(Calendar.HOUR_OF_DAY) != 21 || date.get(Calendar.MINUTE) != 0) return
        val monthly = date.get(Calendar.DAY_OF_MONTH) == date.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (!monthly && date.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) return
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = c.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "回观", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(c, 922, Intent(c, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (monthly) "本月回观" else "本周回观")
            .setContentText("打开遂问，回顾这段时间的卦例。")
            .setContentIntent(open).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try { NotificationManagerCompat.from(c).notify(921, notification) } catch (_: SecurityException) { }
    }
}

class RecallSystemReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RecallSystemReminder.FIRE) {
            RecallSystemReminder.fire(context, intent.getLongExtra("at", 0))
        } else {
            RecallSystemReminder.refresh(context)
        }
    }
}
