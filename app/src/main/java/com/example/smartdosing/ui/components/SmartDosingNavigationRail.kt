package com.example.smartdosing.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.smartdosing.navigation.bottomNavigationDestinations
import com.example.smartdosing.ui.theme.SmartDosingTokens

/**
 * 平板/桌面模式下使用的侧边导航
 */
@Composable
fun SmartDosingNavigationRail(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 安全地获取导航目的地
    val destinations = bottomNavigationDestinations
    if (destinations.isEmpty()) return

    val primaryDestinations = if (destinations.size > 1) {
        destinations.dropLast(1)
    } else {
        destinations
    }
    val trailingDestination = if (destinations.size > 1) {
        destinations.last()
    } else {
        null
    }

    NavigationRail(
        modifier = modifier
            .width(96.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            Column(
                modifier = Modifier
                    .padding(vertical = SmartDosingTokens.spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "SmartDosing",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Smart",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) {
        primaryDestinations.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationRailItem(
                selected = selected,
                onClick = { navigateIfNeeded(navController, destination.route, currentRoute) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.description
                    )
                },
                label = { Text(destination.title) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            )
        }
        Spacer(modifier = Modifier.weight(1f))

        // 后退按钮 - 在系统设置上方
        NavigationRailItem(
            selected = false,
            onClick = {
                if (navController.previousBackStackEntry != null) {
                    navController.popBackStack()
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "后退"
                )
            },
            label = { Text("返回") },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )
        )

        // 只有存在trailing destination时才显示
        trailingDestination?.let { destination ->
            NavigationRailItem(
                selected = currentRoute == destination.route,
                onClick = { navigateIfNeeded(navController, destination.route, currentRoute) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.description
                    )
                },
                label = { Text(destination.title) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            )
        }
    }
}

private fun navigateIfNeeded(
    navController: NavController,
    targetRoute: String,
    currentRoute: String?
) {
    if (currentRoute == targetRoute) return
    navController.navigate(targetRoute) {
        popUpTo(navController.graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
