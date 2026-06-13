package com.example.aichat.ui.navigation

sealed class Routes(val route: String) {
    object Chat : Routes("chat")
    object Settings : Routes("settings")
    object CustomModel : Routes("custom_model")
    object Account : Routes("account")
}
