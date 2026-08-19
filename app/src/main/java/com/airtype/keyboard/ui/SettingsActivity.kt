package com.airtype.keyboard.ui

import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airtype.keyboard.recognition.GesturePrefs
import com.airtype.keyboard.recognition.GestureType
import com.airtype.keyboard.recognition.SpecialCommand

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gesturePrefs = GesturePrefs(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        gesturePrefs = gesturePrefs,
                        onEnableKeyboard = {
                            startActivity(android.content.Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        },
                        onSelectKeyboard = {
                            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showInputMethodPicker()
                        },
                        onResetGestures = {
                            gesturePrefs.resetToDefaults()
                            Toast.makeText(this, "Gestures reset to defaults", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    gesturePrefs: GesturePrefs,
    onEnableKeyboard: () -> Unit,
    onSelectKeyboard: () -> Unit,
    onResetGestures: () -> Unit
) {
    // Force recomposition when mappings change
    var version by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "AirType Settings",
            fontSize = 24.sp,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Fully offline S Pen air-writing keyboard.\n\n" +
                    "1. Enable AirType in system keyboard settings\n" +
                    "2. Select AirType as the current input method\n" +
                    "3. Hold the S Pen side button and write in the air",
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onEnableKeyboard, modifier = Modifier.fillMaxWidth()) {
            Text("Enable AirType Keyboard")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onSelectKeyboard, modifier = Modifier.fillMaxWidth()) {
            Text("Select Input Method")
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "Gesture Mapping",
            fontSize = 18.sp,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Tap a gesture to change what it does. Changes apply immediately.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Re-read prefs each time version changes
        val forceRecompose = version
        gesturePrefs.configurableGestures().forEach { gesture ->
            GestureRow(
                label = gesturePrefs.labelFor(gesture),
                currentAction = gesturePrefs.getAction(gesture),
                allActions = gesturePrefs.allActions(),
                actionLabel = { gesturePrefs.labelFor(it) },
                onSelect = { action ->
                    gesturePrefs.setAction(gesture, action)
                    version++
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = {
            onResetGestures()
            version++
        }) {
            Text("Reset gestures to defaults")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "On-screen keyboard",
            fontSize = 18.sp,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "The fallback QWERTY includes Shift, Space, Enter and ⌫ Backspace. " +
                    "Use it when you need precise input or the S Pen is not available.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Tips for better recognition",
            fontSize = 18.sp,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "• Write one letter at a time with a clear stroke\n" +
                    "• Keep the S Pen roughly facing the screen\n" +
                    "• Wait for “ML Kit ●” in the status bar (model downloaded)\n" +
                    "• First use needs Wi-Fi once to download the ~20 MB model",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GestureRow(
    label: String,
    currentAction: SpecialCommand,
    allActions: List<SpecialCommand>,
    actionLabel: (SpecialCommand) -> String,
    onSelect: (SpecialCommand) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 15.sp)
            Text(
                text = actionLabel(currentAction),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(text = "▾", fontSize = 16.sp)

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            allActions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(actionLabel(action)) },
                    onClick = {
                        onSelect(action)
                        expanded = false
                    }
                )
            }
        }
    }
}
