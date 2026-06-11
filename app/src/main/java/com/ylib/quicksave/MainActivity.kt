package com.ylib.quicksave

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ylib.quicksave.app.QuickSaveApplication
import com.ylib.quicksave.overlay.OverlayService
import com.ylib.quicksave.service.ClipboardMonitorService
import com.ylib.quicksave.ui.screens.HomeScreen
import com.ylib.quicksave.ui.screens.SettingsScreen
import com.ylib.quicksave.ui.theme.QuickSaveTheme
import com.ylib.quicksave.ui.viewmodel.HomeViewModel
import com.ylib.quicksave.util.PermissionHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        ContextCompat.startForegroundService(this, Intent(this, ClipboardMonitorService::class.java))
        lifecycleScope.launch {
            val app = application as QuickSaveApplication
            if (app.overlayRepository.isEnabled().first() &&
                PermissionHelper.canDrawOverlays(this@MainActivity)
            ) {
                this@MainActivity.startService(
                    Intent(this@MainActivity, OverlayService::class.java)
                )
            }
        }
        setContent {
            QuickSaveTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                    AppNavigation(homeViewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(homeViewModel: HomeViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController, homeViewModel) }
        composable("settings") { SettingsScreen(navController) }
    }
}
