package com.vizeno.focuser.blocker

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen pause shown over Instagram, YouTube, or TikTok.
 * Opening asks for a session length and waits a few seconds.
 * Extending a session takes a two-second hold.
 */
class FocusGate(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var secondsLeft = OPEN_WAIT_SECONDS
    private var selectedMinutes = 0
    private var holding = false
    private var holdRunnable: Runnable? = null

    val isShowing: Boolean
        get() = overlay != null

    fun show(
        title: String,
        message: String,
        choices: List<Int>,
        extend: Boolean,
        onClose: () -> Unit,
        onContinue: (minutes: Int) -> Unit,
    ) {
        hide()
        if (choices.isEmpty()) return
        selectedMinutes = choices.first()
        secondsLeft = OPEN_WAIT_SECONDS
        val density = service.resources.displayMetrics.density
        val pad = (28 * density).toInt()

        val titleView = TextView(service).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
        }
        val messageView = TextView(service).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, (8 * density).toInt(), 0, (20 * density).toInt())
        }
        val choiceRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val choiceButtons = choices.map { minutes ->
            Button(service).apply {
                text = "$minutes min"
                isAllCaps = false
                setOnClickListener {
                    selectedMinutes = minutes
                    paintChoices(choiceRow, selectedMinutes)
                }
            }
        }
        choiceButtons.forEach { button ->
            choiceRow.addView(
                button,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = (8 * density).toInt()
                },
            )
        }
        paintChoices(choiceRow, selectedMinutes)

        val status = TextView(service).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, (24 * density).toInt(), 0, (16 * density).toInt())
        }
        val closeButton = Button(service).apply {
            text = "Close"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = buttonBackground(Color.TRANSPARENT)
            setOnClickListener { onClose() }
        }
        val continueButton = Button(service).apply {
            isAllCaps = false
            setTextColor(Color.BLACK)
            background = buttonBackground(Color.WHITE)
        }

        if (extend) {
            status.text = "Hold Continue for 2 seconds"
            continueButton.text = "Hold to continue"
            continueButton.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        holding = true
                        status.text = "Keep holding"
                        val finish = Runnable {
                            if (holding) onContinue(selectedMinutes)
                        }
                        holdRunnable = finish
                        handler.postDelayed(finish, HOLD_MS)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        holding = false
                        holdRunnable?.let { handler.removeCallbacks(it) }
                        status.text = "Hold Continue for 2 seconds"
                        true
                    }
                    else -> false
                }
            }
        } else {
            continueButton.isEnabled = false
            continueButton.text = "Continue"
            status.text = "Continue in $secondsLeft s"
            val tick = object : Runnable {
                override fun run() {
                    if (overlay == null) return
                    secondsLeft -= 1
                    if (secondsLeft <= 0) {
                        status.text = "You can continue"
                        continueButton.isEnabled = true
                    } else {
                        status.text = "Continue in $secondsLeft s"
                        handler.postDelayed(this, 1_000L)
                    }
                }
            }
            continueButton.setOnClickListener {
                if (secondsLeft <= 0) onContinue(selectedMinutes)
            }
            handler.postDelayed(tick, 1_000L)
        }

        val actions = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(closeButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(continueButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val content = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.BLACK)
            addView(titleView)
            addView(messageView)
            addView(choiceRow)
            addView(status)
            addView(actions)
            gravity = Gravity.CENTER_VERTICAL
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP
        }
        overlay = content
        windowManager.addView(content, params)
    }

    fun hide() {
        holding = false
        handler.removeCallbacksAndMessages(null)
        overlay?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
                // The window was already removed.
            }
        }
        overlay = null
    }

    private fun paintChoices(row: LinearLayout, selected: Int) {
        for (index in 0 until row.childCount) {
            val button = row.getChildAt(index) as Button
            val minutes = button.text.toString().substringBefore(' ').toIntOrNull()
            val chosen = minutes == selected
            button.background = buttonBackground(if (chosen) Color.WHITE else Color.parseColor("#333333"))
            button.setTextColor(if (chosen) Color.BLACK else Color.WHITE)
        }
    }

    private fun buttonBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = 24f * service.resources.displayMetrics.density
        }
    }

    private companion object {
        const val OPEN_WAIT_SECONDS = 6
        const val HOLD_MS = 2_000L
    }
}
