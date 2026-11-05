// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcasekit

import java.util.UUID

/** BLE identifiers of the Simple Bragi Smart Case (SBSC) protocol. */
object SbscProtocol {
    /** UUID of the SBSC GATT service. */
    val SERVICE_UUID: UUID = UUID.fromString("E2FEBAD9-CEED-4FD7-A066-DD91AFAA09F7")

    /** UUID of the SBSC characteristic. All SBSC messages go through it. */
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("1CA28021-E4F7-4F05-B833-0DB9A7B1DC86")

    internal val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
