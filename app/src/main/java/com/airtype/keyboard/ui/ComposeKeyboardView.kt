package com.airtype.keyboard.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import com.airtype.keyboard.AirTypeImeService

/**
 * Bridge between InputMethodService and Jetpack Compose keyboard UI.
 */
class ComposeKeyboardView(
    context: Context,
    private val ime: AirTypeImeService
) : AbstractComposeView(context) {

    var isUppercase by mutableStateOf(false)
    var isMlKitReady by mutableStateOf(false)
    var isSpenConnected by mutableStateOf(false)
    var lastRecognized by mutableStateOf("")

    @Composable
    override fun Content() {
        AirTypeKeyboard(
            isUppercase = isUppercase,
            isMlKitReady = isMlKitReady,
            isSpenConnected = isSpenConnected,
            lastRecognized = lastRecognized,
            onKey = { text -> ime.commitTextFromKeyboard(text) },
            onBackspace = { ime.deleteSurroundingText(1, 0) },
            onSpace = { ime.commitTextFromKeyboard(" ") },
            onEnter = {
                ime.currentInputConnection?.sendKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)
                )
                ime.currentInputConnection?.sendKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER)
                )
            },
            onToggleShift = {
                isUppercase = !isUppercase
                ime.setUppercase(isUppercase)
            }
        )
    }

    fun updateStatus(uppercase: Boolean, mlReady: Boolean, spen: Boolean, last: String) {
        isUppercase = uppercase
        isMlKitReady = mlReady
        isSpenConnected = spen
        lastRecognized = last
    }
}
