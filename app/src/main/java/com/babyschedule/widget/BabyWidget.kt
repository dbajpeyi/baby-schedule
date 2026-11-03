package com.babyschedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.babyschedule.BabyScheduleApp
import com.babyschedule.MainActivity
import com.babyschedule.R
import com.babyschedule.data.FeedingType
import com.babyschedule.data.totalSeconds
import com.babyschedule.domain.StatsCalculator
import com.babyschedule.util.formatDuration
import com.babyschedule.util.formatTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home-screen widget: last feed, suggested next side, sleep status and a
 * "Log a session" button. Shows clock times so it stays correct between updates.
 */
class BabyWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                render(context, manager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_LOG_SESSION = "com.babyschedule.LOG_SESSION"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Re-draws every placed widget (call when data changes). */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BabyWidget::class.java))
            if (ids.isEmpty()) return
            scope.launch { render(context, manager, ids) }
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val repo = (context.applicationContext as BabyScheduleApp).repository
            val prefs = repo.prefs
            val last = repo.latestFeeding().first()
            val lastBreast = repo.latestBreastFeeding().first()
            val lastSleep = repo.latestSleep().first()
            val sleepStart = prefs.sleepStart.value
            val timer = prefs.timer.value
            val name = prefs.profile.value.name

            val feedText = when {
                timer.isActive -> "Feeding now (since ${formatTime(timer.sessionStart ?: System.currentTimeMillis())})"
                last == null -> "No feeds logged yet"
                last.type == FeedingType.BREAST ->
                    "Last feed ${formatTime(last.startTime)} · breast ${formatDuration(last.totalSeconds.toLong())}"
                else -> "Last feed ${formatTime(last.startTime)} · bottle ${last.amountMl ?: 0} ml"
            }
            val nextText = "Next side: ${StatsCalculator.suggestedSide(lastBreast).label}"
            val sleepText = when {
                sleepStart != null -> "Sleeping since ${formatTime(sleepStart)}"
                lastSleep != null -> "Woke up at ${formatTime(lastSleep.endTime)}"
                else -> ""
            }

            val open = PendingIntent.getActivity(
                context, 10,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val log = PendingIntent.getActivity(
                context, 11,
                Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_LOG_SESSION, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_baby).apply {
                    setTextViewText(R.id.widget_title, name.ifBlank { context.getString(R.string.app_name) })
                    setTextViewText(R.id.widget_feed, feedText)
                    setTextViewText(R.id.widget_next, nextText)
                    setTextViewText(R.id.widget_sleep, sleepText)
                    setOnClickPendingIntent(R.id.widget_root, open)
                    setOnClickPendingIntent(R.id.widget_log, log)
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}
