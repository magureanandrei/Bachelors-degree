package com.example.diabetesapp.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.diabetesapp.MainActivity
import java.util.Calendar

// ── Channels ──────────────────────────────────────────────────────────────────

const val CHANNEL_WORKOUT        = "WORKOUT_CHANNEL"
const val CHANNEL_BASAL          = "basal_reminder"
const val CHANNEL_SPORT_FOLLOWUP = "sport_followup"
const val CHANNEL_SPORT_EVENING  = "sport_evening"
const val CHANNEL_PRE_SPORT      = "pre_sport"
const val CHANNEL_MEAL_CHECK     = "meal_check"
const val CHANNEL_STALE_BG       = "stale_bg"
const val CHANNEL_PEN_CORRECTION = "pen_correction"
const val CHANNEL_MORNING        = "morning_summary"

// ── Notification IDs ──────────────────────────────────────────────────────────

const val NOTIF_ID_WORKOUT         = 1001
const val NOTIF_ID_BASAL           = 2001
const val NOTIF_ID_SPORT_2H        = 2002
const val NOTIF_ID_SPORT_EVENING   = 2003
const val NOTIF_ID_PRE_SPORT       = 2004
const val NOTIF_ID_PRE_SPORT_BG    = 2009
const val NOTIF_ID_MEAL_CHECK      = 2005
const val NOTIF_ID_STALE_BG        = 2006
const val NOTIF_ID_PEN_CORRECTION  = 2007
const val NOTIF_ID_MORNING         = 2008

// ── BroadcastReceiver ─────────────────────────────────────────────────────────

class WorkoutNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannels(context, nm)

        val tapPending = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        when (intent.getStringExtra("NOTIFICATION_TYPE")) {
            "MISSED_BASAL" -> nm.notify(
                NOTIF_ID_BASAL,
                build(context, CHANNEL_BASAL, tapPending,
                    "Basal Insulin Reminder",
                    "No long-acting insulin logged today. Don't forget your basal dose — missing it increases the risk of hyperglycemia and ketones.")
            )

            "POST_SPORT_2H" -> nm.notify(
                NOTIF_ID_SPORT_2H,
                build(context, CHANNEL_SPORT_FOLLOWUP, tapPending,
                    "Post-Exercise Check",
                    "It's been 2 hours since your workout. Blood glucose can be unpredictable after exercise — worth a quick check if you haven't already.")
            )

            "POST_SPORT_EVENING" -> {
                val lastBg    = intent.getDoubleExtra("LAST_BG", 0.0)
                val hyperLimit = intent.getFloatExtra("HYPER_LIMIT", 180f)
                val (title, body) = if (lastBg > hyperLimit) {
                    "Exercise & Glucose Tonight" to
                        "You exercised today and your BG was elevated. Glucose can be unpredictable overnight after exercise — keep your alerts active."
                } else {
                    "Late Hypo Risk Tonight" to
                        "You exercised today. Blood glucose can drop 7–11 hours after activity — consider a small snack and keep your CGM alerts active overnight."
                }
                nm.notify(NOTIF_ID_SPORT_EVENING, build(context, CHANNEL_SPORT_EVENING, tapPending, title, body))
            }

            "PRE_SPORT_30MIN" -> nm.notify(
                NOTIF_ID_PRE_SPORT,
                build(context, CHANNEL_PRE_SPORT, tapPending,
                    "Workout Starting Soon",
                    "Your planned workout starts in 30 minutes. Check your BG and review your pre-exercise strategy in the app.")
            )

            "PRE_SPORT_BG_CHECK" -> nm.notify(
                NOTIF_ID_PRE_SPORT_BG,
                build(context, CHANNEL_PRE_SPORT, tapPending,
                    "Time to Check BG",
                    "Your workout is starting. Check your blood glucose before you begin — aim for 126–180 mg/dL before aerobic exercise (ISPAD 2022).")
            )

            "POST_MEAL_CHECK" -> nm.notify(
                NOTIF_ID_MEAL_CHECK,
                build(context, CHANNEL_MEAL_CHECK, tapPending,
                    "Post-Meal Glucose Check",
                    "It's been 2 hours since your last meal bolus. Time to check your blood glucose — this helps you understand how your body responded.")
            )

            "STALE_BG" -> nm.notify(
                NOTIF_ID_STALE_BG,
                build(context, CHANNEL_STALE_BG, tapPending,
                    "Time to Check Your BG",
                    "No blood glucose reading logged in the last 4 hours. Regular checks help keep you safe.")
            )

            "PEN_CORRECTION" -> nm.notify(
                NOTIF_ID_PEN_CORRECTION,
                build(context, CHANNEL_PEN_CORRECTION, tapPending,
                    "Pen Correction Check",
                    "Your manual pen correction from 90 minutes ago should be near peak activity. Check your BG — watch for signs of stacking with your pump's automatic doses.")
            )

            "MORNING" -> nm.notify(
                NOTIF_ID_MORNING,
                build(context, CHANNEL_MORNING, tapPending,
                    "Good Morning",
                    "Check out your stats from yesterday in the app and start your day informed.")
            )

            else -> nm.notify(
                NOTIF_ID_WORKOUT,
                build(context, CHANNEL_WORKOUT, tapPending,
                    "Workout Complete?",
                    "Your planned workout just finished. Tap to verify and get post-workout insights.")
            )
        }
    }

    private fun build(
        context: Context,
        channel: String,
        tapPending: PendingIntent,
        title: String,
        body: String
    ) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(tapPending)
        .build()

    private fun ensureChannels(context: Context, nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channels = listOf(
            NotificationChannel(CHANNEL_WORKOUT,        "Workout Reminders",       NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_BASAL,          "Basal Insulin Reminders", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_SPORT_FOLLOWUP, "Post-Sport Check",        NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_SPORT_EVENING,  "Evening Sport Alert",     NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_PRE_SPORT,      "Pre-Sport Reminders",     NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_MEAL_CHECK,     "Post-Meal Check",         NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_STALE_BG,       "BG Check Reminders",      NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_PEN_CORRECTION, "Pen Correction Check",    NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_MORNING,        "Morning Summary",         NotificationManager.IMPORTANCE_DEFAULT)
        )
        channels.forEach { nm.createNotificationChannel(it) }
    }
}

