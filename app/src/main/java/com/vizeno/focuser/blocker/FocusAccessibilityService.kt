package com.vizeno.focuser.blocker

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Closes Instagram, YouTube, or TikTok when a switch or the day's limit says so.
 * Opening those apps waits a few seconds and asks for a session length.
 * Reels and Shorts still close on their own. Browsers share the daily limit.
 */
class FocusAccessibilityService : AccessibilityService() {
    private val prefs by lazy { BlockPreferences(this) }
    private val greyscale by lazy { DisplayGreyscale(this) }
    private val gate by lazy { FocusGate(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var polling = false
    private var watchingGreyscale = false
    private var lastScan = 0L
    private var lastAction = 0L
    private var timingPackage: String? = null
    private var timingMark = 0L
    private var visitPackage: String? = null
    private var visitAllowed = false
    private var sessionLimitMs = 0L
    private var sessionUsedMs = 0L

    private val poll = Runnable { scanAndSchedule() }
    private val greyscaleWatch = Runnable { watchGreyscale() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = SystemClock.uptimeMillis()
        if (polling && now - lastScan < POLL_MS) return
        scanAndSchedule()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        polling = false
        watchingGreyscale = false
        gate.hide()
        greyscale.restore()
        super.onDestroy()
    }

    private fun scanAndSchedule() {
        val keepWatching = try {
            scan()
        } catch (error: Throwable) {
            Log.e(TAG, "scan failed", error)
            false
        }
        if (keepWatching) {
            if (!polling) polling = true
            handler.removeCallbacks(poll)
            handler.postDelayed(poll, POLL_MS)
        } else {
            polling = false
            handler.removeCallbacks(poll)
        }
    }

    private fun scan(): Boolean {
        lastScan = SystemClock.uptimeMillis()
        prefs.ensureToday()
        val root = rootInActiveWindow
        if (root == null) {
            if (!gate.isShowing) leaveWatchedApp()
            return gate.isShowing
        }
        try {
            val packageName = root.packageName?.toString()
            if (packageName == null) {
                if (!gate.isShowing) leaveWatchedApp()
                return gate.isShowing
            }
            if (gate.isShowing && packageName == this.packageName) {
                updateGreyscale(visitPackage)
                return true
            }
            updateGreyscale(packageName)
            if (!BlockPreferences.isCapped(packageName)) {
                leaveWatchedApp()
                return false
            }
            val now = SystemClock.uptimeMillis()
            val interactive = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
            if (!interactive) {
                timingMark = now
                return true
            }
            if (packageName != timingPackage) {
                leaveVisit()
                timingPackage = packageName
                timingMark = now
            }
            if (now - lastAction < ACTION_COOLDOWN_MS) return true
            if (closeImmediately(packageName, root)) {
                leaveVisit()
                return true
            }
            if (overDailyLimit(packageName)) {
                leaveVisit()
                closeApp(BlockPreferences.appLabel(packageName))
                return true
            }
            if (BlockPreferences.isGated(packageName)) {
                runGatedVisit(packageName, root, now)
                return true
            }
            if (prefs.limitToday <= 0) return false
            countBrowser(packageName, now)
            return true
        } finally {
            root.recycle()
        }
    }

    private fun runGatedVisit(packageName: String, root: AccessibilityNodeInfo, now: Long) {
        if (visitPackage != packageName) {
            visitPackage = packageName
            visitAllowed = false
            sessionUsedMs = 0L
            sessionLimitMs = 0L
        }
        if (!visitAllowed) {
            timingMark = now
            if (!gate.isShowing) showOpenGate(packageName)
            return
        }
        val delta = now - timingMark
        timingMark = now
        if (delta in 1..MAX_TICK_MS) {
            prefs.addUsed(packageName, delta)
            sessionUsedMs += delta
        }
        if (overDailyLimit(packageName)) {
            leaveVisit()
            closeApp(BlockPreferences.appLabel(packageName))
            return
        }
        if (sessionLimitMs > 0L && sessionUsedMs >= sessionLimitMs) {
            visitAllowed = false
            if (!gate.isShowing) showExtendGate(packageName)
            return
        }
        if (packageName == BlockPreferences.INSTAGRAM && prefs.blockReels && isReelsOpen(root)) {
            leaveVisit()
            closeApp("Instagram")
        } else         if (packageName == BlockPreferences.YOUTUBE && prefs.blockShorts && shortsOpen(root)) {
            leaveVisit()
            closeApp("YouTube")
        }
    }

    private fun countBrowser(packageName: String, now: Long) {
        if (prefs.limitToday <= 0) return
        val delta = now - timingMark
        timingMark = now
        if (delta in 1..MAX_TICK_MS) prefs.addUsed(packageName, delta)
        if (overDailyLimit(packageName)) closeApp("Browser")
    }

    private fun closeImmediately(packageName: String, root: AccessibilityNodeInfo): Boolean {
        return when (packageName) {
            BlockPreferences.INSTAGRAM ->
                if (prefs.blockInstagram || (prefs.blockReels && isReelsOpen(root))) {
                    closeApp("Instagram")
                } else {
                    false
                }
            BlockPreferences.YOUTUBE ->
                if (prefs.blockYouTube || (prefs.blockShorts && shortsOpen(root))) {
                    closeApp("YouTube")
                } else {
                    false
                }
            else -> false
        }
    }

    private fun overDailyLimit(packageName: String): Boolean {
        val limitMs = prefs.limitToday * 60_000L
        return limitMs > 0L && prefs.usedMillis(packageName) >= limitMs
    }

    private fun showOpenGate(packageName: String) {
        val choices = sessionChoices(packageName)
        val label = BlockPreferences.appLabel(packageName)
        if (choices.isEmpty()) {
            closeApp(label)
            leaveVisit()
            return
        }
        gate.show(
            title = label,
            message = "Pick a session, then wait.",
            choices = choices,
            extend = false,
            onClose = {
                if (closeApp(label)) leaveVisit()
            },
            onContinue = { minutes ->
                sessionLimitMs = minutes * 60_000L
                sessionUsedMs = 0L
                visitAllowed = true
                timingMark = SystemClock.uptimeMillis()
                gate.hide()
            },
        )
    }

    private fun showExtendGate(packageName: String) {
        val choices = sessionChoices(packageName)
        val label = BlockPreferences.appLabel(packageName)
        if (choices.isEmpty()) {
            closeApp(label)
            leaveVisit()
            return
        }
        gate.show(
            title = label,
            message = "That session is over. Pick another, then hold to stay.",
            choices = choices,
            extend = true,
            onClose = {
                if (closeApp(label)) leaveVisit()
            },
            onContinue = { minutes ->
                sessionLimitMs = minutes * 60_000L
                sessionUsedMs = 0L
                visitAllowed = true
                timingMark = SystemClock.uptimeMillis()
                gate.hide()
            },
        )
    }

    private fun sessionChoices(packageName: String): List<Int> {
        val limit = prefs.limitToday
        if (limit <= 0) return SESSION_MINUTES
        val remainingMs = limit * 60_000L - prefs.usedMillis(packageName)
        if (remainingMs < 60_000L) return emptyList()
        val remainingMin = (remainingMs / 60_000L).toInt()
        val allowed = SESSION_MINUTES.filter { it <= remainingMin }
        return allowed.ifEmpty { listOf(remainingMin) }
    }

    private fun leaveWatchedApp() {
        leaveVisit()
        timingPackage = null
    }

    private fun leaveVisit() {
        visitPackage = null
        visitAllowed = false
        sessionUsedMs = 0L
        sessionLimitMs = 0L
        gate.hide()
    }

    private fun isReelsOpen(root: AccessibilityNodeInfo): Boolean {
        val reelsTab = nodeSelected(root, IG_CLIPS_TAB)
        val homeTab = nodeSelected(root, IG_FEED_TAB)
        val playerOpen = hasVisibleView(root, IG_PLAYER_IDS)
        // Instagram keeps the Reels page in its pager, so the home feed is not Reels.
        if (homeTab && !reelsTab) return false
        return reelsTab || playerOpen
    }

    private fun updateGreyscale(packageName: String?) {
        val inTarget = packageName == BlockPreferences.INSTAGRAM || packageName == BlockPreferences.YOUTUBE
        if (prefs.greyscale && inTarget) {
            greyscale.applyIfNeeded()
            if (!watchingGreyscale) {
                watchingGreyscale = true
                handler.postDelayed(greyscaleWatch, GREYSCALE_WATCH_MS)
            }
        } else {
            greyscale.restore()
            watchingGreyscale = false
            handler.removeCallbacks(greyscaleWatch)
        }
    }

    private fun watchGreyscale() {
        watchingGreyscale = false
        val root = rootInActiveWindow
        val packageName = root?.packageName?.toString()
        root?.recycle()
        val visible = if (gate.isShowing && packageName == this.packageName) visitPackage else packageName
        updateGreyscale(visible)
    }

    private fun shortsOpen(root: AccessibilityNodeInfo): Boolean {
        val shortsTab = youtubeTabSelected(root, "Shorts")
        val playerOpen = hasOpenPlayer(root, YT_PLAYER_IDS)
        return shortsTab || playerOpen
    }

    private fun closeApp(name: String): Boolean {
        if (!performGlobalAction(GLOBAL_ACTION_HOME)) return false
        lastAction = SystemClock.uptimeMillis()
        Log.i(TAG, "closed $name")
        return true
    }

    private fun youtubeTabSelected(root: AccessibilityNodeInfo, tab: String): Boolean {
        val bars = root.findAccessibilityNodeInfosByViewId(YT_PIVOT_BAR)
        try {
            return bars.any { bar ->
                val match = findNode(bar) { node ->
                    label(node).equals(tab, ignoreCase = true) && (node.isSelected || node.isChecked)
                }
                val selected = match != null
                match?.recycle()
                selected
            }
        } finally {
            bars.forEach { it.recycle() }
        }
    }

    private fun nodeSelected(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        try {
            return nodes.any { subtreeSelected(it) }
        } finally {
            nodes.forEach { it.recycle() }
        }
    }

    private fun hasOpenPlayer(root: AccessibilityNodeInfo, viewIds: List<String>): Boolean {
        val screenHeight = resources.displayMetrics.heightPixels
        return viewIds.any { viewId ->
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            val open = nodes.any { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                node.isVisibleToUser || bounds.height() > screenHeight / 3
            }
            nodes.forEach { it.recycle() }
            open
        }
    }

    private fun hasVisibleView(root: AccessibilityNodeInfo, viewIds: List<String>): Boolean {
        return viewIds.any { viewId ->
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            val visible = nodes.any { it.isVisibleToUser }
            nodes.forEach { it.recycle() }
            visible
        }
    }

    private fun subtreeSelected(node: AccessibilityNodeInfo, depth: Int = 0): Boolean {
        if (node.isSelected || node.isChecked) return true
        if (depth >= 4) return false
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                if (subtreeSelected(child, depth + 1)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun findNode(
        node: AccessibilityNodeInfo,
        depth: Int = 0,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return AccessibilityNodeInfo.obtain(node)
        if (depth >= 8) return null
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                val found = findNode(child, depth + 1, predicate)
                if (found != null) return found
            } finally {
                child.recycle()
            }
        }
        return null
    }

    private fun label(node: AccessibilityNodeInfo): String {
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        val raw = description.ifEmpty { node.text?.toString()?.trim().orEmpty() }
        return raw.substringBefore(',').substringBefore('\n').trim()
    }

    companion object {
        private const val TAG = "Focuser"
        private const val POLL_MS = 600L
        private const val GREYSCALE_WATCH_MS = 400L
        private const val ACTION_COOLDOWN_MS = 900L
        private const val MAX_TICK_MS = 15_000L
        private val SESSION_MINUTES = listOf(5, 10, 15, 30)

        private const val IG_CLIPS_TAB = "com.instagram.android:id/clips_tab"
        private const val IG_FEED_TAB = "com.instagram.android:id/feed_tab"
        private val IG_PLAYER_IDS = listOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_viewer_container",
            "com.instagram.android:id/clips_video_container",
            "com.instagram.android:id/clips_viewer_action_bar",
        )

        private const val YT_PIVOT_BAR = "com.google.android.youtube:id/pivot_bar"
        private val YT_PLAYER_IDS = listOf(
            "com.google.android.youtube:id/reel_watch_fragment_root",
            "com.google.android.youtube:id/reel_player_page_container",
        )
    }
}
