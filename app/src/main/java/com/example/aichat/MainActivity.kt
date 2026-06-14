package com.example.aichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.aichat.ui.navigation.AppNavGraph
import com.example.aichat.ui.theme.AiChatTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 16 强制 edge-to-edge（windowOptOutEdgeToEdgeEnforcement 已废弃并停用）。
        // enableEdgeToEdge() 在 API 36 上是必需的，确保状态栏/导航栏透明，
        // 内容绘制到系统栏下方。Compose 的 WindowInsets 处理实际的 padding。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AiChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
