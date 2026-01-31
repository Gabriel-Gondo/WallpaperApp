package com.wallpaperapp

import android.Manifest
import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var checkHome: CheckBox
    private lateinit var checkLock: CheckBox
    private lateinit var checkSound: CheckBox
    private lateinit var checkLockSound: CheckBox
    private lateinit var previewHome: ImageView
    private lateinit var previewLock: ImageView
    private lateinit var txtSoundFile: TextView
    private lateinit var txtLockSoundFile: TextView

    private var previewPlayer: MediaPlayer? = null
    private var previewingFile: String? = null

    private val pickHomeLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { saveFileFromUri(it, FILE_HOME) }
        loadHomePreview()
    }

    private val pickLockLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { saveFileFromUri(it, FILE_LOCK) }
        loadLockPreview()
    }

    private val pickSoundLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            saveFileFromUri(it, FILE_SOUND)
            val name = getFileName(it) ?: "audio"
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putString(KEY_SOUND_NAME, name).apply()
        }
        updateSoundLabel()
    }

    private val pickLockSoundLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            saveFileFromUri(it, FILE_LOCK_SOUND)
            val name = getFileName(it) ?: "audio"
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putString(KEY_LOCK_SOUND_NAME, name).apply()
        }
        updateLockSoundLabel()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Permissão de notificação necessária para o serviço", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        checkHome = findViewById(R.id.check_home)
        checkLock = findViewById(R.id.check_lock)
        checkSound = findViewById(R.id.check_sound)
        checkLockSound = findViewById(R.id.check_lock_sound)
        previewHome = findViewById(R.id.preview_home)
        previewLock = findViewById(R.id.preview_lock)
        txtSoundFile = findViewById(R.id.txt_sound_file)
        txtLockSoundFile = findViewById(R.id.txt_lock_sound_file)

        val btnChooseHome: Button = findViewById(R.id.btn_choose_home)
        val btnChooseLock: Button = findViewById(R.id.btn_choose_lock)
        val btnChooseSound: Button = findViewById(R.id.btn_choose_sound)
        val btnPreviewSound: Button = findViewById(R.id.btn_preview_sound)
        val btnChooseLockSound: Button = findViewById(R.id.btn_choose_lock_sound)
        val btnPreviewLockSound: Button = findViewById(R.id.btn_preview_lock_sound)
        val btnApply: Button = findViewById(R.id.btn_apply)

        // Restore checkbox states
        checkHome.isChecked = prefs.getBoolean(KEY_HOME_ENABLED, true)
        checkLock.isChecked = prefs.getBoolean(KEY_LOCK_ENABLED, true)
        checkSound.isChecked = prefs.getBoolean(KEY_SOUND_ENABLED, false)
        checkLockSound.isChecked = prefs.getBoolean(KEY_LOCK_SOUND_ENABLED, false)

        // Save checkbox states on change
        checkHome.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_HOME_ENABLED, isChecked).apply()
        }
        checkLock.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_LOCK_ENABLED, isChecked).apply()
        }
        checkSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_SOUND_ENABLED, isChecked).apply()
            if (!isChecked && !checkLockSound.isChecked) {
                stopSoundService()
            }
        }
        checkLockSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_LOCK_SOUND_ENABLED, isChecked).apply()
            if (!isChecked && !checkSound.isChecked) {
                stopSoundService()
            }
        }

        // File pickers
        btnChooseHome.setOnClickListener { pickHomeLauncher.launch("image/*") }
        btnChooseLock.setOnClickListener { pickLockLauncher.launch("image/*") }
        btnChooseSound.setOnClickListener { pickSoundLauncher.launch(arrayOf("audio/*")) }
        btnChooseLockSound.setOnClickListener { pickLockSoundLauncher.launch(arrayOf("audio/*")) }

        // Preview buttons
        btnPreviewSound.setOnClickListener {
            if (previewingFile == FILE_SOUND) {
                stopPreview()
            } else {
                startPreview(FILE_SOUND, btnPreviewSound)
            }
        }
        btnPreviewLockSound.setOnClickListener {
            if (previewingFile == FILE_LOCK_SOUND) {
                stopPreview()
            } else {
                startPreview(FILE_LOCK_SOUND, btnPreviewLockSound)
            }
        }

        // Apply button
        btnApply.setOnClickListener { apply() }

        // Load previews
        loadHomePreview()
        loadLockPreview()
        updateSoundLabel()
        updateLockSoundLabel()

        // Auto-start service if any sound was enabled and file exists
        val unlockSoundReady = checkSound.isChecked && getSavedFile(FILE_SOUND).exists()
        val lockSoundReady = checkLockSound.isChecked && getSavedFile(FILE_LOCK_SOUND).exists()
        if (unlockSoundReady || lockSoundReady) {
            requestNotificationPermissionIfNeeded()
            startSoundService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPreview()
    }

    private fun apply() {
        var applied = false

        if (checkHome.isChecked) {
            val file = getSavedFile(FILE_HOME)
            if (file.exists()) {
                applyWallpaper(file, WallpaperManager.FLAG_SYSTEM)
                applied = true
            } else {
                Toast.makeText(this, "Selecione uma imagem para a tela inicial", Toast.LENGTH_SHORT).show()
            }
        }

        if (checkLock.isChecked) {
            val file = getSavedFile(FILE_LOCK)
            if (file.exists()) {
                applyWallpaper(file, WallpaperManager.FLAG_LOCK)
                applied = true
            } else {
                Toast.makeText(this, "Selecione uma imagem para a tela de bloqueio", Toast.LENGTH_SHORT).show()
            }
        }

        if (checkSound.isChecked) {
            val file = getSavedFile(FILE_SOUND)
            if (file.exists()) {
                requestNotificationPermissionIfNeeded()
                startSoundService()
                applied = true
            } else {
                Toast.makeText(this, "Selecione um arquivo de som", Toast.LENGTH_SHORT).show()
            }
        }

        if (checkLockSound.isChecked) {
            val file = getSavedFile(FILE_LOCK_SOUND)
            if (file.exists()) {
                requestNotificationPermissionIfNeeded()
                startSoundService()
                applied = true
            } else {
                Toast.makeText(this, "Selecione um arquivo de som para bloqueio", Toast.LENGTH_SHORT).show()
            }
        }

        if (!checkSound.isChecked && !checkLockSound.isChecked) {
            stopSoundService()
        }

        if (applied) {
            Toast.makeText(this, "Configurações aplicadas!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyWallpaper(file: File, flag: Int) {
        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                val wm = WallpaperManager.getInstance(this)
                wm.setBitmap(bitmap, null, true, flag)
                bitmap.recycle()
            }
        } catch (e: IOException) {
            val label = if (flag == WallpaperManager.FLAG_SYSTEM) "home" else "lock"
            Toast.makeText(this, "Erro ao aplicar wallpaper $label: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadHomePreview() {
        val file = getSavedFile(FILE_HOME)
        if (file.exists()) {
            previewHome.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        } else {
            previewHome.setImageResource(R.drawable.placeholder_image)
        }
    }

    private fun loadLockPreview() {
        val file = getSavedFile(FILE_LOCK)
        if (file.exists()) {
            previewLock.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
        } else {
            previewLock.setImageResource(R.drawable.placeholder_image)
        }
    }

    private fun updateSoundLabel() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val name = prefs.getString(KEY_SOUND_NAME, null)
        val file = getSavedFile(FILE_SOUND)
        txtSoundFile.text = if (file.exists() && name != null) name else getString(R.string.no_sound_selected)
    }

    private fun updateLockSoundLabel() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val name = prefs.getString(KEY_LOCK_SOUND_NAME, null)
        val file = getSavedFile(FILE_LOCK_SOUND)
        txtLockSoundFile.text = if (file.exists() && name != null) name else getString(R.string.no_sound_selected)
    }

    private fun startPreview(fileName: String, toggleButton: Button) {
        stopPreview()

        val file = getSavedFile(fileName)
        if (!file.exists()) {
            Toast.makeText(this, "Nenhum som selecionado", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            previewPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    it.release()
                    previewPlayer = null
                    previewingFile = null
                    toggleButton.text = getString(R.string.btn_play_sound)
                }
                start()
            }
            previewingFile = fileName
            toggleButton.text = getString(R.string.btn_stop_sound)
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao reproduzir som: ${e.message}", Toast.LENGTH_SHORT).show()
            stopPreview()
        }
    }

    private fun stopPreview() {
        try {
            previewPlayer?.release()
        } catch (_: Exception) {}
        previewPlayer = null
        previewingFile = null
        findViewById<Button>(R.id.btn_preview_sound)?.text = getString(R.string.btn_play_sound)
        findViewById<Button>(R.id.btn_preview_lock_sound)?.text = getString(R.string.btn_play_sound)
    }

    private fun saveFileFromUri(uri: Uri, fileName: String) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val outFile = getSavedFile(fileName)
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao salvar arquivo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return it.getString(idx)
            }
        }
        return uri.lastPathSegment
    }

    private fun getSavedFile(name: String): File = File(filesDir, name)

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
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
        const val KEY_HOME_ENABLED = "home_enabled"
        const val KEY_LOCK_ENABLED = "lock_enabled"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_SOUND_NAME = "sound_name"
        const val KEY_LOCK_SOUND_ENABLED = "lock_sound_enabled"
        const val KEY_LOCK_SOUND_NAME = "lock_sound_name"
        const val FILE_HOME = "user_home.jpg"
        const val FILE_LOCK = "user_lock.jpg"
        const val FILE_SOUND = "user_sound.mp3"
        const val FILE_LOCK_SOUND = "user_lock_sound.mp3"
    }
}
