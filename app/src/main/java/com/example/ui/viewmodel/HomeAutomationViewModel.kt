package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirebaseEsp32Repository
import com.example.data.model.ActivityLogItem
import com.example.data.model.ConnectionInfo
import com.example.data.model.QuickPreset
import com.example.data.model.Relay
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeAutomationViewModel(application: Application) : AndroidViewModel(application) {

    private val firebaseRepo = FirebaseEsp32Repository(application.applicationContext)
    private val prefsRepo = UserPreferencesRepository(application.applicationContext)

    // Snackbar error/info channel
    private val _userNotices = MutableSharedFlow<String>()
    val userNotices: SharedFlow<String> = _userNotices.asSharedFlow()

    // Local overrides for optimistic UI: key -> Pair<isOn, isPending>
    private val _optimisticRelays = MutableStateFlow<Map<String, Pair<Boolean, Boolean>>>(emptyMap())

    // Active timers: key -> Job
    private val activeTimers = mutableMapOf<String, Job>()
    private val _timerCountdowns = MutableStateFlow<Map<String, Int>>(emptyMap())

    // Dark mode preference
    val isDarkMode: StateFlow<Boolean?> = prefsRepo.isDarkMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Activity logs
    val activityLogs: StateFlow<List<ActivityLogItem>> = prefsRepo.activityLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Connection info from Firebase
    val connectionInfo: StateFlow<ConnectionInfo> = firebaseRepo.connectionState

    // Track previous server states to detect ESP32 wall switch toggles
    private var lastObservedServerStates = mapOf<String, Boolean>()

    // Relays combined stream
    private val defaultRelayDefinitions = listOf(
        Pair("relay1", "Light 1"),
        Pair("relay2", "Light 2"),
        Pair("relay3", "Light 3"),
        Pair("relay4", "Light 4")
    )

    // Combine metadata for all 4 relays
    private val metaFlows = combine(
        prefsRepo.getRelayMeta("relay1"),
        prefsRepo.getRelayMeta("relay2"),
        prefsRepo.getRelayMeta("relay3"),
        prefsRepo.getRelayMeta("relay4")
    ) { m1, m2, m3, m4 ->
        mapOf("relay1" to m1, "relay2" to m2, "relay3" to m3, "relay4" to m4)
    }

    val relays: StateFlow<List<Relay>> = combine(
        firebaseRepo.devicesState,
        _optimisticRelays,
        metaFlows,
        _timerCountdowns
    ) { serverStates, optimisticMap, metas, timers ->

        // Detect external changes (e.g., from physical wall switch on ESP32)
        if (lastObservedServerStates.isNotEmpty()) {
            for ((key, serverVal) in serverStates) {
                val oldVal = lastObservedServerStates[key]
                val isOptimisticPending = optimisticMap[key]?.second == true
                if (oldVal != null && oldVal != serverVal && !isOptimisticPending) {
                    val meta = metas[key]
                    val name = meta?.customName?.takeIf { it.isNotBlank() } ?: "Relay ${key.removePrefix("relay")}"
                    viewModelScope.launch {
                        prefsRepo.addActivityLog(
                            ActivityLogItem(
                                relayKey = key,
                                relayName = name,
                                newState = serverVal,
                                source = "Wall Switch / ESP32"
                            )
                        )
                    }
                }
            }
        }
        lastObservedServerStates = serverStates

        defaultRelayDefinitions.map { (key, defaultName) ->
            val serverIsOn = serverStates[key] ?: false
            val optimistic = optimisticMap[key]

            val isOn = if (optimistic != null && optimistic.second) {
                optimistic.first
            } else {
                serverIsOn
            }

            val isPending = optimistic?.second ?: false
            val meta = metas[key]

            Relay(
                key = key,
                defaultName = defaultName,
                customName = meta?.customName,
                room = meta?.room ?: "Living Room",
                iconKey = meta?.iconKey ?: "lightbulb",
                isOn = isOn,
                isPending = isPending,
                lastUpdatedEpochMs = System.currentTimeMillis(),
                timerRemainingSeconds = timers[key]
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = defaultRelayDefinitions.map { (key, defName) ->
            Relay(key = key, defaultName = defName)
        }
    )

    init {
        // Forward repo errors
        viewModelScope.launch {
            firebaseRepo.errorEvents.collect { error ->
                _userNotices.emit(error)
            }
        }
    }

    /**
     * Toggles an individual relay with optimistic UI
     */
    fun toggleRelay(key: String) {
        val currentRelay = relays.value.find { it.key == key } ?: return
        val targetState = !currentRelay.isOn
        val relayName = currentRelay.displayName

        // 1. Optimistic local update
        val updatedMap = _optimisticRelays.value.toMutableMap()
        updatedMap[key] = Pair(targetState, true)
        _optimisticRelays.value = updatedMap

        // 2. Dispatch to Firebase
        viewModelScope.launch {
            val result = firebaseRepo.setRelayState(key, targetState)
            if (result.isSuccess) {
                // Clear pending flag after short grace period for smooth UI animation
                delay(300)
                val cleanMap = _optimisticRelays.value.toMutableMap()
                cleanMap.remove(key)
                _optimisticRelays.value = cleanMap

                // Add to activity log
                prefsRepo.addActivityLog(
                    ActivityLogItem(
                        relayKey = key,
                        relayName = relayName,
                        newState = targetState,
                        source = "App Tap"
                    )
                )
            } else {
                // Revert optimistic update
                val revertMap = _optimisticRelays.value.toMutableMap()
                revertMap.remove(key)
                _optimisticRelays.value = revertMap
                _userNotices.emit("Failed to toggle $relayName. Reverting switch.")
            }
        }
    }

    /**
     * Master All On
     */
    fun masterAllOn() {
        setAllRelaysMaster(true)
    }

    /**
     * Master All Off
     */
    fun masterAllOff() {
        setAllRelaysMaster(false)
    }

    private fun setAllRelaysMaster(targetState: Boolean) {
        // Optimistic update for all 4 relays
        val newOptimistic = mutableMapOf<String, Pair<Boolean, Boolean>>()
        listOf("relay1", "relay2", "relay3", "relay4").forEach {
            newOptimistic[it] = Pair(targetState, true)
        }
        _optimisticRelays.value = newOptimistic

        viewModelScope.launch {
            val result = firebaseRepo.setAllRelays(targetState)
            if (result.isSuccess) {
                delay(350)
                _optimisticRelays.value = emptyMap()
                val actionName = if (targetState) "All Relays ON" else "All Relays OFF"
                prefsRepo.addActivityLog(
                    ActivityLogItem(
                        relayKey = "all",
                        relayName = "Master Controls",
                        newState = targetState,
                        source = "Master Control ($actionName)"
                    )
                )
                _userNotices.emit(if (targetState) "All 4 relays turned ON" else "All 4 relays turned OFF")
            } else {
                _optimisticRelays.value = emptyMap()
                _userNotices.emit("Master action failed. Please check connection.")
            }
        }
    }

    /**
     * Apply quick preset
     */
    fun applyPreset(preset: QuickPreset) {
        when (preset) {
            QuickPreset.ALL_ON -> masterAllOn()
            QuickPreset.ALL_OFF -> masterAllOff()
            QuickPreset.EVENING_LOUNGE -> {
                viewModelScope.launch {
                    val map = mutableMapOf<String, Pair<Boolean, Boolean>>()
                    map["relay1"] = Pair(true, true)
                    map["relay2"] = Pair(false, true)
                    map["relay3"] = Pair(true, true)
                    map["relay4"] = Pair(false, true)
                    _optimisticRelays.value = map

                    firebaseRepo.setRelayState("relay1", true)
                    firebaseRepo.setRelayState("relay2", false)
                    firebaseRepo.setRelayState("relay3", true)
                    firebaseRepo.setRelayState("relay4", false)
                    delay(300)
                    _optimisticRelays.value = emptyMap()
                    _userNotices.emit("Evening Lounge preset applied")
                }
            }
            QuickPreset.NIGHT_SECURITY -> {
                viewModelScope.launch {
                    val map = mutableMapOf<String, Pair<Boolean, Boolean>>()
                    map["relay1"] = Pair(false, true)
                    map["relay2"] = Pair(true, true)
                    map["relay3"] = Pair(false, true)
                    map["relay4"] = Pair(true, true)
                    _optimisticRelays.value = map

                    firebaseRepo.setRelayState("relay1", false)
                    firebaseRepo.setRelayState("relay2", true)
                    firebaseRepo.setRelayState("relay3", false)
                    firebaseRepo.setRelayState("relay4", true)
                    delay(300)
                    _optimisticRelays.value = emptyMap()
                    _userNotices.emit("Night Safety preset applied")
                }
            }
        }
    }

    /**
     * Saves user customization (name, room, icon) locally
     */
    fun saveRelayCustomization(key: String, name: String?, room: String, icon: String) {
        viewModelScope.launch {
            prefsRepo.saveRelayMeta(key, name, room, icon)
            _userNotices.emit("Relay settings saved")
        }
    }

    /**
     * Start a countdown timer for a relay to turn it OFF after specified minutes
     */
    fun setRelayOffTimer(key: String, minutes: Int) {
        activeTimers[key]?.cancel()
        if (minutes <= 0) {
            val map = _timerCountdowns.value.toMutableMap()
            map.remove(key)
            _timerCountdowns.value = map
            return
        }

        val totalSeconds = minutes * 60
        val job = viewModelScope.launch {
            for (sec in totalSeconds downTo 1) {
                val map = _timerCountdowns.value.toMutableMap()
                map[key] = sec
                _timerCountdowns.value = map
                delay(1000)
            }
            // Timer expired -> Turn relay OFF
            val map = _timerCountdowns.value.toMutableMap()
            map.remove(key)
            _timerCountdowns.value = map

            val relay = relays.value.find { it.key == key }
            if (relay?.isOn == true) {
                toggleRelay(key)
                _userNotices.emit("Timer finished: ${relay.displayName} turned off")
            }
        }
        activeTimers[key] = job
        viewModelScope.launch {
            _userNotices.emit("Timer set: turn off in $minutes min")
        }
    }

    fun cancelTimer(key: String) {
        activeTimers[key]?.cancel()
        activeTimers.remove(key)
        val map = _timerCountdowns.value.toMutableMap()
        map.remove(key)
        _timerCountdowns.value = map
        viewModelScope.launch {
            _userNotices.emit("Timer cancelled")
        }
    }

    fun setDarkMode(enabled: Boolean?) {
        viewModelScope.launch {
            prefsRepo.setDarkMode(enabled)
        }
    }

    fun clearActivityHistory() {
        viewModelScope.launch {
            prefsRepo.clearActivityLogs()
            _userNotices.emit("Activity history cleared")
        }
    }

    fun reauthenticateFirebase() {
        firebaseRepo.reauthenticate()
    }
}
