package com.wallpaperapp

import android.Manifest
import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var switchSound: Switch

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startSoundService()
        } else {
            Toast.makeText(this, "Permissao de notificacao necessaria para o servico", Toast.LENGTH_LONG).show()
            switchSound.isChecked = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val previewHome: ImageView = findViewById(R.id.preview_home)
        val previewLock: ImageView = findViewById(R.id.preview_lock)
        switchSound = findViewById(R.id.switch_sound)
        val btnApply: Button = findViewById(R.id.btn_apply)

        // Load bundled previews from assets/alanzoka/
        try {
            assets.open("alanzoka/home.jpg").use { stream ->
                previewHome.setImageBitmap(BitmapFactory.decodeStream(stream))
            }
        } catch (_: IOException) {
        }

        try {
            assets.open("alanzoka/lock.jpg").use { stream ->
                previewLock.setImageBitmap(BitmapFactory.decodeStream(stream))
            }
        } catch (_: IOException) {
        }

        switchSound.isChecked = prefs.getBoolean(KEY_SOUND_ENABLED, false)

        btnApply.setOnClickListener {
            applyWallpapers()
        }

        switchSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_SOUND_ENABLED, isChecked).apply()
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return@setOnCheckedChangeListener
                    }
                }
                startSoundService()
            } else {
                stopSoundService()
            }
        }

        // Auto-start service if it was enabled
        if (switchSound.isChecked) {
            startSoundService()
        }
    }

    private fun applyWallpapers() {
        val wallpaperManager = WallpaperManager.getInstance(this)
        var success = true

        try {
            assets.open("alanzoka/home.jpg").use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    bitmap.recycle()
                } else {
                    success = false
                }
            }
        } catch (e: IOException) {
            success = false
            Toast.makeText(this, "Erro ao aplicar wallpaper home: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        try {
            assets.open("alanzoka/lock.jpg").use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                    bitmap.recycle()
                } else {
                    success = false
                }
            }
        } catch (e: IOException) {
            success = false
            Toast.makeText(this, "Erro ao aplicar wallpaper lock: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        if (success) {
            Toast.makeText(this, "Wallpapers aplicados!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startSoundService() {
        val intent = Intent(this, UnlockSoundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopSoundService() {
        stopService(Intent(this, UnlockSoundService::class.java))
    }

    companion object {
        const val PREFS_NAME = "wallpaper_prefs"
        const val KEY_SOUND_ENABLED = "sound_enabled"
    }
}
