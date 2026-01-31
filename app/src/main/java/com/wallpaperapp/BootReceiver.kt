package com.wallpaperapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val unlockSoundEnabled = prefs.getBoolean(MainActivity.KEY_SOUND_ENABLED, false)
        val lockSoundEnabled = prefs.getBoolean(MainActivity.KEY_LOCK_SOUND_ENABLED, false)

        if (unlockSoundEnabled || lockSoundEnabled) {
            val serviceIntent = Intent(context, UnlockSoundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
