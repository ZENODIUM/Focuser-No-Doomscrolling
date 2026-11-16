package com.vizeno.focuser.blocker

import android.content.Context
import java.time.LocalDate

class BlockPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var blockReels: Boolean
        get() = prefs.getBoolean(KEY_REELS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_REELS, value).apply()
        }

    var blockShorts: Boolean
        get() = prefs.getBoolean(KEY_SHORTS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHORTS, value).apply()
        }

    var blockInstagram: Boolean
        get() = prefs.getBoolean(KEY_INSTAGRAM, false)
        set(value) {
            prefs.edit().putBoolean(KEY_INSTAGRAM, value).apply()
        }

    var blockYouTube: Boolean
        get() = prefs.getBoolean(KEY_YOUTUBE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_YOUTUBE, value).apply()
        }

    var greyscale: Boolean
        get() = prefs.getBoolean(KEY_GREYSCALE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_GREYSCALE, value).apply()
        }

    val limitToday: Int
        get() {
            ensureToday()
            return prefs.getInt(KEY_LIMIT_TODAY, 0).coerceIn(0, MAX_LIMIT_MINUTES)
        }

    val limitTomorrow: Int
        get() {
            ensureToday()
            return if (prefs.contains(KEY_PENDING_LIMIT)) {
                prefs.getInt(KEY_PENDING_LIMIT, 0).coerceIn(0, MAX_LIMIT_MINUTES)
            } else {
                limitToday
            }
        }

    fun scheduleTomorrow(minutes: Int) {
        ensureToday()
        prefs.edit()
            .putInt(KEY_PENDING_LIMIT, minutes.coerceIn(0, MAX_LIMIT_MINUTES))
            .apply()
    }

    fun usedMillis(packageName: String): Long {
        ensureToday()
        return usage()[bucket(packageName)] ?: 0L
    }

    fun addUsed(packageName: String, deltaMs: Long) {
        if (deltaMs <= 0L) return
        ensureToday()
        val usage = usage().toMutableMap()
        val key = bucket(packageName)
        usage[key] = (usage[key] ?: 0L) + deltaMs
        writeUsage(usage)
    }

    private fun bucket(packageName: String): String {
        return when {
            packageName == TIKTOK || packageName == TIKTOK_LITE || packageName == TIKTOK_ASIA -> TIKTOK
            packageName in BROWSERS -> "browser"
            else -> packageName
        }
    }

    fun ensureToday() {
        val today = LocalDate.now().toString()
        val storedDay = prefs.getString(KEY_DAY, null)
        if (storedDay == today && prefs.contains(KEY_LIMIT_TODAY)) return
        val editor = prefs.edit()
        if (!prefs.contains(KEY_LIMIT_TODAY)) {
            val existing = prefs.getInt(KEY_LIMIT_MINUTES, 0).coerceIn(0, MAX_LIMIT_MINUTES)
            editor.putInt(KEY_LIMIT_TODAY, existing)
        } else if (storedDay != null && storedDay != today) {
            val next = if (prefs.contains(KEY_PENDING_LIMIT)) {
                prefs.getInt(KEY_PENDING_LIMIT, 0)
            } else {
                prefs.getInt(KEY_LIMIT_TODAY, 0)
            }
            editor.putInt(KEY_LIMIT_TODAY, next.coerceIn(0, MAX_LIMIT_MINUTES))
            editor.remove(KEY_PENDING_LIMIT)
            editor.remove(KEY_USED)
        }
        editor.putString(KEY_DAY, today).apply()
    }

    private fun usage(): Map<String, Long> {
        val raw = prefs.getString(KEY_USED, null).orEmpty()
        if (raw.isEmpty()) return emptyMap()
        return raw.split(';').mapNotNull { entry ->
            val pieces = entry.split('=')
            val name = pieces.getOrNull(0) ?: return@mapNotNull null
            val millis = pieces.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            name to millis
        }.toMap()
    }

    private fun writeUsage(usage: Map<String, Long>) {
        val raw = usage.entries.joinToString(";") { "${it.key}=${it.value}" }
        prefs.edit().putString(KEY_USED, raw).apply()
    }

    companion object {
        const val NAME = "focuser"
        private const val KEY_REELS = "block_reels"
        private const val KEY_SHORTS = "block_shorts"
        private const val KEY_INSTAGRAM = "block_instagram"
        private const val KEY_YOUTUBE = "block_youtube"
        private const val KEY_GREYSCALE = "greyscale"
        private const val KEY_LIMIT_MINUTES = "kill_switch_minutes"
        private const val KEY_LIMIT_TODAY = "limit_today"
        private const val KEY_PENDING_LIMIT = "limit_pending"
        private const val KEY_DAY = "limit_day"
        private const val KEY_USED = "limit_used"
        const val MAX_LIMIT_MINUTES = 240

        const val INSTAGRAM = "com.instagram.android"
        const val YOUTUBE = "com.google.android.youtube"
        const val TIKTOK = "com.zhiliaoapp.musically"
        const val TIKTOK_LITE = "com.zhiliaoapp.musically.go"
        const val TIKTOK_ASIA = "com.ss.android.ugc.trill"

        val BROWSERS = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.focus",
            "com.microsoft.emmx",
            "com.sec.android.app.sbrowser",
            "com.brave.browser",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.duckduckgo.mobile.android",
            "com.vivaldi.browser",
            "com.kiwibrowser.browser",
            "com.android.browser",
            "mark.via.gp",
        )

        fun isGated(packageName: String): Boolean {
            return packageName == INSTAGRAM ||
                packageName == YOUTUBE ||
                packageName == TIKTOK ||
                packageName == TIKTOK_LITE ||
                packageName == TIKTOK_ASIA
        }

        fun isCapped(packageName: String): Boolean {
            return isGated(packageName) || packageName in BROWSERS
        }

        fun appLabel(packageName: String): String {
            return when (packageName) {
                INSTAGRAM -> "Instagram"
                YOUTUBE -> "YouTube"
                TIKTOK, TIKTOK_LITE, TIKTOK_ASIA -> "TikTok"
                else -> "Browser"
            }
        }
    }
}
