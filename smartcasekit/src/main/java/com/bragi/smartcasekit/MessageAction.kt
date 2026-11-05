// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcasekit

/** Action of an SBSC message. [code] is the byte value on the wire. */
enum class MessageAction(val code: Int) {
    /** Read the button count, image indexes and background colors. */
    GET_BUTTONS(0x01),

    /** Set the image index of one button. */
    SET_BUTTON_IMAGE(0x02),

    /** Set the background color of one button. */
    SET_BUTTON_BACKGROUND(0x03),

    /** A button on the smart case was used. */
    BUTTON_EVENT(0xFF);

    internal companion object {
        fun fromCode(code: Int): MessageAction? = entries.firstOrNull { it.code == code }
    }
}
