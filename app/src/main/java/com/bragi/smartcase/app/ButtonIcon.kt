// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcase.app

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** Number of icons in `assets/icons`, named `0.svg` to `15.svg`. */
const val ICON_COUNT = 16

/** Shape of a smart case button. */
val ButtonShape = RoundedCornerShape(12.dp)

/** A smart case button: icon [imageIndex] over an RGB background. */
@Composable
fun ButtonIcon(
    imageIndex: Int,
    red: Int,
    green: Int,
    blue: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = "file:///android_asset/icons/${imageIndex % ICON_COUNT}.svg",
        contentDescription = contentDescription,
        modifier = modifier
            .clip(ButtonShape)
            .background(Color(red, green, blue)),
    )
}
