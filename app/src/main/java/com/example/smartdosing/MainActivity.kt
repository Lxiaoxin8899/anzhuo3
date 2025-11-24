package com.example.smartdosing

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.smartdosing.navigation.SmartDosingNavHost
import com.example.smartdosing.ui.components.SmartDosingBottomNavigationBar
import com.example.smartdosing.ui.theme.SmartDosingTheme
import com.example.smartdosing.web.WebService
import com.example.smartdosing.web.WebServiceResult
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var webService: WebService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Web Service
        webService = WebService.getInstance(this)

        enableEdgeToEdge()
        setContent {
            SmartDosingTheme {
                SmartDosingApp()
            }
        }

        // Start Web Service
        startWebService()
    }

    private fun startWebService() {
        lifecycleScope.launch {
            when (val result = webService.startWebService()) {
                is WebServiceResult.Success -> {
                    showToast("Web管理后台已启动: ${result.serverUrl}")
                }
                is WebServiceResult.AlreadyRunning -> {
                    // Optionally show a toast, or just ignore
                }
                is WebServiceResult.NetworkError -> {
                    showToast("网络错误: ${result.message}")
                }
                is WebServiceResult.StartFailed -> {
                    showToast("Web服务启动失败: ${result.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webService.stopWebService()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

@Composable
fun SmartDosingApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            SmartDosingBottomNavigationBar(
                navController = navController
            )
        }
    ) { innerPadding ->
        SmartDosingNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
