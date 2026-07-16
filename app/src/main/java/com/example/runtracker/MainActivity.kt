package com.example.runtracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.runtracker.ui.HistoryScreen
import com.example.runtracker.ui.MainScreen
import com.example.runtracker.ui.SessionDetailScreen
import com.example.runtracker.ui.theme.RunTrackerTheme
import com.example.runtracker.viewmodel.TrackingViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RunTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    var permissionsGranted by remember { mutableStateOf(false) }

    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        launcher.launch(requiredPermissions.toTypedArray())
    }

    if (!permissionsGranted) {
        PermissionRationale(onRetry = { launcher.launch(requiredPermissions.toTypedArray()) })
    } else {
        val viewModel: TrackingViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "main") {
            composable("main") {
                MainScreen(
                    viewModel = viewModel,
                    onOpenHistory = { navController.navigate("history") }
                )
            }
            composable("history") {
                HistoryScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenSession = { id -> navController.navigate("detail/$id") }
                )
            }
            composable("detail/{sessionId}") { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId")?.toLongOrNull() ?: return@composable
                SessionDetailScreen(
                    viewModel = viewModel,
                    sessionId = sessionId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun PermissionRationale(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "RunTracker needs location access to measure your distance and pace using GPS. " +
                "This works fully offline \u2014 no internet connection is used.",
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text("Grant permission")
        }
    }
}
