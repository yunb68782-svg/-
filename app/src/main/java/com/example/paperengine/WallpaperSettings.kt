package com.example.paperengine

import android.content.Context
import android.content.SharedPreferences

object WallpaperSettings {
    private const val PREF_NAME = "paper_engine_prefs"
    private const val KEY_WALLPAPER_PATH = "key_wallpaper_path"
    private const val KEY_IS_MUTED = "key_is_muted"
    private const val KEY_DOUBLE_TAP = "key_double_tap"
    private const val KEY_SPEED = "key_playback_speed"

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

    fun setDoubleTapEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DOUBLE_TAP, enabled).apply()
    }

    fun isDoubleTapEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DOUBLE_TAP, true)
    }

    fun setSpeed(context: Context, speed: Float) {
        getPrefs(context).edit().putFloat(KEY_SPEED, speed).apply()
    }

    fun getSpeed(context: Context): Float {
        return getPrefs(context).getFloat(KEY_SPEED, 1.0f)
    }
}
