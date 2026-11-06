# Earbud mic

A Quick Settings tile for Android 12 and later. While it is on, apps that record from the
normal microphone use your Bluetooth headset's mic instead of the phone's mic.

## Why

On Android, an app records from a Bluetooth headset's mic only if the app asks for the
headset's call link (SCO). Many apps never ask. Beeper, for example, records voice
messages with the phone's mic even when earbuds are connected. Earbud mic opens the call
link for them.

## Install

1. On your phone, download the APK file from
   [the latest release](https://github.com/worldofgeese/earbud-mic/releases/latest).
2. Open the file. If Android asks, let your browser or file app install unknown apps.
3. Open Quick Settings, tap the edit (pencil) button and drag **Earbud mic** into your
   tiles.
4. Optional: to get the **Turn off** button in a notification, open Settings → Apps →
   Earbud mic → Notifications and turn notifications on.

## Use

1. Connect your Bluetooth headset.
2. Swipe down to open Quick Settings and tap **Earbud mic**.
3. Record in any app.
4. Tap **Earbud mic** again when you finish. You can also use **Turn off** in its
   notification.

While it is on, all audio plays at phone-call quality, including music. It turns itself
off when the headset disconnects. If no headset is connected, the tile shows
"No headset".

## How it works

The app sets the headset's call link (SCO) as the communication device. Android honours
that request only while the app plays or records audio, so the app plays silence. While
the call link is the communication device, Android routes recordings from the `MIC` and
`DEFAULT` sources to the headset mic. The app never puts the phone into call mode, so other
apps can still record.

The app has no internet permission and collects no data.

## Build from source

You need [Nix](https://nixos.org/) with flakes enabled. `build.sh` gets the Android SDK
from Nixpkgs, which accepts the Android SDK licence for you. To use an SDK you already
have, set `ANDROID_HOME`. It must contain build-tools 36.1.0 and platform 36.

```sh
./build.sh
adb install -r build/earbud-mic.apk
adb shell pm grant local.earbudmic android.permission.POST_NOTIFICATIONS
adb shell cmd statusbar add-tile local.earbudmic/.MicTileService
```

The first build creates `signing.keystore`. Keep it. Updates install over the old app only
if you sign them with the same key.

## Tested with

- Pixel Fold, Android 17 beta
- HUAWEI FreeBuds Pro 5
- Beeper 4.63.0 voice messages

## Licence

MIT. See [LICENSE](LICENSE).
