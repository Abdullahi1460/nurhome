package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ActivityLogItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nur_home_prefs")

class UserPreferencesRepository(private val context: Context) {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val activityListType = Types.newParameterizedType(List::class.java, ActivityLogItemDto::class.java)
    private val activityAdapter = moshi.adapter<List<ActivityLogItemDto>>(activityListType)

    data class ActivityLogItemDto(
        val id: String,
        val relayKey: String,
        val relayName: String,
        val newState: Boolean,
        val source: String,
        val timestampMs: Long
    )

    data class RelayCustomMeta(
        val customName: String?,
        val room: String,
        val iconKey: String
    )

    // Key helpers
    private fun nameKey(key: String) = stringPreferencesKey("relay_${key}_name")
    private fun roomKey(key: String) = stringPreferencesKey("relay_${key}_room")
    private fun iconKey(key: String) = stringPreferencesKey("relay_${key}_icon")
    private val darkModeKey = booleanPreferencesKey("app_dark_mode")
    private val activityLogKey = stringPreferencesKey("activity_logs_json")

    fun getRelayMeta(key: String): Flow<RelayCustomMeta> {
        val defaultRoom = when (key) {
            "relay1" -> "Living Room"
            "relay2" -> "Master Bedroom"
            "relay3" -> "Kitchen"
            "relay4" -> "Balcony / Hall"
            else -> "Living Room"
        }
        return context.dataStore.data.map { prefs ->
            RelayCustomMeta(
                customName = prefs[nameKey(key)],
                room = prefs[roomKey(key)] ?: defaultRoom,
                iconKey = prefs[iconKey(key)] ?: "lightbulb"
            )
        }
    }

    suspend fun saveRelayMeta(key: String, name: String?, room: String, icon: String) {
        context.dataStore.edit { prefs ->
            if (name.isNullOrBlank()) {
                prefs.remove(nameKey(key))
            } else {
                prefs[nameKey(key)] = name
            }
            prefs[roomKey(key)] = room
            prefs[iconKey(key)] = icon
        }
    }

    val isDarkMode: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        prefs[darkModeKey]
    }

    suspend fun setDarkMode(enabled: Boolean?) {
        context.dataStore.edit { prefs ->
            if (enabled == null) {
                prefs.remove(darkModeKey)
            } else {
                prefs[darkModeKey] = enabled
            }
        }
    }

    val activityLogs: Flow<List<ActivityLogItem>> = context.dataStore.data.map { prefs ->
        val json = prefs[activityLogKey] ?: "[]"
        try {
            val dtoList = activityAdapter.fromJson(json) ?: emptyList()
            dtoList.map { dto ->
                ActivityLogItem(
                    id = dto.id,
                    relayKey = dto.relayKey,
                    relayName = dto.relayName,
                    newState = dto.newState,
                    source = dto.source,
                    timestampMs = dto.timestampMs
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addActivityLog(item: ActivityLogItem) {
        context.dataStore.edit { prefs ->
            val json = prefs[activityLogKey] ?: "[]"
            val existing = try {
                activityAdapter.fromJson(json)?.toMutableList() ?: mutableListOf()
            } catch (_: Exception) {
                mutableListOf()
            }
            val dto = ActivityLogItemDto(
                id = item.id,
                relayKey = item.relayKey,
                relayName = item.relayName,
                newState = item.newState,
                source = item.source,
                timestampMs = item.timestampMs
            )
            existing.add(0, dto)
            // Keep recent 50 logs
            val trimmed = existing.take(50)
            prefs[activityLogKey] = activityAdapter.toJson(trimmed)
        }
    }

    suspend fun clearActivityLogs() {
        context.dataStore.edit { prefs ->
            prefs.remove(activityLogKey)
        }
    }
}
