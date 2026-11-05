// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcase.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bragi.smartcasekit.SmartCaseDevice

/** List of nearby smart cases. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    devices: List<SmartCaseDevice>,
    onRefresh: () -> Unit,
    onSelect: (SmartCaseDevice) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.device_list_title)) },
                actions = { TextButton(onClick = onRefresh) { Text(stringResource(R.string.refresh)) } },
            )
        },
    ) { padding ->
        if (devices.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.searching))
            }
        } else {
            LazyColumn(contentPadding = padding) {
                items(devices, key = { it.address }) { device ->
                    ListItem(
                        headlineContent = { Text(device.name) },
                        supportingContent = { Text(device.address) },
                        modifier = Modifier.clickable { onSelect(device) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
