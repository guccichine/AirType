AirType – S Pen Remote Open SDK (GitHub version)

The official Samsung S Pen Remote SDK JARs are proprietary and require a Samsung Developer account.
They cannot be redistributed.

Instead, this project uses the clean-room open-source reimplementation:

  https://github.com/david-allison/S-Pen-Remote-Open-SDK
  License: Apache License 2.0

The library has been included as a local Gradle module (:spenremote) rather than a pre-built JAR
because:
1. The open SDK is not published as a binary artifact.
2. Building an AAR requires a full Android SDK + build tools (not available in this environment for automatic packaging).
3. Including the source as a module is the cleanest and most maintainable approach.

How to use in code (Phase 2 onward):

  import io.github.davidallison.android.sdk.penremote.SPenRemote
  import io.github.davidallison.android.sdk.penremote.SPenUnitManager
  import io.github.davidallison.android.sdk.penremote.SPenUnitType
  import io.github.davidallison.android.sdk.penremote.ButtonEvent
  import io.github.davidallison.android.sdk.penremote.AirMotionEvent
  import io.github.davidallison.android.sdk.penremote.SPenEventListener

The public API mirrors the official Samsung documentation closely, but lives under the
io.github.davidallison.android.sdk.penremote package.

AIDL interfaces remain under com.samsung.android.sdk.penremote so the library can talk to
the system S Pen Remote service on compatible Samsung devices.
