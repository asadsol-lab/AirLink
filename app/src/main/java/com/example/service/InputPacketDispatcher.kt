package com.example.service

import android.os.Build
import com.example.model.RemoteInputPacket
import com.example.model.StreamQuality

class InputPacketDispatcher(
    private val onQualityChangeRequested: ((StreamQuality) -> Unit)? = null
) {

    private var cursorX = 0.5f
    private var cursorY = 0.5f
    private var dragStartX = 0.5f
    private var dragStartY = 0.5f
    private var isLeftDown = false
    private var hasMovedWhileDown = false

    fun dispatchRawPacket(rawJson: String): Boolean {
        val packet = RemoteInputPacket.fromJson(rawJson) ?: return false

        if (packet is RemoteInputPacket.QualityChange) {
            val quality = StreamQuality.entries.firstOrNull { it.name == packet.preset } ?: return false
            onQualityChangeRequested?.invoke(quality)
            return true
        }

        val service = RemoteAccessibilityService.instance ?: return false

        when (packet) {
            is RemoteInputPacket.MouseMove -> {
                cursorX = packet.x.coerceIn(0f, 1f)
                cursorY = packet.y.coerceIn(0f, 1f)
                if (isLeftDown) {
                    hasMovedWhileDown = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        service.moveContinuousDrag(cursorX, cursorY)
                    }
                }
            }

            is RemoteInputPacket.MouseClick -> {
                val targetX = if (packet.x >= 0f) packet.x.also { cursorX = it } else cursorX
                val targetY = if (packet.y >= 0f) packet.y.also { cursorY = it } else cursorY

                when (packet.action.lowercase()) {
                    "down" -> {
                        if (packet.button.equals("left", ignoreCase = true)) {
                            isLeftDown = true
                            hasMovedWhileDown = false
                            dragStartX = targetX
                            dragStartY = targetY
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                service.startContinuousDrag(targetX, targetY)
                            }
                        }
                    }

                    "up" -> {
                        if (packet.button.equals("left", ignoreCase = true)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                service.endContinuousDrag(targetX, targetY)
                            } else if (isLeftDown && hasMovedWhileDown) {
                                service.performNormalizedSwipe(
                                    dragStartX,
                                    dragStartY,
                                    targetX,
                                    targetY,
                                    durationMs = 200L
                                )
                            } else {
                                service.performNormalizedClick(targetX, targetY, isLongPress = false)
                            }
                            isLeftDown = false
                            hasMovedWhileDown = false
                        } else if (packet.button.equals("right", ignoreCase = true)) {
                            service.performNormalizedClick(targetX, targetY, isLongPress = true)
                        }
                    }

                    else -> {
                        val isRight = packet.button.equals("right", ignoreCase = true)
                        service.performNormalizedClick(targetX, targetY, isLongPress = isRight)
                    }
                }
            }

            is RemoteInputPacket.MouseScroll -> {
                service.performNormalizedScroll(
                    normX = packet.x,
                    normY = packet.y,
                    deltaX = packet.deltaX,
                    deltaY = packet.deltaY
                )
            }

            is RemoteInputPacket.TouchTap -> {
                service.performNormalizedClick(packet.x, packet.y, packet.isLongPress)
            }

            is RemoteInputPacket.TouchSwipe -> {
                service.performNormalizedSwipe(
                    packet.startX,
                    packet.startY,
                    packet.endX,
                    packet.endY,
                    packet.durationMs
                )
            }

            is RemoteInputPacket.SystemAction -> {
                service.executeSystemAction(packet.action)
            }

            is RemoteInputPacket.TextInput -> {
                service.injectTextToFocusedNode(packet.text)
            }

            is RemoteInputPacket.KeyPress -> {
                when (packet.keyCode) {
                    27 -> service.executeSystemAction("back")
                    36 -> service.executeSystemAction("home")
                    8 -> service.sendBackspaceToFocusedNode()
                    13 -> service.sendEnterToFocusedNode()
                    else -> {
                        val hasControlModifier = packet.modifiers.any {
                            it.equals("ctrl", ignoreCase = true) || it.equals("alt", ignoreCase = true)
                        }
                        if (!hasControlModifier && packet.keyName.length == 1) {
                            service.injectTextToFocusedNode(packet.keyName)
                        }
                    }
                }
            }

            is RemoteInputPacket.QualityChange -> Unit
        }
        return true
    }
}
