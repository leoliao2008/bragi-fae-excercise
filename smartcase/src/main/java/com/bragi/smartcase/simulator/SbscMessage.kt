// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcase.simulator

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

internal val SERVICE_UUID: UUID = UUID.fromString("E2FEBAD9-CEED-4FD7-A066-DD91AFAA09F7")
internal val CHARACTERISTIC_UUID: UUID = UUID.fromString("1CA28021-E4F7-4F05-B833-0DB9A7B1DC86")
internal val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

internal enum class MessageType(val code: Int) {
    REQUEST(0x00),
    RESPONSE(0x01),
    NOTIFICATION(0x02),
}

internal enum class MessageAction(val code: Int) {
    GET_BUTTONS(0x01),
    SET_BUTTON_IMAGE(0x02),
    SET_BUTTON_BACKGROUND(0x03),
    BUTTON_EVENT(0xFF),
}

/** A single SBSC protocol message. */
internal class SbscMessage(
    val id: Int,
    val type: MessageType,
    val action: MessageAction,
    val payload: ByteArray = ByteArray(0),
) {
    /** The message serialized for the wire: ID (2 bytes, little endian), type, action, payload. */
    fun toByteArray(): ByteArray =
        byteArrayOf(
            (id and 0xFF).toByte(),
            ((id shr 8) and 0xFF).toByte(),
            type.code.toByte(),
            action.code.toByte(),
        ) + payload

    override fun toString(): String =
        "SbscMessage(id=$id, type=$type, action=$action, payload=${payload.joinToString("") { "%02x".format(it) }})"

    companion object {
        private const val HEADER_SIZE = 4
        private val lastId = AtomicInteger(-1)

        /** Parse a message from wire bytes. Returns null if [bytes] is not a valid SBSC message. */
        fun parse(bytes: ByteArray): SbscMessage? {
            if (bytes.size < HEADER_SIZE) return null
            val type = MessageType.entries.firstOrNull { it.code == bytes[2].toUnsignedInt() } ?: return null
            val action = MessageAction.entries.firstOrNull { it.code == bytes[3].toUnsignedInt() } ?: return null
            val id = bytes[0].toUnsignedInt() or (bytes[1].toUnsignedInt() shl 8)
            return SbscMessage(id, type, action, bytes.copyOfRange(HEADER_SIZE, bytes.size))
        }

        /** ID for the next notification. Wraps after 0xFFFF. */
        fun nextId(): Int = lastId.incrementAndGet() and 0xFFFF
    }
}

internal fun Byte.toUnsignedInt(): Int = toInt() and 0xFF
