package com.scooterre.client.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

private const val ROUTE_HOME = "home"
private const val ROUTE_APP = "app"
private const val ROUTE_DEVICE = "device/{mac}"
private const val ROUTE_VEHICLE_SETTINGS = "device/{mac}/settings"

/**
 * Entry point - hosts navigation between [HomeScreen] (scooter picker + phone relay status),
 * [DeviceScreen] (one scooter's direct connection and actions - the "dashboard" for that scooter)
 * and, one level deeper, [VehicleSettingsScreen]; [AppScreen] (updater) sits next to [DeviceScreen]
 * off [HomeScreen]. Swiping back from the vehicle-settings screen always lands back on that
 * scooter's dashboard, never further, since each swipe pops exactly one level of this stack.
 */
class MainActivity : ComponentActivity() {
    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureBluetoothPermissions()
        setContent {
            ScooterLinkNavHost()
        }
    }

    private fun ensureBluetoothPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val needed = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) requestPermissions.launch(needed.toTypedArray())
    }
}

@Composable
private fun ScooterLinkNavHost() {
    val navController = rememberSwipeDismissableNavController()
    SwipeDismissableNavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            HomeScreen(
                onOpenDevice = { mac -> navController.navigate("device/$mac") },
                onOpenApp = { navController.navigate(ROUTE_APP) },
            )
        }
        composable(ROUTE_DEVICE, arguments = listOf(navArgument("mac") { type = NavType.StringType })) { entry ->
            val mac = entry.arguments?.getString("mac").orEmpty()
            DeviceScreen(
                mac = mac,
                onDisconnectedBack = { navController.popBackStack(ROUTE_HOME, inclusive = false) },
                onOpenSettings = { m -> navController.navigate("device/$m/settings") },
            )
        }
        composable(ROUTE_VEHICLE_SETTINGS, arguments = listOf(navArgument("mac") { type = NavType.StringType })) { entry ->
            val mac = entry.arguments?.getString("mac").orEmpty()
            VehicleSettingsScreen(mac = mac)
        }
        composable(ROUTE_APP) {
            AppScreen()
        }
    }
}
