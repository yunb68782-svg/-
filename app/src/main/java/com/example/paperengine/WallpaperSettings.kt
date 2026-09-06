package com.example.paperengine

import android.content.Context
import android.content.SharedPreferences

object WallpaperSettings {
    private const val PREF_NAME = "paper_engine_prefs"
    private const val KEY_WALLPAPER_PATH = "key_wallpaper_path"
    private const val KEY_IS_MUTED = "key_is_muted"
    private const val KEY_SPEED = "key_playback_speed"
    private const val KEY_SCALE = "key_scale_factor"
    private const val KEY_OFFSET_X = "key_offset_x"
    private const val KEY_OFFSET_Y = "key_offset_y"
    private const val KEY_ROTATION = "key_rotation_deg"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun setWallpaperPath(context: Context, path: String) {
        getPrefs(context).edit().putString(KEY_WALLPAPER_PATH, path).apply()
    }

    fun getWallpaperPath(context: Context): String {
        return getPrefs(context).getString(KEY_WALLPAPER_PATH, "") ?: ""
    }

    fun setMuted(context: Context, muted: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_IS_MUTED, muted).apply()
    }

    fun isMuted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_MUTED, true)
    }

    fun setSpeed(context: Context, speed: Float) {
        getPrefs(context).edit().putFloat(KEY_SPEED, speed).apply()
    }

    fun getSpeed(context: Context): Float {
        return getPrefs(context).getFloat(KEY_SPEED, 1.0f)
    }

    fun setScale(context: Context, scale: Float) {
        getPrefs(context).edit().putFloat(KEY_SCALE, scale).apply()
    }

    fun getScale(context: Context): Float {
        return getPrefs(context).getFloat(KEY_SCALE, 1.0f)
    }

    fun setOffsetX(context: Context, x: Float) {
        getPrefs(context).edit().putFloat(KEY_OFFSET_X, x).apply()
    }

    fun getOffsetX(context: Context): Float {
        return getPrefs(context).getFloat(KEY_OFFSET_X, 0.0f)
    }

    fun setOffsetY(context: Context, y: Float) {
        getPrefs(context).edit().putFloat(KEY_OFFSET_Y, y).apply()
    }

    fun getOffsetY(context: Context): Float {
        return getPrefs(context).getFloat(KEY_OFFSET_Y, 0.0f)
    }

    fun setRotation(context: Context, deg: Int) {
        getPrefs(context).edit().putInt(KEY_ROTATION, deg).apply()
    }

    fun getRotation(context: Context): Int {
        return getPrefs(context).getInt(KEY_ROTATION, 0)
    }
}
