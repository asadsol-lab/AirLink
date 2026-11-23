package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.model.RemoteInputPacket

private data class SpecialKey(
    val label: String,
    val keyCode: Int
)

private val SPECIAL_KEYS = listOf(
    SpecialKey("Esc", 27),
    SpecialKey("Tab", 9),
    SpecialKey("Win", 91),
    SpecialKey("Space", 32),
    SpecialKey("Enter", 13),
    SpecialKey("Bksp", 8),
    SpecialKey("Del", 46),
    SpecialKey("↑", 38),
    SpecialKey("↓", 40),
    SpecialKey("←", 37),
    SpecialKey("→", 39),
    SpecialKey("F4", 115),
    SpecialKey("F5", 116),
    SpecialKey("F11", 122)
)

private fun charToWindowsVk(ch: Char): Int? {
    val upper = ch.uppercaseChar()
    return when {
        upper in 'A'..'Z' -> upper.code
        upper in '0'..'9' -> upper.code
        ch == ' ' -> 32
        ch == '\n' -> 13
        ch == '\t' -> 9
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VirtualKeyboardSheet(
    onSendPacket: (RemoteInputPacket) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var shiftActive by remember { mutableStateOf(false) }
    var textBuffer by remember { mutableStateOf("") }

    fun activeModifiers(): List<String> = buildList {
        if (ctrlActive) add("ctrl")
        if (altActive) add("alt")
        if (shiftActive) add("shift")
    }

    fun emitTypedText(input: String) {
        val mods = activeModifiers()
        if (ctrlActive || altActive) {
            input.forEach { ch ->
                val vk = charToWindowsVk(ch)
                if (vk != null) {
                    onSendPacket(
                        RemoteInputPacket.KeyPress(
                            keyCode = vk,
                            keyName = ch.toString(),
                            modifiers = mods,
                            action = "press"
                        )
                    )
                }
            }
        } else {
            onSendPacket(RemoteInputPacket.TextInput(input))
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Remote Keyboard & Shortcuts",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onClose, modifier = Modifier.testTag("close_keyboard_button")) {
                    Icon(Icons.Filled.Close, contentDescription = "Close virtual keyboard")
                }
            }

            // Modifier & Shortcut toggles: Ctrl / Alt / Shift / Ctrl+Alt+Del / Ctrl+Alt+End
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = ctrlActive,
                    onClick = { ctrlActive = !ctrlActive },
                    label = { Text("Ctrl") },
                    modifier = Modifier.testTag("mod_ctrl_chip")
                )
                FilterChip(
                    selected = altActive,
                    onClick = { altActive = !altActive },
                    label = { Text("Alt") },
                    modifier = Modifier.testTag("mod_alt_chip")
                )
                FilterChip(
                    selected = shiftActive,
                    onClick = { shiftActive = !shiftActive },
                    label = { Text("Shift") },
                    modifier = Modifier.testTag("mod_shift_chip")
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        onSendPacket(
                            RemoteInputPacket.KeyPress(
                                keyCode = 46,
                                keyName = "Delete",
                                modifiers = listOf("ctrl", "alt"),
                                action = "press"
                            )
                        )
                    },
                    label = { Text("Ctrl+Alt+Del") }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        onSendPacket(
                            RemoteInputPacket.KeyPress(
                                keyCode = 35, // VK_END
                                keyName = "End",
                                modifiers = listOf("ctrl", "alt"),
                                action = "press"
                            )
                        )
                    },
                    label = { Text("Ctrl+Alt+End") }
                )
            }

            // Special keys grid
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SPECIAL_KEYS.forEach { special ->
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .clickable {
                                onSendPacket(
                                    RemoteInputPacket.KeyPress(
                                        keyCode = special.keyCode,
                                        keyName = special.label,
                                        modifiers = activeModifiers(),
                                        action = "press"
                                    )
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = special.label,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Text input line that dispatches layout-safe Unicode text or Ctrl/Alt shortcuts
            OutlinedTextField(
                value = textBuffer,
                onValueChange = { updated ->
                    if (updated.length > textBuffer.length) {
                        val added = updated.substring(textBuffer.length)
                        emitTypedText(added)
                    } else if (updated.length < textBuffer.length) {
                        val removedCount = textBuffer.length - updated.length
                        repeat(removedCount) {
                            onSendPacket(
                                RemoteInputPacket.KeyPress(
                                    keyCode = 8,
                                    keyName = "Backspace",
                                    modifiers = activeModifiers(),
                                    action = "press"
                                )
                            )
                        }
                    }
                    textBuffer = updated
                },
                label = { Text("Type text to send directly") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        onSendPacket(
                            RemoteInputPacket.KeyPress(
                                keyCode = 13,
                                keyName = "Enter",
                                modifiers = activeModifiers(),
                                action = "press"
                            )
                        )
                        textBuffer = ""
                    }
                ),
                leadingIcon = {
                    IconButton(
                        onClick = {
                            onSendPacket(
                                RemoteInputPacket.KeyPress(
                                    keyCode = 8,
                                    keyName = "Backspace",
                                    modifiers = activeModifiers(),
                                    action = "press"
                                )
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Send Backspace key"
                        )
                    }
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            onSendPacket(
                                RemoteInputPacket.KeyPress(
                                    keyCode = 13,
                                    keyName = "Enter",
                                    modifiers = activeModifiers(),
                                    action = "press"
                                )
                            )
                            textBuffer = ""
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Enter key"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("virtual_keyboard_text_input")
            )
        }
    }
}
