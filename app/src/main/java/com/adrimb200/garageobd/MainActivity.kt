package com.adrimb200.garageobd

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import com.adrimb200.garageobd.data.GarageViewModel
import com.adrimb200.garageobd.ui.GarageScreen

class MainActivity : ComponentActivity() {
    private val viewModel: GarageViewModel by viewModels()
    private val hasPermission = mutableStateOf(false)
    private val requestBluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission.value = granted
        if (granted) viewModel.refreshDevices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasPermission.value = checkBluetoothPermission()
        setContent {
            GarageScreen(
                vm = viewModel,
                permissionGranted = hasPermission.value,
                onPermissionRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        hasPermission.value = true
                        viewModel.refreshDevices()
                    }
                },
                onBluetoothSettings = {
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }
            )
        }
    }

    private fun checkBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        hasPermission.value = checkBluetoothPermission()
        if (hasPermission.value) viewModel.refreshDevices()
    }

    override fun onStop() {
        // Sin servicio en primer plano: se cancela el sondeo al salir de la app.
        viewModel.disconnect()
        super.onStop()
    }
}
