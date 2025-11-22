package com.example.smartdosing.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.smartdosing.navigation.SmartDosingDestination
import com.example.smartdosing.navigation.bottomNavigationDestinations

/**
 * SmartDosing 应用底部导航栏
 * 工业风格设计，适配平板界面
 */
@Composable
fun SmartDosingBottomNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        modifier = modifier,
        containerColor = Color.White,
        contentColor = Color(0xFF263238),
        tonalElevation = 8.dp
    ) {
        bottomNavigationDestinations.forEach { destination ->
            val selected = currentRoute == destination.route

            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.description,
                        modifier = Modifier.size(28.dp), // 适合平板的图标大小
                        tint = if (selected) Color(0xFF1976D2) else Color(0xFF757575)
                    )
                },
                label = {
                    Text(
                        text = destination.title,
                        fontSize = 14.sp, // 适合平板的文字大小
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Color(0xFF1976D2) else Color(0xFF757575)
                    )
                },
                selected = selected,
                onClick = {
                    if (currentRoute != destination.route) {
                        navController.navigate(destination.route) {
                            // 清空回退栈到起始页面
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            // 避免重复创建同一个destination的实例
                            launchSingleTop = true
                            // 恢复状态
                            restoreState = true
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF1976D2),
                    unselectedIconColor = Color(0xFF757575),
                    selectedTextColor = Color(0xFF1976D2),
                    unselectedTextColor = Color(0xFF757575),
                    indicatorColor = Color(0xFFE3F2FD) // 淡蓝色指示器
                )
            )
        }
    }
}