package aeza.hostmaster.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import aeza.hostmaster.mobile.presentation.navigation.NavGraph
import aeza.hostmaster.mobile.ui.theme.AezaHostTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AezaHostTheme {
                val navController = rememberNavController()
                NavGraph(navController)
            }
        }
    }
}
