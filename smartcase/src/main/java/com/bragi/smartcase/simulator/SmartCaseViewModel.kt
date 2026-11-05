// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcase.simulator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

class SmartCaseViewModel(application: Application) : AndroidViewModel(application) {
    private val smartCase = SmartCase(application)
    private val peripheral = SmartCasePeripheral(application, smartCase::handleRequest)

    /** Current buttons. */
    val buttons: StateFlow<List<ButtonState>> = smartCase.buttons

    /** True while the simulator is advertising. */
    val isAdvertising: StateFlow<Boolean> = peripheral.isAdvertising

    private var started = false

    /** Start the GATT server and advertising. Call after the Bluetooth permissions are granted. */
    fun start() {
        if (started) return
        started = peripheral.start()
    }

    /** Notify the connected app that button [buttonIndex] produced [event]. */
    fun sendButtonEvent(buttonIndex: Int, event: Int) {
        peripheral.sendNotification(smartCase.buttonEvent(buttonIndex, event))
    }

    override fun onCleared() {
        peripheral.stop()
    }
}
