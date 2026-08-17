/**
 * Copyright 2024 David Allison <davidallisongithub@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.davidallison.android.sdk.penremote

import android.os.RemoteException
import android.util.Log
import com.samsung.android.sdk.penremote.ISPenRemoteService
import java.util.EnumMap

/**
 * Manages SPenUnits and provides methods to register/unregister event listeners.
 * Obtained via [SPenRemote.ConnectionResultCallback.onSuccess].
 */
class SPenUnitManager private constructor() {

    companion object {
        private const val TAG = "SPenUnitManager"
        internal val instance = SPenUnitManager()
    }

    internal var remoteService: ISPenRemoteService? = null
        set(value) {
            field = value
            unitCache.clear()
        }

    private var unitCache: MutableMap<SPenUnitType, SPenUnit> = EnumMap(SPenUnitType::class.java)

    fun getUnit(unitType: SPenUnitType): SPenUnit {
        if (remoteService == null) throw RemoteException("Service not connected")
        return unitCache[unitType] ?: SPenUnit(unitType, remoteService!!).also {
            unitCache[unitType] = it
        }
    }

    fun registerSPenEventListener(unit: SPenUnit, listener: SPenEventListener) {
        try {
            unit.registerSpenEventListener(listener)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to register SPenEventListener", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error registering listener", e)
        }
    }

    fun unregisterSpenEventListener(unit: SPenUnit) {
        try {
            unit.unregisterSpenEventListener()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister SPenEventListener", e)
        }
    }

    internal fun clearListeners() {
        unitCache.values.forEach { unregisterSpenEventListener(it) }
    }
}
