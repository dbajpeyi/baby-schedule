package com.babyschedule.ui

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.Today),
    HISTORY("History", Icons.Filled.History),
    STATS("Stats", Icons.Filled.BarChart),
    GROWTH("Growth", Icons.Filled.MonitorWeight),
}

@Composable
fun AppRoot(vm: BabyViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var dialog by remember { mutableStateOf<DialogRequest?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val requestNotifications = rememberNotificationPermissionRequest()
    val ui = remember(scope, snackbar, requestNotifications) {
        UiActions(scope, snackbar, requestNotifications) { dialog = it }
    }
    val profile by vm.profile.collectAsStateWithLifecycle()
    var showFeeding by rememberSaveable { mutableStateOf(false) }
    var detail by rememberSaveable { mutableStateOf<Category?>(null) }

    if (showFeeding) {
        FeedingSessionScreen(vm, ui, onClose = { showFeeding = false })
        DialogHost(dialog, vm, ui, onDismiss = { dialog = null })
        return
    }
    val openDetail = detail
    if (openDetail != null) {
        DayDetailScreen(vm, ui, openDetail, onClose = { detail = null })
        DialogHost(dialog, vm, ui, onDismiss = { dialog = null })
        return
    }

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

    DialogHost(dialog, vm, ui, onDismiss = { dialog = null })
}
