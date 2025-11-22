package com.example.smartdosing.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import com.example.smartdosing.navigation.SmartDosingNavHost
import com.example.smartdosing.ui.components.SmartDosingBottomNavigationBar

/**
 * SmartDosing 应用主框架
 * 包含底部导航栏和导航主机
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartDosingApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    Scaffold(
        modifier = modifier,
        containerColor = Color(0xFFF0F2F5), // 保持原有的工业背景色
        bottomBar = {
            SmartDosingBottomNavigationBar(navController = navController)
        }
    ) { innerPadding ->
        SmartDosingNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}