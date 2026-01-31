package com.wallpaperapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File

class UnlockSoundService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var lockMediaPlayer: MediaPlayer? = null

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                val prefs = context.getSharedPreferences(
                    MainActivity.PREFS_NAME, Context.MODE_PRIVATE
                )
                if (prefs.getBoolean(MainActivity.KEY_SOUND_ENABLED, false)) {
                    Log.d(TAG, "ACTION_USER_PRESENT received — playing sound")
                    playUnlockSound()
                }
            }
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                val prefs = context.getSharedPreferences(
                    MainActivity.PREFS_NAME, Context.MODE_PRIVATE
                )
                if (prefs.getBoolean(MainActivity.KEY_LOCK_SOUND_ENABLED, false)) {
                    Log.d(TAG, "ACTION_SCREEN_OFF received — playing lock sound")
                    playLockSound()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val unlockFilter = IntentFilter(Intent.ACTION_USER_PRESENT)
        val screenOffFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, unlockFilter, RECEIVER_EXPORTED)
            registerReceiver(screenOffReceiver, screenOffFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(unlockReceiver, unlockFilter)
            registerReceiver(screenOffReceiver, screenOffFilter)
        }
        Log.d(TAG, "Service created — receivers registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(unlockReceiver) } catch (_: IllegalArgumentException) {}
        try { unregisterReceiver(screenOffReceiver) } catch (_: IllegalArgumentException) {}
        releasePlayer()
        releaseLockPlayer()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun playUnlockSound() {
        releasePlayer()

        try {
            val soundFile = File(filesDir, MainActivity.FILE_SOUND)
            if (!soundFile.exists()) {
                Log.w(TAG, "Sound file not found")
                return
            }
            mediaPlayer = MediaPlayer().apply {
                setDataSource(soundFile.absolutePath)
                prepare()
                setOnCompletionListener { mp ->
                    mp.release()
                    if (mediaPlayer === mp) {
                        mediaPlayer = null
                    }
                }
                start()
            }
            Log.d(TAG, "Unlock sound playing")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing unlock sound", e)
            releasePlayer()
        }
    }

    private fun playLockSound() {
        releaseLockPlayer()

        try {
            val soundFile = File(filesDir, MainActivity.FILE_LOCK_SOUND)
            if (!soundFile.exists()) {
                Log.w(TAG, "Lock sound file not found")
                return
            }
            lockMediaPlayer = MediaPlayer().apply {
                setDataSource(soundFile.absolutePath)
                prepare()
                setOnCompletionListener { mp ->
                    mp.release()
                    if (lockMediaPlayer === mp) {
                        lockMediaPlayer = null
                    }
                }
                start()
            }
            Log.d(TAG, "Lock sound playing")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing lock sound", e)
            releaseLockPlayer()
        }
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun releaseLockPlayer() {
        try {
            lockMediaPlayer?.release()
        } catch (_: Exception) {}
        lockMediaPlayer = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sons de Bloqueio/Desbloqueio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém o serviço de sons ativo"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WallpaperSound")
            .setContentText("Sons de bloqueio/desbloqueio ativos")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val TAG = "UnlockSoundService"
        private const val CHANNEL_ID = "unlock_sound_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
