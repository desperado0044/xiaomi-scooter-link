package com.scooterre.client.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * A small floating window over every other app (e.g. a navigation app) with the remaining range and the distance of
 * the ride. Drag it to move it; the position is remembered. Shown only while a scooter is connected (it is hosted by
 * [ConnectionService]), the "overlay" setting is on, the "display over other apps" permission is granted and the app
 * itself is not on screen.
 */
class OverlayController(private val context: Context, private val scope: CoroutineScope) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val prefs = context.getSharedPreferences("scooter_prefs", Context.MODE_PRIVATE)
    private var box: LinearLayout? = null
    private var restText: TextView? = null
    private var batteryText: TextView? = null
    private var tripText: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            combine(OverlayBus.enabled, OverlayBus.appVisible, OverlayBus.data) { enabled, appVisible, data ->
                Pair(enabled && !appVisible && Settings.canDrawOverlays(context), data)
            }.collect { (show, data) -> if (show) render(data) else remove() }
        }
    }

    fun stop() {
        job?.cancel()
        remove()
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics)

    private fun render(data: OverlayData) {
        if (box == null) create()
        restText?.text = if (data.standby) "Standby" else data.rest.ifEmpty { "–" }
        batteryText?.text = data.battery
        batteryText?.visibility = if (data.standby || data.battery.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        tripText?.text = data.trip
        tripText?.visibility = if (data.standby || data.trip.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun create() {
        val rest = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val battery = TextView(context).apply {
            setTextColor(Color.parseColor("#CCFFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        val trip = TextView(context).apply {
            setTextColor(Color.parseColor("#CCFFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(10f).toInt()
            setPadding(pad, dp(6f).toInt(), pad, dp(6f).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#B3000000"))
                cornerRadius = dp(12f)
            }
            addView(rest)
            addView(battery)
            addView(trip)
        }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_X, dp(16f).toInt())
            y = prefs.getInt(KEY_Y, dp(160f).toInt())
        }
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        var moved = false
        // How far a touch may wander and still count as a tap (opens the app) rather than a drag - the platform's
        // own slop constant, so it matches what every other view on the phone considers "still just a tap".
        val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        layout.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = p.x; startY = p.y; downX = e.rawX; downY = e.rawY; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(e.rawX - downX) > touchSlop || kotlin.math.abs(e.rawY - downY) > touchSlop) moved = true
                    if (moved) {
                        p.x = startX + (e.rawX - downX).toInt()
                        p.y = startY + (e.rawY - downY).toInt()
                        windowManager.updateViewLayout(v, p)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        prefs.edit().putInt(KEY_X, p.x).putInt(KEY_Y, p.y).apply()
                    } else {
                        // A real tap, not a drag: bring the app back to the foreground.
                        try {
                            context.startActivity(
                                android.content.Intent(context, com.scooterre.client.MainActivity::class.java)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP),
                            )
                        } catch (ex: Exception) {
                            android.util.Log.w(TAG, "could not open the app from the overlay", ex)
                        }
                    }
                    true
                }
                else -> false
            }
        }
        try {
            windowManager.addView(layout, p)
            box = layout
            restText = rest
            batteryText = battery
            tripText = trip
            params = p
        } catch (e: Exception) {
            android.util.Log.w(TAG, "overlay could not be shown", e)
        }
    }

    private fun remove() {
        box?.let { try { windowManager.removeView(it) } catch (e: Exception) { /* already gone */ } }
        box = null
        restText = null
        batteryText = null
        tripText = null
        params = null
    }

    private companion object {
        const val TAG = "OverlayController"
        const val KEY_X = "overlay_x"
        const val KEY_Y = "overlay_y"
    }
}
