// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcase.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragi.smartcasekit.SmartCaseDevice

private val HighlightColor = Color(0xFFFFA500)

/** Details and buttons of the connected smart case. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    device: SmartCaseDevice,
    highlightedButton: Int?,
    onButtonClick: (Int) -> Unit,
    onDisconnect: () -> Unit,
) {
    val isConnected by device.isConnected.collectAsStateWithLifecycle()
    val buttons by device.buttons.collectAsStateWithLifecycle()
    BackHandler(onBack = onDisconnect)
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.device_detail_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                stringResource(
                    R.string.device_summary,
                    device.name,
                    stringResource(if (isConnected) R.string.yes else R.string.no),
                    buttons.size,
                ),
            )
            Button(onClick = onDisconnect, enabled = isConnected) {
                Text(stringResource(R.string.disconnect))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                buttons.forEachIndexed { index, button ->
                    val highlight = if (highlightedButton == index) {
                        Modifier.border(4.dp, HighlightColor, ButtonShape)
                    } else {
                        Modifier
                    }
                    ButtonIcon(
                        imageIndex = button.imageIndex,
                        red = button.backgroundRed,
                        green = button.backgroundGreen,
                        blue = button.backgroundBlue,
                        contentDescription = stringResource(R.string.button_description, index + 1),
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .then(highlight)
                            .clickable { onButtonClick(index) },
                    )
                }
            }
        }
    }
}
