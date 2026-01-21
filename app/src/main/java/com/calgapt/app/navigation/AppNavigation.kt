package com.calgapt.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.calgapt.app.ui.screens.MainScreen
import com.calgapt.app.ui.screens.SettingsScreen
import com.calgapt.app.ui.screens.EventDetailsScreen

import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.calgapt.app.data.models.CalendarEvent
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") { 
            MainScreen(
                onSettingsClick = { navController.navigate("settings") },
                onEventGenerated = { eventJson -> 
                    navController.navigate("event_details/$eventJson") 
                }
            ) 
        }
        composable("settings") { 
            SettingsScreen(onBack = { navController.popBackStack() }) 
        }
        composable(
            route = "event_details/{eventJson}",
            arguments = listOf(navArgument("eventJson") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventJson = backStackEntry.arguments?.getString("eventJson") ?: ""
            // Decode needed if URL encoded
            EventDetailsScreen(
                eventJson = eventJson, 
                onConfirm = { navController.popBackStack() }, // Simplification: Pop back after confirm
                onBack = { navController.popBackStack() }
            ) 
        }
    }
}
