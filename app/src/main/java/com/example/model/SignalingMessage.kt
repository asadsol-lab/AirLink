package com.example.model

import org.json.JSONObject

enum class DevicePlatform(val wireValue: String, val label: String) {
    WINDOWS("windows", "Windows PC"),
    ANDROID("android", "Android Device");

    companion object {
        fun fromWire(value: String?): DevicePlatform =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: WINDOWS
    }
}

sealed class SignalingMessage {
    abstract fun toJson(): String

    data class Register(
        val deviceId: String,
        val deviceName: String,
        val platform: DevicePlatform = DevicePlatform.ANDROID,
        val pin: String
    ) : SignalingMessage() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "register")
            put("deviceId", deviceId)
            put("deviceName", deviceName)
            put("platform", platform.wireValue)
            put("pin", pin)
        }.toString()
    }

    data class Registered(
        val deviceId: String
    ) : SignalingMessage() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "registered")
            put("deviceId", deviceId)
        }.toString()
    }

    data class CallRequest(
        val targetId: String,
        val senderId: String = "",
        val callerName: String,
        val callerPlatform: DevicePlatform = DevicePlatform.ANDROID,
        val pin: String,
        val controlDirection: String = "control_target"
    ) : SignalingMessage() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "call_request")
            put("targetId", targetId)
            put("senderId", senderId)
            put("callerName", callerName)
            put("callerPlatform", callerPlatform.wireValue)
            put("pin", pin)
            put("controlDirection", controlDirection)
        }.toString()
    }

    data class CallAccept(
        val targetId: String,
        val senderId: String = "",
        val accepterName: String,
        val accepterPlatform: DevicePlatform = DevicePlatform.ANDROID
    ) : SignalingMessage() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "call_accept")
            put("targetId", targetId)
            put("senderId", senderId)
            put("accepterName", accepterName)
            put("accepterPlatform", accepterPlatform.wireValue)
        }.toString()
    }

    data class CallReject(
        val targetId: String,
        val senderId: String = "",
        val reason: String
    ) : SignalingMessage() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "call_reject")
            put("targetId", targetId)
            put("senderId", senderId)
            put("reason", reason)
        }.toString()
    }

    data class Offer(
        val targetId: String,
        val senderId: String = "",
        val sdp: String,
        val sdpType: String = "offer"
    ) : SignalingMessage() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "offer")
            put("targetId", targetId)
            put("senderId", senderId)
            put("offer", JSONObject().apply {
                put("type", sdpType)
                put("sdp", sdp)
            })
        }.toString()
    }

    data class Answer(
        val targetId: String,
        val senderId: String = "",
        val sdp: String,
        val sdpType: String = "answer"
    ) : SignalingMessage() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "answer")
            put("targetId", targetId)
            put("senderId", senderId)
            put("answer", JSONObject().apply {
                put("type", sdpType)
                put("sdp", sdp)
            })
        }.toString()
    }

    data class IceCandidateMessage(
        val targetId: String,
        val senderId: String = "",
        val candidate: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int
    ) : SignalingMessage() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "candidate")
            put("targetId", targetId)
            put("senderId", senderId)
            put("candidate", JSONObject().apply {
                put("candidate", candidate)
                put("sdpMid", sdpMid ?: "")
                put("sdpMLineIndex", sdpMLineIndex)
            })
        }.toString()
    }

    data class Disconnect(
        val targetId: String,
        val senderId: String = "",
        val reason: String = "Session ended"
    ) : SignalingMessage() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "disconnect")
            put("targetId", targetId)
            put("senderId", senderId)
            put("reason", reason)
        }.toString()
    }

    data class ErrorMessage(
        val message: String
    ) : SignalingMessage() {
        override fun toJson(): String = JSONObject().apply {
            put("type", "error")
            put("message", message)
        }.toString()
    }

    companion object {
        fun fromJson(raw: String): SignalingMessage? {
            return try {
                val obj = JSONObject(raw)
                when (obj.optString("type")) {
                    "registered" -> Registered(
                        deviceId = obj.optString("deviceId")
                    )
                    "call_request" -> CallRequest(
                        targetId = obj.optString("targetId"),
                        senderId = obj.optString("senderId"),
                        callerName = obj.optString("callerName", "Remote Device"),
                        callerPlatform = DevicePlatform.fromWire(obj.optString("callerPlatform")),
                        pin = obj.optString("pin"),
                        controlDirection = obj.optString("controlDirection", "control_target")
                    )
                    "call_accept" -> CallAccept(
                        targetId = obj.optString("targetId"),
                        senderId = obj.optString("senderId"),
                        accepterName = obj.optString("accepterName", "Remote Host"),
                        accepterPlatform = DevicePlatform.fromWire(obj.optString("accepterPlatform"))
                    )
                    "call_reject" -> CallReject(
                        targetId = obj.optString("targetId"),
                        senderId = obj.optString("senderId"),
                        reason = obj.optString("reason", "Connection declined")
                    )
                    "offer" -> {
                        val offerObj = obj.getJSONObject("offer")
                        Offer(
                            targetId = obj.optString("targetId"),
                            senderId = obj.optString("senderId"),
                            sdp = offerObj.getString("sdp"),
                            sdpType = offerObj.optString("type", "offer")
                        )
                    }
                    "answer" -> {
                        val answerObj = obj.getJSONObject("answer")
                        Answer(
                            targetId = obj.optString("targetId"),
                            senderId = obj.optString("senderId"),
                            sdp = answerObj.getString("sdp"),
                            sdpType = answerObj.optString("type", "answer")
                        )
                    }
                    "candidate" -> {
                        val candObj = obj.getJSONObject("candidate")
                        IceCandidateMessage(
                            targetId = obj.optString("targetId"),
                            senderId = obj.optString("senderId"),
                            candidate = candObj.getString("candidate"),
                            sdpMid = candObj.optString("sdpMid").takeIf { it.isNotEmpty() },
                            sdpMLineIndex = candObj.optInt("sdpMLineIndex", 0)
                        )
                    }
                    "disconnect" -> Disconnect(
                        targetId = obj.optString("targetId"),
                        senderId = obj.optString("senderId"),
                        reason = obj.optString("reason", "Remote peer disconnected")
                    )
                    "error" -> ErrorMessage(
                        message = obj.optString("message", "Unknown signaling error")
                    )
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
