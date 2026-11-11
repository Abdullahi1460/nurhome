package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Relay
import com.example.ui.components.ActivityLogSheet
import com.example.ui.components.ArchitectureGuideDialog
import com.example.ui.components.ConnectionStatusBar
import com.example.ui.components.EditRelayDialog
import com.example.ui.components.MasterControlCard
import com.example.ui.components.NurHomeHeader
import com.example.ui.components.SettingsSheet
import com.example.ui.components.TimerPickerDialog
import com.example.ui.components.RelayCard
import com.example.ui.theme.NurHomeTheme
import com.example.ui.viewmodel.HomeAutomationViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: HomeAutomationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkModePref by viewModel.isDarkMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val effectiveDarkMode = isDarkModePref ?: systemDark

            NurHomeTheme(darkTheme = effectiveDarkMode) {
                NurHomeApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NurHomeApp(
    viewModel: HomeAutomationViewModel,
    modifier: Modifier = Modifier
) {
    val relays by viewModel.relays.collectAsState()
    val connectionInfo by viewModel.connectionInfo.collectAsState()
    val activityLogs by viewModel.activityLogs.collectAsState()
    val isDarkModePref by viewModel.isDarkMode.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Sheet states
    var showActivityLog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showHowItWorks by remember { mutableStateOf(false) }

    val logSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Dialog states for individual relay customization & timer
    var editingRelay by remember { mutableStateOf<Relay?>(null) }
    var timerRelay by remember { mutableStateOf<Relay?>(null) }

    // Listen for snackbar notifications
    LaunchedEffect(Unit) {
        viewModel.userNotices.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val activeCount = relays.count { it.isOn }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp) // Responsive tablet constraint
            ) {
                // Brand Header with Live Status & Action Buttons
                NurHomeHeader(
                    connectionInfo = connectionInfo,
                    onOpenHowItWorks = { showHowItWorks = true },
                    onOpenActivityLog = { showActivityLog = true },
                    onOpenSettings = { showSettings = true }
                )

                // Realtime Connection Banner
                ConnectionStatusBar(
                    connectionInfo = connectionInfo,
                    onReconnect = { viewModel.reauthenticateFirebase() }
                )

                // Scrollable Dashboard Content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("dashboard_relays_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Master Controls Card (ALL ON / ALL OFF / Presets)
                    item {
                        MasterControlCard(
                            onAllOn = { viewModel.masterAllOn() },
                            onAllOff = { viewModel.masterAllOff() },
                            onSelectPreset = { preset -> viewModel.applyPreset(preset) },
                            activeRelaysCount = activeCount,
                            totalRelaysCount = relays.size
                        )
                    }

                    // Section Title: 4-Channel Relays
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ESP32 RELAY CHANNELS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "Path: /devices/relay[1-4]",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // 4 Individual Relay Cards
                    items(relays, key = { it.key }) { relay ->
                        RelayCard(
                            relay = relay,
                            onToggle = { viewModel.toggleRelay(relay.key) },
                            onEdit = { editingRelay = relay },
                            onSetTimer = { timerRelay = relay },
                            onCancelTimer = { viewModel.cancelTimer(relay.key) }
                        )
                    }

                    // Bottom padding space
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Modal: Edit Relay Name, Room & Icon
    editingRelay?.let { relay ->
        EditRelayDialog(
            relay = relay,
            onDismiss = { editingRelay = null },
            onSave = { name, room, icon ->
                viewModel.saveRelayCustomization(relay.key, name, room, icon)
                editingRelay = null
            }
        )
    }

    // Modal: Timer Picker
    timerRelay?.let { relay ->
        TimerPickerDialog(
            relay = relay,
            onDismiss = { timerRelay = null },
            onConfirmTimer = { minutes ->
                viewModel.setRelayOffTimer(relay.key, minutes)
                timerRelay = null
            }
        )
    }

    // Modal: How It Works / Architecture Guide
    if (showHowItWorks) {
        ArchitectureGuideDialog(
            onDismiss = { showHowItWorks = false }
        )
    }

    // Bottom Sheet: Activity History Log
    if (showActivityLog) {
        ActivityLogSheet(
            logs = activityLogs,
            sheetState = logSheetState,
            onDismiss = {
                scope.launch {
                    logSheetState.hide()
                    showActivityLog = false
                }
            },
            onClearLogs = { viewModel.clearActivityHistory() }
        )
    }

    // Bottom Sheet: Settings & Firebase Info
    if (showSettings) {
        SettingsSheet(
            connectionInfo = connectionInfo,
            isDarkMode = isDarkModePref,
            onSetDarkMode = { viewModel.setDarkMode(it) },
            onReauthenticate = { viewModel.reauthenticateFirebase() },
            sheetState = settingsSheetState,
            onDismiss = {
                scope.launch {
                    settingsSheetState.hide()
                    showSettings = false
                }
            }
        )
    }
}
