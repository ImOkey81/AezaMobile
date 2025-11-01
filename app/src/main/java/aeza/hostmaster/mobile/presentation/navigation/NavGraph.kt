package aeza.hostmaster.mobile.presentation.navigation

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import aeza.hostmaster.mobile.presentation.screens.*
import android.annotation.SuppressLint

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun NavGraph(navController: NavHostController) {
    val items = listOf("ping", "http", "tcp", "dns", "info")
    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { screen ->
                    NavigationBarItem(
                        selected = navController.currentDestination?.route == screen,
                        onClick = { navController.navigate(screen) },
                        label = { Text(screen.uppercase()) },
                        icon = {}
                    )
                }
            }
        }
    ) {
        NavHost(navController, startDestination = "ping") {
            composable("ping") { CheckScreen("ping") }
            composable("http") { CheckScreen("http") }
            composable("tcp") { CheckScreen("tcp") }
            composable("dns") { CheckScreen("dns") }
            composable("info") { CheckScreen("info") }
        }
    }
}
