package com.example.paperengine

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceHolder
import java.io.File

class PaperWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return VideoWallpaperEngine()
    }

    inner class VideoWallpaperEngine : Engine() {

        private var mediaPlayer: MediaPlayer? = null
        private var isSurfaceCreated = false
        private var currentMuted = true
        private lateinit var gestureDetector: GestureDetector

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            currentMuted = WallpaperSettings.isMuted(applicationContext)

            gestureDetector = GestureDetector(applicationContext, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (WallpaperSettings.isDoubleTapEnabled(applicationContext)) {
                        toggleMute()
                        return true
                    }
                    return false
                }
            })
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            if (event != null) {
                gestureDetector.onTouchEvent(event)
            }
        }

        private fun toggleMute() {
            currentMuted = !currentMuted
            WallpaperSettings.setMuted(applicationContext, currentMuted)
            applyVolume()
        }

        private fun applyVolume() {
            mediaPlayer?.let { player ->
                try {
                    if (currentMuted) {
                        player.setVolume(0.0f, 0.0f)
                    } else {
                        player.setVolume(1.0f, 1.0f)
                    }
                } catch (e: Exception) {
                    Log.e("PaperEngine", "调节音量失败: " + e.message)
                }
            }
        }

        private fun applySpeed() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mediaPlayer?.let { player ->
                    try {
                        val speed = WallpaperSettings.getSpeed(applicationContext)
                        val params = player.playbackParams
                        params.speed = speed
                        player.playbackParams = params
                    } catch (e: Exception) {
                        Log.e("PaperEngine", "设置倍速失败: " + e.message)
                    }
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                if (mediaPlayer == null) {
                    initMediaPlayer()
                } else {
                    try {
                        mediaPlayer?.start()
                    } catch (e: Exception) {
                        initMediaPlayer()
                    }
                }
            } else {
                try {
                    mediaPlayer?.pause()
                } catch (e: Exception) {
                    Log.e("PaperEngine", "休眠暂停失败: " + e.message)
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            isSurfaceCreated = true
            initMediaPlayer()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            isSurfaceCreated = false
            releaseMediaPlayer()
        }

        private fun initMediaPlayer() {
            val videoPath = WallpaperSettings.getWallpaperPath(applicationContext)
            if (videoPath.isBlank() || !isSurfaceCreated) {
                return
            }

            val videoFile = File(videoPath)
            if (!videoFile.exists() || videoFile.length() == 0L) {
                return
            }

            releaseMediaPlayer()

            try {
                mediaPlayer = MediaPlayer().apply {
                    setDisplay(surfaceHolder)
                    setDataSource(videoFile.absolutePath)
                    isLooping = true

                    setOnCompletionListener { mp ->
                        try {
                            mp.seekTo(0)
                            mp.start()
                        } catch (ex: Exception) {
                            initMediaPlayer()
                        }
                    }

                    setOnPreparedListener { mp ->
                        applyVolume()
                        applySpeed()
                        if (isVisible) {
                            mp.start()
                        }
                    }

                    setOnErrorListener { _, _, _ ->
                        releaseMediaPlayer()
                        initMediaPlayer()
                        true
                    }

                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e("PaperEngine", "播放器初始化异常: " + e.message)
            }
        }

        private fun releaseMediaPlayer() {
            mediaPlayer?.let { player ->
                try {
                    player.stop()
                    player.reset()
                    player.release()
                } catch (e: Exception) {
                    Log.e("PaperEngine", "释放播放器出错: " + e.message)
                }
            }
            mediaPlayer = null
        }

        override fun onDestroy() {
            super.onDestroy()
            releaseMediaPlayer()
        }
    }
}
