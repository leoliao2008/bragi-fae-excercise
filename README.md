# fae-exercise-android

Assignment for Bragi Field Engineer Candidates.

*Copyright (C) Bragi GmbH. All rights reserved.*

## Overview

This repository contains three Gradle modules:

- `smartcasekit` is an Android library. It finds, connects to, and controls a Bragi Smart Case over Bluetooth LE.
- `app` is an Android application named "Smart Case Control". It uses `smartcasekit` to show a list of nearby smart cases. A user can select a smart case from the list. `app` then connects to the smart case and shows its four buttons. A user can tap a button to assign it the next icon and background color from a fixed list.
- `smartcase` is an Android application named "Smart Case". It acts as a Bluetooth LE peripheral, imitating an earbuds charging case with a touchscreen with four buttons. `smartcase` implements the protocol in [`sbsc-protocol.md`](sbsc-protocol.md).

## Requirements

- Android Studio (current stable release), or JDK 17 with the Android SDK (platform 36).
- Two Android phones with Android 10 or newer and Bluetooth LE. The phone that runs `smartcase` must support Bluetooth LE advertising.
- The Bluetooth name of the phone that runs `smartcase` must be 29 characters or shorter, because it is sent in the advertisement.
- On Android 10 and 11, turn on Location on the phone that runs `app`. Android returns no Bluetooth LE scan results without it.

## Building And Running

Open the repository root folder in Android Studio. Run the `smartcase` configuration on one phone and the `app` configuration on the other.

From the command line:

```sh
./gradlew assembleDebug
adb devices
adb -s <SMARTCASE_PHONE_SERIAL> install smartcase/build/outputs/apk/debug/smartcase-debug.apk
adb -s <APP_PHONE_SERIAL> install app/build/outputs/apk/debug/app-debug.apk
```

To run the `smartcasekit` unit tests:

```sh
./gradlew :smartcasekit:testDebugUnitTest
```

Follow these steps to use the apps:

1. Start "Smart Case" on the first phone. Allow the Nearby devices permission if asked. The app starts to advertise itself over Bluetooth.
2. Start "Smart Case Control" on the second phone. Allow Bluetooth access. The app scans for nearby smart cases.
3. Tap the simulated smart case in the list. The app connects to it and shows its four buttons.
4. Tap a button. The app assigns it the next icon and background color. The change appears on the simulated case within a second.
5. Tap a button on the simulated case. The matching button in the app shows an orange border for half a second.
6. Tap Disconnect in the app to close the connection. The app returns to the device list.

## Assignment

QA has found bugs when testing `app` and `smartcase`.

Your assignment is to analyze them, find the root cause and propose fixes.

The bug reports are:

1. When changing button icon and background from `app`, the background color on `smartcase` seems to be slightly wrong. For example when `app` shows a dark red background, `smartcase` shows dark olive.

2. `app` is connected to `smartcase`. When terminating `smartcase` on its phone (swiping it away from the recent apps), `app` doesn't seem to notice it. `app` never reverts to the disconnected state. Some delay noticing it would be acceptable, but nothing happened in the app, even after 30 seconds.

3. Start `smartcase`, then connect to it from `app`. `app` doesn't show the same icons as `smartcase`. When changing icons from `app`, everything is looking good, however when restarting `app`, the icons are wrong again.

4. When `app` starts the first time, the Bluetooth permission prompt says "Allow Bluetooth access to find and control nearby smart watches". This is wrong, the prompt should say "Allow Bluetooth access to find and control nearby smart cases".

5. Tapping a `smartcase` button is visible in `app`, but holding down the button is not visible while it is held. `app` only flashes the button briefly after it is released. Feels like a bug, but maybe this is by design?

6. A new customer was complaining that the properties of the `SmartCaseButton` class in `smartcasekit` are not documented at all, so they don't know how to use them from their app.

# How To Deliver The Solutions

  ### Option 1: Public Git repository

  - Create a public Git repository on GitHub, GitLab or a similar service, with this code as the baseline.
  - Make sure Bragi can open the repository from Europe.
  - For every bugfix, create a new branch in this repository.
  - Deliver the fixes independently in their branches.
  - Tell Bragi the location of the public Git repository and the names of the bugfix branches.

  ### Option 2: Local Git repository as a zip

  - Use this code as the baseline. It is already a Git repository, with `main` as the main branch.
  - For every bugfix, create a new branch from `main`, for example `fix/01-short-name`.
  - Deliver the fixes independently in their branches. Do not merge them into `main`.
  - Write a commit message for each fix that says what changed and why.
  - Zip the whole repository folder, **including the hidden `.git` folder**. Without `.git`, we cannot see your branches or your commit history.
  - Send the zip to Bragi.

  To check that the zip has your branches, unzip it into a new folder and run `git branch`. You should see every bugfix branch in the list.