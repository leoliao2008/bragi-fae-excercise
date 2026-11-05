// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcasekit

/** A smart case button: an image index plus a background color. */
data class SmartCaseButton(
    /**
     * The index of the image to be displayed on the button. The collection of images and the size of the collection are up to the developer.
     */
    val imageIndex: Int,
    /**
     * The red component of the bg color, between 0 and 255.
     */
    val backgroundRed: Int,
    /**
     * The green component of the bg color, between 0 and 255.
     */
    val backgroundGreen: Int,
    /**
     * The blue component of the bg color, between 0 and 255.
     */
    val backgroundBlue: Int,
)
