package com.samsung.android.sdk.penremote;

parcelable SpenEvent;

interface ISpenEventListener {
    void onEvent(in SpenEvent event);
}
