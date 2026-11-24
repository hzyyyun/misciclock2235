package com.musicclock.alarm.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.musicclock.alarm.ui.screens.AlarmEditorScreen
import com.musicclock.alarm.ui.screens.HomeScreen
import com.musicclock.alarm.ui.screens.SettingsScreen
import com.musicclock.alarm.ui.theme.MusicClockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicClockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MusicClockApp()
                }
            }
        }
    }
}

@Composable
private fun MusicClockApp() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "home",
    ) {
        composable("home") {
            val homeViewModel: HomeViewModel = viewModel()
            HomeScreen(
                viewModel = homeViewModel,
                onAddAlarm = { navController.navigate("editor/0") },
                onEditAlarm = { id -> navController.navigate("editor/$id") },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable(
            route = "editor/{alarmId}",
            arguments = listOf(navArgument("alarmId") { type = NavType.LongType }),
        ) { entry ->
            val alarmId = entry.arguments?.getLong("alarmId") ?: 0L
            val application = androidx.compose.ui.platform.LocalContext.current.applicationContext
                as android.app.Application
            val editorViewModel: AlarmEditorViewModel = viewModel(
                key = "editor-$alarmId",
                factory = AlarmEditorViewModel.factory(application, alarmId),
            )
            AlarmEditorScreen(
                viewModel = editorViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable("settings") {
            val settingsViewModel: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
