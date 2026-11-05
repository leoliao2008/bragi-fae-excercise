# Simple Bragi Smart Case Protocol

_Version 0.1_

_Copyright (c) Bragi Gmbh. All rights reserved._

## General Message Format

* Message ID: 2 bytes (little endian)

* Message Type: 1 byte

  * Request: 0x00
  * Response: 0x01
  * Notification: 0x02

* Action: 1 byte
* Payload: 0 or more bytes

Notes:

* When BLE is used as transport layer, total message size should be under the negotiated maximum characteristic data size between the connected devices

* Message IDs are incremented with every request of notification. For responses, the message ID is the same as the request message ID

## BLE Service

* Service UUID: E2FEBAD9-CEED-4FD7-A066-DD91AFAA09F7
* Characteristic UUID: 1CA28021-E4F7-4F05-B833-0DB9A7B1DC86
  * Properties: Notify, Write, WriteWithoutResponse, Read

## Requests And Responses

### Get Buttons

Get the number of configurable buttons on the smart case and their image indexes and background colors.

* Note: Assumption is that the smart case firmware has 4 buttons and 10-16 image bitmaps that can be assigned to any button.

Request:

* Message Type: Request = 0x00
* Action: GetButtons = 0x01
* Payload: -

Response:

* Message Type: Response = 0x01
* Action: GetButtons = 0x01
* Payload:
  * Status: 1 byte (0: OK, !=0: error)
  * Total number of buttons: 1 byte (= 4)
  * Button 0 image index: 1 byte
  * Button 0 background red component: 1 byte
  * Button 0 background green component: 1 byte
  * Button 0 background blue component: 1 byte
  * Button 1 image index: 1 byte
  * Button 1 background red component: 1 byte
  * Button 1 background green component: 1 byte
  * Button 1 background blue component: 1 byte
  * Button 2 image index: 1 byte
  * Button 2 background red component: 1 byte
  * Button 2 background green component: 1 byte
  * Button 2 background blue component: 1 byte
  * Button 3 image index: 1 byte
  * Button 3 background red component: 1 byte
  * Button 3 background green component: 1 byte
  * Button 3 background blue component: 1 byte

### Set Button Image Index

Set button image to the given image index.

Request:

* Message Type: Request = 0x01
* Action: SetButtonImage = 0x02
* Payload:
  * Button index: 1 byte (0, 1, 2 or 3)
  * Image index: 1 byte

Response:

* Message Type: Response = 0x01
* Action: SetButtonImage = 0x02
* Payload:
  * Status: 1 byte (0: OK, !=0: error)
  
### Set Button Background Color

Set the background RGB color of the given button index.

Request:

* Message Type: Request = 0x00
* Action: SetButtonBackground = 0x03
* Payload:
  * Button index: 1 byte (0, 1, 2 or 3)
  * Background red component: 1 byte
  * Background green component: 1 byte
  * Background blue component: 1 byte

Response:

* Message Type: Response = 0x01
* Action: SetButtonBackground = 0x03
* Payload:
  * Status: 1 byte (0: OK, !=0: error)
  
### Button Event

This notification is sent by the charging case when the user taps on a smart case button.

Notification:

* Message Type: Notification = 0x02
* Action: ButtonEvent = 0xff
* Payload:
  * Button index: 1 byte (0, 1, 2 or 3)
  * Button event: 1 byte (ButtonTap = 0x00)
