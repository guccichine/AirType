package com.airtype.keyboard

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import com.airtype.keyboard.recognition.RecognitionResult
import com.airtype.keyboard.recognition.SpecialCommand
import com.airtype.keyboard.recognition.StrokeRecognizer
import com.airtype.keyboard.ui.ComposeKeyboardView
import io.github.davidallison.android.sdk.penremote.AirMotionEvent
import io.github.davidallison.android.sdk.penremote.ButtonEvent
import io.github.davidallison.android.sdk.penremote.SPenEventListener
import io.github.davidallison.android.sdk.penremote.SPenRemote
import io.github.davidallison.android.sdk.penremote.SPenUnit
import io.github.davidallison.android.sdk.penremote.SPenUnitManager
import io.github.davidallison.android.sdk.penremote.SPenUnitType
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.hypot

class AirTypeImeService : InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    companion object {
        private const val TAG = "AirTypeIME"
        private const val MAX_STROKE_POINTS = 2048
        private const val DOUBLE_PRESS_TIMEOUT_MS = 350L
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var unitManager: SPenUnitManager? = null
    private var buttonUnit: SPenUnit? = null
    private var airMotionUnit: SPenUnit? = null
    @Volatile private var isSpenConnected: Boolean = false
    @Volatile private var isTracking: Boolean = false
    private val currentStroke = CopyOnWriteArrayList<Pair<Float, Float>>()
    private val absolutePath = mutableListOf<Pair<Float, Float>>()
    private var currentX = 0f
    private var currentY = 0f
    private var lastButtonUpTime = 0L
    private var pendingSinglePress = false
    @Volatile private var isUppercase = false
    private lateinit var strokeRecognizer: StrokeRecognizer
    private var keyboardView: ComposeKeyboardView? = null
    private var lastRecognizedText = ""

    private val buttonEventListener = SPenEventListener { event ->
        handleButtonEvent(ButtonEvent(event))
    }
    private val airMotionEventListener = SPenEventListener { event ->
        handleAirMotionEvent(AirMotionEvent(event))
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        strokeRecognizer = StrokeRecognizer(this)
        Log.i(TAG, "AirTypeImeService onCreate")
        connectSpenRemote()
    }

    override fun onBindInput() {
        super.onBindInput()
        if (!isSpenConnected) connectSpenRemote()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        if (isSpenConnected) registerSpenListeners()
        updateKeyboardStatus()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        if (isTracking) finishStrokeAndRecognize()
        unregisterSpenListeners()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        Log.i(TAG, "AirTypeImeService onDestroy")
        unregisterSpenListeners()
        disconnectSpenRemote()
        strokeRecognizer.close()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        val view = ComposeKeyboardView(this, this)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        keyboardView = view
        updateKeyboardStatus()
        return view
    }

    private fun connectSpenRemote() {
        if (SPenRemote.isConnected) return
        Log.i(TAG, "Connecting to S Pen Remote...")
        SPenRemote.connect(this, object : SPenRemote.ConnectionResultCallback {
            override fun onSuccess(manager: SPenUnitManager) {
                Log.i(TAG, "S Pen Remote connected")
                unitManager = manager
                isSpenConnected = true
                try {
                    buttonUnit = manager.getUnit(SPenUnitType.TYPE_BUTTON)
                    airMotionUnit = manager.getUnit(SPenUnitType.TYPE_AIR_MOTION)
                    registerSpenListeners()
                    updateKeyboardStatus()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to obtain units", e)
                }
            }
            override fun onFailure(code: SPenRemote.ConnectionResultCallback.Error) {
                isSpenConnected = false
                Log.w(TAG, "S Pen connection failed: $code")
                updateKeyboardStatus()
            }
        })
    }

    private fun disconnectSpenRemote() {
        if (!SPenRemote.isConnected) return
        try { SPenRemote.disconnect(this) } catch (_: Exception) {}
        isSpenConnected = false
        unitManager = null
        buttonUnit = null
        airMotionUnit = null
    }

    private fun registerSpenListeners() {
        val manager = unitManager ?: return
        val btn = buttonUnit ?: return
        val air = airMotionUnit ?: return
        try {
            manager.registerSPenEventListener(btn, buttonEventListener)
            manager.registerSPenEventListener(air, airMotionEventListener)
        } catch (e: Exception) {
            Log.e(TAG, "Register listeners failed", e)
        }
    }

