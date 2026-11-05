// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcase.simulator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.svg.SvgDecoder

private val BLUETOOTH_PERMISSIONS: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        emptyArray()
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
                val viewModel: SmartCaseViewModel = viewModel()
                val context = LocalContext.current
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { results ->
                    if (results.values.all { it }) viewModel.start()
                }
                LaunchedEffect(Unit) {
                    val granted = BLUETOOTH_PERMISSIONS.all {
                        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (granted) viewModel.start() else launcher.launch(BLUETOOTH_PERMISSIONS)
                }
                val buttons by viewModel.buttons.collectAsStateWithLifecycle()
                val isAdvertising by viewModel.isAdvertising.collectAsStateWithLifecycle()
                SmartCaseScreen(buttons, isAdvertising, viewModel::sendButtonEvent)
            }
        }
    }
}
