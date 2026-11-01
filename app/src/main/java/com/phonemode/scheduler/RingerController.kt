package com.phonemode.scheduler

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.util.Log

/** Changes the phone's ringer mode. */
object RingerController {

    private const val TAG = "RingerController"

    fun hasDndAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)!!.isNotificationPolicyAccessGranted

    fun currentMode(context: Context): RingMode =
        when (context.getSystemService(AudioManager::class.java)!!.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> RingMode.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> RingMode.VIBRATE
            else -> RingMode.NORMAL
        }

    /**
     * Applies [mode]. Silent mode (and leaving Do Not Disturb) needs
     * "Do Not Disturb access". Without it, Silent falls back to Vibrate.
     * Returns the mode that was actually set, or null if the change failed.
     */
    fun apply(context: Context, mode: RingMode): RingMode? {
        val audio = context.getSystemService(AudioManager::class.java)!!
        val target = if (mode == RingMode.SILENT && !hasDndAccess(context)) RingMode.VIBRATE else mode
        return try {
            audio.ringerMode = when (target) {
                RingMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
                RingMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                RingMode.SILENT -> AudioManager.RINGER_MODE_SILENT
            }
            Log.i(TAG, "Ringer mode set to $target")
            target
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to set ringer mode to $target", e)
            null
        }
    }
}
