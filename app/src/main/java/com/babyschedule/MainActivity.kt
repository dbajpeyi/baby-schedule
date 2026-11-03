package com.babyschedule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.babyschedule.ui.AppRoot
import com.babyschedule.ui.BabyViewModel
import com.babyschedule.ui.theme.BabyScheduleTheme
import com.babyschedule.widget.BabyWidget

class MainActivity : ComponentActivity() {
    /** Bumped when the widget's "Log a session" button opens the app. */
    private val logSessionRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            BabyScheduleTheme {
                val vm: BabyViewModel = viewModel(factory = BabyViewModel.Factory)
                AppRoot(vm, logSessionRequest = logSessionRequests.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(BabyWidget.EXTRA_LOG_SESSION, false) == true) {
            logSessionRequests.intValue++
            intent.removeExtra(BabyWidget.EXTRA_LOG_SESSION)
        }
    }
}
