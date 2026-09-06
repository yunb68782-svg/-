package com.example.paperengine

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class PaperWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return OpenGlVideoWallpaperEngine()
    }

    inner class OpenGlVideoWallpaperEngine : Engine() {

        private var mediaPlayer: MediaPlayer? = null
        private var isSurfaceCreated = false
        private lateinit var gestureDetector: GestureDetector

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        private var textureId: Int = -1
        private var surfaceTexture: SurfaceTexture? = null
        private var renderSurface: Surface? = null

        private var program: Int = 0
        private var uMVPMatrixHandle: Int = 0
        private var uSTMatrixHandle: Int = 0
        private var aPositionHandle: Int = 0
        private var aTextureHandle: Int = 0

        private val mvpMatrix = FloatArray(16)
        private val stMatrix = FloatArray(16)

        private val vertexData = floatArrayOf(
            -1.0f, -1.0f, 0.0f,
             1.0f, -1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f,
             1.0f,  1.0f, 0.0f
        )

        private val textureData = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )

        private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(vertexData)
                position(0)
            }

        private val textureBuffer: FloatBuffer = ByteBuffer.allocateDirect(textureData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(textureData)
                position(0)
            }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)

            // 突破系统 60Hz 限制：向底层 SurfaceFlinger 强行申请 120Hz 刷新率
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    surfaceHolder.surface.setFrameRate(120.0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                } catch (e: Exception) {
                    Log.e("PaperEngine", "120Hz 帧率设置失败: " + e.message)
                }
            }

            gestureDetector = GestureDetector(applicationContext, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val muted = !WallpaperSettings.isMuted(applicationContext)
                    WallpaperSettings.setMuted(applicationContext, muted)
                    applyVolume()
                    return true
                }
            })
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            event?.let { gestureDetector.onTouchEvent(it) }
        }

        private fun applyVolume() {
            mediaPlayer?.let { player ->
                try {
                    if (WallpaperSettings.isMuted(applicationContext)) {
                        player.setVolume(0.0f, 0.0f)
                    } else {
                        player.setVolume(1.0f, 1.0f)
                    }
                } catch (e: Exception) {
                    Log.e("PaperEngine", "音量失败: " + e.message)
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                if (mediaPlayer == null) {
                    initEngine()
                } else {
                    try { mediaPlayer?.start() } catch (e: Exception) { initEngine() }
                }
            } else {
                try { mediaPlayer?.pause() } catch (e: Exception) { Log.e("PaperEngine", "暂停失败: " + e.message) }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            isSurfaceCreated = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    holder.surface.setFrameRate(120.0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                } catch (e: Exception) {}
            }
            initEngine()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            isSurfaceCreated = false
            releaseEngine()
        }

        private fun initEngine() {
            if (!isSurfaceCreated) return
            val path = WallpaperSettings.getWallpaperPath(applicationContext)
            if (path.isBlank() || !File(path).exists()) return

            initEGL()
            initGLShaders()
            initMediaPlayer(path)
        }

        private fun initEGL() {
            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)
            val config = configs[0]!!

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surfaceHolder, surfaceAttribs, 0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        }

        private fun initGLShaders() {
            val vertexShaderCode = """
                uniform mat4 uMVPMatrix;
                uniform mat4 uSTMatrix;
                attribute vec4 aPosition;
                attribute vec4 aTextureCoord;
                varying vec2 vTextureCoord;
                void main() {
                    gl_Position = uMVPMatrix * aPosition;
                    vTextureCoord = (uSTMatrix * aTextureCoord).xy;
                }
            """.trimIndent()

            val fragmentShaderCode = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform samplerExternalOES sTexture;
                void main() {
                    gl_FragColor = texture2D(sTexture, vTextureCoord);
                }
            """.trimIndent()

            val vShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
            program = GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, vShader)
                GLES20.glAttachShader(it, fShader)
                GLES20.glLinkProgram(it)
            }

            aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
            uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
            uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            surfaceTexture = SurfaceTexture(textureId).apply {
                setOnFrameAvailableListener {
                    if (isVisible) drawFrame()
                }
            }
            renderSurface = Surface(surfaceTexture)
        }

        private fun loadShader(type: Int, code: String): Int {
            return GLES20.glCreateShader(type).also { shader ->
                GLES20.glShaderSource(shader, code)
                GLES20.glCompileShader(shader)
            }
        }

        private fun drawFrame() {
            if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) return
            try {
                surfaceTexture?.updateTexImage()
                surfaceTexture?.getTransformMatrix(stMatrix)

                GLES20.glUseProgram(program)

                val scale = WallpaperSettings.getScale(applicationContext)
                val offX = WallpaperSettings.getOffsetX(applicationContext)
                val offY = WallpaperSettings.getOffsetY(applicationContext)
                val rot = WallpaperSettings.getRotation(applicationContext)

                Matrix.setIdentityM(mvpMatrix, 0)
                Matrix.translateM(mvpMatrix, 0, offX, offY, 0f)
                Matrix.rotateM(mvpMatrix, 0, rot.toFloat(), 0f, 0f, 1f)
                Matrix.scaleM(mvpMatrix, 0, scale, scale, 1f)

                GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
                GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, stMatrix, 0)

                GLES20.glEnableVertexAttribArray(aPositionHandle)
                GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

                GLES20.glEnableVertexAttribArray(aTextureHandle)
                GLES20.glVertexAttribPointer(aTextureHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer)

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)

            } catch (e: Exception) {
                Log.e("PaperEngine", "渲染异常: " + e.message)
            }
        }

        private fun initMediaPlayer(path: String) {
            releaseMediaPlayer()
            try {
                mediaPlayer = MediaPlayer().apply {
                    setSurface(renderSurface)
                    setDataSource(path)
                    isLooping = true

                    setOnCompletionListener { mp ->
                        try {
                            mp.seekTo(0)
                            mp.start()
                        } catch (e: Exception) {
                            initEngine()
                        }
                    }

                    setOnPreparedListener { mp ->
                        applyVolume()
                        if (isVisible) mp.start()
                    }

                    setOnErrorListener { _, _, _ ->
                        releaseEngine()
                        initEngine()
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
                } catch (e: Exception) {}
            }
            mediaPlayer = null
        }

        private fun releaseEngine() {
            releaseMediaPlayer()
            renderSurface?.release()
            renderSurface = null
            surfaceTexture?.release()
            surfaceTexture = null

            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
        }

        override fun onDestroy() {
            super.onDestroy()
            releaseEngine()
        }
    }
}
