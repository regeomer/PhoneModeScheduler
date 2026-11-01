package com.phonemode.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Runs when a rule starts or ends. */
class ModeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val triggerAt = intent.getLongExtra(ModeScheduler.EXTRA_TRIGGER_AT, 0L)
        val planned = if (triggerAt > 0) {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(triggerAt), ZoneId.systemDefault())
        } else null
        ModeScheduler.sync(context, applyNow = true, evaluateAt = planned)
    }

    companion object {
        const val ACTION_TRANSITION = "com.phonemode.scheduler.TRANSITION"
    }
}

/** Sets the mode and alarm again after a reboot, app update, clock change or permission change. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ModeScheduler.sync(context, applyNow = true)
    }
}
