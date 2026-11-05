// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcase.app

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bragi.smartcasekit.SmartCaseDevice
import com.bragi.smartcasekit.SmartCaseException
import com.bragi.smartcasekit.SmartCaseScanner
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "SmartCaseViewModel"
private val HIGHLIGHT_DURATION = 500.milliseconds

/** Icons to cycle through when a button is tapped. */
private val ICON_INDICES = (0 until ICON_COUNT).toList()

private data class Rgb(val red: Int, val green: Int, val blue: Int)

/** Dark background colors to cycle through when a button is tapped, one per icon. */
private val BACKGROUND_COLORS = listOf(
    Rgb(60, 20, 20),
    Rgb(20, 60, 20),
    Rgb(20, 20, 60),
    Rgb(60, 60, 20),
    Rgb(20, 60, 60),
    Rgb(60, 20, 60),
    Rgb(70, 40, 20),
    Rgb(40, 20, 70),
    Rgb(20, 70, 40),
    Rgb(70, 20, 40),
    Rgb(40, 70, 20),
    Rgb(20, 40, 70),
    Rgb(55, 35, 15),
    Rgb(15, 55, 35),
    Rgb(35, 15, 55),
    Rgb(55, 15, 35),
)

class SmartCaseViewModel(application: Application) : AndroidViewModel(application) {
    private val scanner = SmartCaseScanner(application)
    private val scanRequests = MutableStateFlow(0)
    private var eventsJob: Job? = null
    private var connectJob: Job? = null

    private val _selectedDevice = MutableStateFlow<SmartCaseDevice?>(null)

    /** The device shown in the detail screen, or null to show the device list. */
    val selectedDevice: StateFlow<SmartCaseDevice?> = _selectedDevice.asStateFlow()

    private val _highlightedButton = MutableStateFlow<Int?>(null)

    /** Index of the button the smart case last reported an event for, or null. */
    val highlightedButton: StateFlow<Int?> = _highlightedButton.asStateFlow()

    /** Nearby smart cases. Scans only while collected and no device is selected. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val devices: StateFlow<List<SmartCaseDevice>> =
        combine(_selectedDevice, scanRequests) { device, _ -> device == null }
            .flatMapLatest { shouldScan ->
                if (shouldScan) {
                    scanner.devices().catch { e ->
                        Log.w(TAG, "Scan failed", e)
                        emit(emptyList())
                    }
                } else {
                    flowOf(emptyList())
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Restart scanning. */
    fun refresh() {
        scanRequests.update { it + 1 }
    }

    /** Disconnect the selected device, if any, then connect [device]. */
    fun select(device: SmartCaseDevice) {
        val previous = _selectedDevice.value
        if (previous == device) return
        _selectedDevice.value = device
        _highlightedButton.value = null
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            device.buttonEvents.collectLatest { event ->
                _highlightedButton.value = event.buttonIndex
                delay(HIGHLIGHT_DURATION)
                _highlightedButton.value = null
            }
        }
        val previousConnect = connectJob
        connectJob = viewModelScope.launch {
            previousConnect?.cancelAndJoin()
            previous?.disconnect()
            try {
                device.connect()
            } catch (e: SmartCaseException) {
                Log.w(TAG, "Connecting to $device failed", e)
                if (_selectedDevice.value === device) deselect()
            }
        }
    }

    /** Disconnect the selected device and return to the device list. */
    fun deselect() {
        val device = _selectedDevice.value ?: return
        _selectedDevice.value = null
        eventsJob?.cancel()
        _highlightedButton.value = null
        val pendingConnect = connectJob
        connectJob = null
        viewModelScope.launch {
            pendingConnect?.cancelAndJoin()
            device.disconnect()
        }
    }

    /** Assign the next icon and background color to button [index]. */
    fun onButtonClick(index: Int) {
        val device = _selectedDevice.value ?: return
        val button = device.buttons.value.getOrNull(index) ?: return
        val nextImageIndex = ICON_INDICES[(ICON_INDICES.indexOf(button.imageIndex) + 1) % ICON_INDICES.size]
        val colorPosition = BACKGROUND_COLORS.indexOf(Rgb(button.backgroundRed, button.backgroundGreen, button.backgroundBlue))
        val nextColor = BACKGROUND_COLORS[(colorPosition + 1) % BACKGROUND_COLORS.size]
        viewModelScope.launch {
            try {
                device.setButton(index, nextImageIndex, nextColor.red, nextColor.green, nextColor.blue)
            } catch (e: SmartCaseException) {
                Log.w(TAG, "Setting button $index failed", e)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        val device = _selectedDevice.value ?: return
        // viewModelScope is already cancelled here; disconnect() is bounded by its own timeout.
        GlobalScope.launch { device.disconnect() }
    }
}
