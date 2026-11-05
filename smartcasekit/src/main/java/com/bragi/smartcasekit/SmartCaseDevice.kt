// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcasekit

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val TAG = "SmartCaseDevice"
private const val DEFAULT_MTU = 23
private const val ATT_HEADER_SIZE = 3
private const val REQUESTED_MTU = 517
private val CONNECT_TIMEOUT = 10.seconds
private val MTU_TIMEOUT = 5.seconds
private val DISCOVERY_TIMEOUT = 10.seconds
private val DESCRIPTOR_TIMEOUT = 5.seconds
private val REQUEST_TIMEOUT = 5.seconds
private val DISCONNECT_TIMEOUT = 5.seconds

/**
 * A discovered smart case. Get instances from [SmartCaseScanner.devices].
 *
 * Threading: Bluetooth callbacks arrive on a binder thread. All flows are thread-safe and may emit on any
 * thread, so collect them on the dispatcher you need. All functions are main-safe. Requests are sent one at a time.
 *
 * The caller must hold `BLUETOOTH_CONNECT` on API 31 and higher.
 */
@SuppressLint("MissingPermission")
class SmartCaseDevice internal constructor(
    context: Context,
    private val bluetoothDevice: BluetoothDevice,
    /** Advertised name of the smart case. */
    val name: String,
) {
    private val context = context.applicationContext

    /** Bluetooth address of the smart case. */
    val address: String
        get() = bluetoothDevice.address

    private val _isConnected = MutableStateFlow(false)

    /** True while connected and ready for requests. */
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _buttons = MutableStateFlow<List<SmartCaseButton>>(emptyList())

    /** Last known buttons. Updated by [fetchButtons], [setButtonImage] and [setButton]. */
    val buttons: StateFlow<List<SmartCaseButton>> = _buttons.asStateFlow()

    private val _buttonEvents = MutableSharedFlow<ButtonEvent>(extraBufferCapacity = 16)

    /** Button events sent by the smart case while connected. */
    val buttonEvents: SharedFlow<ButtonEvent> = _buttonEvents.asSharedFlow()

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var characteristic: BluetoothGattCharacteristic? = null
    @Volatile private var mtu = DEFAULT_MTU
    @Volatile private var connectionWaiter: CompletableDeferred<Unit>? = null
    @Volatile private var mtuWaiter: CompletableDeferred<Unit>? = null
    @Volatile private var servicesWaiter: CompletableDeferred<Unit>? = null
    @Volatile private var descriptorWaiter: CompletableDeferred<Unit>? = null
    @Volatile private var disconnectWaiter: CompletableDeferred<Unit>? = null
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<SbscMessage>>()
    private val requestMutex = Mutex()
    private val connectionMutex = Mutex()

    /**
     * Connect, discover the SBSC service, enable notifications, and fetch the buttons.
     * Returns once the device is ready for requests.
     *
     * @throws SmartCaseException if a step fails or times out. The connection is closed in that case.
     */
    suspend fun connect() {
        connectionMutex.withLock {
            if (_isConnected.value) return
            Log.d(TAG, "Connect $address")
            try {
                awaitCallback("connect", CONNECT_TIMEOUT) { waiter ->
                    connectionWaiter = waiter
                    gatt = bluetoothDevice.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                }
                val gatt = gatt ?: throw SmartCaseException.NotConnected()
                try {
                    awaitCallback("requestMtu", MTU_TIMEOUT) { waiter ->
                        mtuWaiter = waiter
                        if (!gatt.requestMtu(REQUESTED_MTU)) {
                            waiter.completeExceptionally(SmartCaseException.GattFailure("requestMtu did not start"))
                        }
                    }
                } catch (e: SmartCaseException) {
                    Log.w(TAG, "MTU request failed, using $mtu", e)
                }
                awaitCallback("discoverServices", DISCOVERY_TIMEOUT) { waiter ->
                    servicesWaiter = waiter
                    if (!gatt.discoverServices()) {
                        waiter.completeExceptionally(SmartCaseException.GattFailure("discoverServices did not start"))
                    }
                }
                val characteristic = gatt.getService(SbscProtocol.SERVICE_UUID)
                    ?.getCharacteristic(SbscProtocol.CHARACTERISTIC_UUID)
                    ?: throw SmartCaseException.GattFailure("SBSC characteristic not found")
                val cccd = characteristic.getDescriptor(SbscProtocol.CCCD_UUID)
                    ?: throw SmartCaseException.GattFailure("Notification descriptor not found")
                gatt.setCharacteristicNotification(characteristic, true)
                awaitCallback("enableNotifications", DESCRIPTOR_TIMEOUT) { waiter ->
                    descriptorWaiter = waiter
                    if (!writeDescriptor(gatt, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                        waiter.completeExceptionally(SmartCaseException.GattFailure("Descriptor write did not start"))
                    }
                }
                this.characteristic = characteristic
                _isConnected.value = true
                Log.d(TAG, "Connected $address")
            } catch (e: Throwable) {
                Log.w(TAG, "Connect failed", e)
                closeGatt()
                throw e
            }
        }
        try {
            fetchButtons()
        } catch (e: SmartCaseException) {
            Log.w(TAG, "Fetching buttons failed", e)
        }
    }

    /** Disconnect. Returns once disconnected, or after a timeout. The connection is closed in both cases. */
    suspend fun disconnect() {
        val gatt = gatt ?: return
        Log.d(TAG, "Disconnect $address")
        try {
            awaitCallback("disconnect", DISCONNECT_TIMEOUT) { waiter ->
                disconnectWaiter = waiter
                gatt.disconnect()
            }
        } catch (e: SmartCaseException.Timeout) {
            Log.w(TAG, "Disconnect timed out", e)
        } finally {
            closeGatt()
        }
    }

    /**
     * Read the buttons from the smart case, update [buttons], and return them.
     *
     * @throws SmartCaseException if not connected, or if the request fails or times out.
     */
    suspend fun fetchButtons(): List<SmartCaseButton> {
        val response = sendRequest(SbscMessage(SbscMessage.nextId(), MessageType.REQUEST, MessageAction.GET_BUTTONS))
        val payload = response.payload
        if (payload.size < 2 || response.status != 0) throw SmartCaseException.InvalidResponse(response.toString())
        val buttonCount = payload[1].toUnsignedInt()
        if (payload.size < 2 + buttonCount * 4) throw SmartCaseException.InvalidResponse(response.toString())
        val buttons = (0 until buttonCount).map { buttonIndex ->
            val offset = 2 + buttonIndex * 4
            SmartCaseButton(
                imageIndex = payload[offset + 1].toUnsignedInt(),
                backgroundRed = payload[offset + 1].toUnsignedInt(),
                backgroundGreen = payload[offset + 2].toUnsignedInt(),
                backgroundBlue = payload[offset + 3].toUnsignedInt(),
            )
        }
        Log.d(TAG, "Buttons $buttons")
        _buttons.value = buttons
        return buttons
    }

    /**
     * Set the image of button [index] to [imageIndex].
     *
     * @throws SmartCaseException if not connected, or if the request fails or times out.
     */
    suspend fun setButtonImage(index: Int, imageIndex: Int) {
        val payload = byteArrayOf(index.toByte(), imageIndex.toByte())
        val response = sendRequest(
            SbscMessage(SbscMessage.nextId(), MessageType.REQUEST, MessageAction.SET_BUTTON_IMAGE, payload),
        )
        if (response.status != 0) throw SmartCaseException.InvalidResponse(response.toString())
        updateButton(index) { it.copy(imageIndex = imageIndex) }
    }

    /**
     * Set the background color and the image of button [index].
     *
     * @throws SmartCaseException if not connected, or if a request fails or times out.
     */
    suspend fun setButton(index: Int, imageIndex: Int, red: Int, green: Int, blue: Int) {
        val payload = byteArrayOf(index.toByte(), red.toByte(), green.toByte(), blue.toByte())
        val response = sendRequest(
            SbscMessage(SbscMessage.nextId(), MessageType.REQUEST, MessageAction.SET_BUTTON_BACKGROUND, payload),
        )
        if (response.status != 0) throw SmartCaseException.InvalidResponse(response.toString())
        updateButton(index) { it.copy(backgroundRed = red, backgroundGreen = green, backgroundBlue = blue) }
        setButtonImage(index, imageIndex)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "Connection state $newState, status $status")
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS ->
                    connectionWaiter?.complete(Unit)
                newState == BluetoothProfile.STATE_DISCONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    closeGatt()
                    disconnectWaiter?.complete(Unit)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                servicesWaiter?.complete(Unit)
            } else {
                servicesWaiter?.completeExceptionally(SmartCaseException.GattFailure("Service discovery status $status"))
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                descriptorWaiter?.complete(Unit)
            } else {
                descriptorWaiter?.completeExceptionally(SmartCaseException.GattFailure("Descriptor write status $status"))
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU $mtu, status $status")
            if (status == BluetoothGatt.GATT_SUCCESS) this@SmartCaseDevice.mtu = mtu
            mtuWaiter?.complete(Unit)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleIncoming(value)
        }

        @Deprecated("Called on API 32 and lower")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleIncoming(characteristic.value ?: return)
        }
    }

    private fun handleIncoming(bytes: ByteArray) {
        val message = SbscMessage.parse(bytes)
        if (message == null) {
            Log.e(TAG, "Not an SBSC message: ${bytes.toHex()}")
            return
        }
        when (message.type) {
            MessageType.RESPONSE -> {
                Log.d(TAG, "Response $message")
                val pending = pendingRequests.remove(message.id)
                if (pending == null) Log.e(TAG, "No pending request for $message") else pending.complete(message)
            }
            MessageType.NOTIFICATION -> {
                Log.d(TAG, "Notification $message")
                if (message.action == MessageAction.BUTTON_EVENT && message.payload.size >= 2) {
                    _buttonEvents.tryEmit(
                        ButtonEvent(message.payload[0].toUnsignedInt(), message.payload[1].toUnsignedInt()),
                    )
                }
            }
            MessageType.REQUEST -> Log.e(TAG, "Unexpected request $message")
        }
    }

    private suspend fun sendRequest(request: SbscMessage): SbscMessage = requestMutex.withLock {
        val gatt = gatt
        val characteristic = characteristic
        if (gatt == null || characteristic == null || !_isConnected.value) throw SmartCaseException.NotConnected()
        val bytes = request.toByteArray()
        val maxLength = mtu - ATT_HEADER_SIZE
        if (bytes.size > maxLength) throw SmartCaseException.RequestTooLong(bytes.size, maxLength)
        val response = CompletableDeferred<SbscMessage>()
        pendingRequests[request.id] = response
        try {
            Log.d(TAG, "Request $request")
            if (!writeCharacteristic(gatt, characteristic, bytes)) {
                throw SmartCaseException.GattFailure("Write did not start for $request")
            }
            withTimeoutOrNull(REQUEST_TIMEOUT) { response.await() } ?: throw SmartCaseException.Timeout(request.action.name)
        } finally {
            pendingRequests.remove(request.id)
        }
    }

    private suspend fun awaitCallback(
        operation: String,
        timeout: Duration,
        start: (CompletableDeferred<Unit>) -> Unit,
    ) {
        val waiter = CompletableDeferred<Unit>()
        start(waiter)
        withTimeoutOrNull(timeout) { waiter.await() } ?: throw SmartCaseException.Timeout(operation)
    }

    private fun updateButton(index: Int, transform: (SmartCaseButton) -> SmartCaseButton) {
        _buttons.update { buttons -> buttons.mapIndexed { i, button -> if (i == index) transform(button) else button } }
    }

    private fun closeGatt() {
        gatt?.close()
        gatt = null
        characteristic = null
        mtu = DEFAULT_MTU
        _isConnected.value = false
        pendingRequests.values.forEach { it.completeExceptionally(SmartCaseException.NotConnected()) }
        pendingRequests.clear()
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                value,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            characteristic.value = value
            gatt.writeCharacteristic(characteristic)
        }

    @Suppress("DEPRECATION")
    private fun writeDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = value
            gatt.writeDescriptor(descriptor)
        }

    override fun toString(): String = "SmartCaseDevice(name=$name, address=$address)"
}
