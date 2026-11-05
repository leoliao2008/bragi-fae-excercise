// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcase.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder

private val BLUETOOTH_PERMISSIONS: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            setSingletonImageLoaderFactory { context ->
                ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
            }
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    PermissionGate { SmartCaseApp() }
                }
            }
        }
    }
}

/** Explains and requests the Bluetooth permissions, then shows [content]. */
@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            BLUETOOTH_PERMISSIONS.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED },
        )
    }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        granted = results.values.all { it }
        denied = !granted
    }
    when {
        granted -> content()
        denied -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.permission_denied))
        }
        else -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.bluetooth_permission_title)) },
            text = { Text(stringResource(R.string.bluetooth_permission_rationale)) },
            confirmButton = {
                TextButton(onClick = { launcher.launch(BLUETOOTH_PERMISSIONS) }) {
                    Text(stringResource(R.string.continue_button))
                }
            },
        )
    }
}

@Composable
private fun SmartCaseApp(viewModel: SmartCaseViewModel = viewModel()) {
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val device = selectedDevice
    if (device == null) {
        val devices by viewModel.devices.collectAsStateWithLifecycle()
        DeviceListScreen(devices = devices, onRefresh = viewModel::refresh, onSelect = viewModel::select)
    } else {
        val highlightedButton by viewModel.highlightedButton.collectAsStateWithLifecycle()
        DeviceDetailScreen(
            device = device,
            highlightedButton = highlightedButton,
            onButtonClick = viewModel::onButtonClick,
            onDisconnect = viewModel::deselect,
        )
    }
}
