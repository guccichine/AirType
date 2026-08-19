package com.airtype.keyboard.recognition

import android.content.Context
import android.content.SharedPreferences

/**
 * User-configurable mapping from detected air gestures to actions.
 * Defaults match the original design; everything is overridable in Settings.
 */
class GesturePrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAction(gesture: GestureType): SpecialCommand {
        val key = KEY_PREFIX + gesture.name
        val stored = prefs.getString(key, null)
        if (stored != null) {
            return try {
                SpecialCommand.valueOf(stored)
            } catch (_: Exception) {
                defaultAction(gesture)
            }
        }
        return defaultAction(gesture)
    }

    fun setAction(gesture: GestureType, action: SpecialCommand) {
        prefs.edit().putString(KEY_PREFIX + gesture.name, action.name).apply()
    }

    fun resetToDefaults() {
        val editor = prefs.edit()
        GestureType.entries.forEach { g ->
            editor.remove(KEY_PREFIX + g.name)
        }
        editor.apply()
    }

    /** All gestures that the user is allowed to re-map. */
    fun configurableGestures(): List<GestureType> = listOf(
        GestureType.CIRCLE_CW,
        GestureType.CIRCLE_CCW,
        GestureType.FLICK_LEFT,
        GestureType.FLICK_RIGHT
    )

    fun allActions(): List<SpecialCommand> = SpecialCommand.entries.toList()

    fun labelFor(gesture: GestureType): String = when (gesture) {
        GestureType.CIRCLE_CW -> "Circle clockwise"
        GestureType.CIRCLE_CCW -> "Circle counter-clockwise"
        GestureType.FLICK_LEFT -> "Flick left"
        GestureType.FLICK_RIGHT -> "Flick right"
        GestureType.SHORT_LETTER -> "Short stroke (letter)"
        GestureType.LONG_WORD -> "Long stroke"
        GestureType.UNKNOWN -> "Unknown"
    }

    fun labelFor(action: SpecialCommand): String = when (action) {
        SpecialCommand.UNDO -> "Undo / Delete last"
        SpecialCommand.SPACE -> "Space"
        SpecialCommand.ENTER -> "Enter"
        SpecialCommand.BACKSPACE -> "Backspace"
        SpecialCommand.CURSOR_LEFT -> "Cursor left"
        SpecialCommand.CURSOR_RIGHT -> "Cursor right"
        SpecialCommand.TOGGLE_SHIFT -> "Toggle Shift"
    }

    companion object {
        private const val PREFS_NAME = "airtype_gesture_prefs"
        private const val KEY_PREFIX = "gesture_"

        fun defaultAction(gesture: GestureType): SpecialCommand = when (gesture) {
            GestureType.CIRCLE_CW -> SpecialCommand.UNDO
            GestureType.CIRCLE_CCW -> SpecialCommand.SPACE
            GestureType.FLICK_LEFT -> SpecialCommand.BACKSPACE
            GestureType.FLICK_RIGHT -> SpecialCommand.CURSOR_RIGHT
            else -> SpecialCommand.SPACE
        }
    }
}
