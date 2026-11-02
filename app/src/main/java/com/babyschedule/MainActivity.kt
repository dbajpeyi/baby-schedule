package com.babyschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.babyschedule.ui.AppRoot
import com.babyschedule.ui.BabyViewModel
import com.babyschedule.ui.theme.BabyScheduleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BabyScheduleTheme {
                val vm: BabyViewModel = viewModel(factory = BabyViewModel.Factory)
                AppRoot(vm)
            }
        }
    }
}
