# Shannon Band Menu

A root Android app for locking bands on Shannon Google Pixels. Supports GSM, WCDMA, LTE and NR (SA/NSA) band selection, RAT locks, and NR mode switching, backed by a native daemon (`shannon-bandlockd`) that talks to the modem over root shell.

<img width="300" alt="image" src="https://github.com/user-attachments/assets/d55a31db-3080-47db-af61-6368d3f5aae1" />


## Requirements

- Root access (Magisk/KernelSU)
- Android 11 or newer
- Shannon modem

## Building

Open the `app/` folder in Android Studio, or run:

```
cd app
./gradlew assembleDebug
```

The APK is output to `app/build/outputs/apk/debug/`.

## Usage

1. Install the APK and grant root access.
2. Select the RATs and bands you want on the **Bands** page and tap **Apply**. **Reset** restores defaults.
3. Use **Info > Settings** to filter which bands appear on the main page and are included when applying.

Bands hidden by the settings filter are excluded from Apply, but the modem capability query itself is never changed.

## Acknowlegements

UI by [@h3nnes](https://github.com/h3nnes), built with the miuix framework, libsu and AndroidLiquidGlass.
