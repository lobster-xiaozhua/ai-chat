package com.example.aichat.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aichat.ui.chat.ChatScreen
import com.example.aichat.ui.chat.ModelPickerScreen
import com.example.aichat.ui.settings.AboutScreen
import com.example.aichat.ui.settings.AccountScreen
import com.example.aichat.ui.settings.CustomModelScreen
import com.example.aichat.ui.settings.SettingsScreen
import com.example.aichat.ui.settings.UpdateDialog
import com.example.aichat.ui.settings.UpdateViewModel

@Composable
fun AppNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    // 全局更新检查 ViewModel（跨页面共享状态）
    val updateViewModel: UpdateViewModel = hiltViewModel()
    val updateState by updateViewModel.updateState.collectAsState()

    // 启动时静默检查更新
    LaunchedEffect(Unit) {
        updateViewModel.checkForUpdate(silent = true)
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Chat.route,
        modifier = modifier
    ) {
        composable(Routes.Chat.route) {
            ChatScreen(
                onNavigateToSettings = { navController.navigate(Routes.Settings.route) { launchSingleTop = true } },
                onNavigateToAccount = { navController.navigate(Routes.Account.route) { launchSingleTop = true } },
                onNavigateToModelPicker = { navController.navigate(Routes.ModelPicker.route) { launchSingleTop = true } }
            )

            // 启动时发现新版本 → 在聊天页弹出更新对话框
            when (val state = updateState) {
                is UpdateViewModel.UpdateState.UpdateAvailable -> {
                    UpdateDialog(
                        release = state.release,
                        onConfirm = { updateViewModel.downloadAndInstall(state.release) },
                        onDismiss = { updateViewModel.resetState() }
                    )
                }
                else -> {}
            }
        }
        composable(Routes.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToCustomModel = { navController.navigate(Routes.CustomModel.route) { launchSingleTop = true } },
                onNavigateToModelPicker = { navController.navigate(Routes.ModelPicker.route) { launchSingleTop = true } },
                onNavigateToAbout = { navController.navigate(Routes.About.route) { launchSingleTop = true } }
            )
        }
        composable(Routes.CustomModel.route) {
            CustomModelScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.Account.route) {
            AccountScreen(
                onBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Routes.Settings.route) { launchSingleTop = true } }
            )
        }
        composable(Routes.ModelPicker.route) {
            ModelPickerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
