package com.example.model

import org.json.JSONArray
import org.json.JSONObject

sealed class RemoteInputPacket {
    abstract fun toJson(): String

    data class MouseMove(
        val x: Float,
        val y: Float
    ) : RemoteInputPacket() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "mouse_move")
            put("x", x.coerceIn(0f, 1f).toDouble())
            put("y", y.coerceIn(0f, 1f).toDouble())
        }.toString()
    }

    data class MouseClick(
        val button: String, // "left" | "right" | "middle"
        val action: String, // "down" | "up" | "click"
        val x: Float = -1f,
        val y: Float = -1f
    ) : RemoteInputPacket() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "mouse_click")
            put("button", button)
            put("action", action)
            if (x >= 0f && y >= 0f) {
                put("x", x.coerceIn(0f, 1f).toDouble())
                put("y", y.coerceIn(0f, 1f).toDouble())
            }
        }.toString()
    }

    data class MouseScroll(
        val deltaX: Float,
        val deltaY: Float,
        val x: Float = 0.5f,
        val y: Float = 0.5f
    ) : RemoteInputPacket() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "mouse_scroll")
            put("deltaX", deltaX.toDouble())
            put("deltaY", deltaY.toDouble())
            put("x", x.coerceIn(0f, 1f).toDouble())
            put("y", y.coerceIn(0f, 1f).toDouble())
        }.toString()
    }

    data class KeyPress(
        val keyCode: Int,
        val keyName: String = "",
        val modifiers: List<String> = emptyList(),
        val action: String = "press"
    ) : RemoteInputPacket() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "key_press")
            put("keyCode", keyCode)
            put("keyName", keyName)
            put("modifiers", JSONArray(modifiers))
            put("action", action)
        }.toString()
    }

    data class TextInput(
        val text: String
    ) : RemoteInputPacket() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "text_input")
            put("text", text)
        }.toString()
    }

    data class TouchTap(
        val x: Float,
        val y: Float,
        val isLongPress: Boolean = false
    ) : RemoteInputPacket() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "touch_tap")
            put("x", x.coerceIn(0f, 1f).toDouble())
            put("y", y.coerceIn(0f, 1f).toDouble())
            put("isLongPress", isLongPress)
        }.toString()
    }

    data class TouchSwipe(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val durationMs: Long = 220L
    ) : RemoteInputPacket() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "touch_swipe")
            put("startX", startX.coerceIn(0f, 1f).toDouble())
            put("startY", startY.coerceIn(0f, 1f).toDouble())
            put("endX", endX.coerceIn(0f, 1f).toDouble())
            put("endY", endY.coerceIn(0f, 1f).toDouble())
            put("durationMs", durationMs.coerceIn(40L, 1500L))
        }.toString()
    }

    data class SystemAction(
        val action: String // "back" | "home" | "recents" | "notifications"
    ) : RemoteInputPacket() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "system_action")
            put("action", action)
        }.toString()
    }

    data class QualityChange(
        val preset: String
    ) : RemoteInputPacket() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "quality_change")
            put("preset", preset)
        }.toString()
    }

    companion object {
        fun fromJson(raw: String): RemoteInputPacket? {
            return try {
                val obj = JSONObject(raw)
                when (obj.optString("type")) {
                    "mouse_move" -> MouseMove(
                        x = obj.getDouble("x").toFloat(),
                        y = obj.getDouble("y").toFloat()
                    )
                    "mouse_click" -> MouseClick(
                        button = obj.optString("button", "left"),
                        action = obj.optString("action", "click"),
                        x = if (obj.has("x")) obj.getDouble("x").toFloat() else -1f,
                        y = if (obj.has("y")) obj.getDouble("y").toFloat() else -1f
                    )
                    "mouse_scroll" -> MouseScroll(
                        deltaX = obj.optDouble("deltaX", 0.0).toFloat(),
                        deltaY = obj.optDouble("deltaY", 0.0).toFloat(),
                        x = obj.optDouble("x", 0.5).toFloat(),
                        y = obj.optDouble("y", 0.5).toFloat()
                    )
                    "key_press" -> {
                        val modArray = obj.optJSONArray("modifiers")
                        val modifiers = buildList {
                            if (modArray != null) {
                                for (i in 0 until modArray.length()) {
                                    add(modArray.getString(i))
                                }
                            }
                        }
                        KeyPress(
                            keyCode = obj.optInt("keyCode", 0),
                            keyName = obj.optString("keyName", ""),
                            modifiers = modifiers,
                            action = obj.optString("action", "press")
                        )
                    }
                    "text_input" -> TextInput(
                        text = obj.optString("text", "")
                    )
                    "touch_tap" -> TouchTap(
                        x = obj.getDouble("x").toFloat(),
                        y = obj.getDouble("y").toFloat(),
                        isLongPress = obj.optBoolean("isLongPress", false)
                    )
                    "touch_swipe" -> TouchSwipe(
                        startX = obj.getDouble("startX").toFloat(),
                        startY = obj.getDouble("startY").toFloat(),
                        endX = obj.getDouble("endX").toFloat(),
                        endY = obj.getDouble("endY").toFloat(),
                        durationMs = obj.optLong("durationMs", 220L)
                    )
                    "system_action" -> SystemAction(
                        action = obj.optString("action", "back")
                    )
                    "quality_change" -> QualityChange(
                        preset = obj.optString("preset", StreamQuality.HD_720_60.name)
                    )
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
