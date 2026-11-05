// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcase.simulator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The simulated smart case touchscreen: four buttons on black. */
@Composable
fun SmartCaseScreen(
    buttons: List<ButtonState>,
    isAdvertising: Boolean,
    onButtonEvent: (buttonIndex: Int, event: Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.title),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        if (!isAdvertising) {
            Text(stringResource(R.string.not_advertising), color = Color.White, textAlign = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            buttons.forEachIndexed { index, button ->
                ButtonIcon(
                    imageIndex = button.imageIndex,
                    red = button.red,
                    green = button.green,
                    blue = button.blue,
                    contentDescription = stringResource(R.string.button_description, index + 1),
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clickable { onButtonEvent(index, 0) },
                )
            }
        }
    }
}
