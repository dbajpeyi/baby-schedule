package com.babyschedule.notify

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.core.content.ContextCompat
import com.babyschedule.BabyScheduleApp
import com.babyschedule.MainActivity
import com.babyschedule.R
import com.babyschedule.data.AppPrefs
import com.babyschedule.util.formatDuration
import com.babyschedule.util.formatTime

/**
 * Schedules a one-off alarm for "baby has been asleep for N minutes" while a
 * sleep is in progress, and cancels it when the baby wakes up.
 */
class SleepAlertScheduler(private val context: Context, private val prefs: AppPrefs) {

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Sleep alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Lets you know when a nap has gone on for a long time"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Brings the alarm in line with the current sleep state and settings. */
    fun sync() {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent()
        val start = prefs.sleepStart.value
        val alert = prefs.sleepAlert.value
        if (start == null) {
            alarm.cancel(pending)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        if (!alert.enabled) {
            alarm.cancel(pending)
            return
        }
        val at = start + alert.minutes * 60_000L
        val now = System.currentTimeMillis()
        if (at <= now && prefs.sleepAlertShownFor == start) {
            alarm.cancel(pending)
            return
        }
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(at, now + 1_000), pending)
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, SleepAlertReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission")
    fun showIfDue() {
        val start = prefs.sleepStart.value ?: return
        val alert = prefs.sleepAlert.value
        if (!alert.enabled) return
        val now = System.currentTimeMillis()
        if (now < start + alert.minutes * 60_000L - 60_000L) {
            sync() // fired early; reschedule
            return
        }
        prefs.sleepAlertShownFor = start
        if (!canNotify(context)) return

        val name = prefs.profile.value.name.ifBlank { "Baby" }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$name has been asleep for ${formatDuration((now - start) / 1000)}")
            .setContentText("Asleep since ${formatTime(start)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "sleep_alerts"
        private const val NOTIFICATION_ID = 2001
        private const val REQUEST_CODE = 2001

        fun canNotify(context: Context): Boolean =
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

/** Fired by the alarm when the sleep threshold is reached. */
class SleepAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as BabyScheduleApp).sleepAlerts.showIfDue()
    }
}

/** Alarms are cleared on reboot, so re-schedule if a sleep is in progress. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            (context.applicationContext as BabyScheduleApp).sleepAlerts.sync()
        }
    }
}
