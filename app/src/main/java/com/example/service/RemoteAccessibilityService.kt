package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.roundToLong

class RemoteAccessibilityService : AccessibilityService() {

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        @Volatile
        var instance: RemoteAccessibilityService? = null
            private set
    }

    private var activeDragStroke: GestureDescription.StrokeDescription? = null
    private var lastDragPxX = 0f
    private var lastDragPxY = 0f
    private var dragBusy = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isRunning.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // On-demand node focus lookup only when text_input arrives.
    }

    override fun onInterrupt() {
        resetDragState()
    }

    override fun onDestroy() {
        super.onDestroy()
        resetDragState()
        if (instance === this) {
            instance = null
        }
        _isRunning.value = false
    }

    private fun resetDragState() {
        activeDragStroke = null
        dragBusy = false
    }

    private fun getScreenSizePx(): Pair<Float, Float> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width().toFloat() to bounds.height().toFloat()
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
            metrics.widthPixels.toFloat() to metrics.heightPixels.toFloat()
        }
    }

    private fun toPixelCoords(normX: Float, normY: Float): Pair<Float, Float> {
        val (w, h) = getScreenSizePx()
        val px = (normX.coerceIn(0f, 1f) * w).coerceIn(1f, (w - 1f).coerceAtLeast(1f))
        val py = (normY.coerceIn(0f, 1f) * h).coerceIn(1f, (h - 1f).coerceAtLeast(1f))
        return px to py
    }

    fun performNormalizedClick(normX: Float, normY: Float, isLongPress: Boolean = false) {
        val (px, py) = toPixelCoords(normX, normY)
        val path = Path().apply { moveTo(px, py) }
        val duration = if (isLongPress) 650L else 45L
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun startContinuousDrag(normX: Float, normY: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val (px, py) = toPixelCoords(normX, normY)
        lastDragPxX = px
        lastDragPxY = py
        val path = Path().apply { moveTo(px, py) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 40L, true)
        activeDragStroke = stroke
        dragBusy = true
        dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    dragBusy = false
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    resetDragState()
                }
            },
            null
        )
    }

    fun moveContinuousDrag(normX: Float, normY: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val prev = activeDragStroke ?: return
        if (dragBusy) return
        val (px, py) = toPixelCoords(normX, normY)
        if (abs(px - lastDragPxX) < 1.5f && abs(py - lastDragPxY) < 1.5f) return

        val path = Path().apply {
            moveTo(lastDragPxX, lastDragPxY)
            lineTo(px, py)
        }
        lastDragPxX = px
        lastDragPxY = py
        val nextStroke = prev.continueStroke(path, 0L, 35L, true)
        activeDragStroke = nextStroke
        dragBusy = true
        dispatchGesture(
            GestureDescription.Builder().addStroke(nextStroke).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    dragBusy = false
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    resetDragState()
                }
            },
            null
        )
    }

    fun endContinuousDrag(normX: Float, normY: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val prev = activeDragStroke ?: return
        val (px, py) = toPixelCoords(normX, normY)
        val path = Path().apply {
            moveTo(lastDragPxX, lastDragPxY)
            lineTo(px, py)
        }
        val finalStroke = prev.continueStroke(path, 0L, 30L, false)
        activeDragStroke = null
        dragBusy = false
        dispatchGesture(GestureDescription.Builder().addStroke(finalStroke).build(), null, null)
    }

    fun performNormalizedSwipe(
        startNormX: Float,
        startNormY: Float,
        endNormX: Float,
        endNormY: Float,
        durationMs: Long = 220L
    ) {
        val (sx, sy) = toPixelCoords(startNormX, startNormY)
        val (ex, ey) = toPixelCoords(endNormX, endNormY)

        if (abs(ex - sx) < 2f && abs(ey - sy) < 2f) {
            performNormalizedClick(startNormX, startNormY)
            return
        }

        val path = Path().apply {
            moveTo(sx, sy)
            lineTo(ex, ey)
        }
        val safeDuration = durationMs.coerceIn(50L, 1500L)
        val stroke = GestureDescription.StrokeDescription(path, 0L, safeDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun performNormalizedScroll(
        normX: Float,
        normY: Float,
        deltaX: Float,
        deltaY: Float
    ) {
        // Positive deltaY = scroll content up (swipe finger down); negative deltaY = scroll content down (swipe finger up)
        val stepY = (deltaY * 0.14f).coerceIn(-0.35f, 0.35f)
        val stepX = (deltaX * 0.14f).coerceIn(-0.35f, 0.35f)
        val endX = (normX + stepX).coerceIn(0.02f, 0.98f)
        val endY = (normY + stepY).coerceIn(0.02f, 0.98f)
        val duration = (110f + abs(stepY) * 260f).roundToLong().coerceIn(80L, 380L)
        performNormalizedSwipe(normX, normY, endX, endY, duration)
    }

    fun injectTextToFocusedNode(text: String): Boolean {
        if (text.isEmpty()) return false
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false

        val existing = if (focused.isEditable && focused.text != null && focused.hintText != focused.text) {
            focused.text.toString()
        } else {
            ""
        }

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, existing + text)
        }
        val setOk = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (setOk) return true

        // Fallback via Clipboard + ACTION_PASTE
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("AirLink Input", text))
        return focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    fun sendBackspaceToFocusedNode(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val existing = focused.text?.toString() ?: return false
        if (existing.isEmpty() || focused.hintText?.toString() == existing) return false

        val updated = existing.dropLast(1)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun sendEnterToFocusedNode(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) {
                return true
            }
        }
        return injectTextToFocusedNode("\n")
    }

    fun executeSystemAction(action: String): Boolean {
        return when (action.lowercase()) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "lock" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            } else {
                false
            }
            else -> false
        }
    }
}
