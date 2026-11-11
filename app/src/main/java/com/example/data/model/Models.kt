package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.ModeFanOff
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents a single relay on the 4-channel ESP32-S3 relay board.
 * Note: Firebase schema strictly requires boolean values at /devices/relayN.
 * User customizable metadata (name, room, icon) is persisted locally.
 */
data class Relay(
    val key: String, // "relay1", "relay2", "relay3", "relay4"
    val defaultName: String, // "Light 1", "Light 2", "Light 3", "Light 4"
    val customName: String? = null,
    val room: String = "Living Room",
    val iconKey: String = "lightbulb",
    val isOn: Boolean = false,
    val isPending: Boolean = false,
    val lastUpdatedEpochMs: Long = System.currentTimeMillis(),
    val timerRemainingSeconds: Int? = null
) {
    val displayName: String
        get() = if (!customName.isNullOrBlank()) customName else defaultName

    val firebasePath: String
        get() = "/devices/$key"

    fun getIcon(): ImageVector {
        return when (iconKey) {
            "lightbulb" -> Icons.Default.Lightbulb
            "lamp" -> Icons.Default.Highlight
            "bed" -> Icons.Default.Bed
            "kitchen" -> Icons.Default.Kitchen
            "room" -> Icons.Default.MeetingRoom
            "fan" -> Icons.Default.ModeFanOff
            "tv" -> Icons.Default.Tv
            "socket" -> Icons.Default.Power
            else -> Icons.Default.Lightbulb
        }
    }
}

/**
 * Represents a toggle event in the local activity log.
 */
data class ActivityLogItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val relayKey: String,
    val relayName: String,
    val newState: Boolean,
    val source: String, // "App Tap", "Wall Switch / ESP32", "Master Control", "Schedule Timer"
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Firebase Realtime Database connection & session state.
 */
data class ConnectionInfo(
    val isConnected: Boolean = false,
    val isAuthenticating: Boolean = false,
    val authUid: String? = null,
    val errorMessage: String? = null,
    val lastSyncEpochMs: Long = 0L,
    val activeProtocol: String = "Firebase Realtime DB (WebSocket)"
)

/**
 * Quick preset modes.
 */
enum class QuickPreset(val title: String, val subtitle: String) {
    ALL_ON("All On", "Power on all 4 channels"),
    ALL_OFF("All Off", "Shut down all channels"),
    EVENING_LOUNGE("Evening Lounge", "Relay 1 & Relay 3 On"),
    NIGHT_SECURITY("Night Safety", "Relay 2 & Relay 4 On")
}
