package com.phonemode.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Sets the correct mode now and sets one alarm for the next rule start or end.
 * When the alarm fires, [ModeAlarmReceiver] calls [sync] again.
 */
object ModeScheduler {

    private const val TAG = "ModeScheduler"
    const val EXTRA_TRIGGER_AT = "trigger_at"

    /**
     * @param applyNow set the mode right away. Use true for boot, alarms and
     * rule edits. With false, a manual change by the user stays until the next
     * rule start or end.
     * @param evaluateAt the time to check the rules at. Alarms pass their planned
     * time so a few milliseconds of timing error can't pick the wrong side of a boundary.
     */
    fun sync(context: Context, applyNow: Boolean = true, evaluateAt: LocalDateTime? = null) {
        val store = RuleStore(context)
        val alarm = context.getSystemService(AlarmManager::class.java)!!
        val pending = alarmIntent(context)

        if (!store.automationEnabled) {
            alarm.cancel(pending)
            return
        }

        val rules = store.loadRules()
        val now = LocalDateTime.now().let { if (evaluateAt != null && evaluateAt.isAfter(it)) evaluateAt else it }

        if (applyNow) {
            RingerController.apply(context, ScheduleEngine.desiredMode(rules, now, store.defaultMode))
        }

        val next = ScheduleEngine.nextTransition(rules, now)
        if (next == null) {
            alarm.cancel(pending)
            return
        }
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = alarmIntent(context, triggerAt)

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
        if (canExact) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        } else {
            // Without the exact-alarm permission Android may run this a few minutes late.
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }
        Log.i(TAG, "Next check at $next (exact=$canExact)")
    }

    private fun alarmIntent(context: Context, triggerAt: Long = 0L): PendingIntent {
        val intent = Intent(context, ModeAlarmReceiver::class.java)
            .setAction(ModeAlarmReceiver.ACTION_TRANSITION)
            .putExtra(EXTRA_TRIGGER_AT, triggerAt)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
