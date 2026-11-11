package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.model.ConnectionInfo
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class FirebaseEsp32Repository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "FirebaseEsp32Repo"
        const val DATABASE_URL = "https://abrob-home-automation-2b56b-default-rtdb.firebaseio.com/"
        const val API_KEY = "AIzaSyAGqR2022Vwhk8Dp1XlQwrH2Sp48QMvo4I"
        const val PROJECT_ID = "abrob-home-automation-2b56b"
        const val APPLICATION_ID = "com.aistudio.nurhome.esp32"
        const val DEVICES_PATH = "devices"
    }

    private var firebaseApp: FirebaseApp? = null
    private var firebaseAuth: FirebaseAuth? = null
    private var database: FirebaseDatabase? = null
    private var devicesRef: DatabaseReference? = null
    private var connectedRef: DatabaseReference? = null

    private val _connectionState = MutableStateFlow(ConnectionInfo())
    val connectionState: StateFlow<ConnectionInfo> = _connectionState.asStateFlow()

    // Real-time map of relay states from Firebase: "relay1" -> Boolean, etc.
    private val _devicesState = MutableStateFlow<Map<String, Boolean>>(
        mapOf("relay1" to false, "relay2" to false, "relay3" to false, "relay4" to false)
    )
    val devicesState: StateFlow<Map<String, Boolean>> = _devicesState.asStateFlow()

    // Error event channel for snackbars
    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    init {
        initFirebase()
    }

    private fun initFirebase() {
        scope.launch {
            try {
                _connectionState.value = _connectionState.value.copy(
                    isAuthenticating = true,
                    errorMessage = null
                )

                // Initialize FirebaseApp if not already done
                firebaseApp = try {
                    FirebaseApp.getInstance()
                } catch (_: Exception) {
                    val options = FirebaseOptions.Builder()
                        .setApiKey(API_KEY)
                        .setApplicationId(APPLICATION_ID)
                        .setDatabaseUrl(DATABASE_URL)
                        .setProjectId(PROJECT_ID)
                        .build()
                    FirebaseApp.initializeApp(context, options)
                }

                val app = firebaseApp ?: FirebaseApp.getInstance()
                firebaseAuth = FirebaseAuth.getInstance(app)
                database = FirebaseDatabase.getInstance(app, DATABASE_URL)

                // Perform anonymous sign in as required by the ESP32 project rules
                val currentUser = firebaseAuth?.currentUser
                if (currentUser == null) {
                    Log.d(TAG, "Signing in anonymously to Firebase Auth...")
                    val authResult = firebaseAuth?.signInAnonymously()?.await()
                    val uid = authResult?.user?.uid
                    Log.d(TAG, "Signed in anonymously with UID: $uid")
                    _connectionState.value = _connectionState.value.copy(
                        authUid = uid,
                        isAuthenticating = false
                    )
                } else {
                    Log.d(TAG, "Already signed in anonymously with UID: ${currentUser.uid}")
                    _connectionState.value = _connectionState.value.copy(
                        authUid = currentUser.uid,
                        isAuthenticating = false
                    )
                }

                // Listen to .info/connected
                connectedRef = database?.getReference(".info/connected")
                connectedRef?.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val connected = snapshot.getValue(Boolean::class.java) ?: false
                        Log.d(TAG, "Firebase .info/connected: $connected")
                        _connectionState.value = _connectionState.value.copy(
                            isConnected = connected,
                            lastSyncEpochMs = System.currentTimeMillis()
                        )
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.w(TAG, "Connection listener cancelled: ${error.message}")
                        _connectionState.value = _connectionState.value.copy(
                            isConnected = false,
                            errorMessage = error.message
                        )
                    }
                })

                // Listen to /devices in real time
                devicesRef = database?.getReference(DEVICES_PATH)
                devicesRef?.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val newMap = mutableMapOf<String, Boolean>()
                        for (key in listOf("relay1", "relay2", "relay3", "relay4")) {
                            val child = snapshot.child(key)
                            // Parse strictly as boolean
                            val value = when (val raw = child.value) {
                                is Boolean -> raw
                                is Number -> raw.toInt() == 1
                                is String -> raw.equals("true", ignoreCase = true) || raw == "1"
                                else -> false
                            }
                            newMap[key] = value
                        }
                        Log.d(TAG, "Realtime update from /devices: $newMap")
                        _devicesState.value = newMap
                        _connectionState.value = _connectionState.value.copy(
                            lastSyncEpochMs = System.currentTimeMillis(),
                            isConnected = true
                        )
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Devices listener cancelled: ${error.message}")
                        scope.launch {
                            _errorEvents.emit("Sync error: ${error.message}")
                        }
                    }
                })

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Firebase: ${e.message}", e)
                _connectionState.value = _connectionState.value.copy(
                    isAuthenticating = false,
                    errorMessage = e.message ?: "Authentication failed"
                )
                // Attempt REST sync as fallback
                fetchRestState()
            }
        }
    }

    /**
     * Writes boolean value directly to /devices/relayN
     */
    suspend fun setRelayState(relayKey: String, newState: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ref = devicesRef?.child(relayKey)
            if (ref != null) {
                ref.setValue(newState).await()
                Log.d(TAG, "Successfully wrote $newState to /devices/$relayKey via RTDB SDK")
                _connectionState.value = _connectionState.value.copy(
                    lastSyncEpochMs = System.currentTimeMillis(),
                    isConnected = true
                )
                Result.success(Unit)
            } else {
                // Fallback to REST API
                setRelayStateRest(relayKey, newState)
            }
        } catch (e: Exception) {
            Log.e(TAG, "SDK write failed for $relayKey, attempting REST fallback: ${e.message}")
            try {
                setRelayStateRest(relayKey, newState)
            } catch (restEx: Exception) {
                Log.e(TAG, "REST write also failed: ${restEx.message}")
                _errorEvents.emit("Failed to toggle $relayKey: ${restEx.message ?: e.message}")
                Result.failure(restEx)
            }
        }
    }

    /**
     * Master multi-path update to write all 4 relays atomically
     */
    suspend fun setAllRelays(allState: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val updates: Map<String, Any> = mapOf(
            "relay1" to allState,
            "relay2" to allState,
            "relay3" to allState,
            "relay4" to allState
        )
        try {
            val ref = devicesRef
            if (ref != null) {
                ref.updateChildren(updates).await()
                Log.d(TAG, "Successfully updated all 4 relays to $allState via updateChildren")
                _connectionState.value = _connectionState.value.copy(
                    lastSyncEpochMs = System.currentTimeMillis(),
                    isConnected = true
                )
                Result.success(Unit)
            } else {
                setAllRelaysRest(allState)
            }
        } catch (e: Exception) {
            Log.e(TAG, "SDK multi-path write failed, trying REST fallback: ${e.message}")
            try {
                setAllRelaysRest(allState)
            } catch (restEx: Exception) {
                Log.e(TAG, "REST multi-path write failed: ${restEx.message}")
                _errorEvents.emit("Master write failed: ${restEx.message ?: e.message}")
                Result.failure(restEx)
            }
        }
    }

    /**
     * Re-authenticates anonymously on user demand
     */
    fun reauthenticate() {
        scope.launch {
            try {
                _connectionState.value = _connectionState.value.copy(isAuthenticating = true)
                firebaseAuth?.signOut()
                val result = firebaseAuth?.signInAnonymously()?.await()
                _connectionState.value = _connectionState.value.copy(
                    isAuthenticating = false,
                    authUid = result?.user?.uid,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _connectionState.value = _connectionState.value.copy(
                    isAuthenticating = false,
                    errorMessage = e.message
                )
                _errorEvents.emit("Re-auth failed: ${e.message}")
            }
        }
    }

    /**
     * REST fallback for writing a single relay boolean value
     */
    private fun setRelayStateRest(relayKey: String, newState: Boolean): Result<Unit> {
        val url = "${DATABASE_URL}devices/$relayKey.json"
        val requestBody = newState.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .put(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        return if (response.isSuccessful) {
            response.close()
            Result.success(Unit)
        } else {
            val code = response.code
            val body = response.body?.string()
            response.close()
            Result.failure(Exception("HTTP $code: $body"))
        }
    }

    /**
     * REST fallback for multi-path update
     */
    private fun setAllRelaysRest(allState: Boolean): Result<Unit> {
        val url = "${DATABASE_URL}devices.json"
        val jsonPayload = """{"relay1":$allState,"relay2":$allState,"relay3":$allState,"relay4":$allState}"""
        val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .patch(requestBody)
            .build()

        val response = okHttpClient.newCall(request).execute()
        return if (response.isSuccessful) {
            response.close()
            Result.success(Unit)
        } else {
            val code = response.code
            val body = response.body?.string()
            response.close()
            Result.failure(Exception("HTTP $code: $body"))
        }
    }

    /**
     * REST fallback to read state
     */
    private fun fetchRestState() {
        scope.launch {
            try {
                val url = "${DATABASE_URL}devices.json"
                val request = Request.Builder().url(url).get().build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    response.close()
                    // Simple parse
                    val newMap = mutableMapOf<String, Boolean>()
                    for (i in 1..4) {
                        val key = "relay$i"
                        val isTrue = body.contains("\"$key\":true") || body.contains("\"$key\": 1")
                        newMap[key] = isTrue
                    }
                    _devicesState.value = newMap
                    _connectionState.value = _connectionState.value.copy(
                        isConnected = true,
                        lastSyncEpochMs = System.currentTimeMillis()
                    )
                } else {
                    response.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "REST fetch failed: ${e.message}")
            }
        }
    }
}
