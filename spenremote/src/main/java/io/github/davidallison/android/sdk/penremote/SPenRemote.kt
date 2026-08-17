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

object SPenRemote {
    const val VERSION_CODE = 16777217
    const val VERSION_NAME = "1.0.1"

    private const val SERVICE_CLASS_NAME =
        "com.samsung.android.service.aircommand.remotespen.external.RemoteSpenBindingService"
    private const val AIR_COMMAND_PACKAGE_NAME = "com.samsung.android.service.aircommand"

    private var stateChangeListener: ConnectionStateChangeListener? = null

    var isConnected: Boolean = false
        private set

    private var iSpenRemoteService: ISPenRemoteService? = null
    private var connectionResultCallback: ConnectionResultCallback? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
            Log.i("Spen", "Service connected")
            if (service == null) {
                connectionResultCallback?.onFailure(ConnectionResultCallback.Error.CONNECTION_FAILED)
                return
            }
            iSpenRemoteService = ISPenRemoteService.Stub.asInterface(service)
            SPenUnitManager.instance.remoteService = iSpenRemoteService
            connectionResultCallback?.onSuccess(SPenUnitManager.instance)
            stateChangeListener?.onChange(ConnectionStateChangeListener.State.CONNECTED)
        }

        override fun onServiceDisconnected(className: ComponentName?) {
            Log.i("Spen", "Service disconnected")
            iSpenRemoteService = null
            SPenUnitManager.instance.remoteService = null
            isConnected = false
            stateChangeListener?.onChange(ConnectionStateChangeListener.State.DISCONNECTED_BY_UNKNOWN_REASON)
        }
    }

    private var semFeatureList: List<String>? = null

    fun isFeatureEnabled(feature: Feature): Boolean {
        if (semFeatureList != null) {
            return semFeatureList!!.contains(feature.samsungFeatureName)
        }
        val featureList = FloatingFeatureReflected().getString(SemFeatures.SPEN_FEATURE_LIST)
        semFeatureList = featureList?.split(",") ?: listOf()
        return isFeatureEnabled(feature)
    }

    fun connect(context: Context, listener: ConnectionResultCallback) {
        fun unsupported() {
            Log.e("SPen", "Unsupported device")
            listener.onFailure(ConnectionResultCallback.Error.UNSUPPORTED_DEVICE)
        }

        Log.i("Spen", VERSION_NAME)

        if (!(Build.BRAND.equals("Samsung", true) &&
                    Build.MANUFACTURER.equals("Samsung", true))
        ) {
            unsupported()
            return
        }

        try {
            context.packageManager.getApplicationInfo(
                AIR_COMMAND_PACKAGE_NAME,
                PackageManager.GET_META_DATA
            )
        } catch (e: PackageManager.NameNotFoundException) {
            unsupported()
            return
        }

        if (!FloatingFeatureReflected().getBoolean(SemFeatures.HAS_BLUETOOTH_LOW_ENERGY)) {
            unsupported()
            return
        }

        if (!isFeatureEnabled(Feature.FEATURE_TYPE_BUTTON)) {
            unsupported()
            return
        }

        connectionResultCallback = listener

        @SuppressLint("WrongConstant")
        val intent = Intent().apply {
            flags = BIND_AUTO_CREATE
            setClassName(AIR_COMMAND_PACKAGE_NAME, SERVICE_CLASS_NAME)
            putExtra("binderType", 2)
            putExtra("clientVersion", VERSION_CODE)
            putExtra("clientPackageName", context.packageName)
        }

        try {
            context.bindService(intent, serviceConnection, BIND_AUTO_CREATE)
            isConnected = true
        } catch (e: SecurityException) {
            Log.e("Spen", "Permission com.samsung.android.sdk.penremote.BIND_SPEN_REMOTE is required")
        }
    }

    fun setConnectionStateChangeListener(listener: ConnectionStateChangeListener) {
        this.stateChangeListener = listener
    }

    fun disconnect(context: Context) {
        if (!isConnected) return
        Log.i("Spen", "Service is disconnecting")
        SPenUnitManager.instance.clearListeners()
        context.unbindService(serviceConnection)
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
        var clazz: Class<*>?
        var instance: Any?

        init {
            val classLoader = ClassLoader.getSystemClassLoader()
            clazz = classLoader.loadClass(className)
            instance = clazz?.getDeclaredMethod("getInstance")?.invoke(null, *arrayOf())
        }

        fun getString(feature: SemFeatures): String? {
            return clazz?.getDeclaredMethod("getString", java.lang.String::class.java)
                ?.invoke(instance, feature.feature) as String?
        }

        fun getBoolean(feature: SemFeatures): Boolean {
            return clazz?.getDeclaredMethod(
                "getBoolean",
                java.lang.String::class.java,
                Boolean::class.javaPrimitiveType
            )?.invoke(instance, feature.feature, false) as Boolean? ?: false
        }
    }
}
