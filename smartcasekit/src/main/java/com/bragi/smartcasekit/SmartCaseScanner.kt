// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcasekit

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "SmartCaseScanner"

/**
 * Finds nearby smart cases over Bluetooth LE.
 *
 * The caller must hold the scan permissions of the running API level:
 * `ACCESS_FINE_LOCATION` on API 29 and 30, `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` on API 31 and higher.
 */
@SuppressLint("MissingPermission")
class SmartCaseScanner(context: Context) {
    private val context = context.applicationContext
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)

    /**
     * Scans while the flow is collected, and stops scanning when collection is cancelled.
     *
     * Emits an empty list first, then the discovered smart cases, sorted by name, each time a new one is found.
     * The flow fails with [IllegalStateException] if Bluetooth is off or the scan cannot start.
     */
    fun devices(): Flow<List<SmartCaseDevice>> = callbackFlow {
        val scanner = bluetoothManager.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
            ?: throw IllegalStateException("Bluetooth is off")
        val found = LinkedHashMap<String, SmartCaseDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device.address
                if (address in found) return
                val name = result.scanRecord?.deviceName ?: return
                Log.d(TAG, "Found $name ($address)")
                found[address] = SmartCaseDevice(context, result.device, name)
                trySend(found.values.sortedBy { it.name.lowercase() })
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("Scan failed with error $errorCode"))
            }
        }
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SbscProtocol.SERVICE_UUID)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        trySend(emptyList())
        Log.d(TAG, "Start scan")
        scanner.startScan(filters, settings, callback)
        awaitClose {
            Log.d(TAG, "Stop scan")
            runCatching { scanner.stopScan(callback) }
        }
    }
}
