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

import com.samsung.android.sdk.penremote.ISpenEventListener
import com.samsung.android.sdk.penremote.ISPenRemoteService
import com.samsung.android.sdk.penremote.SpenEvent as SamSpenEvent

class SPenUnit internal constructor(
    val type: SPenUnitType,
    val remoteService: ISPenRemoteService
): ISpenEventListener.Stub() {

    private var sPenEventListener: SPenEventListener? = null

    internal fun registerSpenEventListener(listener: SPenEventListener) {
        sPenEventListener = listener
        remoteService.registerSpenEventListener(type.code, this)
    }

    fun unregisterSpenEventListener(){
        sPenEventListener?.let {
            remoteService.unregisterSpenEventListener(type.code, this)
        }
    }

    override fun onEvent(event: SamSpenEvent?) {
        event?.let { sPenEventListener?.onEvent(SPenEvent(it)) }
    }
}
