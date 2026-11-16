package com.vizeno.focuser.blocker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * Turns the whole screen grey while Instagram or YouTube is open, then puts the
 * previous color setting back. Android only allows this with a one-time secure setting.
 */
class DisplayGreyscale(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val appContext = context.applicationContext
    private var applied = false
    private var previousEnabled = 0
    private var previousMode = DALTONIZER_DISABLED

    fun isPermitted(): Boolean {
        return appContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun applyIfNeeded() {
        if (applied || !isPermitted()) return
        try {
            previousEnabled = Settings.Secure.getInt(
                resolver,
                DALTONIZER_ENABLED,
                0,
            )
            previousMode = Settings.Secure.getInt(
                resolver,
                DALTONIZER,
                DALTONIZER_DISABLED,
            )
            Settings.Secure.putInt(
                resolver,
                DALTONIZER,
                DALTONIZER_GRAYSCALE,
            )
            Settings.Secure.putInt(
                resolver,
                DALTONIZER_ENABLED,
                1,
            )
            applied = true
            Log.i(TAG, "greyscale on")
        } catch (error: SecurityException) {
            Log.e(TAG, "greyscale permission missing", error)
        }
    }

    fun restore() {
        if (!applied) return
        try {
            Settings.Secure.putInt(resolver, DALTONIZER, previousMode)
            Settings.Secure.putInt(resolver, DALTONIZER_ENABLED, previousEnabled)
        } catch (error: SecurityException) {
            Log.e(TAG, "could not restore color", error)
        } finally {
            applied = false
            Log.i(TAG, "greyscale off")
        }
    }

    private companion object {
        const val TAG = "Focuser"
        const val DALTONIZER = "accessibility_display_daltonizer"
        const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        const val DALTONIZER_DISABLED = -1
        const val DALTONIZER_GRAYSCALE = 0
    }
}
