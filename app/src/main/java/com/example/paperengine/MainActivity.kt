package com.example.paperengine

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.SeekBar
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
                Toast.makeText(this, "请先选一个壁纸！", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "未能直接唤起，请在系统壁纸里手动选择 牛逼壁纸工具", Toast.LENGTH_LONG).show()
            }
        }

        // 自由缩放调节 (0.5x ~ 3.0x)
        val currentScale = WallpaperSettings.getScale(this)
        val progressScale = ((currentScale - 0.5f) / 2.5f * 250).toInt()
        binding.sbScale.progress = progressScale
        binding.tvScaleValue.text = (currentScale * 100).toInt().toString() + "%"

        binding.sbScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = 0.5f + (progress / 250.0f) * 2.5f
                binding.tvScaleValue.text = (scale * 100).toInt().toString() + "%"
                WallpaperSettings.setScale(this@MainActivity, scale)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                Toast.makeText(this@MainActivity, "缩放已更新，回到桌面即生效！", Toast.LENGTH_SHORT).show()
            }
        })

        // 水平左右位移
        val currentOffX = WallpaperSettings.getOffsetX(this)
        binding.sbOffsetX.progress = ((currentOffX + 1.0f) / 2.0f * 200).toInt()
        updateOffsetXText(currentOffX)

        binding.sbOffsetX.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val offX = (progress / 200.0f) * 2.0f - 1.0f
                updateOffsetXText(offX)
                WallpaperSettings.setOffsetX(this@MainActivity, offX)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // 垂直上下位移
        val currentOffY = WallpaperSettings.getOffsetY(this)
        binding.sbOffsetY.progress = ((currentOffY + 1.0f) / 2.0f * 200).toInt()
        updateOffsetYText(currentOffY)

        binding.sbOffsetY.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val offY = (progress / 200.0f) * 2.0f - 1.0f
                updateOffsetYText(offY)
                WallpaperSettings.setOffsetY(this@MainActivity, offY)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // 旋转 90 度
        binding.btnRotate.setOnClickListener {
            val nextRot = (WallpaperSettings.getRotation(this) + 90) % 360
            WallpaperSettings.setRotation(this, nextRot)
            Toast.makeText(this, "已旋转至 " + nextRot + "°", Toast.LENGTH_SHORT).show()
        }

        // 一键恢复居中
        binding.btnResetTransform.setOnClickListener {
            WallpaperSettings.setScale(this, 1.0f)
            WallpaperSettings.setOffsetX(this, 0.0f)
            WallpaperSettings.setOffsetY(this, 0.0f)
            WallpaperSettings.setRotation(this, 0)
            binding.sbScale.progress = 50
            binding.sbOffsetX.progress = 100
            binding.sbOffsetY.progress = 100
            binding.tvScaleValue.text = "100%"
            binding.tvOffsetXValue.text = "居中 (0)"
            binding.tvOffsetYValue.text = "居中 (0)"
            Toast.makeText(this, "已重置为默认居中！", Toast.LENGTH_SHORT).show()
        }

        // 120FPS 内置预设导入
        binding.btnPreset1.setOnClickListener { loadPreset("preset_matrix.mp4", "赛博矩阵 120F") }
        binding.btnPreset2.setOnClickListener { loadPreset("preset_neon.mp4", "霓虹几何 120F") }
        binding.btnPreset3.setOnClickListener { loadPreset("preset_quantum.mp4", "量子核心 120F") }
        binding.btnPreset4.setOnClickListener { loadPreset("preset_space.mp4", "深空视界 120F") }

        // 静音
        binding.switchMute.isChecked = WallpaperSettings.isMuted(this)
        binding.switchMute.setOnCheckedChangeListener { _, isChecked ->
            WallpaperSettings.setMuted(this, isChecked)
        }
    }

    private fun updateOffsetXText(v: Float) {
        val percent = (v * 100).toInt()
        binding.tvOffsetXValue.text = if (percent == 0) "居中 (0)" else (if (percent > 0) "右移 " else "左移 ") + kotlin.math.abs(percent) + "%"
    }

    private fun updateOffsetYText(v: Float) {
        val percent = (v * 100).toInt()
        binding.tvOffsetYValue.text = if (percent == 0) "居中 (0)" else (if (percent > 0) "上移 " else "下移 ") + kotlin.math.abs(percent) + "%"
    }

    private fun loadPreset(assetName: String, label: String) {
        try {
            val storageDir = File(filesDir, "wallpapers")
            if (!storageDir.exists()) storageDir.mkdirs()
            val target = File(storageDir, assetName)
            assets.open("presets/" + assetName).use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            WallpaperSettings.setWallpaperPath(this, target.absolutePath)
            Toast.makeText(this, "已装载: " + label, Toast.LENGTH_SHORT).show()
            refreshStatus()
        } catch (e: Exception) {
            Toast.makeText(this, "加载失败: " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleImportedVideo(sourceUri: Uri) {
        try {
            val storageDir = File(filesDir, "wallpapers")
            if (!storageDir.exists()) storageDir.mkdirs()

            val targetFile = File(storageDir, "custom_wallpaper.mp4")
            contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            WallpaperSettings.setWallpaperPath(this, targetFile.absolutePath)
            Toast.makeText(this, "视频导入成功！", Toast.LENGTH_SHORT).show()
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
            binding.tvCurrentWallpaper.text = "已装载: " + file.name + " (" + sizeMb + " MB)"
            binding.tvCurrentWallpaper.setTextColor(resources.getColor(R.color.accent_cyan, null))
        } else {
            binding.tvCurrentWallpaper.text = getString(R.string.no_wallpaper_selected)
            binding.tvCurrentWallpaper.setTextColor(resources.getColor(R.color.text_secondary, null))
        }
    }
}
