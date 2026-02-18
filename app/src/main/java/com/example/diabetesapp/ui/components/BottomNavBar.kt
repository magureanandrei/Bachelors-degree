package com.example.diabetesapp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    object Home : BottomNavItem("home", Icons.Default.Home, "Home")
    object Bolus : BottomNavItem("bolus", Icons.Default.MedicalServices, "Bolus")
    object History : BottomNavItem("history", Icons.Default.DateRange, "History")
    object Stats : BottomNavItem("stats", Icons.Default.ShowChart, "Stats")
    object Menu : BottomNavItem("menu", Icons.Default.Menu, "Menu")
}

@Composable
fun BottomNavBar(
    selectedRoute: String,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Bolus,
        BottomNavItem.History,
        BottomNavItem.Stats,
        BottomNavItem.Menu
    )

    NavigationBar(
        containerColor = Color.White,
        contentColor = Color(0xFF00897B)
    ) {
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = { Text(item.label) },
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

