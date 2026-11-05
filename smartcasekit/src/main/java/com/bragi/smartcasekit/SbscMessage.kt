// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcasekit

import java.util.Objects
import java.util.concurrent.atomic.AtomicInteger

/**
 * A single SBSC protocol message.
 *
 * @property id message ID, 0..65535. A response carries the ID of its request.
 * @property type message type.
 * @property action message action.
 * @property payload action-specific bytes, possibly empty.
 */
class SbscMessage(
    val id: Int,
    val type: MessageType,
    val action: MessageAction,
    val payload: ByteArray = ByteArray(0),
) {
    /** First payload byte as an unsigned value (0 means OK), or null if the payload is empty. */
    val status: Int?
        get() = payload.firstOrNull()?.toUnsignedInt()

    /** The message serialized for the wire: ID (2 bytes, little endian), type, action, payload. */
    fun toByteArray(): ByteArray =
        byteArrayOf(
            (id and 0xFF).toByte(),
            ((id shr 8) and 0xFF).toByte(),
            type.code.toByte(),
            action.code.toByte(),
        ) + payload

    override fun equals(other: Any?): Boolean =
        other is SbscMessage &&
            id == other.id &&
            type == other.type &&
            action == other.action &&
            payload.contentEquals(other.payload)

    override fun hashCode(): Int = Objects.hash(id, type, action, payload.contentHashCode())

    override fun toString(): String = "SbscMessage(id=$id, type=$type, action=$action, payload=${payload.toHex()})"

    companion object {
        private const val HEADER_SIZE = 4
        private val lastId = AtomicInteger(-1)

        /** Parse a message from wire bytes. Returns null if [bytes] is not a valid SBSC message. */
        fun parse(bytes: ByteArray): SbscMessage? {
            if (bytes.size < HEADER_SIZE) return null
            val type = MessageType.fromCode(bytes[2].toUnsignedInt()) ?: return null
            val action = MessageAction.fromCode(bytes[3].toUnsignedInt()) ?: return null
            val id = bytes[0].toUnsignedInt() or (bytes[1].toUnsignedInt() shl 8)
            return SbscMessage(id, type, action, bytes.copyOfRange(HEADER_SIZE, bytes.size))
        }

        /** ID for the next request. Wraps after 0xFFFF. */
        internal fun nextId(): Int = lastId.incrementAndGet() and 0xFFFF
    }
}

internal fun Byte.toUnsignedInt(): Int = toInt() and 0xFF

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
