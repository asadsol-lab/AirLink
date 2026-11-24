package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.DevicePlatform

@Entity(tableName = "connected_devices")
data class ConnectedDeviceEntity(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val platform: String = DevicePlatform.WINDOWS.wireValue,
    val lastPin: String = "",
    val lastConnectedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
