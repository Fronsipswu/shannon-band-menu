# Shannon Band Menu

A root Android app for locking bands on Shannon Google Pixels. Supports GSM, WCDMA, LTE, NR-SA and NR-NSA band selection, RAT locks, and NR mode switching, backed by a native daemon (`shannon-bandlockd`) that talks to the modem over root shell.

<img width="275" alt="image" src="https://github.com/user-attachments/assets/e2033c4f-4345-4d7f-ae5c-fd6f9c5c640f" />



## Requirements

- Root access (Magisk/KernelSU)
- Android 11 or newer

## Building

The Android app uses Material 3 and requires JDK 17. Open the `app/` folder in Android Studio, or run a clean build from the command line:

```shell
cd app
./gradlew clean assembleDebug
```

On Windows PowerShell, use `./gradlew.bat clean assembleDebug` instead. The debug APK is output to `app/build/outputs/apk/debug/ShannonBandMenu-debug.apk` and is minified and resource-shrunk.

For a release build, run `./gradlew clean assembleRelease`. The output in `app/build/outputs/apk/release/` must be signed with your release key before distribution.

For the local Windows checkout with the Android SDK at `D:\AndroidSDK`, tests and the
R8 release build can be run with:

```powershell
$env:ANDROID_HOME = 'D:\AndroidSDK'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
Set-Location .\app
.\gradlew.bat test assembleRelease

$apk = Get-Item .\build\outputs\apk\release\ShannonBandMenu-release.apk
if ($apk.Length -ge 3MB) { throw "Release APK is $($apk.Length) bytes; expected under 3 MiB." }
```

The release variant currently uses the machine's debug signing key. An APK built on a
different machine therefore cannot update an existing installation unless both builds
use the same signing key.

### Standalone command-line build

The rooted Android/Termux command-line version uses stable release filenames:

```powershell
$clang = Join-Path $env:ANDROID_NDK_HOME 'toolchains\llvm\prebuilt\windows-x86_64\bin\clang.exe'
& $clang -target aarch64-linux-android34 -O2 -nostdlib -fuse-ld=lld -static `
    -Wall -Wextra -Werror shannon-band-menu.c -o shannon-band-menu
```

Release identity comes from `PROGRAM_VERSION` in the source and the corresponding Git tag/release. Do not rename these files to include `v5`, `v6`, or another release number.

## Usage

1. Install the APK and grant root access.
2. Select the RATs and bands you want on the **Bands** page and tap **Apply**. **Reset** restores defaults.
3. Use the overflow menu's **Band display settings** option to filter which bands appear on the main page and are included when applying.

Bands hidden by the settings filter are excluded from Apply, but the modem capability query itself is never changed.

## Acknowledgements

Base UI design by [@h3nnes](https://github.com/h3nnes). Built with Material 3 and libsu.