    private fun unregisterSpenListeners() {
        val manager = unitManager ?: return
        try {
            buttonUnit?.let { manager.unregisterSpenEventListener(it) }
            airMotionUnit?.let { manager.unregisterSpenEventListener(it) }
        } catch (_: Exception) {}
    }

    private fun handleButtonEvent(buttonEvent: ButtonEvent) {
        when (buttonEvent.action) {
            ButtonEvent.ButtonAction.ACTION_DOWN -> {
                val now = System.currentTimeMillis()
                if (now - lastButtonUpTime < DOUBLE_PRESS_TIMEOUT_MS && pendingSinglePress) {
                    pendingSinglePress = false
                    isUppercase = !isUppercase
                    Log.i(TAG, "Double press -> uppercase=$isUppercase")
                    updateKeyboardStatus()
                    return
                }
                startStroke()
            }
            ButtonEvent.ButtonAction.ACTION_UP -> {
                lastButtonUpTime = System.currentTimeMillis()
                pendingSinglePress = true
                finishStrokeAndRecognize()
            }
        }
    }

    private fun handleAirMotionEvent(ev: AirMotionEvent) {
        if (!isTracking) return
        val dx = ev.deltaX
        val dy = ev.deltaY
        if (hypot(dx.toDouble(), dy.toDouble()) < 0.001) return
        if (currentStroke.size >= MAX_STROKE_POINTS) {
            finishStrokeAndRecognize()
            return
        }
        currentStroke.add(dx to dy)
        currentX += dx
        currentY += dy
        absolutePath.add(currentX to currentY)
    }

    private fun startStroke() {
        isTracking = true
        currentStroke.clear()
        absolutePath.clear()
        currentX = 0f
        currentY = 0f
    }

    private fun finishStrokeAndRecognize() {
        if (!isTracking) return
        isTracking = false
        val points = absolutePath.toList()
        currentStroke.clear()
        absolutePath.clear()
        if (points.size < 3) return
        // Pre-context helps ML Kit disambiguate letters (n/u, etc.)
        val preContext = try {
            currentInputConnection?.getTextBeforeCursor(20, 0)?.toString() ?: ""
        } catch (_: Exception) { "" }
        val result = strokeRecognizer.recognize(points, isUppercase, preContext)
        when (result) {
            is RecognitionResult.Text -> {
                commitText(result.text)
                lastRecognizedText = result.text
                if (isUppercase && result.text.length == 1 && result.text[0].isUpperCase()) {
                    isUppercase = false
                }
                updateKeyboardStatus()
            }
            is RecognitionResult.Command -> {
                executeCommand(result.command)
                lastRecognizedText = result.command.name.lowercase()
                updateKeyboardStatus()
            }
            RecognitionResult.None -> {
                lastRecognizedText = "?"
                updateKeyboardStatus()
                android.util.Log.w("AirTypeIME", "Recognition returned None (${points.size} pts)")
            }
        }
    }

    private fun executeCommand(command: SpecialCommand) {
        val ic = currentInputConnection ?: return
        when (command) {
            SpecialCommand.BACKSPACE -> ic.deleteSurroundingText(1, 0)
            SpecialCommand.CURSOR_LEFT -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
            }
            SpecialCommand.CURSOR_RIGHT -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
            }
            SpecialCommand.SPACE -> ic.commitText(" ", 1)
            SpecialCommand.ENTER -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            SpecialCommand.UNDO -> ic.deleteSurroundingText(1, 0)
            SpecialCommand.TOGGLE_SHIFT -> {
                isUppercase = !isUppercase
                updateKeyboardStatus()
            }
        }
        Log.i(TAG, "Command: $command")
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
        Log.i(TAG, "Committed: \"$text\"")
    }

    fun commitTextFromKeyboard(text: String) = commitText(text)

    fun deleteSurroundingText(before: Int, after: Int) {
        currentInputConnection?.deleteSurroundingText(before, after)
    }

    fun setUppercase(value: Boolean) {
        isUppercase = value
        updateKeyboardStatus()
    }

    private fun updateKeyboardStatus() {
        keyboardView?.updateStatus(
            uppercase = isUppercase,
            mlReady = strokeRecognizer.isMlKitReady,
            spen = isSpenConnected,
            last = lastRecognizedText
        )
    }
}
