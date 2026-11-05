// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcase.simulator

/** One simulated button: an image index and a background color. All values are 0..255. */
data class ButtonState(
    val imageIndex: Int = 0,
    val red: Int = 32,
    val green: Int = 32,
    val blue: Int = 32,
)
