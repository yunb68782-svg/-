package com.example.paperengine

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.paperengine.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val selectVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleImportedVideo(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        refreshStatus()
    }

    private fun setupUI() {
        binding.btnPickVideo.setOnClickListener {
            selectVideoLauncher.launch("video/*")
        }

        binding.btnApplyWallpaper.setOnClickListener {
            val currentPath = WallpaperSettings.getWallpaperPath(this)
            if (currentPath.isBlank()) {
                Toast.makeText(this, "请先导入一个视频壁纸！", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, PaperWallpaperService::class.java)
                    )
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "未能直接唤起系统壁纸页，请在系统壁纸中手动选择 牛逼壁纸工具", Toast.LENGTH_LONG).show()
            }
        }

        binding.switchMute.isChecked = WallpaperSettings.isMuted(this)
        binding.switchMute.setOnCheckedChangeListener { _, isChecked ->
            WallpaperSettings.setMuted(this, isChecked)
        }

        binding.switchDoubleTap.isChecked = WallpaperSettings.isDoubleTapEnabled(this)
        binding.switchDoubleTap.setOnCheckedChangeListener { _, isChecked ->
            WallpaperSettings.setDoubleTapEnabled(this, isChecked)
        }

        when (WallpaperSettings.getSpeed(this)) {
            0.75f -> binding.rbSpeed075.isChecked = true
            1.25f -> binding.rbSpeed125.isChecked = true
            else -> binding.rbSpeed10.isChecked = true
        }

        binding.rgSpeed.setOnCheckedChangeListener { _, checkedId ->
            val speed = when (checkedId) {
                R.id.rbSpeed075 -> 0.75f
                R.id.rbSpeed125 -> 1.25f
                else -> 1.0f
            }
            WallpaperSettings.setSpeed(this, speed)
        }
    }

    private fun handleImportedVideo(sourceUri: Uri) {
        try {
            val storageDir = File(filesDir, "wallpapers")
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }

            val targetFile = File(storageDir, "active_wallpaper.mp4")
            contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            WallpaperSettings.setWallpaperPath(this, targetFile.absolutePath)
            Toast.makeText(this, "壁纸导入成功！", Toast.LENGTH_SHORT).show()
            refreshStatus()

        } catch (e: Exception) {
            Toast.makeText(this, "视频导入失败: " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshStatus() {
        val path = WallpaperSettings.getWallpaperPath(this)
        if (path.isNotBlank() && File(path).exists()) {
            val file = File(path)
            val sizeMb = file.length() / (1024 * 1024)
            binding.tvCurrentWallpaper.text = "已就绪: active_wallpaper.mp4 (" + sizeMb + " MB)"
            binding.tvCurrentWallpaper.setTextColor(resources.getColor(R.color.accent_green, null))
        } else {
            binding.tvCurrentWallpaper.text = getString(R.string.no_wallpaper_selected)
            binding.tvCurrentWallpaper.setTextColor(resources.getColor(R.color.text_secondary, null))
        }
    }
}
