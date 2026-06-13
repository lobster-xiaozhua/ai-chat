package com.example.aichat.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aichat.ui.chat.ChatScreen
import com.example.aichat.ui.settings.AccountScreen
import com.example.aichat.ui.settings.CustomModelScreen
import com.example.aichat.ui.settings.SettingsScreen

@Composable
fun AppNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.Chat.route,
        modifier = modifier
    ) {
        composable(Routes.Chat.route) {
            ChatScreen(
                onNavigateToSettings = { navController.navigate(Routes.Settings.route) },
                onNavigateToAccount = { navController.navigate(Routes.Account.route) }
            )
        }
        composable(Routes.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToCustomModel = { navController.navigate(Routes.CustomModel.route) }
            )
        }
        composable(Routes.CustomModel.route) {
            CustomModelScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.Account.route) {
            AccountScreen(
                onBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Routes.Settings.route) }
            )
        }
    }
}
