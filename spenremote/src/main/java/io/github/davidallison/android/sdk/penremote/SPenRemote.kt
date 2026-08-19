package io.github.davidallison.android.sdk.penremote

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.samsung.android.sdk.penremote.ISPenRemoteService

/**
 * Clean-room open reimplementation of Samsung S Pen Remote connect path.
 * Hardened: soft feature checks, always report failures to the callback.
 */
object SPenRemote {
    const val VERSION_CODE = 16777217
    const val VERSION_NAME = "1.0.2"

    private const val TAG = "SPenRemote"
    private const val SERVICE_CLASS_NAME =
        "com.samsung.android.service.aircommand.remotespen.external.RemoteSpenBindingService"
    private const val AIR_COMMAND_PACKAGE_NAME = "com.samsung.android.service.aircommand"

    private var stateChangeListener: ConnectionStateChangeListener? = null

    @Volatile
    var isConnected: Boolean = false
        private set

    /** Last human-readable failure reason for UI. */
    @Volatile
    var lastErrorMessage: String = ""
        private set

    private var iSpenRemoteService: ISPenRemoteService? = null
    private var connectionResultCallback: ConnectionResultCallback? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
            Log.i(TAG, "Service connected: $className")
            if (service == null) {
                isConnected = false
                lastErrorMessage = "Binder null"
                connectionResultCallback?.onFailure(ConnectionResultCallback.Error.CONNECTION_FAILED)
                return
            }
            iSpenRemoteService = ISPenRemoteService.Stub.asInterface(service)
            SPenUnitManager.instance.remoteService = iSpenRemoteService
            isConnected = true
            lastErrorMessage = ""
            connectionResultCallback?.onSuccess(SPenUnitManager.instance)
            stateChangeListener?.onChange(ConnectionStateChangeListener.State.CONNECTED)
        }

        override fun onServiceDisconnected(className: ComponentName?) {
            Log.i(TAG, "Service disconnected")
            iSpenRemoteService = null
            SPenUnitManager.instance.remoteService = null
            isConnected = false
            lastErrorMessage = "Disconnected"
            stateChangeListener?.onChange(ConnectionStateChangeListener.State.DISCONNECTED_BY_UNKNOWN_REASON)
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.e(TAG, "Null binding from $name")
            isConnected = false
            lastErrorMessage = "Null binding (service refused)"
            connectionResultCallback?.onFailure(ConnectionResultCallback.Error.CONNECTION_FAILED)
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.e(TAG, "Binding died: $name")
            isConnected = false
            lastErrorMessage = "Binding died"
            stateChangeListener?.onChange(ConnectionStateChangeListener.State.DISCONNECTED_BY_UNKNOWN_REASON)
        }
    }

    private var semFeatureList: List<String>? = null

    fun isFeatureEnabled(feature: Feature): Boolean {
        return try {
            if (semFeatureList != null) {
                return semFeatureList!!.any { it.contains(feature.samsungFeatureName, ignoreCase = true) }
            }
            val featureList = FloatingFeatureReflected().getString(SemFeatures.SPEN_FEATURE_LIST)
            semFeatureList = featureList?.split(",")?.map { it.trim() } ?: listOf()
            semFeatureList!!.any { it.contains(feature.samsungFeatureName, ignoreCase = true) }
        } catch (e: Exception) {
            Log.w(TAG, "Feature check failed, assuming available: ${e.message}")
            true // don't block on reflection failures
        }
    }

    fun connect(context: Context, listener: ConnectionResultCallback) {
        connectionResultCallback = listener
        lastErrorMessage = ""

        Log.i(TAG, "connect() v$VERSION_NAME brand=${Build.BRAND} manuf=${Build.MANUFACTURER} model=${Build.MODEL}")

        // Soft Samsung check
        val isSamsung = Build.BRAND.equals("Samsung", true) ||
                Build.MANUFACTURER.equals("Samsung", true)
        if (!isSamsung) {
            lastErrorMessage = "Not a Samsung device"
            Log.e(TAG, lastErrorMessage)
            listener.onFailure(ConnectionResultCallback.Error.UNSUPPORTED_DEVICE)
            return
        }

        // Air Command package must exist
        try {
            context.packageManager.getApplicationInfo(
                AIR_COMMAND_PACKAGE_NAME,
                PackageManager.GET_META_DATA
            )
        } catch (e: PackageManager.NameNotFoundException) {
            lastErrorMessage = "Air Command app missing"
            Log.e(TAG, lastErrorMessage)
            listener.onFailure(ConnectionResultCallback.Error.UNSUPPORTED_DEVICE)
            return
        }

        // Soft feature checks – log warnings but still try to bind
        try {
            val hasBle = FloatingFeatureReflected().getBoolean(SemFeatures.HAS_BLUETOOTH_LOW_ENERGY)
            if (!hasBle) {
                Log.w(TAG, "BLE S Pen feature flag false – still attempting bind")
            }
        } catch (e: Exception) {
            Log.w(TAG, "BLE feature reflection failed: ${e.message}")
        }

        if (!isFeatureEnabled(Feature.FEATURE_TYPE_BUTTON)) {
            Log.w(TAG, "Button feature not listed – still attempting bind")
        }

        @SuppressLint("WrongConstant")
        val intent = Intent().apply {
            setClassName(AIR_COMMAND_PACKAGE_NAME, SERVICE_CLASS_NAME)
            putExtra("binderType", 2)
            putExtra("clientVersion", VERSION_CODE)
            putExtra("clientPackageName", context.packageName)
        }

        try {
            val bound = context.bindService(intent, serviceConnection, BIND_AUTO_CREATE)
            Log.i(TAG, "bindService returned $bound")
            if (!bound) {
                isConnected = false
                lastErrorMessage = "bindService returned false"
                listener.onFailure(ConnectionResultCallback.Error.CONNECTION_FAILED)
            }
            // isConnected set true only in onServiceConnected
        } catch (e: SecurityException) {
            isConnected = false
            lastErrorMessage = "Missing BIND_SPEN_REMOTE permission"
            Log.e(TAG, lastErrorMessage, e)
            listener.onFailure(ConnectionResultCallback.Error.CONNECTION_FAILED)
        } catch (e: Exception) {
            isConnected = false
            lastErrorMessage = "bind failed: ${e.message}"
            Log.e(TAG, lastErrorMessage, e)
            listener.onFailure(ConnectionResultCallback.Error.CONNECTION_FAILED)
        }
    }

    fun setConnectionStateChangeListener(listener: ConnectionStateChangeListener?) {
        this.stateChangeListener = listener
    }

    fun disconnect(context: Context) {
        if (!isConnected && iSpenRemoteService == null) return
        Log.i(TAG, "disconnect()")
        try {
            SPenUnitManager.instance.clearListeners()
            context.unbindService(serviceConnection)
        } catch (e: Exception) {
            Log.w(TAG, "unbind: ${e.message}")
        }
        iSpenRemoteService = null
        SPenUnitManager.instance.remoteService = null
        isConnected = false
        stateChangeListener?.onChange(ConnectionStateChangeListener.State.DISCONNECTED)
    }

    private enum class SemFeatures(val feature: String) {
        SPEN_FEATURE_LIST("SEC_FLOATING_FEATURE_COMMON_CONFIG_BLE_SPEN_SPEC"),
        HAS_BLUETOOTH_LOW_ENERGY("SEC_FLOATING_FEATURE_COMMON_SUPPORT_BLE_SPEN"),
    }

    enum class Feature(val code: Int, val samsungFeatureName: String) {
        FEATURE_TYPE_BUTTON(0, "button"),
        FEATURE_TYPE_AIR_MOTION(1, "airmotion"),
    }

    interface ConnectionStateChangeListener {
        fun onChange(state: State)
        enum class State(val code: Int) {
            CONNECTED(0),
            DISCONNECTED(-1),
            DISCONNECTED_BY_UNKNOWN_REASON(-2),
        }
    }

    interface ConnectionResultCallback {
        fun onSuccess(unitManager: SPenUnitManager)
        fun onFailure(code: Error)
        enum class Error(val code: Int) {
            UNSUPPORTED_DEVICE(-1),
            CONNECTION_FAILED(-2),
            UNKNOWN(-100),
        }
    }

    private class FloatingFeatureReflected {
        val className = "com.samsung.android.feature.SemFloatingFeature"
        var clazz: Class<*>? = null
        var instance: Any? = null

        init {
            try {
                val classLoader = ClassLoader.getSystemClassLoader()
                clazz = classLoader.loadClass(className)
                instance = clazz?.getDeclaredMethod("getInstance")?.invoke(null)
            } catch (e: Exception) {
                Log.w(TAG, "SemFloatingFeature unavailable: ${e.message}")
            }
        }

        fun getString(feature: SemFeatures): String? {
            return try {
                clazz?.getDeclaredMethod("getString", String::class.java)
                    ?.invoke(instance, feature.feature) as String?
            } catch (e: Exception) {
                null
            }
        }

        fun getBoolean(feature: SemFeatures): Boolean {
            return try {
                clazz?.getDeclaredMethod(
                    "getBoolean",
                    String::class.java,
                    Boolean::class.javaPrimitiveType
                )?.invoke(instance, feature.feature, false) as Boolean? ?: false
            } catch (e: Exception) {
                false
            }
        }
    }
}
