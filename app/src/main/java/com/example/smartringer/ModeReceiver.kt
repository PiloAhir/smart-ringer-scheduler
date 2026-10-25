package com.example.smartringer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.app.NotificationManager
import android.os.Build
import android.provider.Settings

class ModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mode = intent.getIntExtra("mode", AudioManager.RINGER_MODE_NORMAL)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mode == AudioManager.RINGER_MODE_SILENT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !nm.isNotificationPolicyAccessGranted) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Scheduler.scheduleNext(context)
            return
        }
        try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.ringerMode = mode
        } catch (_: SecurityException) { }
        Scheduler.scheduleNext(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Scheduler.scheduleNext(context)
    }
}
