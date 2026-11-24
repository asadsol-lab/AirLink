package com.example.model

import androidx.annotation.Keep
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@Keep
@IgnoreExtraProperties
data class CloudDevice(
    @DocumentId
    val deviceId: String = "",
    val deviceName: String = "",
    val platform: String = "android",
    val online: Boolean = true,
    @ServerTimestamp
    val lastSeen: Date? = null,
    val controlMode: String = "full_control" // "view_only" or "full_control"
)
