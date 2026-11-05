// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcasekit

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SbscMessageTest {

    @Test
    fun messageRoundTrip() {
        val original = SbscMessage(
            id = 42,
            type = MessageType.REQUEST,
            action = MessageAction.SET_BUTTON_BACKGROUND,
            payload = byteArrayOf(0, 10, 20, 30),
        )

        val decoded = SbscMessage.parse(original.toByteArray())

        assertEquals(original, decoded)
    }

    @Test
    fun idIsLittleEndian() {
        val message = SbscMessage(id = 0x1234, type = MessageType.REQUEST, action = MessageAction.GET_BUTTONS)

        assertArrayEquals(byteArrayOf(0x34, 0x12, 0x00, 0x01), message.toByteArray())
    }

    @Test
    fun messageTooShortIsRejected() {
        assertNull(SbscMessage.parse(byteArrayOf(0, 1)))
    }
}
