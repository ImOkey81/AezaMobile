package aeza.hostmaster.mobile.presentation.navigation

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import aeza.hostmaster.mobile.domain.model.CheckType
import aeza.hostmaster.mobile.presentation.screens.CheckScreen
import androidx.annotation.SuppressLint
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun NavGraph(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val items = CheckType.items

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { type ->
                    NavigationBarItem(
                        selected = currentRoute == type.route,
                        onClick = {
                            if (currentRoute != type.route) {
                                navController.navigate(type.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        label = { Text(type.title) },
                        icon = {}
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = CheckType.Ping.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            items.forEach { type ->
                composable(type.route) {
                    CheckScreen(checkType = type)
                }
            }
        }
    }
}
