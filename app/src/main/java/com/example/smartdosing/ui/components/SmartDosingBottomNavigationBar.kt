package com.example.smartdosing.ui.components

import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.smartdosing.navigation.bottomNavigationDestinations

/**
 * 底部导航栏的响应式参数
 * 通过统一的数据结构约束高度、图标尺寸、字号与标签显隐
 */
private data class BottomBarMetrics(
    val height: Dp,
    val iconSize: Dp,
    val fontSize: TextUnit,
    val showLabel: Boolean
)

/**
 * SmartDosing 底部导航栏
 *
 * 1. 动态根据屏幕宽高选择合适的高度与字号，保证 5 个入口在任何终端都完整显示
 * 2. 只在需要的场景才绘制文字标签，紧凑模式下专注于图标，减少“挤压”导致的半行展示
 * 3. 配合 NavController 状态恢复策略，切换 tab 时仍可保留滚动/搜索等上下文
 */
@Composable
fun SmartDosingBottomNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val configuration = LocalConfiguration.current

    // 依据屏幕尺寸切换最合适的展示参数
    val metrics = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        val isUltraCompactWidth = configuration.screenWidthDp < 360
        val isCompactHeight = configuration.screenHeightDp < 520
        when {
            isUltraCompactWidth || isCompactHeight -> {
                // 紧凑模式：只展示图标，避免与系统手势条抢空间
                BottomBarMetrics(
                    height = 60.dp,
                    iconSize = 22.dp,
                    fontSize = 10.sp,
                    showLabel = false
                )
            }
            configuration.screenWidthDp < 600 -> {
                // 常规手机/小平板：图标+文字
                BottomBarMetrics(
                    height = 66.dp,
                    iconSize = 22.dp,
                    fontSize = 11.sp,
                    showLabel = true
                )
            }
            else -> {
                // 大屏/桌面：更大高度与字号，提升可读性
                BottomBarMetrics(
                    height = 72.dp,
                    iconSize = 24.dp,
                    fontSize = 13.sp,
                    showLabel = true
                )
            }
        }
    }

    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.height),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        // 只处理底部安全间距，兼容手势导航 / 异形全面屏
        windowInsets = NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Bottom)
    ) {
        bottomNavigationDestinations.forEach { destination ->
            val selected = currentRoute == destination.route

            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute != destination.route) {
                        navController.navigate(destination.route) {
                            // 回退到图谱根节点，避免 back stack 无限制膨胀
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true // 避免重复创建相同页面
                            restoreState = true // 恢复历史滚动/筛选状态
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.description,
                        modifier = Modifier.size(metrics.iconSize)
                    )
                },
                label = if (metrics.showLabel) {
                    {
                        Text(
                            text = destination.title,
                            fontSize = metrics.fontSize,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    null
                },
                alwaysShowLabel = metrics.showLabel,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            )
        }
    }
}
