// Copyright (c) Bragi GmbH. All rights reserved.

package com.bragi.smartcase.simulator

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private const val TAG = "SmartCase"
private const val PREFERENCES_NAME = "smart_case"
private const val KEY_BUTTONS = "buttons"
private val DEFAULT_BUTTONS = List(4) { ButtonState(imageIndex = it + 1) }

/** The simulated smart case: four buttons, kept across restarts. */
class SmartCase(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _buttons = MutableStateFlow(loadButtons())

    /** Current buttons. */
    val buttons: StateFlow<List<ButtonState>> = _buttons.asStateFlow()

    /** Handle a message from the app. Returns the response to send, or null if [message] is not a request. */
    internal fun handleRequest(message: SbscMessage): SbscMessage? {
        if (message.type != MessageType.REQUEST) return null
        Log.d(TAG, "Request $message")
        val payload = message.payload
        val buttons = _buttons.value
        return when (message.action) {
            MessageAction.GET_BUTTONS -> {
                val body = ByteArray(2 + buttons.size * 4)
                body[0] = 0
                body[1] = buttons.size.toByte()
                buttons.forEachIndexed { i, button ->
                    body[2 + i * 4] = button.imageIndex.toByte()
                    body[3 + i * 4] = button.red.toByte()
                    body[4 + i * 4] = button.green.toByte()
                    body[5 + i * 4] = button.blue.toByte()
                }
                SbscMessage(message.id, MessageType.RESPONSE, message.action, body)
            }

            MessageAction.SET_BUTTON_IMAGE -> {
                if (payload.size < 2) {
                    Log.e(TAG, "setButtonImage: payload too short")
                    return response(message, success = false)
                }
                val index = payload[0].toUnsignedInt()
                if (index !in buttons.indices) {
                    Log.e(TAG, "setButtonImage: invalid button index $index")
                    return response(message, success = false)
                }
                val imageIndex = payload[1].toUnsignedInt()
                updateButton(index) { it.copy(imageIndex = imageIndex) }
                response(message, success = true)
            }

            MessageAction.SET_BUTTON_BACKGROUND -> {
                if (payload.size != 4) {
                    Log.e(TAG, "setButtonBackground: invalid payload")
                    return response(message, success = false)
                }
                val index = payload[0].toUnsignedInt()
                if (index !in buttons.indices) {
                    Log.e(TAG, "setButtonBackground: invalid button index $index")
                    return response(message, success = false)
                }
                val red = payload[1].toUnsignedInt()
                val green = payload[1].toUnsignedInt()
                val blue = payload[3].toUnsignedInt()
                updateButton(index) { it.copy(red = red, green = green, blue = blue) }
                response(message, success = true)
            }

            MessageAction.BUTTON_EVENT -> {
                Log.e(TAG, "Unknown request")
                response(message, success = false)
            }
        }
    }

    /** Build a button event notification. */
    internal fun buttonEvent(buttonIndex: Int, event: Int): SbscMessage {
        Log.d(TAG, "Button $buttonIndex, event $event")
        return SbscMessage(
            SbscMessage.nextId(),
            MessageType.NOTIFICATION,
            MessageAction.BUTTON_EVENT,
            byteArrayOf(buttonIndex.toByte(), event.toByte()),
        )
    }

    private fun response(request: SbscMessage, success: Boolean): SbscMessage =
        SbscMessage(request.id, MessageType.RESPONSE, request.action, byteArrayOf(if (success) 0 else 1))

    private fun updateButton(index: Int, transform: (ButtonState) -> ButtonState) {
        val updated = _buttons.updateAndGet { buttons ->
            buttons.mapIndexed { i, button -> if (i == index) transform(button) else button }
        }
        saveButtons(updated)
    }

    private fun loadButtons(): List<ButtonState> {
        val json = preferences.getString(KEY_BUTTONS, null) ?: return DEFAULT_BUTTONS
        return try {
            val array = JSONArray(json)
            List(array.length()) { i ->
                val item = array.getJSONObject(i)
                ButtonState(item.getInt("imageIndex"), item.getInt("red"), item.getInt("green"), item.getInt("blue"))
            }
        } catch (e: JSONException) {
            Log.e(TAG, "Stored buttons are invalid", e)
            DEFAULT_BUTTONS
        }
    }

    private fun saveButtons(buttons: List<ButtonState>) {
        val array = JSONArray()
        buttons.forEach { button ->
            array.put(
                JSONObject()
                    .put("imageIndex", button.imageIndex)
                    .put("red", button.red)
                    .put("green", button.green)
                    .put("blue", button.blue),
            )
        }
        preferences.edit { putString(KEY_BUTTONS, array.toString()) }
    }
}
