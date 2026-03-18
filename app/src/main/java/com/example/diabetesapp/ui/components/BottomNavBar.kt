package com.example.diabetesapp.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    object Home : BottomNavItem("home", Icons.Default.Home, "Home")
    object History : BottomNavItem("history", Icons.Default.FormatListBulleted, "History")
    object Stats : BottomNavItem("stats", Icons.Default.Insights, "Insights")
    object Education : BottomNavItem("education", Icons.Default.MenuBook, "Learn")
    object Menu : BottomNavItem("menu", Icons.Default.Menu, "Menu")
}

@Composable
fun BottomNavBar(
    selectedRoute: String,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.History,
        BottomNavItem.Stats,
        BottomNavItem.Education,
        BottomNavItem.Menu
    )

    NavigationBar(
        containerColor = Color.White,
        contentColor = Color(0xFF00897B),
        tonalElevation = 8.dp,
        windowInsets = NavigationBarDefaults.windowInsets,  // handles gesture bar automatically
        modifier = Modifier.height(56.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
    ) {
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        fontSize = 10.sp
                    )
                },
                selected = selectedRoute == item.route,
                onClick = { onNavigate(item.route) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF00897B),
                    selectedTextColor = Color(0xFF00897B),
                    indicatorColor = Color(0xFFE0F2F1),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
            )
        }
    }
}