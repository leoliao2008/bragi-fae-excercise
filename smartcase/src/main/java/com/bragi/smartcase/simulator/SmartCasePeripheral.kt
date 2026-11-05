// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcase.simulator

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "SmartCasePeripheral"

/**
 * Bluetooth LE side of the simulator: a GATT server with the SBSC service, and an advertiser.
 * Needs `BLUETOOTH_ADVERTISE` and `BLUETOOTH_CONNECT` on API 31 and higher.
 */
@SuppressLint("MissingPermission")
internal class SmartCasePeripheral(
    context: Context,
    private val requestHandler: (SbscMessage) -> SbscMessage?,
) {
    private val context = context.applicationContext
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val characteristic = BluetoothGattCharacteristic(
        CHARACTERISTIC_UUID,
        BluetoothGattCharacteristic.PROPERTY_NOTIFY or
            BluetoothGattCharacteristic.PROPERTY_WRITE or
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
            BluetoothGattCharacteristic.PROPERTY_READ,
        BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE,
    ).apply {
        addDescriptor(
            BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            ),
        )
    }
    private val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
        addCharacteristic(characteristic)
    }
    private val subscribers = CopyOnWriteArraySet<BluetoothDevice>()

    private class PendingNotification(val device: BluetoothDevice, val bytes: ByteArray)

    private val notificationLock = Any()
    private val notificationQueue = ArrayDeque<PendingNotification>()
    private var notificationInFlight: BluetoothDevice? = null

    private val _isAdvertising = MutableStateFlow(false)

    /** True while advertising is running. */
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    @Volatile private var gattServer: BluetoothGattServer? = null
    @Volatile private var advertiser: BluetoothLeAdvertiser? = null

    /**
     * Open the GATT server and start advertising. Returns false if Bluetooth LE is off or unsupported.
     * Advertising starts asynchronously; observe [isAdvertising].
     */
    fun start(): Boolean {
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth is off")
            return false
        }
        val advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "Bluetooth LE advertising is not supported")
            return false
        }
        val server = bluetoothManager.openGattServer(context, serverCallback)
        if (server == null) {
            Log.e(TAG, "Cannot open GATT server")
            return false
        }
        this.advertiser = advertiser
        gattServer = server
        server.addService(service)
        return true
    }

    /** Stop advertising and close the GATT server. */
    fun stop() {
        advertiser?.stopAdvertising(advertiseCallback)
        advertiser = null
        gattServer?.close()
        gattServer = null
        subscribers.clear()
        synchronized(notificationLock) { notificationQueue.clear(); notificationInFlight = null }
        _isAdvertising.value = false
    }

    /** Send [message] as a notification to every subscribed central. */
    fun sendNotification(message: SbscMessage) {
        Log.d(TAG, "Notify $message")
        val bytes = message.toByteArray()
        subscribers.forEach { notify(it, bytes) }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Service ${service.uuid} added")
                startAdvertising()
            } else {
                Log.e(TAG, "Adding service failed with status $status")
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(TAG, "${device.address}: state $newState, status $status")
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                subscribers.remove(device)
                synchronized(notificationLock) {
                    notificationQueue.removeAll { it.device == device }
                    if (notificationInFlight == device) sendNextNotificationLocked()
                }
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) Log.e(TAG, "Notification to ${device.address} failed with status $status")
            synchronized(notificationLock) { sendNextNotificationLocked() }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            val bytes = value ?: return
            val message = SbscMessage.parse(bytes)
            if (message == null) {
                Log.e(TAG, "Not an SBSC message: ${bytes.joinToString("") { "%02x".format(it) }}")
                return
            }
            val response = requestHandler(message) ?: return
            Log.d(TAG, "Respond $response")
            notify(device, response.toByteArray())
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, ByteArray(0))
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (descriptor.uuid == CCCD_UUID) {
                if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    Log.d(TAG, "${device.address} subscribed")
                    subscribers.add(device)
                } else {
                    Log.d(TAG, "${device.address} unsubscribed")
                    subscribers.remove(device)
                }
            }
            if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor,
        ) {
            val value = if (device in subscribers) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "Advertising")
            _isAdvertising.value = true
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed with error $errorCode")
            _isAdvertising.value = false
        }
    }

    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder().addServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val scanResponse = AdvertiseData.Builder().setIncludeDeviceName(true).build()
        advertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
    }

    private fun notify(device: BluetoothDevice, bytes: ByteArray) {
        synchronized(notificationLock) {
            notificationQueue.addLast(PendingNotification(device, bytes))
            if (notificationInFlight == null) sendNextNotificationLocked()
        }
    }

    private fun sendNextNotificationLocked() {
        notificationInFlight = null
        val server = gattServer
        if (server == null) {
            notificationQueue.clear()
            return
        }
        while (notificationQueue.isNotEmpty()) {
            val next = notificationQueue.removeFirst()
            if (postNotification(server, next)) {
                notificationInFlight = next.device
                return
            }
            Log.e(TAG, "Notification to ${next.device.address} could not be sent")
        }
    }

    @Suppress("DEPRECATION")
    private fun postNotification(server: BluetoothGattServer, notification: PendingNotification): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            server.notifyCharacteristicChanged(notification.device, characteristic, false, notification.bytes) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.value = notification.bytes
            server.notifyCharacteristicChanged(notification.device, characteristic, false)
        }
}
