package com.airtype.keyboard.ui

import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceed: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        onEnableKeyboard = {
                            startActivity(android.content.Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        },
                        onSelectKeyboard = {
                            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showInputMethodPicker()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            text = "AirType Settings",
            fontSize = 24.sp,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "AirType is a fully offline S Pen air-writing keyboard.\n\n" +
                    "1. Enable AirType in system keyboard settings\n" +
                    "2. Select AirType as the current input method\n" +
                    "3. Hold the S Pen side button and write letters in the air\n\n" +
                    "Gestures:\n" +
                    "\u2022 Short stroke \u2192 letter\n" +
                    "\u2022 Circle CW \u2192 undo\n" +
                    "\u2022 Circle CCW \u2192 space\n" +
                    "\u2022 Flick left \u2192 backspace\n" +
                    "\u2022 Flick right \u2192 cursor right\n" +
                    "\u2022 Double press \u2192 toggle shift",
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onEnableKeyboard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enable AirType Keyboard")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onSelectKeyboard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select Input Method")
        }
    }
}