// ── Scheduler ─────────────────────────────────────────────────────────────────

object WorkoutNotificationManager {

    /** Existing: schedule the workout-completion notification. */
    fun scheduleNotification(context: Context, triggerTimeMillis: Long) {
        scheduleAlarm(context, NOTIF_ID_WORKOUT, triggerTimeMillis,
            Intent(context, WorkoutNotificationReceiver::class.java))
    }

    /** Notification 1 — Missed Basal (MDI only). */
    fun scheduleMissedBasalReminder(context: Context) {
        val now       = System.currentTimeMillis()
        val today1300 = midnightMillis() + 13 * 3600_000L
        val triggerAt = if (now < today1300) today1300 else now + 1_000L
        scheduleAlarm(context, NOTIF_ID_BASAL, triggerAt,
            Intent(context, WorkoutNotificationReceiver::class.java)
                .putExtra("NOTIFICATION_TYPE", "MISSED_BASAL"))
    }

    fun cancelMissedBasalReminder(context: Context) {
        cancelAlarm(context, NOTIF_ID_BASAL,
            Intent(context, WorkoutNotificationReceiver::class.java))
    }

    /** Notification 2 — Post-Sport 2 h heads-up. */
    fun schedulePostSportTwoHour(context: Context, sportEndTimeMillis: Long) {
        val triggerAt = sportEndTimeMillis + 2 * 3600_000L
        if (triggerAt <= System.currentTimeMillis()) return
        scheduleAlarm(context, NOTIF_ID_SPORT_2H, triggerAt,
            Intent(context, WorkoutNotificationReceiver::class.java)
                .putExtra("NOTIFICATION_TYPE", "POST_SPORT_2H"))
    }

    /** Notification 3 — Post-Sport evening (21:00). */
    fun schedulePostSportEvening(context: Context, lastBGReading: Double, hyperLimit: Float) {
        val today2100 = midnightMillis() + 21 * 3600_000L
        if (System.currentTimeMillis() >= today2100) return
        scheduleAlarm(context, NOTIF_ID_SPORT_EVENING, today2100,
            Intent(context, WorkoutNotificationReceiver::class.java)
                .putExtra("NOTIFICATION_TYPE", "POST_SPORT_EVENING")
                .putExtra("LAST_BG", lastBGReading)
                .putExtra("HYPER_LIMIT", hyperLimit))
    }

