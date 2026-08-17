package com.samsung.android.sdk.penremote;

import com.samsung.android.sdk.penremote.ISpenEventListener;

interface ISPenRemoteService {
   void registerSpenEventListener(int type, ISpenEventListener listener);
   void unregisterSpenEventListener(int type, ISpenEventListener listener);
}
