// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcasekit

/** Errors thrown by SmartCaseKit. */
sealed class SmartCaseException(message: String) : Exception(message) {
    /** The device is not connected. */
    class NotConnected : SmartCaseException("Not connected")

    /** The serialized request does not fit into one write. */
    class RequestTooLong(size: Int, max: Int) : SmartCaseException("Request too long: $size > $max bytes")

    /** The smart case answered with a malformed or failed response. */
    class InvalidResponse(detail: String) : SmartCaseException("Invalid response: $detail")

    /** A Bluetooth GATT operation could not start, or reported an error. */
    class GattFailure(detail: String) : SmartCaseException("GATT failure: $detail")

    /** A Bluetooth operation did not complete in time. */
    class Timeout(operation: String) : SmartCaseException("Timed out: $operation")
}
