package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ConnectedDeviceEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AirLinkDatabase : RoomDatabase() {
    abstract fun deviceHistoryDao(): DeviceHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AirLinkDatabase? = null

        fun getInstance(context: Context): AirLinkDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AirLinkDatabase::class.java,
                    "airlink_devices.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
