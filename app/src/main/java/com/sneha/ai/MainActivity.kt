package com.sneha.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sneha.ai.ui.CodingScreen
import com.sneha.ai.ui.CodingViewModel
import com.sneha.ai.ui.HomeScreen
import com.sneha.ai.ui.MainViewModel
import com.sneha.ai.ui.SettingsScreen
import com.sneha.ai.ui.SnehaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val s by vm.settings.collectAsStateWithLifecycle()
            SnehaTheme(s.theme, s.fontScale) {
                var screen by rememberSaveable { mutableStateOf("home") }
                BackHandler(screen != "home") { screen = "home" }
                when {
                    screen == "coding" -> {
                        val cvm: CodingViewModel = viewModel()
                        CodingScreen(cvm) { screen = "home" }
                    }
                    screen.startsWith("settings") ->
                        SettingsScreen(vm, screen.substringAfter(":", "").ifBlank { null }) { screen = "home" }
                    else -> HomeScreen(vm) { screen = it }
                }
            }
        }
    }
}
