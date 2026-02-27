package com.example.smartdosing

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.smartdosing.navigation.SmartDosingNavHost
import com.example.smartdosing.ui.components.SmartDosingBottomNavigationBar
import com.example.smartdosing.ui.components.SmartDosingNavigationRail
import com.example.smartdosing.ui.theme.SmartDosingTheme
import com.example.smartdosing.ui.theme.SmartDosingTokens
import com.example.smartdosing.ui.theme.LocalWindowSize
import com.example.smartdosing.ui.theme.SmartDosingWindowHeightClass
import com.example.smartdosing.ui.theme.SmartDosingWindowWidthClass
import com.example.smartdosing.web.WebService
import com.example.smartdosing.web.WebServiceResult
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var webService: WebService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 无线传输服务初始化（本机 API + 局域网传输）
        webService = WebService.getInstance(this)

        // TTS 语音系统已软下线，如需恢复请取消注释
        // initializeXiaomiTTS()

        enableEdgeToEdge()
        setContent {
            SmartDosingTheme {
                SmartDosingApp()
            }
        }

        // 启动无线传输服务
        if (webService.isAutoStartEnabled()) {
            startWebService()
        }
    }

    private fun startWebService() {
        lifecycleScope.launch {
            val preferredPort = webService.getPreferredPort()
            when (val result = webService.startWebService(preferredPort)) {
                is WebServiceResult.Success -> {
                    showToast("无线传输服务已启动: ${result.serverUrl}")
                }
                is WebServiceResult.AlreadyRunning -> {
                    // Optionally show a toast, or just ignore
                }
                is WebServiceResult.NetworkError -> {
                    showToast("网络错误: ${result.message}")
                }
                is WebServiceResult.StartFailed -> {
                    showToast("无线传输服务启动失败: ${result.message}")
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
    val windowSize = LocalWindowSize.current
    val useNavigationRail = windowSize.widthClass != SmartDosingWindowWidthClass.Compact

    if (useNavigationRail) {
        SmartDosingLargeScreenLayout(navController)
    } else {
        SmartDosingBottomBarLayout(navController)
    }
}

@Composable
private fun SmartDosingBottomBarLayout(navController: NavHostController) {
    val windowSize = LocalWindowSize.current
    val horizontalPadding = when (windowSize.widthClass) {
        SmartDosingWindowWidthClass.Compact -> 12.dp
        SmartDosingWindowWidthClass.Medium -> 20.dp
        SmartDosingWindowWidthClass.Expanded -> 28.dp
    }
    val verticalPadding = when (windowSize.heightClass) {
        SmartDosingWindowHeightClass.Compact -> 8.dp
        SmartDosingWindowHeightClass.Medium -> 12.dp
        SmartDosingWindowHeightClass.Expanded -> 16.dp
    }

    // 投料页隐藏底部导航栏，进入全屏沉浸模式
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDosingPage = currentRoute?.startsWith("material_configuration") == true
    val showBottomBar = !isDosingPage

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                SmartDosingBottomNavigationBar(navController = navController)
            }
        }
    ) { innerPadding ->
        SmartDosingNavHost(
            navController = navController,
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        )
    }
}

@Composable
private fun SmartDosingLargeScreenLayout(navController: NavHostController) {
    val windowSize = LocalWindowSize.current
    val railWidth = 96.dp
    val dividerWidth = 1.dp
    val outerPadding = when (windowSize.widthClass) {
        SmartDosingWindowWidthClass.Compact -> SmartDosingTokens.spacing.md
        SmartDosingWindowWidthClass.Medium -> SmartDosingTokens.spacing.lg
        SmartDosingWindowWidthClass.Expanded -> SmartDosingTokens.spacing.xl
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Navigation Rail
        SmartDosingNavigationRail(
            navController = navController,
            modifier = Modifier
                .width(railWidth)
                .fillMaxHeight()
        )

        // Divider
        VerticalDivider(
            modifier = Modifier
                .width(dividerWidth)
                .fillMaxHeight()
        )

        // Main content
        SmartDosingNavHost(
            navController = navController,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(outerPadding)
        )
    }
}
