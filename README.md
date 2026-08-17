# AirType

**Fully offline Android virtual keyboard** that lets you write letters in the air with a Samsung S Pen.

Hold the S Pen side button, draw a letter in the air, release — the character is inserted into any text field.

## Features

- **Air-writing** with S Pen Remote (button + gyroscope / accelerometer)
- **Offline recognition**
  - Geometric classifier works immediately (no download)
  - Google ML Kit Digital Ink Recognition (en-US model ~20 MB, downloaded once)
- **Gesture commands**
  | Gesture              | Action            |
  |----------------------|-------------------|
  | Short stroke         | Letter            |
  | Circle clockwise     | Undo              |
  | Circle counter-clockwise | Space        |
  | Flick left           | Backspace         |
  | Flick right          | Cursor right      |
  | Double button press  | Toggle Shift      |
- **Fallback on-screen QWERTY** (Jetpack Compose)
- **System IME** – selectable as the device keyboard
- Works on Samsung devices with S Pen Remote (Note10 / Note20 / S22 Ultra / Tab S series and later with compatible S Pen)

## Requirements

- Android 9+ (API 28)
- Samsung device with S Pen that supports Air Actions / Remote SDK
- For ML Kit recognition: one-time model download (Wi-Fi recommended)

## Build & Install

### Android Studio

1. Clone this repository
2. Open the project in Android Studio (Hedgehog / Ladybug or newer)
3. Let Gradle sync
4. Run on a physical Samsung device (emulator has no S Pen)

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Enable the keyboard

1. Open the AirType app
2. Tap **Enable AirType in Settings**
3. Toggle AirType on
4. Tap **Select AirType as Keyboard** (or use the system input picker)
5. Pull out the S Pen and start writing

## Project structure

```
app/                  Main application (IME service + Compose UI)
spenremote/           Clean-room S Pen Remote Open SDK (Apache 2.0)
```

## Recognition pipeline

1. Button DOWN → start stroke  
2. AirMotion deltas → absolute path  
3. Button UP → classify gesture  
4. Noise filter → smooth → resample → normalize  
5. Geometric match (instant)  
6. If needed → ML Kit Digital Ink (offline after download)  
7. `InputConnection.commitText()` or special command

## License

Apache License 2.0

Includes [S-Pen-Remote-Open-SDK](https://github.com/david-allison/S-Pen-Remote-Open-SDK) (Apache 2.0).

## Notes

- S Pen Remote events are battery-intensive — listeners are unregistered when the IME is not active.
- Official Samsung S Pen Remote JARs are proprietary and not redistributed. This project uses the open-source reimplementation.
- Geometric accuracy is a bootstrap; ML Kit provides higher quality once the model is present.
