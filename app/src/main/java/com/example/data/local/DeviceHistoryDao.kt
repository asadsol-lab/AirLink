package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceHistoryDao {
    @Query("SELECT * FROM connected_devices ORDER BY isFavorite DESC, lastConnectedAt DESC")
    fun observeRecentDevices(): Flow<List<ConnectedDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDevice(device: ConnectedDeviceEntity)

    @Query("UPDATE connected_devices SET isFavorite = NOT isFavorite WHERE deviceId = :deviceId")
    suspend fun toggleFavorite(deviceId: String)

    @Query("DELETE FROM connected_devices WHERE deviceId = :deviceId")
    suspend fun deleteDevice(deviceId: String)

    @Query("DELETE FROM connected_devices")
    suspend fun clearHistory()
}
