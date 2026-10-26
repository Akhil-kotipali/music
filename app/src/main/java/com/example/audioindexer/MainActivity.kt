package com.example.audioindexer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.audioindexer.ui.MainScreen
import com.example.audioindexer.ui.MainViewModel
import com.example.audioindexer.ui.MainViewModelFactory
import com.example.audioindexer.ui.theme.AudioIndexerTheme
import com.example.audioindexer.util.PermissionUtils

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application as AudioIndexerApp)
    }

    // Launcher for standard runtime permissions (READ_EXTERNAL_STORAGE / READ_MEDIA_AUDIO / POST_NOTIFICATIONS)
    private val standardPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshState(this)
    }

    // Launcher for MANAGE_EXTERNAL_STORAGE All Files Access settings activity
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshState(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.bindServices(this)

        setContent {
            AudioIndexerTheme {
                MainScreen(
                    viewModel = viewModel,
                    onRequestStandardPermissions = {
                        requestStandardPermissions()
                    },
                    onRequestManageStorage = {
                        requestManageStoragePermission()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshState(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.unbindServices(this)
    }

    private fun requestStandardPermissions() {
        val permissions = PermissionUtils.getRequiredRuntimePermissions()
        standardPermissionsLauncher.launch(permissions)
    }

    private fun requestManageStoragePermission() {
        val intent = PermissionUtils.createManageExternalStorageIntent(this)
        manageStorageLauncher.launch(intent)
    }
}