    /** Notification 4 — Pre-Sport 30 min reminder. */
    fun schedulePreSport30min(context: Context, plannedStartTimeMillis: Long) {
        val triggerAt = plannedStartTimeMillis - 30 * 60_000L
        if (triggerAt <= System.currentTimeMillis()) return
        scheduleAlarm(context, NOTIF_ID_PRE_SPORT, triggerAt,
            Intent(context, WorkoutNotificationReceiver::class.java)
                .putExtra("NOTIFICATION_TYPE", "PRE_SPORT_30MIN"))
    }

    /** Notification 5 — Pre-Sport BG check at start time (no CGM only). */
    fun schedulePreSportBgCheck(context: Context, plannedStartTimeMillis: Long) {
        if (plannedStartTimeMillis <= System.currentTimeMillis()) return
        scheduleAlarm(context, NOTIF_ID_PRE_SPORT_BG, plannedStartTimeMillis,
            Intent(context, WorkoutNotificationReceiver::class.java)
                .putExtra("NOTIFICATION_TYPE", "PRE_SPORT_BG_CHECK"))
    }

    /** Notification 6 — Post-Meal 2 h check (no CGM only). */
    fun schedulePostMealCheck(context: Context) {
        val triggerAt = System.currentTimeMillis() + 2 * 3600_000L
        scheduleAlarm(context, NOTIF_ID_MEAL_CHECK, triggerAt,
            Intent(context, WorkoutNotificationReceiver::class.java)
                .putExtra("NOTIFICATION_TYPE", "POST_MEAL_CHECK"))
    }

    /** Notification 7 — Stale BG reminder (manual mode only, 08:00–22:00 window). */
    fun scheduleStaleBgReminder(context: Context, lastBGTimestampMillis: Long) {
        val now       = System.currentTimeMillis()
        val midnight  = midnightMillis()
        val today0800 = midnight + 8  * 3600_000L
        val today2200 = midnight + 22 * 3600_000L

        val nextCheck = if (lastBGTimestampMillis <= 0L) now + 4 * 3600_000L
                        else lastBGTimestampMillis + 4 * 3600_000L

        val triggerAt = when {
            nextCheck > now  && nextCheck in today0800..today2200 -> nextCheck
            nextCheck <= now && now      in today0800..today2200 -> now + 1_000L
            else -> return
        }
        scheduleAlarm(context, NOTIF_ID_STALE_BG, triggerAt,
            Intent(context, WorkoutNotificationReceiver::class.java)
                .putExtra("NOTIFICATION_TYPE", "STALE_BG"))
    }

    fun cancelStaleBgReminder(context: Context) {
        cancelAlarm(context, NOTIF_ID_STALE_BG,
            Intent(context, WorkoutNotificationReceiver::class.java))
    }

    /** Notification 8 — Pen correction 90 min check (AID only). */
    fun schedulePenCorrectionCheck(context: Context) {
        val triggerAt = System.currentTimeMillis() + 90 * 60_000L
        scheduleAlarm(context, NOTIF_ID_PEN_CORRECTION, triggerAt,
            Intent(context, WorkoutNotificationReceiver::class.java)
                .putExtra("NOTIFICATION_TYPE", "PEN_CORRECTION"))
    }

    /** Notification 9 — Good Morning (daily 08:00). */
    fun scheduleMorningReminder(context: Context) {
        val midnight  = midnightMillis()
        val today0800 = midnight + 8 * 3600_000L
        val triggerAt = if (System.currentTimeMillis() < today0800) today0800
                        else today0800 + 24 * 3600_000L
        scheduleAlarm(context, NOTIF_ID_MORNING, triggerAt,
            Intent(context, WorkoutNotificationReceiver::class.java)
                .putExtra("NOTIFICATION_TYPE", "MORNING"))
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun midnightMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun scheduleAlarm(context: Context, requestCode: Int, triggerAt: Long, intent: Intent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 60_000L, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun cancelAlarm(context: Context, requestCode: Int, intent: Intent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }
}
