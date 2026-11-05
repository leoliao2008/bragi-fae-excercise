// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcasekit

/** Type of an SBSC message. [code] is the byte value on the wire. */
enum class MessageType(val code: Int) {
    /** Sent by the app. The smart case answers with a [RESPONSE]. */
    REQUEST(0x00),

    /** Answer to a [REQUEST]. Carries the ID of the request. */
    RESPONSE(0x01),

    /** Sent by the smart case on its own. */
    NOTIFICATION(0x02);

    internal companion object {
        fun fromCode(code: Int): MessageType? = entries.firstOrNull { it.code == code }
    }
}
