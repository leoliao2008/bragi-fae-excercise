// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcasekit

/**
 * A button event notification from the smart case.
 *
 * @property buttonIndex index of the button, 0..3.
 * @property type event type. The protocol defines only [TAP].
 */
data class ButtonEvent(val buttonIndex: Int, val type: Int) {
    companion object {
        /** The button was tapped. */
        const val TAP = 0x00
    }
}
