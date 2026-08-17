package com.airtype.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AirTypeKeyboard(
    isUppercase: Boolean,
    isMlKitReady: Boolean,
    isSpenConnected: Boolean,
    lastRecognized: String,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    onToggleShift: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isSpenConnected) "S Pen \u25CF" else "S Pen \u25CB",
                color = if (isSpenConnected) Color(0xFF4CAF50) else Color(0xFF888888),
                fontSize = 12.sp
            )
            Text(
                text = if (lastRecognized.isNotEmpty()) "\u2192 $lastRecognized" else "AirType",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (isMlKitReady) "ML Kit \u25CF" else "Geo only",
                color = if (isMlKitReady) Color(0xFF2196F3) else Color(0xFF888888),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        KeyRow(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"), isUppercase, onKey)
        KeyRow(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"), isUppercase, onKey)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            KeyButton("\u21E7", width = 48.dp, onClick = onToggleShift)
            Spacer(modifier = Modifier.width(4.dp))
            KeyRow(listOf("z", "x", "c", "v", "b", "n", "m"), isUppercase, onKey)
            Spacer(modifier = Modifier.width(4.dp))
            KeyButton("\u232B", width = 48.dp, onClick = onBackspace)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            KeyButton("123", width = 52.dp, onClick = { })
            Spacer(modifier = Modifier.width(6.dp))
            KeyButton("space", width = 160.dp, onClick = onSpace)
            Spacer(modifier = Modifier.width(6.dp))
            KeyButton("\u21B5", width = 52.dp, onClick = onEnter)
        }
    }
}

@Composable
private fun KeyRow(
    keys: List<String>,
    uppercase: Boolean,
    onKey: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        keys.forEach { key ->
            val label = if (uppercase) key.uppercase() else key
            KeyButton(label, onClick = { onKey(label) })
            Spacer(modifier = Modifier.width(3.dp))
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    width: androidx.compose.ui.unit.Dp = 34.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(42.dp)
            .background(Color(0xFF2C2C2C), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = if (label.length > 1) 13.sp else 16.sp,
            textAlign = TextAlign.Center
        )
    }
}
