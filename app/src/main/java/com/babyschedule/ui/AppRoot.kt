package com.babyschedule.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babyschedule.domain.SessionDraft
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.Today),
    HISTORY("History", Icons.Filled.History),
    STATS("Stats", Icons.Filled.BarChart),
    GROWTH("Growth", Icons.Filled.MonitorWeight),
}

@Composable
fun AppRoot(vm: BabyViewModel, logSessionRequest: Int = 0) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var dialog by remember { mutableStateOf<DialogRequest?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val requestNotifications = rememberNotificationPermissionRequest()
    var editor by remember { mutableStateOf<SessionDraft?>(null) }
    var confetti by remember { mutableIntStateOf(0) }
    val ui = remember(scope, snackbar, requestNotifications) {
        UiActions(
            scope = scope,
            snackbar = snackbar,
            requestNotifications = requestNotifications,
            openDialog = { dialog = it },
            openSession = { id -> scope.launch { vm.loadSessionDraft(id)?.let { editor = it } } },
            editDraft = { editor = it },
            celebrate = { if (vm.confetti.value) confetti++ },
        )
    }
    // The widget's "Log a session" button opens the session form directly.
    LaunchedEffect(logSessionRequest) {
        if (logSessionRequest > 0) editor = SessionDraft.new(System.currentTimeMillis())
    }
    val profile by vm.profile.collectAsStateWithLifecycle()
    var showFeeding by rememberSaveable { mutableStateOf(false) }
    var detail by rememberSaveable { mutableStateOf<Category?>(null) }

    @Composable
    fun MainTabs() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (tab) {
                                Tab.TODAY -> "Baby Schedule"
                                Tab.HISTORY -> "History"
                                Tab.STATS -> "Statistics"
                                Tab.GROWTH -> profile.name.ifBlank { "Growth" }.let { if (it == "Growth") it else "$it's growth" }
                            }
                        )
                    },
                    actions = {
                        IconButton(onClick = { dialog = DialogRequest.AppSettings }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    Tab.TODAY -> TodayScreen(
                        vm, ui,
                        onOpenFeeding = { showFeeding = true },
                        onOpenDetail = {
                            vm.selectDay(java.time.LocalDate.now())
                            detail = it
                        },
                        onSeeHistory = {
                            vm.selectDay(java.time.LocalDate.now())
                            tab = Tab.HISTORY
                        },
                    )
                    Tab.HISTORY -> HistoryScreen(vm, ui, onOpenDetail = { detail = it })
                    Tab.STATS -> StatsScreen(vm)
                    Tab.GROWTH -> GrowthScreen(vm, ui)
                }
            }
        }
    }

    val openEditor = editor
    val openDetail = detail
    Box(Modifier.fillMaxSize()) {
        when {
            openEditor != null -> SessionFormScreen(openEditor, vm, ui, onClose = { editor = null })
            showFeeding -> FeedingSessionScreen(
                vm, ui,
                onClose = { showFeeding = false },
                onFinish = { draft ->
                    showFeeding = false
                    editor = draft
                },
            )
            openDetail != null -> DayDetailScreen(vm, ui, openDetail, onClose = { detail = null })
            else -> MainTabs()
        }
        ConfettiOverlay(confetti)
    }

    DialogHost(dialog, vm, ui, onDismiss = { dialog = null })
}
