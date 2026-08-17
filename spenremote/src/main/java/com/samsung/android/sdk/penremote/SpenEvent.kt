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

package com.samsung.android.sdk.penremote

import android.os.Parcel
import android.os.Parcelable

data class SpenEvent(val timeStamp: Long, val values: Array<Float>) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        Array(parcel.readInt() + 1) { parcel.readFloat() }
    )
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(timeStamp)
        parcel.writeFloatArray(values.toFloatArray())
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<SpenEvent> {
        override fun createFromParcel(parcel: Parcel): SpenEvent {
            return SpenEvent(parcel)
        }

        override fun newArray(size: Int): Array<SpenEvent?> {
            return arrayOfNulls(size)
        }
    }
}
